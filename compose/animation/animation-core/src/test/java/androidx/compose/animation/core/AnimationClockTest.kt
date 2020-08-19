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

package androidx.compose.animation.core

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(JUnit4::class)
class AnimationClockTest {
    private lateinit var clock: ManualAnimationClock

    @Before
    fun setup() {
        clock = ManualAnimationClock(0L)
    }

    /**
     * This function tests BaseAnimationClock's ability to handle an addition to its subscribers
     * while it is processing other subscribers.
     *
     * First a blocking subscription is added - when the time changes it will hang the execution
     * of all the subscriptions in the clock
     *
     * Second, we change the time to trigger said subscription
     *
     * While the clock is being blocked, we subscribe a second observer - at this time it should
     * not be triggered
     *
     * Lastly, we unblock the execution and the second observer should have an execution on the
     * same frame
     */
    @Test
    fun testSubscriptionDuringFrameCallback() {
        lateinit var blockingObserverContinuation: Continuation<Unit>
        val blockingSubscription = ignoreFirstFrameObserver { _ ->
            blockWithContinuation { blockingObserverContinuation = it }
        }

        // Subscribe a blocking subscription - it will hang clock.dispatchTime until
        // blockingObserverContinuation is resumed.
        clock.subscribe(blockingSubscription)

        // Block the thread until we dispatch the frame callbacks in another thread
        lateinit var frameCallbackJob: Job
        blockWithContinuation {
            val assertionContinuation = it
            // Call dispatchTime and allow it to hang
            frameCallbackJob = GlobalScope.launch {
                assertionContinuation.resume(Unit)
                clock.clockTimeMillis += 100
            }
        }

        var newObserverFrameTime = -1L

        val newObserver = ignoreFirstFrameObserver {
            newObserverFrameTime = it
        }

        // Subscribe a observer while dispatchTime is hanging
        clock.subscribe(newObserver)

        assertEquals(-1L, newObserverFrameTime)

        // Unblock the dispatchTime
        blockingObserverContinuation.resume(Unit)

        // Allow dispatchTime to finish its work
        runBlocking {
            frameCallbackJob.join()
        }

        // Within the same frame, the new subscription should have called back.
        assertEquals(100L, newObserverFrameTime)
    }

    @Test
    fun testRemovalBeforeAdd() {
        val recordedFrameTimes = mutableListOf<Long>()
        val observer = object : AnimationClockObserver {
            override fun onAnimationFrame(frameTimeMillis: Long) {
                recordedFrameTimes.add(frameTimeMillis)
            }
        }

        clock.clockTimeMillis = 0L
        clock.clockTimeMillis = 1L
        clock.unsubscribe(observer)
        clock.subscribe(observer)
        // observer should record 1L
        clock.clockTimeMillis = 2L
        // observer should record 2L
        clock.unsubscribe(observer)
        clock.clockTimeMillis = 3L
        clock.clockTimeMillis = 4L
        clock.subscribe(observer)
        // observer should record 4L
        clock.clockTimeMillis = 5L
        // observer should record 5L
        clock.clockTimeMillis = 6L
        // observer should record 6L
        clock.unsubscribe(observer)
        clock.clockTimeMillis = 7L
        clock.subscribe(observer)
        clock.unsubscribe(observer)
        clock.unsubscribe(observer)
        clock.subscribe(observer)
        clock.clockTimeMillis = 8L

        val expectedRecording = listOf(1L, 2L, 4L, 5L, 6L, 7L, 7L, 8L)
        assertEquals(expectedRecording, recordedFrameTimes)
    }

    @Test
    fun testResubscriptionObserverOrder() {
        var callIndex = 0

        var dummyACallIndex = -1
        var dummyBCallIndex = -1
        var dummyCCallIndex = -1

        val dummyA = dummyObserver {
            dummyACallIndex = callIndex++
        }
        val dummyB = dummyObserver {
            dummyBCallIndex = callIndex++
        }
        val dummyC = dummyObserver {
            dummyCCallIndex = callIndex++
        }

        clock.subscribe(dummyA)
        clock.subscribe(dummyB)
        clock.subscribe(dummyC)

        clock.clockTimeMillis = 1L

        // Starts at 3 since subscribe calls callback in ManualAnimationClock
        assertEquals(3, dummyACallIndex)
        assertEquals(4, dummyBCallIndex)
        assertEquals(5, dummyCCallIndex)

        clock.unsubscribe(dummyB)
        clock.subscribe(dummyB)

        clock.clockTimeMillis = 2L

        assertEquals(7, dummyACallIndex)
        assertEquals(9, dummyBCallIndex)
        assertEquals(8, dummyCCallIndex)
    }
}

private fun ignoreFirstFrameObserver(block: (Long) -> Unit): AnimationClockObserver {
    return object : IgnoreFirstFrameObserver() {
        override fun onNonFirstAnimationFrame(frameTimeMillis: Long) {
            block(frameTimeMillis)
        }
    }
}

private abstract class IgnoreFirstFrameObserver : AnimationClockObserver {
    abstract fun onNonFirstAnimationFrame(frameTimeMillis: Long)
    private var firstFrameSkipped = false
    override fun onAnimationFrame(frameTimeMillis: Long) {
        if (firstFrameSkipped) {
            onNonFirstAnimationFrame(frameTimeMillis)
        } else {
            firstFrameSkipped = true
        }
    }
}

private fun dummyObserver(onAnimationFrame: (Long) -> Unit = {}): AnimationClockObserver =
    object : AnimationClockObserver {
        override fun onAnimationFrame(frameTimeMillis: Long) {
            onAnimationFrame.invoke(frameTimeMillis)
        }
    }

private fun blockWithContinuation(block: (Continuation<Unit>) -> Unit) = runBlocking {
    suspendCoroutine(block)
}