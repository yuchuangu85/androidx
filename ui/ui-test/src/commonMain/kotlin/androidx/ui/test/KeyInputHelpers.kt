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

package androidx.ui.test

import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.node.ExperimentalLayoutNodeApi

/**
 * Send the specified [KeyEvent] to the focused component.
 *
 * @return true if the event was consumed. False otherwise.
 */
@OptIn(ExperimentalKeyInput::class)
fun SemanticsNodeInteraction.performKeyPress(keyEvent: KeyEvent): Boolean {
    val semanticsNode = fetchSemanticsNode("Failed to send key Event (${keyEvent.key})")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = semanticsNode.componentNode.owner
    requireNotNull(owner) { "Failed to find owner" }
    @Suppress("DEPRECATION")
    return runOnUiThread { owner.sendKeyEvent(keyEvent) }
}
