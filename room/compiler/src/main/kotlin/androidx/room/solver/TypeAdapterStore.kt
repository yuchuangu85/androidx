/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.solver

import androidx.room.ext.CommonTypeNames
import androidx.room.ext.GuavaBaseTypeNames
import androidx.room.ext.isEntityElement
import androidx.room.parser.ParsedQuery
import androidx.room.parser.SQLTypeAffinity
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.asDeclaredType
import androidx.room.compiler.processing.isArray
import androidx.room.compiler.processing.isDeclared
import androidx.room.ext.isNotByte
import androidx.room.processor.Context
import androidx.room.processor.EntityProcessor
import androidx.room.processor.FieldProcessor
import androidx.room.processor.PojoProcessor
import androidx.room.solver.binderprovider.CoroutineFlowResultBinderProvider
import androidx.room.solver.binderprovider.CursorQueryResultBinderProvider
import androidx.room.solver.binderprovider.DataSourceFactoryQueryResultBinderProvider
import androidx.room.solver.binderprovider.DataSourceQueryResultBinderProvider
import androidx.room.solver.binderprovider.GuavaListenableFutureQueryResultBinderProvider
import androidx.room.solver.binderprovider.InstantQueryResultBinderProvider
import androidx.room.solver.binderprovider.LiveDataQueryResultBinderProvider
import androidx.room.solver.binderprovider.PagingSourceQueryResultBinderProvider
import androidx.room.solver.binderprovider.RxCallableQueryResultBinderProvider
import androidx.room.solver.binderprovider.RxQueryResultBinderProvider
import androidx.room.solver.prepared.binder.InstantPreparedQueryResultBinder
import androidx.room.solver.prepared.binder.PreparedQueryResultBinder
import androidx.room.solver.prepared.binderprovider.GuavaListenableFuturePreparedQueryResultBinderProvider
import androidx.room.solver.prepared.binderprovider.InstantPreparedQueryResultBinderProvider
import androidx.room.solver.prepared.binderprovider.PreparedQueryResultBinderProvider
import androidx.room.solver.prepared.binderprovider.RxPreparedQueryResultBinderProvider
import androidx.room.solver.prepared.result.PreparedQueryResultAdapter
import androidx.room.solver.query.parameter.ArrayQueryParameterAdapter
import androidx.room.solver.query.parameter.BasicQueryParameterAdapter
import androidx.room.solver.query.parameter.CollectionQueryParameterAdapter
import androidx.room.solver.query.parameter.QueryParameterAdapter
import androidx.room.solver.query.result.ArrayQueryResultAdapter
import androidx.room.solver.query.result.EntityRowAdapter
import androidx.room.solver.query.result.GuavaOptionalQueryResultAdapter
import androidx.room.solver.query.result.ImmutableListQueryResultAdapter
import androidx.room.solver.query.result.InstantQueryResultBinder
import androidx.room.solver.query.result.ListQueryResultAdapter
import androidx.room.solver.query.result.OptionalQueryResultAdapter
import androidx.room.solver.query.result.PojoRowAdapter
import androidx.room.solver.query.result.QueryResultAdapter
import androidx.room.solver.query.result.QueryResultBinder
import androidx.room.solver.query.result.RowAdapter
import androidx.room.solver.query.result.SingleColumnRowAdapter
import androidx.room.solver.query.result.SingleEntityQueryResultAdapter
import androidx.room.solver.shortcut.binder.DeleteOrUpdateMethodBinder
import androidx.room.solver.shortcut.binder.InsertMethodBinder
import androidx.room.solver.shortcut.binder.InstantDeleteOrUpdateMethodBinder
import androidx.room.solver.shortcut.binder.InstantInsertMethodBinder
import androidx.room.solver.shortcut.binderprovider.DeleteOrUpdateMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.GuavaListenableFutureDeleteOrUpdateMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.GuavaListenableFutureInsertMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.InsertMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.InstantDeleteOrUpdateMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.InstantInsertMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.RxCallableDeleteOrUpdateMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.RxCallableInsertMethodBinderProvider
import androidx.room.solver.shortcut.result.DeleteOrUpdateMethodAdapter
import androidx.room.solver.shortcut.result.InsertMethodAdapter
import androidx.room.solver.types.BoxedBooleanToBoxedIntConverter
import androidx.room.solver.types.BoxedPrimitiveColumnTypeAdapter
import androidx.room.solver.types.ByteArrayColumnTypeAdapter
import androidx.room.solver.types.ByteBufferColumnTypeAdapter
import androidx.room.solver.types.ColumnTypeAdapter
import androidx.room.solver.types.CompositeAdapter
import androidx.room.solver.types.CompositeTypeConverter
import androidx.room.solver.types.CursorValueReader
import androidx.room.solver.types.NoOpConverter
import androidx.room.solver.types.PrimitiveBooleanToIntConverter
import androidx.room.solver.types.PrimitiveColumnTypeAdapter
import androidx.room.solver.types.StatementValueBinder
import androidx.room.solver.types.StringColumnTypeAdapter
import androidx.room.solver.types.TypeConverter
import androidx.room.vo.ShortcutQueryParameter
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import java.util.LinkedList

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
/**
 * Holds all type adapters and can create on demand composite type adapters to convert a type into a
 * database column.
 */
