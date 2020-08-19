/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.room.solver.binderprovider

import androidx.room.ext.PagingTypeNames
import androidx.room.parser.ParsedQuery
import androidx.room.compiler.processing.XDeclaredType
import androidx.room.compiler.processing.XType
import androidx.room.processor.Context
import androidx.room.processor.ProcessorErrors
import androidx.room.solver.QueryResultBinderProvider
import androidx.room.solver.query.result.ListQueryResultAdapter
import androidx.room.solver.query.result.PagingSourceQueryResultBinder
import androidx.room.solver.query.result.PositionalDataSourceQueryResultBinder
import androidx.room.solver.query.result.QueryResultBinder
import com.squareup.javapoet.TypeName

class PagingSourceQueryResultBinderProvider(val context: Context) : QueryResultBinderProvider {
    private val pagingSourceTypeMirror: XType? by lazy {
        context.processingEnv.findType(PagingTypeNames.PAGING_SOURCE)
    }

    override fun provide(declared: XDeclaredType, query: ParsedQuery): QueryResultBinder {
        if (query.tables.isEmpty()) {
            context.logger.e(ProcessorErrors.OBSERVABLE_QUERY_NOTHING_TO_OBSERVE)
        }
        val typeArg = declared.typeArguments.last()
        val listAdapter = context.typeAdapterStore.findRowAdapter(typeArg, query)?.let {
            ListQueryResultAdapter(it)
        }
        val tableNames = ((listAdapter?.accessedTableNames() ?: emptyList()) +
                query.tables.map { it.name }).toSet()
        return PagingSourceQueryResultBinder(
            PositionalDataSourceQueryResultBinder(listAdapter, tableNames)
        )
    }

    override fun matches(declared: XDeclaredType): Boolean {
        if (pagingSourceTypeMirror == null) {
            return false
        }

        if (declared.typeArguments.isEmpty()) {
            return false
        }

        val erasure = declared.erasure()
        if (!pagingSourceTypeMirror!!.isAssignableFrom(erasure)) {
            return false
        }

        if (declared.typeArguments.first().typeName != TypeName.INT.box()) {
            context.logger.e(ProcessorErrors.PAGING_SPECIFY_PAGING_SOURCE_TYPE)
        }

        return true
    }
}
