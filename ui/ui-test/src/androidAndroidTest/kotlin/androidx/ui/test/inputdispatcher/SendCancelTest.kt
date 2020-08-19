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

package androidx.ui.test.inputdispatcher

import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_POINTER_DOWN
import androidx.compose.ui.geometry.Offset
import androidx.test.filters.SmallTest
import androidx.ui.test.InputDispatcher.Companion.eventPeriod
import androidx.ui.test.android.AndroidInputDispatcher
import androidx.ui.test.util.assertHasValidEventTimes
import androidx.ui.test.util.expectError
import androidx.ui.test.util.verifyEvent
import androidx.ui.test.util.verifyPointer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests if [AndroidInputDispatcher.enqueueCancel] works
 */
@SmallTest
class SendCancelTest : InputDispatcherTest() {
    companion object {
        // pointerIds
        private const val pointer1 = 11
        private const val pointer2 = 22

        // positions (used with corresponding pointerId: pointerX with positionX_Y)
        private val position1_1 = Offset(11f, 11f)
        private val position2_1 = Offset(21f, 21f)
    }

    private fun AndroidInputDispatcher.generateCancelAndCheckPointers(delay: Long? = null) {
        generateCancelAndCheck(delay)
        assertThat(getCurrentPosition(pointer1)).isNull()
        assertThat(getCurrentPosition(pointer2)).isNull()
    }

    @Test
    fun onePointer() {
        subject.generateDownAndCheck(pointer1, position1_1)
        subject.generateCancelAndCheckPointers()
        subject.verifyNoGestureInProgress()
        subject.sendAllSynchronous()
        recorder.assertHasValidEventTimes()

        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(2)
            this[0].verifyEvent(1, ACTION_DOWN, 0, t) // pointer1
            this[0].verifyPointer(pointer1, position1_1)

            t += eventPeriod
            this[1].verifyEvent(1, ACTION_CANCEL, 0, t)
            this[1].verifyPointer(pointer1, position1_1)
        }
    }

    @Test
    fun onePointerWithDelay() {
        subject.generateDownAndCheck(pointer1, position1_1)
        subject.generateCancelAndCheckPointers(2 * eventPeriod)
        subject.verifyNoGestureInProgress()
        subject.sendAllSynchronous()
        recorder.assertHasValidEventTimes()

        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(2)
            this[0].verifyEvent(1, ACTION_DOWN, 0, t) // pointer1
            this[0].verifyPointer(pointer1, position1_1)

            t += 2 * eventPeriod
            this[1].verifyEvent(1, ACTION_CANCEL, 0, t)
            this[1].verifyPointer(pointer1, position1_1)
        }
    }

    @Test
    fun multiplePointers() {
        subject.generateDownAndCheck(pointer1, position1_1)
        subject.generateDownAndCheck(pointer2, position2_1)
        subject.generateCancelAndCheckPointers()
        subject.verifyNoGestureInProgress()
        subject.sendAllSynchronous()
        recorder.assertHasValidEventTimes()

        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(3)
            this[0].verifyEvent(1, ACTION_DOWN, 0, t) // pointer1
            this[0].verifyPointer(pointer1, position1_1)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t) // pointer2
            this[1].verifyPointer(pointer1, position1_1)
            this[1].verifyPointer(pointer2, position2_1)

            t += eventPeriod
            this[2].verifyEvent(2, ACTION_CANCEL, 0, t)
            this[2].verifyPointer(pointer1, position1_1)
            this[2].verifyPointer(pointer2, position2_1)
        }
    }

    @Test
    fun cancelWithoutDown() {
        expectError<IllegalStateException> {
            subject.enqueueCancel()
        }
    }

    @Test
    fun cancelAfterUp() {
        subject.enqueueDown(pointer1, position1_1)
        subject.enqueueUp(pointer1)
        expectError<IllegalStateException> {
            subject.enqueueCancel()
        }
    }

    @Test
    fun cancelAfterCancel() {
        subject.enqueueDown(pointer1, position1_1)
        subject.enqueueCancel()
        expectError<IllegalStateException> {
            subject.enqueueCancel()
        }
    }
}
