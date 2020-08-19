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

package androidx.compose.ui

import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusState

/**
 * A [modifier][Modifier.Element] that can be used to observe focus state changes.
 */
@ExperimentalFocus
interface FocusObserverModifier : Modifier.Element {
    /**
     * A callback that is called whenever focus state changes.
     */
    val onFocusChange: (FocusState) -> Unit
}

@OptIn(ExperimentalFocus::class)
internal class FocusObserverModifierImpl(
    override val onFocusChange: (FocusState) -> Unit
) : FocusObserverModifier

/**
 * Add this modifier to a component to observe focus state changes.
 */
@ExperimentalFocus
fun Modifier.focusObserver(onFocusChange: (FocusState) -> Unit): Modifier = composed {
    FocusObserverModifierImpl(onFocusChange)
}