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

import androidx.room.ext.LifecyclesTypeNames
import androidx.room.compiler.processing.XDeclaredType
import androidx.room.compiler.processing.XType
import androidx.room.processor.Context
import androidx.room.solver.ObservableQueryResultBinderProvider
import androidx.room.solver.query.result.LiveDataQueryResultBinder
import androidx.room.solver.query.result.QueryResultAdapter
import androidx.room.solver.query.result.QueryResultBinder

class LiveDataQueryResultBinderProvider(context: Context) :
    ObservableQueryResultBinderProvider(context) {
    private val liveDataTypeMirror: XType? by lazy {
        context.processingEnv.findType(LifecyclesTypeNames.LIVE_DATA)
    }

    override fun extractTypeArg(declared: XDeclaredType): XType = declared.typeArguments.first()

    override fun create(
        typeArg: XType,
        resultAdapter: QueryResultAdapter?,
        tableNames: Set<String>
    ): QueryResultBinder {
        return LiveDataQueryResultBinder(
                typeArg = typeArg,
                tableNames = tableNames,
                adapter = resultAdapter)
    }

    override fun matches(declared: XDeclaredType): Boolean =
            declared.typeArguments.size == 1 && isLiveData(declared)

    private fun isLiveData(declared: XDeclaredType): Boolean {
        if (liveDataTypeMirror == null) {
            return false
        }
        val erasure = declared.erasure()
        return erasure.isAssignableFrom(liveDataTypeMirror!!)
    }
}