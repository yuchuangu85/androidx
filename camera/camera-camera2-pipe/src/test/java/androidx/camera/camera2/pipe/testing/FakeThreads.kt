/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.camera2.pipe.testing

import android.os.Handler
import androidx.camera.camera2.pipe.impl.Threads
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor

object FakeThreads {
    val forTests = Threads(
        CoroutineScope(Dispatchers.Default.plus(CoroutineName("CXCP-TestScope"))),
        Dispatchers.Default.asExecutor(),
        Dispatchers.Default,
        Dispatchers.IO.asExecutor(),
        Dispatchers.IO
    ) {
        @Suppress("DEPRECATION")
        (Handler())
    }

    fun fromExecutor(executor: Executor): Threads {
        return fromDispatcher(executor.asCoroutineDispatcher())
    }

    fun fromDispatcher(dispatcher: CoroutineDispatcher): Threads {
        val executor = dispatcher.asExecutor()

        return Threads(
            CoroutineScope(dispatcher.plus(CoroutineName("CXCP-TestScope"))),
            defaultExecutor = executor,
            defaultDispatcher = dispatcher,
            ioExecutor = executor,
            ioDispatcher = dispatcher
        ) {
            @Suppress("DEPRECATION")
            (Handler())
        }
    }
}