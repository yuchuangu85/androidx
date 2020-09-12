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

package androidx.camera.camera2.pipe.impl

import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.Request
import kotlinx.atomicfu.atomic

internal val cameraGraphSessionIds = atomic(0)
class CameraGraphSessionImpl(
    private val token: TokenLock.Token,
    private val graphProcessor: GraphProcessor
) : CameraGraph.Session {
    private val debugId = cameraGraphSessionIds.incrementAndGet()

    override fun submit(request: Request) {
        graphProcessor.submit(request)
    }

    override fun submit(requests: List<Request>) {
        graphProcessor.submit(requests)
    }

    override fun setRepeating(request: Request) {
        graphProcessor.setRepeating(request)
    }

    override fun abort() {
        graphProcessor.abort()
    }

    override fun close() {
        // Release the token so that a new instance of session can be created.
        token.release()
    }

    override fun toString(): String = "CameraGraph.Session-$debugId"
}