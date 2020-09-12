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

import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationClockObserver
import androidx.compose.animation.core.ExponentialDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollableColumn
import androidx.compose.foundation.animation.FlingConfig
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.TouchSlop
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.milliseconds
import androidx.test.filters.MediumTest
import androidx.ui.test.bottomCenter
import androidx.ui.test.bottomRight
import androidx.ui.test.createComposeRule
import androidx.ui.test.down
import androidx.ui.test.moveTo
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.performGesture
import androidx.ui.test.swipe
import androidx.ui.test.swipeDown
import androidx.ui.test.swipeLeft
import androidx.ui.test.swipeRight
import androidx.ui.test.swipeUp
import androidx.ui.test.topCenter
import androidx.ui.test.topLeft
import androidx.ui.test.up
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.SinglePointerInputRecorder
import androidx.ui.test.util.assertDecreasing
import androidx.ui.test.util.assertIncreasing
import androidx.ui.test.util.assertOnlyLastEventIsUp
import androidx.ui.test.util.assertSame
import androidx.ui.test.util.assertTimestampsAreIncreasing
import androidx.ui.test.util.isAlmostEqualTo
import androidx.ui.test.util.verify
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class SendSwipeTest {
    companion object {
        private const val tag = "widget"
    }

    @get:Rule
    val rule = createComposeRule(disableTransitions = true)

    private val recorder = SinglePointerInputRecorder()

    @Composable
    fun Ui(alignment: Alignment) {
        Stack(Modifier.fillMaxSize().wrapContentSize(alignment)) {
            ClickableTestBox(modifier = recorder, tag = tag)
        }
    }

    @Test
    fun swipeUp() {
        rule.setContent { Ui(Alignment.TopStart) }
        rule.onNodeWithTag(tag).performGesture { swipeUp() }
        rule.runOnIdle {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()
                assertSwipeIsUp()
            }
        }
    }

    @Test
    fun swipeDown() {
        rule.setContent { Ui(Alignment.TopEnd) }
        rule.onNodeWithTag(tag).performGesture { swipeDown() }
        rule.runOnIdle {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()
                assertSwipeIsDown()
            }
        }
    }

    @Test
    fun swipeLeft() {
        rule.setContent { Ui(Alignment.BottomEnd) }
        rule.onNodeWithTag(tag).performGesture { swipeLeft() }
        rule.runOnIdle {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()
                assertSwipeIsLeft()
            }
        }
    }

    @Test
    fun swipeRight() {
        rule.setContent { Ui(Alignment.BottomStart) }
        rule.onNodeWithTag(tag).performGesture { swipeRight() }
        rule.runOnIdle {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()
                assertSwipeIsRight()
            }
        }
    }

    @Test
    fun swipeShort() {
        rule.setContent { Ui(Alignment.Center) }
        rule.onNodeWithTag(tag).performGesture { swipe(topLeft, bottomRight, 1.milliseconds) }
        rule.runOnIdle {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()

                // DOWN, MOVE, UP
                assertThat(events.size).isEqualTo(3)

                // DOWN is in top left corner (0, 0)
                events[0].verify(null, null, true, Offset(0f, 0f))

                val t = events[0].timestamp + 1.milliseconds
                val pointerId = events[0].id

                // MOVE is in bottom right corner (box is 100x100, so corner is (99, 99))
                events[1].verify(t, pointerId, true, Offset(99f, 99f))
                // UP is also in bottom right corner
                events[2].verify(t, pointerId, false, Offset(99f, 99f))
            }
        }
    }

    @Test
    fun swipeScrollable() {
        val touchSlop = with(rule.density) { TouchSlop.toPx() }
        val scrollState = ScrollState(
            initial = 0f,
            flingConfig = FlingConfig(ExponentialDecay()),
            animationClock = object : AnimationClockObservable {
                // Use a "broken" clock, we just want response to input, not to time
                override fun subscribe(observer: AnimationClockObserver) {}
                override fun unsubscribe(observer: AnimationClockObserver) {}
            }
        )
        rule.setContent {
            with(DensityAmbient.current) {
                // Scrollable with a viewport the size of 10 boxes
                ScrollableColumn(
                    Modifier.testTag("scrollable").size(100.toDp(), 1000.toDp()),
                    scrollState
                ) {
                    repeat(100) {
                        ClickableTestBox()
                    }
                }
            }
        }

        rule.runOnIdle {
            assertThat(scrollState.value).isEqualTo(0f)
            // numBoxes * boxHeight - viewportHeight = 100 * 100 - 1000
            assertThat(scrollState.maxValue).isEqualTo(9000f)
        }

        val swipeDistance = 800f - touchSlop
        rule.onNodeWithTag("scrollable").performGesture {
            val from = bottomCenter - Offset(0f, 99f)
            val touchSlopThreshold = from - Offset(0f, touchSlop)
            val to = topCenter + Offset(0f, 100f)

            down(from)
            moveTo(touchSlopThreshold)
            moveTo(to)
            up()
        }

        rule.runOnIdle {
            assertThat(scrollState.value).isAlmostEqualTo(swipeDistance, 1e-3f)
            assertThat(scrollState.maxValue).isEqualTo(9000f)
        }
    }

    private fun SinglePointerInputRecorder.assertSwipeIsUp() {
        // Must have at least two events to have a direction
        assertThat(events.size).isAtLeast(2)
        // Last event must be above first event
        assertThat(events.last().position.y).isLessThan(events.first().position.y)
        // All events in between only move up
        events.map { it.position.x }.assertSame(tolerance = 0.001f)
        events.map { it.position.y }.assertDecreasing()
    }

    private fun SinglePointerInputRecorder.assertSwipeIsDown() {
        // Must have at least two events to have a direction
        assertThat(events.size).isAtLeast(2)
        // Last event must be below first event
        assertThat(events.last().position.y).isGreaterThan(events.first().position.y)
        // All events in between only move down
        events.map { it.position.x }.assertSame(tolerance = 0.001f)
        events.map { it.position.y }.assertIncreasing()
    }

    private fun SinglePointerInputRecorder.assertSwipeIsLeft() {
        // Must have at least two events to have a direction
        assertThat(events.size).isAtLeast(2)
        // Last event must be to the left of first event
        assertThat(events.last().position.x).isLessThan(events.first().position.x)
        // All events in between only move to the left
        events.map { it.position.x }.assertDecreasing()
        events.map { it.position.y }.assertSame(tolerance = 0.001f)
    }

    private fun SinglePointerInputRecorder.assertSwipeIsRight() {
        // Must have at least two events to have a direction
        assertThat(events.size).isAtLeast(2)
        // Last event must be to the right of first event
        assertThat(events.last().position.x).isGreaterThan(events.first().position.x)
        // All events in between only move to the right
        events.map { it.position.x }.assertIncreasing()
        events.map { it.position.y }.assertSame(tolerance = 0.001f)
    }
}
