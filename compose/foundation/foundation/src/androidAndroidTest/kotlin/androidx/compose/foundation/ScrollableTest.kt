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

package androidx.compose.foundation

import androidx.compose.animation.core.ExponentialDecay
import androidx.compose.animation.core.ManualFrameClock
import androidx.compose.animation.core.advanceClockMillis
import androidx.compose.foundation.animation.FlingConfig
import androidx.compose.foundation.gestures.ScrollableController
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.milliseconds
import androidx.test.filters.LargeTest
import androidx.ui.test.TestUiDispatcher
import androidx.ui.test.center
import androidx.ui.test.createComposeRule
import androidx.ui.test.monotonicFrameAnimationClockOf
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.performGesture
import androidx.ui.test.runBlockingWithManualClock
import androidx.ui.test.runOnIdle
import androidx.ui.test.runOnUiThread
import androidx.ui.test.swipe
import androidx.ui.test.swipeWithVelocity
import androidx.ui.test.waitForIdle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class ScrollableTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val scrollableBoxTag = "scrollableBox"

    @Test
    fun scrollable_horizontalScroll() = runBlockingWithManualClock { clock ->
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(
                controller = controller,
                orientation = Orientation.Horizontal
            )
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)

        val lastTotal = runOnIdle {
            assertThat(total).isGreaterThan(0)
            total
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y + 100f),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)

        runOnIdle {
            assertThat(total).isEqualTo(lastTotal)
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x - 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)
        runOnIdle {
            assertThat(total).isLessThan(0.01f)
        }
    }

    @Test
    fun scrollable_verticalScroll() = runBlockingWithManualClock { clock ->
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(
                controller = controller,
                orientation = Orientation.Vertical
            )
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y + 100f),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)

        val lastTotal = runOnIdle {
            assertThat(total).isGreaterThan(0)
            total
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)

        runOnIdle {
            assertThat(total).isEqualTo(lastTotal)
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y - 100f),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)
        runOnIdle {
            assertThat(total).isLessThan(0.01f)
        }
    }

    @Test
    fun scrollable_startStop_notify() = runBlockingWithManualClock(true) { clock ->
        var startTrigger = 0f
        var stopTrigger = 0f
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(
                controller = controller,
                orientation = Orientation.Horizontal,
                onScrollStarted = { startTrigger++ },
                onScrollStopped = { stopTrigger++ }
            )
        }
        runOnIdle {
            assertThat(startTrigger).isEqualTo(0)
            assertThat(stopTrigger).isEqualTo(0)
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        // don't wait for animation so stop is 0, as we flinging still
        runOnIdle {
            assertThat(startTrigger).isEqualTo(1)
            assertThat(stopTrigger).isEqualTo(0)
        }
        advanceClockAndAwaitAnimation(clock)
        // after wait we expect stop to trigger
        runOnIdle {
            assertThat(startTrigger).isEqualTo(1)
            assertThat(stopTrigger).isEqualTo(1)
        }
    }

    @Test
    fun scrollable_disabledWontCallLambda() = runBlockingWithManualClock(true) { clock ->
        val enabled = mutableStateOf(true)
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(
                controller = controller,
                orientation = Orientation.Horizontal,
                enabled = enabled.value
            )
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)
        val prevTotal = runOnIdle {
            assertThat(total).isGreaterThan(0f)
            enabled.value = false
            total
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        advanceClockAndAwaitAnimation(clock)
        runOnIdle {
            assertThat(total).isEqualTo(prevTotal)
        }
    }

    @Test
    fun scrollable_velocityProxy() = runBlockingWithManualClock { clock ->
        var velocityTriggered = 0f
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(
                controller = controller,
                orientation = Orientation.Horizontal,
                onScrollStopped = { velocity ->
                    velocityTriggered = velocity
                }
            )
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipeWithVelocity(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                endVelocity = 112f,
                duration = 100.milliseconds

            )
        }
        // don't advance clocks, so animation won't trigger yet
        // and interrupt
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipeWithVelocity(
                start = this.center,
                end = Offset(this.center.x - 100f, this.center.y),
                endVelocity = 312f,
                duration = 100.milliseconds

            )
        }
        runOnIdle {
            // should be first velocity, as fling was disrupted
            assertThat(velocityTriggered - 112f).isLessThan(0.1f)
        }
    }

    @Test
    fun scrollable_startWithoutSlop_ifFlinging() = runBlockingWithManualClock { clock ->
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(
                controller = controller,
                orientation = Orientation.Horizontal
            )
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100f, this.center.y),
                duration = 100.milliseconds
            )
        }
        // don't advance clocks
        val prevTotal = runOnUiThread {
            assertThat(total).isGreaterThan(0f)
            total
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 114f, this.center.y),
                duration = 100.milliseconds
            )
        }
        runOnIdle {
            // last swipe should add exactly 114 as we don't advance clocks and already flinging
            val expected = prevTotal + 114
            assertThat(total - expected).isLessThan(0.1f)
        }
    }

    @Test
    fun scrollable_cancel_callsDragStop() = runBlockingWithManualClock { clock ->
        var total by mutableStateOf(0f)
        var dragStopped = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            if (total < 20) {
                Modifier.scrollable(
                    controller = controller,
                    orientation = Orientation.Horizontal,
                    onScrollStopped = {
                        dragStopped++
                    }
                )
            } else {
                Modifier
            }
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 100, this.center.y),
                duration = 100.milliseconds
            )
        }
        runOnIdle {
            assertThat(total).isGreaterThan(0f)
            assertThat(dragStopped).isEqualTo(1f)
        }
    }

    @Test
    fun scrollable_snappingScrolling() = runBlockingWithManualClock(true) { clock ->
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            Modifier.scrollable(orientation = Orientation.Vertical, controller = controller)
        }
        runOnIdle {
            assertThat(total).isEqualTo(0f)
        }
        runOnIdle {
            controller.smoothScrollBy(1000f)
        }
        advanceClockAndAwaitAnimation(clock)
        runOnIdle {
            assertThat(total).isEqualTo(1000f)
        }
        runOnIdle {
            controller.smoothScrollBy(-200f)
        }
        advanceClockAndAwaitAnimation(clock)
        runOnIdle {
            assertThat(total).isEqualTo(800f)
        }
    }

    @Test
    fun scrollable_explicitDisposal() = runBlockingWithManualClock(true) { clock ->
        val disposed = mutableStateOf(false)
        var total = 0f
        val controller = ScrollableController(
            consumeScrollDelta = {
                assertWithMessage("Animating after dispose!").that(disposed.value).isFalse()
                total += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        )
        setScrollableContent {
            if (!disposed.value) {
                Modifier.scrollable(orientation = Orientation.Vertical, controller = controller)
            } else {
                Modifier
            }
        }
        runOnIdle {
            controller.smoothScrollBy(300f)
        }
        advanceClockAndAwaitAnimation(clock)
        runOnIdle {
            assertThat(total).isEqualTo(300f)
        }
        runOnIdle {
            controller.smoothScrollBy(200f)
        }
        // don't advance clocks yet, toggle disposed value
        runOnUiThread {
            disposed.value = true
        }
        advanceClockAndAwaitAnimation(clock)
        // still 300 and didn't fail in onScrollConsumptionRequested.. lambda
        runOnIdle {
            assertThat(total).isEqualTo(300f)
        }
    }

    @Test
    fun scrollable_nestedDrag() = runBlockingWithManualClock { clock ->
        var innerDrag = 0f
        var outerDrag = 0f
        val animationClock = monotonicFrameAnimationClockOf(coroutineContext, clock)
        val outerState = ScrollableController(
            consumeScrollDelta = {
                outerDrag += it
                it
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = animationClock
        )
        val innerState = ScrollableController(
            consumeScrollDelta = {
                innerDrag += it / 2
                it / 2
            },
            flingConfig = FlingConfig(decayAnimation = ExponentialDecay()),
            animationClock = animationClock
        )

        composeTestRule.setContent {
            Stack {
                Box(
                    gravity = ContentGravity.Center,
                    modifier = Modifier
                        .testTag(scrollableBoxTag)
                        .preferredSize(300.dp)
                        .scrollable(
                            controller = outerState,
                            orientation = Orientation.Horizontal
                        )
                ) {
                    Box(
                        modifier = Modifier.preferredSize(300.dp).scrollable(
                            controller = innerState,
                            orientation = Orientation.Horizontal
                        )
                    )
                }
            }
        }
        onNodeWithTag(scrollableBoxTag).performGesture {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x + 200f, this.center.y),
                duration = 300.milliseconds
            )
        }
        val lastEqualDrag = runOnIdle {
            assertThat(innerDrag).isGreaterThan(0f)
            assertThat(outerDrag).isGreaterThan(0f)
            // we consumed half delta in child, so exactly half should go to the parent
            assertThat(outerDrag).isEqualTo(innerDrag)
            innerDrag
        }
        advanceClockAndAwaitAnimation(clock)
        advanceClockAndAwaitAnimation(clock)
        // and nothing should change as we don't do nested fling
        runOnIdle {
            assertThat(outerDrag).isEqualTo(lastEqualDrag)
        }
    }

    private fun setScrollableContent(scrollableModifierFactory: @Composable () -> Modifier) {
        composeTestRule.setContent {
            Stack {
                val scrollable = scrollableModifierFactory()
                Box(
                    modifier = Modifier
                        .testTag(scrollableBoxTag)
                        .preferredSize(100.dp).then(scrollable)
                )
            }
        }
    }

    private suspend fun advanceClockAndAwaitAnimation(clock: ManualFrameClock) {
        waitForIdle()
        withContext(TestUiDispatcher.Main) {
            clock.advanceClockMillis(5000L)
        }
    }
}
