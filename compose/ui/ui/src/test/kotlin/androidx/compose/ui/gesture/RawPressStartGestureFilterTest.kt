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

package androidx.compose.ui.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
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
class RawPressStartGestureFilterTest {

    private lateinit var filter: RawPressStartGestureFilter

    @Before
    fun setup() {
        filter = RawPressStartGestureFilter()
        filter.onPressStart = mock()
    }

    // Verification of scenarios where onPressStart should not be called.

    @Test
    fun onPointerInput_downConsumed_onPressStartNotCalled() {
        filter::onPointerInput.invokeOverAllPasses(down(0).consumeDownChange())
        verify(filter.onPressStart, never()).invoke(any())
    }

    @Test
    fun onPointerInput_downConsumedDown_onPressStartNotCalled() {
        var pointer1 = down(1, duration = 0.milliseconds).consumeDownChange()
        filter::onPointerInput.invokeOverAllPasses(pointer1)
        pointer1 = pointer1.moveBy(10.milliseconds)
        val pointer2 = down(2, duration = 10.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)
        verify(filter.onPressStart, never()).invoke(any())
    }

    @Test
    fun onPointerInput_disabledDown_onPressStartNotCalled() {
        filter.setEnabled(false)
        filter::onPointerInput.invokeOverAllPasses(down(0))
        verify(filter.onPressStart, never()).invoke(any())
    }

    @Test
    fun onPointerInput_disabledDownEnabledDown_onPressStartNotCalled() {

        filter.setEnabled(false)
        var pointer1 = down(1, duration = 0.milliseconds).consumeDownChange()
        filter::onPointerInput.invokeOverAllPasses(pointer1)
        filter.setEnabled(true)
        pointer1 = pointer1.moveBy(10.milliseconds)
        val pointer2 = down(2, duration = 10.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)
        verify(filter.onPressStart, never()).invoke(any())
    }

    // Verification of scenarios where onPressStart should be called once.

    @Test
    fun onPointerInput_down_onPressStartCalledOnce() {
        filter::onPointerInput.invokeOverAllPasses(down(0))
        verify(filter.onPressStart).invoke(any())
    }

    @Test
    fun onPointerInput_downDown_onPressStartCalledOnce() {
        var pointer0 = down(0)
        filter::onPointerInput.invokeOverAllPasses(pointer0)
        pointer0 = pointer0.moveTo(1.milliseconds)
        val pointer1 = down(1, 1.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onPressStart).invoke(any())
    }

    @Test
    fun onPointerInput_2Down1Up1Down_onPressStartCalledOnce() {
        var pointer0 = down(0)
        var pointer1 = down(1)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = pointer0.up(100.milliseconds)
        pointer1 = pointer1.moveTo(100.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)
        pointer0 = down(0, duration = 200.milliseconds)
        pointer1 = pointer1.moveTo(200.milliseconds)
        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onPressStart).invoke(any())
    }

    @Test
    fun onPointerInput_1DownMoveOutside2ndDown_onPressStartOnlyCalledOnce() {
        var pointer0 = down(0, x = 0f, y = 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer0, IntSize(5, 5))
        pointer0 = pointer0.moveTo(100.milliseconds, 10f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer0, IntSize(5, 5))
        pointer0 = pointer0.moveTo(200.milliseconds)
        val pointer1 = down(1, x = 0f, y = 0f)

        filter::onPointerInput.invokeOverAllPasses(pointer0, pointer1)

        verify(filter.onPressStart).invoke(any())
    }

    // Verification of correct position returned by onPressStart.

    @Test
    fun onPointerInput_down_downPositionIsCorrect() {
        filter::onPointerInput.invokeOverAllPasses(down(0, x = 13f, y = 17f))
        verify(filter.onPressStart).invoke(Offset(13f, 17f))
    }

    // Verification of correct consumption behavior.

    @Test
    fun onPointerInput_disabledDown_noDownChangeConsumed() {
        filter.setEnabled(false)
        var pointer = down(0)
        pointer = filter::onPointerInput.invokeOverAllPasses(pointer)
        assertThat(pointer.consumed.downChange, `is`(false))
    }

    // Verification of correct cancellation handling.

    @Test
    fun onCancel_justCancel_noCallbacksCalled() {
        filter.onCancel()

        verifyNoMoreInteractions(filter.onPressStart)
    }

    @Test
    fun onCancel_downCancelDown_onPressStartCalledTwice() {
        filter::onPointerInput.invokeOverAllPasses(down(id = 0, duration = 0.milliseconds))
        filter.onCancel()
        filter::onPointerInput.invokeOverAllPasses(down(id = 0, duration = 1.milliseconds))

        verify(filter.onPressStart, times(2)).invoke(any())
    }

    // Verification of correct execution pass behavior

    @Test
    fun onPointerInput_initial_behaviorOccursAtCorrectTime() {
        filter.setExecutionPass(PointerEventPass.Initial)

        val pointer = filter::onPointerInput.invokeOverPasses(
            down(0),
            PointerEventPass.Initial
        )

        verify(filter.onPressStart).invoke(any())
        assertThat(pointer.consumed.downChange, `is`(true))
    }

    @Test
    fun onPointerInput_Main_behaviorOccursAtCorrectTime() {
        filter.setExecutionPass(PointerEventPass.Main)

        var pointer = filter::onPointerInput.invokeOverPasses(
            down(0),
            PointerEventPass.Initial
        )

        verify(filter.onPressStart, never()).invoke(any())
        assertThat(pointer.consumed.downChange, `is`(false))

        pointer = filter::onPointerInput.invokeOverPasses(
            down(1),
            PointerEventPass.Main
        )

        verify(filter.onPressStart).invoke(any())
        assertThat(pointer.consumed.downChange, `is`(true))
    }

    @Test
    fun onPointerInput_final_behaviorOccursAtCorrectTime() {
        filter.setExecutionPass(PointerEventPass.Final)

        var pointer = filter::onPointerInput.invokeOverPasses(
            down(0),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        verify(filter.onPressStart, never()).invoke(any())
        assertThat(pointer.consumed.downChange, `is`(false))

        pointer = filter::onPointerInput.invokeOverPasses(
            down(1),
            PointerEventPass.Final
        )

        verify(filter.onPressStart).invoke(any())
        assertThat(pointer.consumed.downChange, `is`(true))
    }

    // Verification of correct cancellation behavior.

    // The purpose of this test is hard to understand, but it proves that the cancel event sets the
    // state of the gesture detector to inactive such that when a new stream of events starts,
    // and the 1st down is already consumed, the gesture detector won't consume the 2nd down.
    @Test
    fun onCancel_downCancelDownConsumedDown_thirdDownNotConsumed() {
        filter::onPointerInput
            .invokeOverAllPasses(down(id = 0, duration = 0.milliseconds))
        filter.onCancel()
        var pointer1 = down(id = 1, duration = 10.milliseconds).consumeDownChange()
        filter::onPointerInput.invokeOverAllPasses(pointer1)
        pointer1 = pointer1.moveTo(20.milliseconds, 0f, 0f)
        val pointer2 = down(id = 2, duration = 20.milliseconds)
        val results = filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        assertThat(results[0].consumed.downChange, `is`(false))
        assertThat(results[1].consumed.downChange, `is`(false))
    }
}