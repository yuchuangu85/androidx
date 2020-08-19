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

package androidx.compose.ui.focus

import androidx.compose.foundation.Box
import androidx.compose.ui.FocusModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus
import androidx.compose.ui.focus.FocusState.Active
import androidx.compose.ui.focus.FocusState.ActiveParent
import androidx.compose.ui.focus.FocusState.Captured
import androidx.compose.ui.focus.FocusState.Disabled
import androidx.compose.ui.focus.FocusState.Inactive
import androidx.compose.ui.focusObserver
import androidx.compose.ui.focusRequester
import androidx.test.filters.SmallTest
import androidx.ui.test.createComposeRule
import androidx.ui.test.runOnIdle
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@OptIn(ExperimentalFocus::class)
@RunWith(JUnit4::class)
class FocusObserverTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun active_requestFocus() {
        // Arrange.
        lateinit var focusState: FocusState
        val focusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState = it }
                    .focusRequester(focusRequester)
                    .then(FocusModifier(Active))
            )
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState).isEqualTo(Active)
        }
    }

    @Test
    fun activeParent_requestFocus() {
        // Arrange.
        lateinit var focusState: FocusState
        val focusRequester = FocusRequester()
        val childFocusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState = it }
                    .focusRequester(focusRequester)
                    .focus()
            ) {
                Box(
                    modifier = Modifier
                        .focusRequester(childFocusRequester)
                        .focus()
                )
            }
        }
        runOnIdle {
            childFocusRequester.requestFocus()
            assertThat(focusState).isEqualTo(ActiveParent)
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState).isEqualTo(Active)
        }
    }

    @Test
    fun captured_requestFocus() {
        // Arrange.
        lateinit var focusState: FocusState
        val focusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState = it }
                    .focusRequester(focusRequester)
                    .then(FocusModifier(Captured))
            )
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState).isEqualTo(Captured)
        }
    }

    @Test
    fun disabled_requestFocus() {
        // Arrange.
        lateinit var focusState: FocusState
        val focusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState = it }
                    .focusRequester(focusRequester)
                    .then(FocusModifier(Disabled))
            )
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState).isEqualTo(Disabled)
        }
    }

    @Test
    fun inactive_requestFocus() {
        // Arrange.
        lateinit var focusState: FocusState
        val focusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState = it }
                    .focusRequester(focusRequester)
                    .then(FocusModifier(Inactive))
            )
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState).isEqualTo(Active)
        }
    }

    @Test
    fun inactive_requestFocus_multipleObservers() {
        // Arrange.
        lateinit var focusState1: FocusState
        lateinit var focusState2: FocusState
        lateinit var focusState3: FocusState
        lateinit var focusState4: FocusState
        lateinit var focusState5: FocusState
        lateinit var focusState6: FocusState
        val focusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState1 = it }
                    .focusObserver { focusState2 = it }
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .focusObserver { focusState3 = it }
                            .focusObserver { focusState4 = it }
                    ) {
                        Box(
                            modifier = Modifier
                                .focusObserver { focusState5 = it }
                                .focusObserver { focusState6 = it }
                                .focusRequester(focusRequester)
                                .then(FocusModifier(Inactive))
                        )
                    }
                }
            }
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState1).isEqualTo(Active)
            assertThat(focusState2).isEqualTo(Active)
            assertThat(focusState3).isEqualTo(Active)
            assertThat(focusState4).isEqualTo(Active)
            assertThat(focusState5).isEqualTo(Active)
            assertThat(focusState6).isEqualTo(Active)
        }
    }

    @Test
    fun active_requestFocus_multipleObserversWithExtraFocusModifierInBetween() {
        // Arrange.
        var focusState1: FocusState? = null
        var focusState2: FocusState? = null
        var focusState3: FocusState? = null
        var focusState4: FocusState? = null
        val focusRequester = FocusRequester()
        composeTestRule.setFocusableContent {
            Box(
                modifier = Modifier
                    .focusObserver { focusState1 = it }
                    .focusObserver { focusState2 = it }
                    .focus()
                    .focusObserver { focusState3 = it }
                    .focusObserver { focusState4 = it }
                    .focusRequester(focusRequester)
                    .focus()
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
            focusState1 = null
            focusState2 = null
            focusState3 = null
            focusState4 = null
        }

        runOnIdle {
            // Act.
            focusRequester.requestFocus()

            // Assert.
            assertThat(focusState1).isNull()
            assertThat(focusState2).isNull()
            assertThat(focusState3).isEqualTo(Active)
            assertThat(focusState4).isEqualTo(Active)
        }
    }
}