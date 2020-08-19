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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.accessibilityLabel
import androidx.compose.ui.semantics.semantics
import androidx.test.filters.MediumTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class CallSemanticsActionTest {

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun performSemanticsAction() {
        composeTestRule.setContent {
            val state = remember { mutableStateOf("Nothing") }
            BoundaryNode {
                setString("SetString") { state.value = it; return@setString true }
                accessibilityLabel = state.value
            }
        }

        onNodeWithLabel("Nothing")
            .assertExists()
            .performSemanticsAction(MyActions.SetString) { it("Hello") }

        onNodeWithLabel("Nothing")
            .assertDoesNotExist()

        onNodeWithLabel("Hello")
            .assertExists()
    }

    object MyActions {
        val SetString = SemanticsPropertyKey<AccessibilityAction<(String) -> Boolean>>("SetString")
    }

    fun SemanticsPropertyReceiver.setString(label: String? = null, action: (String) -> Boolean) {
        this[MyActions.SetString] = AccessibilityAction(label, action)
    }

    @Composable
    fun BoundaryNode(props: (SemanticsPropertyReceiver.() -> Unit)) {
        Column(Modifier.semantics(properties = props)) {}
    }
}