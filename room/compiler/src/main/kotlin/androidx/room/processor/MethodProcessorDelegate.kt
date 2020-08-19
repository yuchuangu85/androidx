/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.room.processor

import androidx.room.ext.KotlinTypeNames
import androidx.room.ext.L
import androidx.room.ext.N
import androidx.room.ext.RoomCoroutinesTypeNames
import androidx.room.ext.T
import androidx.room.parser.ParsedQuery
import androidx.room.compiler.processing.XDeclaredType
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XVariableElement
import androidx.room.solver.prepared.binder.CallablePreparedQueryResultBinder.Companion.createPreparedBinder
import androidx.room.solver.prepared.binder.PreparedQueryResultBinder
import androidx.room.solver.query.result.CoroutineResultBinder
import androidx.room.solver.query.result.QueryResultBinder
import androidx.room.solver.shortcut.binder.CallableDeleteOrUpdateMethodBinder.Companion.createDeleteOrUpdateBinder
import androidx.room.solver.shortcut.binder.CallableInsertMethodBinder.Companion.createInsertBinder
import androidx.room.solver.shortcut.binder.DeleteOrUpdateMethodBinder
import androidx.room.solver.shortcut.binder.InsertMethodBinder
import androidx.room.solver.transaction.binder.CoroutineTransactionMethodBinder
import androidx.room.solver.transaction.binder.InstantTransactionMethodBinder
import androidx.room.solver.transaction.binder.TransactionMethodBinder
import androidx.room.solver.transaction.result.TransactionMethodAdapter
import androidx.room.vo.QueryParameter
import androidx.room.vo.ShortcutQueryParameter
import androidx.room.vo.TransactionMethod

/**
 *  Delegate class with common functionality for DAO method processors.
 */
abstract class MethodProcessorDelegate(
    val context: Context,
    val containing: XDeclaredType,
    val executableElement: XMethodElement
) {

    abstract fun extractReturnType(): XType

    abstract fun extractParams(): List<XVariableElement>

    fun extractQueryParams(): List<QueryParameter> {
        return extractParams().map { variableElement ->
            QueryParameterProcessor(
                baseContext = context,
                containing = containing,
                element = variableElement,
                sqlName = variableElement.name
            ).process()
        }
    }

    abstract fun findResultBinder(returnType: XType, query: ParsedQuery): QueryResultBinder

    abstract fun findPreparedResultBinder(
        returnType: XType,
        query: ParsedQuery
    ): PreparedQueryResultBinder

    abstract fun findInsertMethodBinder(
        returnType: XType,
        params: List<ShortcutQueryParameter>
    ): InsertMethodBinder

    abstract fun findDeleteOrUpdateMethodBinder(returnType: XType): DeleteOrUpdateMethodBinder

    abstract fun findTransactionMethodBinder(
        callType: TransactionMethod.CallType
    ): TransactionMethodBinder

    companion object {
        fun createFor(
            context: Context,
            containing: XDeclaredType,
            executableElement: XMethodElement
        ): MethodProcessorDelegate {
            return if (executableElement.isSuspendFunction()) {
                val hasCoroutineArtifact = context.processingEnv
                    .findTypeElement(RoomCoroutinesTypeNames.COROUTINES_ROOM.toString()) != null
                if (!hasCoroutineArtifact) {
                    context.logger.e(ProcessorErrors.MISSING_ROOM_COROUTINE_ARTIFACT)
                }
                SuspendMethodProcessorDelegate(
                    context,
                    containing,
                    executableElement
                )
            } else {
                DefaultMethodProcessorDelegate(
                    context,
                    containing,
                    executableElement
                )
            }
        }
    }
}

/**
 * Default delegate for DAO methods.
 */
