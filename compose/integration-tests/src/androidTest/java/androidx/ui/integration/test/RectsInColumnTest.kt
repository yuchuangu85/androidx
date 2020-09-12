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

package androidx.ui.integration.test

import androidx.activity.ComponentActivity
import androidx.compose.testutils.assertMeasureSizeIsPositive
import androidx.compose.testutils.assertNoPendingChanges
import androidx.compose.testutils.forGivenTestCase
import androidx.test.filters.MediumTest
import androidx.ui.integration.test.foundation.RectsInColumnTestCase
import androidx.ui.test.ExperimentalTesting
import androidx.ui.test.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Ensure correctness of [RectsInColumnTestCase].
 */
@MediumTest
@RunWith(Parameterized::class)
@OptIn(ExperimentalTesting::class)
class RectsInColumnTest(private val numberOfRectangles: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun initParameters(): Array<Any> = arrayOf(1, 10)
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(disableTransitions = true)

    @Test
    fun toggleRectangleColor_compose() {
        val testCase = RectsInColumnTestCase(numberOfRectangles)
        composeTestRule
            .forGivenTestCase(testCase)
            .performTestWithEventsControl {
                doFrame()
                assertNoPendingChanges()
                assertMeasureSizeIsPositive()
                testCase.toggleState()
                doFrame()
                assertNoPendingChanges()
            }
    }
}