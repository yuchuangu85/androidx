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
package androidx.compose.material

import androidx.compose.foundation.Strings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.AccessibilityRangeInfo
import androidx.compose.ui.unit.dp
import androidx.test.filters.LargeTest
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.assertRangeInfoEquals
import androidx.ui.test.assertValueEquals
import androidx.ui.test.assertWidthIsEqualTo
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.runOnUiThread
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class ProgressIndicatorTest {

    private val ExpectedLinearWidth = 240.dp
    private val ExpectedLinearHeight = 4.dp

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun determinateLinearProgressIndicator_Progress() {
        val tag = "linear"
        val progress = mutableStateOf(0f)

        composeTestRule
            .setMaterialContent {
                LinearProgressIndicator(modifier = Modifier.testTag(tag), progress = progress.value)
            }

        onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("0 percent")
            .assertRangeInfoEquals(AccessibilityRangeInfo(0f, 0f..1f))

        runOnUiThread {
            progress.value = 0.5f
        }

        onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("50 percent")
            .assertRangeInfoEquals(AccessibilityRangeInfo(0.5f, 0f..1f))
    }

    @Test
    fun determinateLinearProgressIndicator_Size() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                LinearProgressIndicator(progress = 0f)
            }
            .assertWidthIsEqualTo(ExpectedLinearWidth)
            .assertHeightIsEqualTo(ExpectedLinearHeight)
    }

    @Test
    fun indeterminateLinearProgressIndicator_progress() {
        val tag = "linear"

        composeTestRule
            .setMaterialContent {
                LinearProgressIndicator(modifier = Modifier.testTag(tag))
            }

        onNodeWithTag(tag)
            .assertValueEquals(Strings.InProgress)
    }

    @Test
    fun indeterminateLinearProgressIndicator_Size() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                LinearProgressIndicator()
            }
            .assertWidthIsEqualTo(ExpectedLinearWidth)
            .assertHeightIsEqualTo(ExpectedLinearHeight)
    }

    @Test
    fun determinateCircularProgressIndicator_Progress() {
        val tag = "circular"
        val progress = mutableStateOf(0f)

        composeTestRule
            .setMaterialContent {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(tag),
                    progress = progress.value
                )
            }

        onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("0 percent")
            .assertRangeInfoEquals(AccessibilityRangeInfo(0f, 0f..1f))

        runOnUiThread {
            progress.value = 0.5f
        }

        onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("50 percent")
            .assertRangeInfoEquals(AccessibilityRangeInfo(0.5f, 0f..1f))
    }

    @Test
    fun determinateCircularProgressIndicator_Size() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                CircularProgressIndicator(progress = 0f)
            }
            .assertIsSquareWithSize(40.dp)
    }

    @Test
    fun indeterminateCircularProgressIndicator_progress() {
        val tag = "circular"

        composeTestRule
            .setMaterialContent {
                CircularProgressIndicator(modifier = Modifier.testTag(tag))
            }

        onNodeWithTag(tag)
            .assertValueEquals(Strings.InProgress)
    }

    @Test
    fun indeterminateCircularProgressIndicator_Size() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                CircularProgressIndicator()
            }
            .assertIsSquareWithSize(40.dp)
    }
}
