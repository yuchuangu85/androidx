/*
 * Copyright 2019 The Android Open Source Project
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

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onCommit
import androidx.compose.runtime.remember
import androidx.test.filters.SmallTest
import androidx.ui.test.android.AndroidComposeTestRule
import androidx.ui.test.android.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class AndroidComposeTestCaseRunnerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(
        disableTransitions = true
    )

    internal fun <T : ComponentActivity> AndroidComposeTestRule<T>.forGivenContent(
        composable: @Composable () -> Unit
    ): ComposeTestCaseSetup {
        return forGivenTestCase(object : ComposeTestCase {
            @Composable
            override fun emitContent() {
                composable()
            }
        })
    }

    @Test
    fun foreverRecomposing_viaModel_shouldFai2l() {
        val count = mutableStateOf(0)
        composeTestRule.forGivenContent {
            Text("Hello ${count.value}")
            count.value++
        }.performTestWithEventsControl {
            // Force the first recompose as the changes during initial composition are not
            // considered to invalidate the composition.
            count.value++
            assertFailsWith<AssertionError>(
                "Changes are still pending after '10' frames.") {
                doFramesAssertAllHadChangesExceptLastOne(10)
            }
        }
    }

    // @Test //- TODO: Does not work, performs only 1 frame until stable
    fun foreverRecomposing_viaState_shouldFail() {
        composeTestRule.forGivenContent {
            val state = remember { mutableStateOf(0) }
            Text("Hello ${state.value}")
            state.value++
        }.performTestWithEventsControl {
            assertFailsWith<AssertionError>(
                "Changes are still pending after '10' frames.") {
                doFramesAssertAllHadChangesExceptLastOne(10)
            }
        }
    }

    // @Test //- TODO: Does not work, performs only 1 frame until stable
    fun foreverRecomposing_viaStatePreCommit_shouldFail() {
        composeTestRule.forGivenContent {
            val state = remember { mutableStateOf(0) }
            Text("Hello ${state.value}")
            onCommit {
                state.value++
            }
        }.performTestWithEventsControl {
            assertFailsWith<AssertionError>(
                "Changes are still pending after '10' frames.") {
                doFramesAssertAllHadChangesExceptLastOne(10)
            }
        }
    }

    @Test
    fun recomposeZeroTime() {
        composeTestRule.forGivenContent {
            // Just empty composable
        }.performTestWithEventsControl {
            doFrame()
            assertNoPendingChanges()
        }
    }

    @Test
    fun recomposeZeroTime2() {
        composeTestRule.forGivenContent {
            Text("Hello")
        }.performTestWithEventsControl {
            doFrame()
            assertNoPendingChanges()
        }
    }

    @Test
    fun recomposeOnce() {
        composeTestRule.forGivenContent {
            val state = remember { mutableStateOf(0) }
            if (state.value < 1) {
                state.value++
            }
        }.performTestWithEventsControl {
            doFrame()
            assertNoPendingChanges()
        }
    }

    // @Test //- TODO: Does not work, performs only 1 frame until stable
    fun recomposeTwice() {
        composeTestRule.forGivenContent {
            val state = remember { mutableStateOf(0) }
            if (state.value < 2) {
                state.value++
            }
        }.performTestWithEventsControl {
            doFramesAssertAllHadChangesExceptLastOne(2)
        }
    }

    @Test
    fun recomposeTwice2() {
        val count = mutableStateOf(0)
        composeTestRule.forGivenContent {
            Text("Hello ${count.value}")
            if (count.value < 3) {
                count.value++
            }
        }.performTestWithEventsControl {
            // Force the first recompose as the changes during initial composition are not
            // considered to invalidate the composition.
            count.value++
            doFramesAssertAllHadChangesExceptLastOne(2)
        }
    }

    @Test
    fun measurePositiveOnEmptyShouldFail() {
        composeTestRule.forGivenContent {
            // Just empty composable
        }.performTestWithEventsControl {
            doFrame()
            assertFailsWith<AssertionError> {
                assertMeasureSizeIsPositive()
            }
        }
    }

    @Test
    fun measurePositive() {
        composeTestRule.forGivenContent {
            Box {
                Text("Hello")
            }
        }.performTestWithEventsControl {
            doFrame()
            assertMeasureSizeIsPositive()
        }
    }

    private inline fun <reified T : Throwable> assertFailsWith(
        expectedErrorMessage: String? = null,
        block: () -> Any
    ) {
        try {
            block()
        } catch (e: Throwable) {
            if (e !is T) {
                throw AssertionError("Expected exception not thrown, received: $e")
            }
            if (expectedErrorMessage != null && e.localizedMessage != expectedErrorMessage) {
                throw AssertionError("Expected error message not found, received: '" +
                        "${e.localizedMessage}'")
            }
            return
        }

        throw AssertionError("Expected exception not thrown")
    }
}