class TypeAdapterStore private constructor(
    val context: Context,
    /**
     * first type adapter has the highest priority
     */
    private val columnTypeAdapters: List<ColumnTypeAdapter>,
    /**
     * first converter has the highest priority
     */
    private val typeConverters: List<TypeConverter>
) {

    companion object {
        fun copy(context: Context, store: TypeAdapterStore): TypeAdapterStore {
            return TypeAdapterStore(context = context,
                    columnTypeAdapters = store.columnTypeAdapters,
                    typeConverters = store.typeConverters)
        }

        fun create(context: Context, vararg extras: Any): TypeAdapterStore {
            val adapters = arrayListOf<ColumnTypeAdapter>()
            val converters = arrayListOf<TypeConverter>()

            fun addAny(extra: Any?) {
                when (extra) {
                    is TypeConverter -> converters.add(extra)
                    is ColumnTypeAdapter -> adapters.add(extra)
                    is List<*> -> extra.forEach(::addAny)
                    else -> throw IllegalArgumentException("unknown extra $extra")
                }
            }

            extras.forEach(::addAny)
            fun addTypeConverter(converter: TypeConverter) {
                converters.add(converter)
            }

            fun addColumnAdapter(adapter: ColumnTypeAdapter) {
                adapters.add(adapter)
            }

            val primitives = PrimitiveColumnTypeAdapter
                    .createPrimitiveAdapters(context.processingEnv)
            primitives.forEach(::addColumnAdapter)
            BoxedPrimitiveColumnTypeAdapter
                    .createBoxedPrimitiveAdapters(primitives)
                    .forEach(::addColumnAdapter)
            addColumnAdapter(StringColumnTypeAdapter(context.processingEnv))
            addColumnAdapter(ByteArrayColumnTypeAdapter(context.processingEnv))
            addColumnAdapter(ByteBufferColumnTypeAdapter(context.processingEnv))
            PrimitiveBooleanToIntConverter.create(context.processingEnv).forEach(::addTypeConverter)
            BoxedBooleanToBoxedIntConverter.create(context.processingEnv)
                    .forEach(::addTypeConverter)
            return TypeAdapterStore(context = context, columnTypeAdapters = adapters,
                    typeConverters = converters)
        }
    }

    val queryResultBinderProviders: List<QueryResultBinderProvider> =
        mutableListOf<QueryResultBinderProvider>().apply {
            add(CursorQueryResultBinderProvider(context))
            add(LiveDataQueryResultBinderProvider(context))
            add(GuavaListenableFutureQueryResultBinderProvider(context))
            addAll(RxQueryResultBinderProvider.getAll(context))
            addAll(RxCallableQueryResultBinderProvider.getAll(context))
            add(DataSourceQueryResultBinderProvider(context))
            add(DataSourceFactoryQueryResultBinderProvider(context))
            add(PagingSourceQueryResultBinderProvider(context))
            add(CoroutineFlowResultBinderProvider(context))
            add(InstantQueryResultBinderProvider(context))
        }

    val preparedQueryResultBinderProviders: List<PreparedQueryResultBinderProvider> =
        mutableListOf<PreparedQueryResultBinderProvider>().apply {
            addAll(RxPreparedQueryResultBinderProvider.getAll(context))
            add(GuavaListenableFuturePreparedQueryResultBinderProvider(context))
            add(InstantPreparedQueryResultBinderProvider(context))
        }

    val insertBinderProviders: List<InsertMethodBinderProvider> =
        mutableListOf<InsertMethodBinderProvider>().apply {
            addAll(RxCallableInsertMethodBinderProvider.getAll(context))
            add(GuavaListenableFutureInsertMethodBinderProvider(context))
            add(InstantInsertMethodBinderProvider(context))
        }

    val deleteOrUpdateBinderProvider: List<DeleteOrUpdateMethodBinderProvider> =
        mutableListOf<DeleteOrUpdateMethodBinderProvider>().apply {
            addAll(RxCallableDeleteOrUpdateMethodBinderProvider.getAll(context))
            add(GuavaListenableFutureDeleteOrUpdateMethodBinderProvider(context))
            add(InstantDeleteOrUpdateMethodBinderProvider(context))
        }

    // type mirrors that be converted into columns w/o an extra converter
    private val knownColumnTypeMirrors by lazy {
        columnTypeAdapters.map { it.out }
    }

    /**
     * Searches 1 way to bind a value into a statement.
     */
    fun findStatementValueBinder(
        input: XType,
        affinity: SQLTypeAffinity?
    ): StatementValueBinder? {
        if (input.isError()) {
            return null
        }
        val adapter = findDirectAdapterFor(input, affinity)
        if (adapter != null) {
            return adapter
        }
        val targetTypes = targetTypeMirrorsFor(affinity)
        val binder = findTypeConverter(input, targetTypes) ?: return null
        // columnAdapter should not be null but we are receiving errors on crash in `first()` so
        // this safeguard allows us to dispatch the real problem to the user (e.g. why we couldn't
        // find the right adapter)
        val columnAdapter = getAllColumnAdapters(binder.to).firstOrNull() ?: return null
        return CompositeAdapter(input, columnAdapter, binder, null)
    }

    /**
     * Returns which entities targets the given affinity.
     */
    private fun targetTypeMirrorsFor(affinity: SQLTypeAffinity?): List<XType> {
        val specifiedTargets = affinity?.getTypeMirrors(context.processingEnv)
        return if (specifiedTargets == null || specifiedTargets.isEmpty()) {
            knownColumnTypeMirrors
        } else {
            specifiedTargets
        }
    }

    /**
     * Searches 1 way to read it from cursor
     */
    fun findCursorValueReader(output: XType, affinity: SQLTypeAffinity?): CursorValueReader? {
        if (output.isError()) {
            return null
        }
        val adapter = findColumnTypeAdapter(output, affinity)
        if (adapter != null) {
            // two way is better
            return adapter
        }
        // we could not find a two way version, search for anything
        val targetTypes = targetTypeMirrorsFor(affinity)
        val converter = findTypeConverter(targetTypes, output) ?: return null
        return CompositeAdapter(output,
                getAllColumnAdapters(converter.from).first(), null, converter)
    }

    /**
     * Tries to reverse the converter going through the same nodes, if possible.
     */
    @VisibleForTesting
    fun reverse(converter: TypeConverter): TypeConverter? {
        return when (converter) {
            is NoOpConverter -> converter
            is CompositeTypeConverter -> {
                val r1 = reverse(converter.conv1) ?: return null
                val r2 = reverse(converter.conv2) ?: return null
                CompositeTypeConverter(r2, r1)
            }
            else -> {
                typeConverters.firstOrNull {
                    it.from.isSameType(converter.to) &&
                            it.to.isSameType(converter.from)
                }
            }
        }
    }

    /**
     * Finds a two way converter, if you need 1 way, use findStatementValueBinder or
     * findCursorValueReader.
     */
    fun findColumnTypeAdapter(out: XType, affinity: SQLTypeAffinity?): ColumnTypeAdapter? {
        if (out.isError()) {
            return null
        }
        val adapter = findDirectAdapterFor(out, affinity)
        if (adapter != null) {
            return adapter
        }
        val targetTypes = targetTypeMirrorsFor(affinity)
        val intoStatement = findTypeConverter(out, targetTypes) ?: return null
        // ok found a converter, try the reverse now
        val fromCursor = reverse(intoStatement) ?: findTypeConverter(intoStatement.to, out)
        ?: return null
        return CompositeAdapter(out, getAllColumnAdapters(intoStatement.to).first(), intoStatement,
                fromCursor)
    }

    private fun findDirectAdapterFor(
        out: XType,
        affinity: SQLTypeAffinity?
    ): ColumnTypeAdapter? {
        return getAllColumnAdapters(out).firstOrNull {
            affinity == null || it.typeAffinity == affinity
        }
    }

    fun findTypeConverter(input: XType, output: XType): TypeConverter? {
        return findTypeConverter(listOf(input), listOf(output))
    }

    fun findDeleteOrUpdateMethodBinder(typeMirror: XType): DeleteOrUpdateMethodBinder {
        val adapter = findDeleteOrUpdateAdapter(typeMirror)
        return if (typeMirror.isDeclared()) {
            deleteOrUpdateBinderProvider.first {
                it.matches(typeMirror)
            }.provide(typeMirror)
        } else {
            InstantDeleteOrUpdateMethodBinder(adapter)
        }
    }

    fun findInsertMethodBinder(
        typeMirror: XType,
        params: List<ShortcutQueryParameter>
    ): InsertMethodBinder {
        return if (typeMirror.isDeclared()) {
            insertBinderProviders.first {
                it.matches(typeMirror)
            }.provide(typeMirror, params)
        } else {
            InstantInsertMethodBinder(findInsertAdapter(typeMirror, params))
        }
    }

    fun findQueryResultBinder(typeMirror: XType, query: ParsedQuery): QueryResultBinder {
        return if (typeMirror.isDeclared()) {
            return queryResultBinderProviders.first {
                it.matches(typeMirror)
            }.provide(typeMirror, query)
        } else {
            InstantQueryResultBinder(findQueryResultAdapter(typeMirror, query))
        }
    }

    fun findPreparedQueryResultBinder(
        typeMirror: XType,
        query: ParsedQuery
    ): PreparedQueryResultBinder {
        return if (typeMirror.isDeclared()) {
            return preparedQueryResultBinderProviders.first {
                it.matches(typeMirror)
            }.provide(typeMirror, query)
        } else {
            InstantPreparedQueryResultBinder(findPreparedQueryResultAdapter(typeMirror, query))
        }
    }

    fun findPreparedQueryResultAdapter(typeMirror: XType, query: ParsedQuery) =
        PreparedQueryResultAdapter.create(typeMirror, query.type)

    fun findDeleteOrUpdateAdapter(typeMirror: XType): DeleteOrUpdateMethodAdapter? {
        return DeleteOrUpdateMethodAdapter.create(typeMirror)
    }

    fun findInsertAdapter(
        typeMirror: XType,
        params: List<ShortcutQueryParameter>
    ): InsertMethodAdapter? {
        return InsertMethodAdapter.create(typeMirror, params)
    }

    fun findQueryResultAdapter(typeMirror: XType, query: ParsedQuery): QueryResultAdapter? {
        if (typeMirror.isError()) {
            return null
        }
        if (typeMirror.isDeclared()) {
            if (typeMirror.typeArguments.isEmpty()) {
                val rowAdapter = findRowAdapter(typeMirror, query) ?: return null
                return SingleEntityQueryResultAdapter(rowAdapter)
            } else if (typeMirror.rawType.typeName == GuavaBaseTypeNames.OPTIONAL) {
                // Handle Guava Optional by unpacking its generic type argument and adapting that.
                // The Optional adapter will reappend the Optional type.
                val typeArg = typeMirror.typeArguments.first()
                val rowAdapter = findRowAdapter(typeArg, query) ?: return null
                return GuavaOptionalQueryResultAdapter(SingleEntityQueryResultAdapter(rowAdapter))
            } else if (typeMirror.rawType.typeName == CommonTypeNames.OPTIONAL) {
                // Handle java.util.Optional similarly.
                val typeArg = typeMirror.typeArguments.first()
                val rowAdapter = findRowAdapter(typeArg, query) ?: return null
                return OptionalQueryResultAdapter(SingleEntityQueryResultAdapter(rowAdapter))
            } else if (typeMirror.isTypeOf(ImmutableList::class)) {
                val typeArg = typeMirror.typeArguments.first().extendsBoundOrSelf()
                val rowAdapter = findRowAdapter(typeArg, query) ?: return null
                return ImmutableListQueryResultAdapter(rowAdapter)
            } else if (typeMirror.isTypeOf(java.util.List::class)) {
                val typeArg = typeMirror.typeArguments.first().extendsBoundOrSelf()
                val rowAdapter = findRowAdapter(typeArg, query) ?: return null
                return ListQueryResultAdapter(rowAdapter)
            }
            return null
        } else if (typeMirror.isArray() && typeMirror.componentType.isNotByte()) {
            val rowAdapter =
                    findRowAdapter(typeMirror.componentType, query) ?: return null
            return ArrayQueryResultAdapter(rowAdapter)
        } else {
            val rowAdapter = findRowAdapter(typeMirror, query) ?: return null
            return SingleEntityQueryResultAdapter(rowAdapter)
        }
    }

    /**
     * Find a converter from cursor to the given type mirror.
     * If there is information about the query result, we try to use it to accept *any* POJO.
     */
    fun findRowAdapter(typeMirror: XType, query: ParsedQuery): RowAdapter? {
        if (typeMirror.isError()) {
            return null
        }
        if (typeMirror.isDeclared()) {
            if (typeMirror.typeArguments.isNotEmpty()) {
                // TODO one day support this
                return null
            }
            val resultInfo = query.resultInfo

            val (rowAdapter, rowAdapterLogs) = if (resultInfo != null && query.errors.isEmpty() &&
                    resultInfo.error == null) {
                // if result info is not null, first try a pojo row adapter
                context.collectLogs { subContext ->
                    val pojo = PojoProcessor.createFor(
                            context = subContext,
                            element = typeMirror.asTypeElement(),
                            bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                            parent = null
                    ).process()
                    PojoRowAdapter(
                            context = subContext,
                            info = resultInfo,
                            pojo = pojo,
                            out = typeMirror)
                }
            } else {
                Pair(null, null)
            }

            if (rowAdapter == null && query.resultInfo == null) {
                // we don't know what query returns. Check for entity.
                val asElement = typeMirror.asTypeElement()
                if (asElement.isEntityElement()) {
                    return EntityRowAdapter(EntityProcessor(
                            context = context,
                            element = asElement.asTypeElement()
                    ).process())
                }
            }

            if (rowAdapter != null && rowAdapterLogs?.hasErrors() != true) {
                rowAdapterLogs?.writeTo(context)
                return rowAdapter
            }

            if ((resultInfo?.columns?.size ?: 1) == 1) {
                val singleColumn = findCursorValueReader(typeMirror,
                        resultInfo?.columns?.get(0)?.type)
                if (singleColumn != null) {
                    return SingleColumnRowAdapter(singleColumn)
                }
            }
            // if we tried, return its errors
            if (rowAdapter != null) {
                rowAdapterLogs?.writeTo(context)
                return rowAdapter
            }
            if (query.runtimeQueryPlaceholder) {
                // just go w/ pojo and hope for the best. this happens for @RawQuery where we
                // try to guess user's intention and hope that their query fits the result.
                val pojo = PojoProcessor.createFor(
                        context = context,
                        element = typeMirror.asTypeElement(),
                        bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                        parent = null
                ).process()
                return PojoRowAdapter(
                        context = context,
                        info = null,
                        pojo = pojo,
                        out = typeMirror)
            }
            return null
        } else {
            val singleColumn = findCursorValueReader(typeMirror, null) ?: return null
            return SingleColumnRowAdapter(singleColumn)
        }
    }

    fun findQueryParameterAdapter(typeMirror: XType): QueryParameterAdapter? {
        if (typeMirror.isType() &&
            context.COMMON_TYPES.COLLECTION.rawType.isAssignableFrom(typeMirror)) {
            val declared = typeMirror.asDeclaredType()
            val binder = findStatementValueBinder(
                declared.typeArguments.first().extendsBoundOrSelf(), null)
            if (binder != null) {
                return CollectionQueryParameterAdapter(binder)
            } else {
                // maybe user wants to convert this collection themselves. look for a match
                val collectionBinder = findStatementValueBinder(typeMirror, null) ?: return null
                return BasicQueryParameterAdapter(collectionBinder)
            }
        } else if (typeMirror.isArray() && typeMirror.componentType.isNotByte()) {
            val component = typeMirror.componentType
            val binder = findStatementValueBinder(component, null) ?: return null
            return ArrayQueryParameterAdapter(binder)
        } else {
            val binder = findStatementValueBinder(typeMirror, null) ?: return null
            return BasicQueryParameterAdapter(binder)
        }
    }

    private fun findTypeConverter(input: XType, outputs: List<XType>): TypeConverter? {
        return findTypeConverter(listOf(input), outputs)
    }

    private fun findTypeConverter(input: List<XType>, output: XType): TypeConverter? {
        return findTypeConverter(input, listOf(output))
    }

    private fun findTypeConverter(
        inputs: List<XType>,
        outputs: List<XType>
    ): TypeConverter? {
        if (inputs.isEmpty()) {
            return null
        }
        inputs.forEach { input ->
            if (outputs.any { output -> input.isSameType(output) }) {
                return NoOpConverter(input)
            }
        }

        val excludes = arrayListOf<XType>()

        val queue = LinkedList<TypeConverter>()
        fun exactMatch(candidates: List<TypeConverter>): TypeConverter? {
            return candidates.firstOrNull {
                outputs.any { output -> it.to.isAssignableFromWithoutVariance(output) }
            }
        }
        inputs.forEach { input ->
            val candidates = getAllTypeConverters(input, excludes)
            val match = exactMatch(candidates)
            if (match != null) {
                return match
            }
            candidates.forEach {
                excludes.add(it.to)
                queue.add(it)
            }
        }
        excludes.addAll(inputs)
        while (queue.isNotEmpty()) {
            val prev = queue.pop()
            val from = prev.to
            val candidates = getAllTypeConverters(from, excludes)
            val match = exactMatch(candidates)
            if (match != null) {
                return CompositeTypeConverter(prev, match)
            }
            candidates.forEach {
                excludes.add(it.to)
                queue.add(CompositeTypeConverter(prev, it))
            }
        }
        return null
    }

    private fun getAllColumnAdapters(input: XType): List<ColumnTypeAdapter> {
        return columnTypeAdapters.filter {
            input.isSameType(it.out)
        }
    }

    /**
     * Returns all type converters that can receive input type and return into another type.
     * The returned list is ordered by priority such that if we have an exact match, it is
     * prioritized.
     */
    private fun getAllTypeConverters(input: XType, excludes: List<XType>):
            List<TypeConverter> {
        // for input, check assignability because it defines whether we can use the method or not.
        // for excludes, use exact match
        return typeConverters.filter { converter ->
            converter.from.isAssignableFrom(input) &&
                    !excludes.any { it.isSameType(converter.to) }
        }.sortedByDescending {
            // if it is the same, prioritize
            if (it.from.isSameType(input)) {
                2
            } else {
                1
            }
        }
    }
}
