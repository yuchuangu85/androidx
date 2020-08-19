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

package androidx.compose.ui.input.key

import androidx.compose.foundation.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus
import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.setFocusableContent
import androidx.compose.ui.focusRequester
import androidx.compose.ui.input.key.Key.Companion.A
import androidx.compose.ui.input.key.KeyEventType.KeyUp
import androidx.test.filters.SmallTest
import androidx.ui.test.createComposeRule
import androidx.ui.test.onRoot
import androidx.ui.test.performKeyPress
import androidx.ui.test.runOnIdle
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("DEPRECATION")
@SmallTest
@RunWith(JUnit4::class)
@OptIn(
    ExperimentalFocus::class,
    ExperimentalKeyInput::class
)
class ProcessKeyInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test(expected = IllegalStateException::class)
    fun noRootFocusModifier_throwsException() {
        // Arrange.
        composeTestRule.setContent {
            Box(modifier = KeyInputModifier(null, null))
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))
    }

    @Test(expected = IllegalStateException::class)
    fun noFocusModifier_throwsException() {
        // Arrange.
        composeTestRule.setFocusableContent {
            Box(modifier = Modifier.keyInputFilter { true })
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))
    }

    @Test(expected = IllegalStateException::class)
    fun focusModifierNotFocused_throwsException() {

        // Arrange.
        composeTestRule.setFocusableContent {
            Box(modifier = Modifier.focus().keyInputFilter { true })
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))
    }

    @Test
    fun onKeyEvent_triggered() {
        // Arrange.
        val focusRequester = FocusRequester()
        lateinit var receivedKeyEvent: KeyEvent
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focus()
                    .keyInputFilter {
                        receivedKeyEvent = it
                        true
                    }
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        val keyConsumed = onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            receivedKeyEvent.assertEqualTo(keyEvent(A, KeyUp))
            assertThat(keyConsumed).isTrue()
        }
    }

    @Test
    fun onPreviewKeyEvent_triggered() {
        // Arrange.
        val focusRequester = FocusRequester()
        lateinit var receivedKeyEvent: KeyEvent
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focus()
                    .previewKeyInputFilter {
                        receivedKeyEvent = it
                        true
                    }
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        val keyConsumed = onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            receivedKeyEvent.assertEqualTo(keyEvent(A, KeyUp))
            assertThat(keyConsumed).isTrue()
        }
    }

    @Test
    fun onKeyEventNotTriggered_ifOnPreviewKeyEventConsumesEvent() {
        // Arrange.
        val focusRequester = FocusRequester()
        lateinit var receivedPreviewKeyEvent: KeyEvent
        var receivedKeyEvent: KeyEvent? = null
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focus()
                    .keyInputFilter {
                        receivedKeyEvent = it
                        true
                    }
                    .previewKeyInputFilter {
                        receivedPreviewKeyEvent = it
                        true
                    }
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            receivedPreviewKeyEvent.assertEqualTo(keyEvent(A, KeyUp))
            assertThat(receivedKeyEvent).isNull()
        }
    }

    @Test
    fun onKeyEvent_triggeredAfter_onPreviewKeyEvent() {
        // Arrange.
        val focusRequester = FocusRequester()
        var triggerIndex = 1
        var onKeyEventTrigger = 0
        var onPreviewKeyEventTrigger = 0
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focus()
                    .keyInputFilter {
                        onKeyEventTrigger = triggerIndex++
                        true
                    }
                    .previewKeyInputFilter {
                        onPreviewKeyEventTrigger = triggerIndex++
                        false
                    }
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            assertThat(onPreviewKeyEventTrigger).isEqualTo(1)
            assertThat(onKeyEventTrigger).isEqualTo(2)
        }
    }

    @Test
    fun parent_child() {
        // Arrange.
        val focusRequester = FocusRequester()
        var triggerIndex = 1
        var parentOnKeyEventTrigger = 0
        var parentOnPreviewKeyEventTrigger = 0
        var childOnKeyEventTrigger = 0
        var childOnPreviewKeyEventTrigger = 0
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focus()
                    .keyInputFilter {
                        parentOnKeyEventTrigger = triggerIndex++
                        false
                    }
                    .previewKeyInputFilter {
                        parentOnPreviewKeyEventTrigger = triggerIndex++
                        false
                    }
            ) {
                Box(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .focus()
                        .keyInputFilter {
                            childOnKeyEventTrigger = triggerIndex++
                            false
                        }
                        .previewKeyInputFilter {
                            childOnPreviewKeyEventTrigger = triggerIndex++
                            false
                        }
                )
            }
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            assertThat(parentOnPreviewKeyEventTrigger).isEqualTo(1)
            assertThat(childOnPreviewKeyEventTrigger).isEqualTo(2)
            assertThat(childOnKeyEventTrigger).isEqualTo(3)
            assertThat(parentOnKeyEventTrigger).isEqualTo(4)
        }
    }

    @Test
    fun parent_child_noFocusModifierForParent() {
        // Arrange.
        val focusRequester = FocusRequester()
        var triggerIndex = 1
        var parentOnKeyEventTrigger = 0
        var parentOnPreviewKeyEventTrigger = 0
        var childOnKeyEventTrigger = 0
        var childOnPreviewKeyEventTrigger = 0
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .keyInputFilter {
                        parentOnKeyEventTrigger = triggerIndex++
                        false
                    }
                    .previewKeyInputFilter {
                        parentOnPreviewKeyEventTrigger = triggerIndex++
                        false
                    }
            ) {
                Box(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .focus()
                        .keyInputFilter {
                            childOnKeyEventTrigger = triggerIndex++
                            false
                        }
                        .previewKeyInputFilter {
                            childOnPreviewKeyEventTrigger = triggerIndex++
                            false
                        }
                )
            }
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            assertThat(parentOnPreviewKeyEventTrigger).isEqualTo(1)
            assertThat(childOnPreviewKeyEventTrigger).isEqualTo(2)
            assertThat(childOnKeyEventTrigger).isEqualTo(3)
            assertThat(parentOnKeyEventTrigger).isEqualTo(4)
        }
    }

    @Test
    fun grandParent_parent_child() {
        // Arrange.
        val focusRequester = FocusRequester()
        var triggerIndex = 1
        var grandParentOnKeyEventTrigger = 0
        var grandParentOnPreviewKeyEventTrigger = 0
        var parentOnKeyEventTrigger = 0
        var parentOnPreviewKeyEventTrigger = 0
        var childOnKeyEventTrigger = 0
        var childOnPreviewKeyEventTrigger = 0
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focus()
                    .keyInputFilter {
                        grandParentOnKeyEventTrigger = triggerIndex++
                        false
                    }
                    .previewKeyInputFilter {
                        grandParentOnPreviewKeyEventTrigger = triggerIndex++
                        false
                    }
            ) {
                Box(
                    modifier = Modifier
                        .focus()
                        .keyInputFilter {
                            parentOnKeyEventTrigger = triggerIndex++
                            false
                        }
                        .previewKeyInputFilter {
                            parentOnPreviewKeyEventTrigger = triggerIndex++
                            false
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .focus()
                            .keyInputFilter {
                                childOnKeyEventTrigger = triggerIndex++
                                false
                            }
                            .previewKeyInputFilter {
                                childOnPreviewKeyEventTrigger = triggerIndex++
                                false
                            }
                    )
                }
            }
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        // Act.
        onRoot().performKeyPress(keyEvent(A, KeyUp))

        // Assert.
        runOnIdle {
            assertThat(grandParentOnPreviewKeyEventTrigger).isEqualTo(1)
            assertThat(parentOnPreviewKeyEventTrigger).isEqualTo(2)
            assertThat(childOnPreviewKeyEventTrigger).isEqualTo(3)
            assertThat(childOnKeyEventTrigger).isEqualTo(4)
            assertThat(parentOnKeyEventTrigger).isEqualTo(5)
            assertThat(grandParentOnKeyEventTrigger).isEqualTo(6)
        }
    }
}
