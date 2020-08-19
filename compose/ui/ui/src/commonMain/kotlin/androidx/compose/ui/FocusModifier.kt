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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.ModifiedFocusNode

/**
 * A [Modifier.Element] that wraps makes the modifiers on the right into a Focusable. Use a
 * different instance of [FocusModifier] for each focusable component.
 */
@OptIn(ExperimentalFocus::class)
internal class FocusModifier(
    initialFocus: FocusState
) : Modifier.Element {

    var focusState: FocusState = initialFocus
        set(value) {
            field = value
            focusNode.wrappedBy?.propagateFocusStateChange(value)
        }

    var focusedChild: ModifiedFocusNode? = null

    lateinit var focusNode: ModifiedFocusNode
}

/**
 * Add this modifier to a component to make it focusable.
 */
@ExperimentalFocus
@Composable
fun Modifier.focus(): Modifier = this.then(remember { FocusModifier(FocusState.Inactive) })
