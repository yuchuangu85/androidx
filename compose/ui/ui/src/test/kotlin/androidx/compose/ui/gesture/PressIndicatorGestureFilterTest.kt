/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package androidx.compose.ui.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.down
import androidx.compose.ui.input.pointer.invokeOverAllPasses
import androidx.compose.ui.input.pointer.invokeOverPasses
import androidx.compose.ui.input.pointer.moveBy
import androidx.compose.ui.input.pointer.moveTo
import androidx.compose.ui.input.pointer.up
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.milliseconds
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PressIndicatorGestureFilterTest {

    private lateinit var filter: PressIndicatorGestureFilter

    @Before
    fun setup() {
        filter = PressIndicatorGestureFilter()
        filter.onStart = mock()
        filter.onStop = mock()
        filter.onCancel = mock()
    }

    // Verification of scenarios where onStart should not be called.

    @Test
    fun onPointerInput_downConsumed_onStartNotCalled() {
        filter::onPointerInput
            .invokeOverAllPasses(down(0, 0.milliseconds).consumeDownChange())
        verify(filter.onStart!!, never()).invoke(any())
    }

    @Test
    fun onPointerInput_disabledDown_onStartNotCalled() {
        filter.setEnabled(false)
        filter::onPointerInput
            .invokeOverAllPasses(down(0, 0.milliseconds).consumeDownChange())
        verify(filter.onStart!!, never()).invoke(any())
    }

    // Verification of scenarios where onStart should be called once.

    @Test
    fun onPointerInput_down_onStartCalledOnce() {
        filter::onPointerInput.invokeOverAllPasses(down(0, 0.milliseconds))
        verify(filter.onStart!!).invoke(any())
    }

    @Test
    fun onPointerInput_downDown_onStartCalledOnce() {
        var pointer0 = down(0)
        filter::onPointerInput.invokeOverAllPasses(pointer0)
        pointer0 = pointer0.moveTo(1.milliseconds)
        val pointer1 = down(1, 1.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onStart!!).invoke(any())
    }

    @Test
    fun onPointerInput_2Down1Up1Down_onStartCalledOnce() {
        var pointer0 = down(0)
        var pointer1 = down(1)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = pointer0.up(100.milliseconds)
        pointer1 = pointer1.moveTo(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = down(0, duration = 200.milliseconds)
        pointer1 = pointer1.moveTo(200.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onStart!!).invoke(any())
    }

    @Test
    fun onPointerInput_1DownMoveOutside2ndDown_onStartOnlyCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer0, IntSize(5, 5))
        pointer0 = pointer0.moveTo(100.milliseconds, 10f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer0, IntSize(5, 5))
        pointer0 = pointer0.moveTo(200.milliseconds)
        val pointer1 = down(1, x = 0f, y = 0f)

        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onStart!!).invoke(any())
    }

    // Verification of scenarios where onStop should not be called.

    @Test
    fun onPointerInput_downMoveConsumedUp_onStopNotCalled() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveTo(100.milliseconds, 5f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(200.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!, never()).invoke()
    }

    @Test
    fun onPointerInput_downConsumedUp_onStopNotCalled() {
        var pointer = down(0, 0.milliseconds).consumeDownChange()
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!, never()).invoke()
    }

    @Test
    fun onPointerInput_2DownUp_onStopNotCalled() {
        var pointer0 = down(0)
        var pointer1 = down(1)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = pointer0.moveTo(100.milliseconds)
        pointer1 = pointer1.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onStop!!, never()).invoke()
    }

    @Test
    fun onPointerInput_downDisabledUp_onStopNotCalled() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.setEnabled(false)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!, never()).invoke()
    }

    @Test
    fun onPointerInput_downDisabledEnabledUp_onStopNotCalled() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.setEnabled(false)
        filter.setEnabled(true)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!, never()).invoke()
    }

    // Verification of scenarios where onStop should be called once.

    @Test
    fun onPointerInput_downUp_onStopCalledOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!).invoke()
    }

    @Test
    fun onPointerInput_downUpConsumed_onStopCalledOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(100.milliseconds).consumeDownChange()
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!).invoke()
    }

    @Test
    fun onPointerInput_downMoveUp_onStopCalledOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveTo(100.milliseconds, 5f)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(200.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onStop!!).invoke()
    }

    @Test
    fun onPointerInput_2Down2Up_onStopCalledOnce() {
        var pointer1 = down(0)
        var pointer2 = down(1)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)
        pointer1 = pointer1.up(100.milliseconds)
        pointer2 = pointer2.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        verify(filter.onStop!!).invoke()
    }

    // Verification of scenarios where onCancel should not be called.

    @Test
    fun onPointerInput_downConsumedMoveConsumed_onCancelNotCalled() {
        var pointer = down(0, 0.milliseconds).consumeDownChange()
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveBy(100.milliseconds, 5f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onCancel!!, never()).invoke()
    }

    @Test
    fun onPointerInput_downUp_onCancelNotCalled() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onCancel!!, never()).invoke()
    }

    @Test
    fun onPointerInput_downMoveUp_onCancelNotCalled() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveTo(100.milliseconds, 5f)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onCancel!!, never()).invoke()
    }

    @Test
    fun onPointerInput_2DownOneMoveOutsideOfBounds_onCancelNotCalled() {
        var pointer0 = down(0, x = 0f, y = 0f)
        var pointer1 = down(0, x = 4f, y = 4f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, pointer1, size = IntSize(5, 5))
        pointer0 = pointer0.moveTo(100.milliseconds, 0f, 0f)
        pointer1 = pointer1.moveTo(100.milliseconds, 5f, 4f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, pointer1, size = IntSize(5, 5))

        verify(filter.onCancel!!, never()).invoke()
    }

    @Test
    fun onPointerInput_notEnabledDownNotEnabled_onCancelNotCalled() {
        filter.setEnabled(false)
        filter::onPointerInput.invokeOverAllPasses(down(0, 0.milliseconds))
        filter.setEnabled(false)

        verify(filter.onCancel!!, never()).invoke()
    }

    // Verification of scenarios where onCancel should be called once.

    @Test
    fun onPointerInput_downMoveConsumed_onCancelCalledOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveBy(100.milliseconds, 5f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_downMoveConsumedMoveConsumed_onCancelCalledOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveBy(100.milliseconds, 5f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveBy(100.milliseconds, 5f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_2Down2MoveConsumed_onCancelCalledOnce() {
        var pointer0 = down(0)
        var pointer1 = down(1)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = pointer0.moveBy(100.milliseconds, 5f).consume(1f)
        pointer1 = pointer1.moveBy(100.milliseconds, 5f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_2Down1MoveConsumedTheOtherMoveConsume_onCancelCalledOnce() {
        var pointer0 = down(0)
        filter::onPointerInput.invokeOverAllPasses(pointer0)
        pointer0 = pointer0.moveTo(100.milliseconds)
        var pointer1 = down(1, duration = 100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = pointer0.moveBy(100L.milliseconds, 5f).consume(5f)
        pointer1 = pointer1.moveBy(100L.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = pointer0.moveBy(100L.milliseconds)
        pointer1 = pointer1.moveBy(100L.milliseconds, 5f).consume(5f)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_1DownMoveOutsideOfBoundsLeft_onCancelCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(100.milliseconds, -1f, 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_1DownMoveOutsideOfBoundsRight_onCancelCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(100.milliseconds, 1f, 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_1DownMoveOutsideOfBoundsUp_onCancelCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(100.milliseconds, 0f, -1f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_1DownMoveOutsideOfBoundsDown_onCancelCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(100.milliseconds, 0f, 1f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_2DownBothMoveOutsideOfBounds_onCancelCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 4f)
        var pointer1 = down(1, x = 4f, y = 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, pointer1, size = IntSize(5, 5))
        pointer0 = pointer0.moveTo(100.milliseconds, 0f, 5f)
        pointer1 = pointer1.moveTo(100.milliseconds, 5f, 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, pointer1, size = IntSize(5, 5))

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_1DownMoveOutsideBoundsThenInsideThenOutside_onCancelCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(100.milliseconds, 0f, 1f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(200.milliseconds, 0f, 0f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))
        pointer0 = pointer0.moveTo(300.milliseconds, 0f, 1f)
        filter::onPointerInput
            .invokeOverAllPasses(pointer0, IntSize(1, 1))

        verify(filter.onCancel!!).invoke()
    }

    @Test
    fun onPointerInput_1DownMoveOutsideBoundsUpTwice_onCancelCalledTwice() {
        var time = 0L

        repeat(2) {
            var pointer = down(0, x = 0f, y = 0f)
            filter::onPointerInput.invokeOverAllPasses(pointer, IntSize(1, 1))
            pointer = pointer.moveTo(time.milliseconds, 0f, 1f)
            filter::onPointerInput.invokeOverAllPasses(pointer, IntSize(1, 1))
            pointer = pointer.up(time.milliseconds)
            filter::onPointerInput.invokeOverAllPasses(pointer, IntSize(1, 1))
            time += 100L
        }

        verify(filter.onCancel!!, times(2)).invoke()
    }

    @Test
    fun onPointerInput_downDisabled_onCancelCalledOnce() {
        val pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.setEnabled(false)

        verify(filter.onCancel!!).invoke()
    }

    // Verification of correct position returned by onStart.

    @Test
    fun onPointerInput_down_downPositionIsCorrect() {
        filter::onPointerInput
            .invokeOverAllPasses(down(0, 0.milliseconds, x = 13f, y = 17f))
        verify(filter.onStart!!).invoke(Offset(13f, 17f))
    }

    // Verification of correct consumption behavior.

    @Test
    fun onPointerInput_down_downChangeConsumedDuringMain() {
        var pointer = down(0, 0.milliseconds)
        pointer = filter::onPointerInput.invokeOverPasses(
            pointer,
            PointerEventPass.Initial
        )
        assertThat(pointer.consumed.downChange, `is`(false))

        pointer = filter::onPointerInput.invoke(
            listOf(pointer),
            PointerEventPass.Main,
            IntSize(0, 0)
        ).first()
        assertThat(pointer.consumed.downChange, `is`(true))
    }

    @Test
    fun onPointerInput_disabledDown_noDownChangeConsumed() {
        filter.setEnabled(false)
        var pointer = down(0, 0.milliseconds)
        pointer = filter::onPointerInput.invokeOverAllPasses(pointer)
        assertThat(pointer.consumed.downChange, `is`(false))
    }

    // Verification of correct cancellation handling.

    @Test
    fun onCancel_justCancel_noCallbacksCalled() {
        filter.onCancel()

        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }

    @Test
    fun onCancel_downConsumedCancel_noCallbacksCalled() {
        filter::onPointerInput
            .invokeOverAllPasses(down(0, 0.milliseconds).consumeDownChange())
        filter.onCancel()

        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }

    @Test
    fun onCancel_downCancel_justStartAndCancelCalledInOrderOnce() {
        filter::onPointerInput.invokeOverAllPasses(down(0, 0.milliseconds))
        filter.onCancel()

        inOrder(filter.onStart!!, filter.onCancel!!) {
            verify(filter.onStart!!).invoke(any())
            verify(filter.onCancel!!).invoke()
        }
        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }

    @Test
    fun onCancel_downUpCancel_justStartAndStopCalledInOrderOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.up(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.onCancel()

        inOrder(filter.onStart!!, filter.onStop!!) {
            verify(filter.onStart!!).invoke(any())
            verify(filter.onStop!!).invoke()
        }
        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }

    @Test
    fun onCancel_downMoveCancel_justStartAndCancelCalledInOrderOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveTo(50.milliseconds, 1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.onCancel()

        inOrder(filter.onStart!!, filter.onCancel!!) {
            verify(filter.onStart!!).invoke(any())
            verify(filter.onCancel!!).invoke()
        }
        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }

    @Test
    fun onCancel_downMoveConsumedCancel_justStartAndCancelCalledInOrderOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        pointer = pointer.moveTo(50.milliseconds, 1f).consume(1f)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.onCancel()

        inOrder(filter.onStart!!, filter.onCancel!!) {
            verify(filter.onStart!!).invoke(any())
            verify(filter.onCancel!!).invoke()
        }
        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }

    @Test
    fun onCancel_downThenCancelThenDown_justStartCancelStartCalledInOrderOnce() {
        var pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)
        filter.onCancel()
        pointer = down(0, 0.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        inOrder(filter.onStart!!, filter.onCancel!!) {
            verify(filter.onStart!!).invoke(any())
            verify(filter.onCancel!!).invoke()
            verify(filter.onStart!!).invoke(any())
        }
        verifyNoMoreInteractions(filter.onStart, filter.onStop, filter.onCancel)
    }
}