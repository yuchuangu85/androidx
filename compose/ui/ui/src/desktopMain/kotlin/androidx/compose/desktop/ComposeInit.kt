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

@file:Suppress("DEPRECATION_ERROR")

package androidx.compose.desktop

import org.jetbrains.skiko.Library

/**
 * Can be called multiple times.
 *
 * Initialization will occur only on the first call. The next calls will do nothing.
 *
 * Should be called in a class that uses Jetpack Compose Api:
 *
 * class SomeClass {
 *     companion object {
 *         init {
 *             initCompose()
 *         }
 *     }
 * }
 */
fun initCompose() {
    // call object initializer only once
    ComposeInit
}

private object ComposeInit {
    init {
        Library.load("/", "skiko")
        // Until https://github.com/Kotlin/kotlinx.coroutines/issues/2039 is resolved
        // we have to set this property manually for coroutines to work.
        System.getProperties().setProperty("kotlinx.coroutines.fast.service.loader", "false")
    }
}