class DefaultMethodProcessorDelegate(
    context: Context,
    containing: XDeclaredType,
    executableElement: XMethodElement
) : MethodProcessorDelegate(context, containing, executableElement) {

    override fun extractReturnType(): XType {
        val asMember = executableElement.asMemberOf(containing)
        return asMember.returnType
    }

    override fun extractParams() = executableElement.parameters

    override fun findResultBinder(returnType: XType, query: ParsedQuery) =
        context.typeAdapterStore.findQueryResultBinder(returnType, query)

    override fun findPreparedResultBinder(
        returnType: XType,
        query: ParsedQuery
    ) = context.typeAdapterStore.findPreparedQueryResultBinder(returnType, query)

    override fun findInsertMethodBinder(
        returnType: XType,
        params: List<ShortcutQueryParameter>
    ) = context.typeAdapterStore.findInsertMethodBinder(returnType, params)

    override fun findDeleteOrUpdateMethodBinder(returnType: XType) =
        context.typeAdapterStore.findDeleteOrUpdateMethodBinder(returnType)

    override fun findTransactionMethodBinder(callType: TransactionMethod.CallType) =
        InstantTransactionMethodBinder(
            TransactionMethodAdapter(executableElement.name, callType))
}

/**
 * Delegate for DAO methods that are a suspend function.
 */
class SuspendMethodProcessorDelegate(
    context: Context,
    containing: XDeclaredType,
    executableElement: XMethodElement
) : MethodProcessorDelegate(context, containing, executableElement) {

    private val continuationParam: XVariableElement by lazy {
        val continuationType = context.processingEnv
            .requireType(KotlinTypeNames.CONTINUATION.toString()).erasure()
        executableElement.parameters.last {
            it.type.erasure().isSameType(continuationType)
        }
    }

    override fun extractReturnType(): XType {
        val asMember = executableElement.asMemberOf(containing)
        return asMember.getSuspendFunctionReturnType()
    }

    override fun extractParams() =
        executableElement.parameters.filterNot {
            it == continuationParam
        }

    override fun findResultBinder(returnType: XType, query: ParsedQuery) =
        CoroutineResultBinder(
            typeArg = returnType,
            adapter = context.typeAdapterStore.findQueryResultAdapter(returnType, query),
            continuationParamName = continuationParam.name
        )

    override fun findPreparedResultBinder(
        returnType: XType,
        query: ParsedQuery
    ) = createPreparedBinder(
        returnType = returnType,
        adapter = context.typeAdapterStore.findPreparedQueryResultAdapter(returnType, query)
    ) { callableImpl, dbField ->
        addStatement(
            "return $T.execute($N, $L, $L, $N)",
            RoomCoroutinesTypeNames.COROUTINES_ROOM,
            dbField,
            "true", // inTransaction
            callableImpl,
            continuationParam.name
        )
    }

    override fun findInsertMethodBinder(
        returnType: XType,
        params: List<ShortcutQueryParameter>
    ) = createInsertBinder(
        typeArg = returnType,
        adapter = context.typeAdapterStore.findInsertAdapter(returnType, params)
    ) { callableImpl, dbField ->
        addStatement(
            "return $T.execute($N, $L, $L, $N)",
            RoomCoroutinesTypeNames.COROUTINES_ROOM,
            dbField,
            "true", // inTransaction
            callableImpl,
            continuationParam.name
        )
    }

    override fun findDeleteOrUpdateMethodBinder(returnType: XType) =
        createDeleteOrUpdateBinder(
            typeArg = returnType,
            adapter = context.typeAdapterStore.findDeleteOrUpdateAdapter(returnType)
        ) { callableImpl, dbField ->
            addStatement(
                "return $T.execute($N, $L, $L, $N)",
                RoomCoroutinesTypeNames.COROUTINES_ROOM,
                dbField,
                "true", // inTransaction
                callableImpl,
                continuationParam.name
            )
        }

    override fun findTransactionMethodBinder(callType: TransactionMethod.CallType) =
        CoroutineTransactionMethodBinder(
            adapter = TransactionMethodAdapter(executableElement.name, callType),
            continuationParamName = continuationParam.name
        )
}