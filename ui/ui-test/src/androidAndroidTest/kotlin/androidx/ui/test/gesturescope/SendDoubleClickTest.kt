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

package androidx.ui.test.gesturescope

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.doubleTapGestureFilter
import androidx.compose.ui.unit.Duration
import androidx.compose.ui.unit.milliseconds
import androidx.test.filters.MediumTest
import androidx.ui.test.InputDispatcher.Companion.eventPeriod
import androidx.ui.test.AndroidInputDispatcher.InputDispatcherTestRule
import androidx.ui.test.createComposeRule
import androidx.ui.test.doubleClick
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.performGesture
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.ClickableTestBox.defaultSize
import androidx.ui.test.util.ClickableTestBox.defaultTag
import androidx.ui.test.util.SinglePointerInputRecorder
import androidx.ui.test.util.recordedDuration
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class SendDoubleClickTest(private val config: TestConfig) {
    data class TestConfig(val position: Offset?, val delay: Duration?)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return mutableListOf<TestConfig>().apply {
                for (delay in listOf(null, 50.milliseconds)) {
                    for (x in listOf(1.0f, 33.0f, 99.0f)) {
                        for (y in listOf(1.0f, 33.0f, 99.0f)) {
                            add(TestConfig(Offset(x, y), delay))
                        }
                    }
                    add(TestConfig(null, delay))
                }
            }
        }
    }

    @get:Rule
    val rule = createComposeRule(disableTransitions = true)

    @get:Rule
    val inputDispatcherRule: TestRule = InputDispatcherTestRule(disableDispatchInRealTime = true)

    private val recordedDoubleClicks = mutableListOf<Offset>()
    private val expectedClickPosition =
        config.position ?: Offset(defaultSize / 2, defaultSize / 2)
    // The delay plus 2 clicks
    private val expectedDuration =
        (config.delay ?: 145.milliseconds) + (2 * eventPeriod).milliseconds

    private fun recordDoubleClick(position: Offset) {
        recordedDoubleClicks.add(position)
    }

    @Test
    fun testDoubleClick() {
        // Given some content
        val recorder = SinglePointerInputRecorder()
        rule.setContent {
            ClickableTestBox(
                Modifier
                    .doubleTapGestureFilter(this::recordDoubleClick)
                    .then(recorder)
            )
        }

        // When we inject a double click
        rule.onNodeWithTag(defaultTag).performGesture {
            if (config.position != null && config.delay != null) {
                doubleClick(config.position, config.delay)
            } else if (config.position != null) {
                doubleClick(config.position)
            } else if (config.delay != null) {
                doubleClick(delay = config.delay)
            } else {
                doubleClick()
            }
        }

        rule.waitForIdle()

        // Then we record 1 double click at the expected position
        assertThat(recordedDoubleClicks).isEqualTo(listOf(expectedClickPosition))

        // And that the duration was as expected
        assertThat(recorder.recordedDuration).isEqualTo(expectedDuration)
    }
}
