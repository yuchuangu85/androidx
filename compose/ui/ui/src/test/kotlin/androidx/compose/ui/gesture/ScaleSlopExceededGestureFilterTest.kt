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

import androidx.compose.ui.input.pointer.down
import androidx.compose.ui.input.pointer.invokeOverAllPasses
import androidx.compose.ui.input.pointer.moveBy
import androidx.compose.ui.input.pointer.moveTo
import androidx.compose.ui.unit.Duration
import androidx.compose.ui.unit.milliseconds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// TODO(shepshapard): Write the following tests.
// Test for cases where things are reset when last pointer goes up
// Verify all methods called during onMain

// Changing this value will break tests that expect the value to be 5.
private const val TestTouchSlop = 5

@RunWith(JUnit4::class)
class ScaleSlopExceededGestureFilterTest {

    private val onScaleSlopExceeded: () -> Unit = { onScaleSlopExceededCount++ }
    private var onScaleSlopExceededCount: Int = 0
    private lateinit var filter: ScaleSlopExceededGestureFilter

    private val TinyNum = .01f

    @Before
    fun setup() {
        onScaleSlopExceededCount = 0
        filter = ScaleSlopExceededGestureFilter(TestTouchSlop.toFloat())
        filter.onScaleSlopExceeded = onScaleSlopExceeded
    }

    // Verifies the circumstances under which onScaleSlopExceeded should not be called.

    @Test
    fun onPointerInputChanges_1PointerMoves10TimesScaleSlopInXAndY_onTouchSlopExceededNotCalled() {
        var pointer = down(0)
        filter::onPointerInput.invokeOverAllPasses(pointer)

        pointer = pointer.moveBy(
            Duration(milliseconds = 10),
            TestTouchSlop.toFloat() * 10,
            TestTouchSlop.toFloat() * 10
        )
        filter::onPointerInput.invokeOverAllPasses(pointer)

        assertThat(onScaleSlopExceededCount).isEqualTo(0)
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveAwayToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            1f, 0f,
            0f, 0f,
            11f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveAwayOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            1f, 0f,
            0f, 0f,
            11 + TinyNum, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveAwayToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            0f, 1f,
            0f, 0f,
            0f, 11f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveAwayOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            0f, 1f,
            0f, 0f,
            0f, 11 + TinyNum,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveTowardToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            11f, 0f,
            0f, 0f,
            1f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveTowardOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            11 + TinyNum, 0f,
            0f, 0f,
            1f, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveTowardToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            0f, 11f,
            0f, 0f,
            0f, 1f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers1MoveTowardOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            0f, 11 + TinyNum,
            0f, 0f,
            0f, 1f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveAwayToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            1f, 0f,
            -5f, 0f,
            6f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveAwayOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            1f, 0f,
            -5f, 0f,
            6 + TinyNum, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveAwayToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            0f, 1f,
            0f, -5f,
            0f, 6f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveAwayOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, 0f,
            0f, 1f,
            0f, -5f,
            0f, 6 + TinyNum,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveTowardToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            -5f, 0f,
            6f, 0f,
            0f, 0f,
            1f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveTowardOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            -5f, 0f,
            6 + TinyNum, 0f,
            0f, 0f,
            1f, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveTowardToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_2Pointers(
            0f, -5f,
            0f, 6f,
            0f, 0f,
            0f, 1f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_2Pointers2MoveTowardOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_2Pointers(
            0f, -5f,
            0f, 6 + TinyNum,
            0f, 0f,
            0f, 1f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveAwayToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            1f, 0f,
            2f, 0f,
            0f, 0f,
            1f, 0f,
            13.25f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveAwayOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            1f, 0f,
            2f, 0f,
            0f, 0f,
            1f, 0f,
            13.26f, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveAwayToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            0f, 1f,
            0f, 2f,
            0f, 0f,
            0f, 1f,
            0f, 13.25f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveAwayOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            0f, 1f,
            0f, 2f,
            0f, 0f,
            0f, 1f,
            0f, 13.26f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveTowardToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            1f, 0f,
            13.25f, 0f,
            0f, 0f,
            1f, 0f,
            2f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveTowardOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            1f, 0f,
            13.26f, 0f,
            0f, 0f,
            1f, 0f,
            2f, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveTowardToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            0f, 1f,
            0f, 13.25f,
            0f, 0f,
            0f, 1f,
            0f, 2f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers1MoveTowardOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            0f, 0f,
            0f, 1f,
            0f, 13.26f,
            0f, 0f,
            0f, 1f,
            0f, 2f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveAwayToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            -1f, 0f,
            0f, 0f,
            1f, 0f,
            -8.5f, 0f,
            0f, 0f,
            8.5f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveAwayOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            -1f, 0f,
            0f, 0f,
            1f, 0f,
            -8.6f, 0f,
            0f, 0f,
            8.5f, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveAwayToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            0f, -1f,
            0f, 0f,
            0f, 1f,
            0f, -8.5f,
            0f, 0f,
            0f, 8.5f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveAwayOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            0f, -1f,
            0f, 0f,
            0f, 1f,
            0f, -8.6f,
            0f, 0f,
            0f, 8.5f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveTowardToSlopX_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            -8.5f, 0f,
            0f, 0f,
            8.5f, 0f,
            -1f, 0f,
            0f, 0f,
            1f, 0f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveTowardOverSlopX_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            -8.6f, 0f,
            0f, 0f,
            8.5f, 0f,
            -1f, 0f,
            0f, 0f,
            1f, 0f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveTowardToSlopY_onTouchSlopExceededNotCalled() {
        onPointerInputChanges_3Pointers(
            0f, -8.5f,
            0f, 0f,
            0f, 8.5f,
            0f, -1f,
            0f, 0f,
            0f, 1f,
            0
        )
    }

    @Test
    fun onPointerInputChanges_3Pointers2MoveTowardOverSlopY_onTouchSlopExceededCalledOnce() {
        onPointerInputChanges_3Pointers(
            0f, -8.6f,
            0f, 0f,
            0f, 8.5f,
            0f, -1f,
            0f, 0f,
            0f, 1f,
            1
        )
    }

    @Test
    fun onPointerInputChanges_2PointersMoveAroundUnderSlop_onTouchSlopExceededNotCalled() {
        // Arrange
        var pointer1 = down(0, 0.milliseconds, 0f, 0f)
        var pointer2 = down(1, 0.milliseconds, 0f, 50f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act

        // Translate, rotate and scale up.
        pointer1 = pointer1.moveTo(
            10.milliseconds,
            70f,
            100f
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            10f,
            100f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Translate, rotate and scale down.
        pointer1 = pointer1.moveTo(
            20.milliseconds,
            -40f,
            35f
        )
        pointer2 = pointer2.moveTo(
            20.milliseconds,
            -40f,
            75f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Translate, rotate and scale up.
        pointer1 = pointer1.moveTo(
            30.milliseconds,
            -20f,
            -20f
        )
        pointer2 = pointer2.moveTo(
            30.milliseconds,
            40f,
            -20f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Translate, rotate and scale down.
        pointer1 = pointer1.moveTo(
            40.milliseconds,
            20f,
            -40f
        )
        pointer2 = pointer2.moveTo(
            40.milliseconds,
            20f,
            -80f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        assertThat(onScaleSlopExceededCount).isEqualTo(0)
    }

    @Test
    fun onPointerInputChanges_2PointersMoveOverIntoAndOverSlop_onTouchSlopExceededCalledOnce() {
        // Arrange
        var pointer1 = down(0, 0.milliseconds, 0f, 0f)
        var pointer2 = down(1, 0.milliseconds, 0f, 20f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act

        // Over
        pointer1 = pointer1.moveTo(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            0f,
            30 + TinyNum
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Under
        pointer1 = pointer1.moveTo(
            20.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            20.milliseconds,
            0f,
            30 - TinyNum
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Over
        pointer1 = pointer1.moveTo(
            30.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            30.milliseconds,
            0f,
            30 + TinyNum
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        assertThat(onScaleSlopExceededCount).isEqualTo(1)
    }

    @Test
    fun onPointerInputChanges_2PointersStepToSlopThenOverX_onTouchSlopExceededCalledOnceOver() {

        // Arrange
        var pointer1 = down(0, 0.milliseconds, 0f, 0f)
        var pointer2 = down(1, 0.milliseconds, 1f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act

        // Increment to slop, but not over.
        repeat(10) {
            pointer1 = pointer1.moveBy(
                10.milliseconds,
                0f,
                0f
            )
            pointer2 = pointer2.moveBy(
                10.milliseconds,
                1f,
                0f
            )
            filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)
        }

        // Verify that we have not gone over.
        assertThat(onScaleSlopExceededCount).isEqualTo(0)

        // Go over slop.
        pointer1 = pointer1.moveBy(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveBy(
            10.milliseconds,
            TinyNum,
            0f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Verify we have gone over.
        assertThat(onScaleSlopExceededCount).isEqualTo(1)
    }

    @Test
    fun onPointerInputChanges_2PointersStepToSlopThenOverY_onTouchSlopExceededCalledOnceOver() {
        // Arrange
        var pointer1 = down(0, 0.milliseconds, 0f, 0f)
        var pointer2 = down(1, 0.milliseconds, 0f, 1f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act

        // Increment to slop, but not over.
        repeat(10) {
            pointer1 = pointer1.moveBy(
                10.milliseconds,
                0f,
                0f
            )
            pointer2 = pointer2.moveBy(
                10.milliseconds,
                0f,
                1f
            )
            filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)
        }

        // Verify that we have not gone over.
        assertThat(onScaleSlopExceededCount).isEqualTo(0)

        // Go over slop.
        pointer1 = pointer1.moveBy(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveBy(
            10.milliseconds,
            0f,
            TinyNum
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Verify we have gone over.
        assertThat(onScaleSlopExceededCount).isEqualTo(1)
    }

    // Tests that verify correct cancelling behavior.

    @Test
    fun onCancel_scaleUnderCancelScaleUnder_onScaleSlopExceededNotCalled() {

        // Arrange

        var pointer1 = down(0, 0.milliseconds, 0f, 0f)
        var pointer2 = down(1, 0L.milliseconds, 1f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        pointer1 = pointer1.moveTo(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            11f,
            0f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act

        filter.onCancel()

        pointer1 = down(0, 0.milliseconds, 0f, 0f)
        pointer2 = down(1, 0L.milliseconds, 1f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        pointer1 = pointer1.moveTo(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            11f,
            0f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Assert

        assertThat(onScaleSlopExceededCount).isEqualTo(0)
    }

    @Test
    fun onCancel_scalePastCancelScalePast_onScaleSlopExceededCalledTwice() {

        // Arrange

        var pointer1 = down(0, 0.milliseconds, 0f, 0f)
        var pointer2 = down(1, 0L.milliseconds, 1f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        pointer1 = pointer1.moveTo(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            11 + TinyNum,
            0f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act

        filter.onCancel()

        pointer1 = down(0, 0.milliseconds, 0f, 0f)
        pointer2 = down(1, 0L.milliseconds, 1f, 0f)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        pointer1 = pointer1.moveTo(
            10.milliseconds,
            0f,
            0f
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            11 + TinyNum,
            0f
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Assert

        assertThat(onScaleSlopExceededCount).isEqualTo(2)
    }

    private fun onPointerInputChanges_2Pointers(
        x1s: Float,
        y1s: Float,
        x2s: Float,
        y2s: Float,
        x1e: Float,
        y1e: Float,
        x2e: Float,
        y2e: Float,
        expectedCound: Int
    ) {
        // Arrange
        var pointer1 = down(0, 0.milliseconds, x1s, y1s)
        var pointer2 = down(1, 0L.milliseconds, x2s, y2s)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        // Act
        pointer1 = pointer1.moveTo(
            10.milliseconds,
            x1e,
            y1e
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            x2e,
            y2e
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2)

        assertThat(onScaleSlopExceededCount).isEqualTo(expectedCound)
    }

    private fun onPointerInputChanges_3Pointers(
        x1s: Float,
        y1s: Float,
        x2s: Float,
        y2s: Float,
        x3s: Float,
        y3s: Float,
        x1e: Float,
        y1e: Float,
        x2e: Float,
        y2e: Float,
        x3e: Float,
        y3e: Float,
        expectedCound: Int
    ) {
        // Arrange
        var pointer1 = down(0, 0.milliseconds, x1s, y1s)
        var pointer2 = down(1, 0.milliseconds, x2s, y2s)
        var pointer3 = down(2, 0.milliseconds, x3s, y3s)
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2, pointer3)

        // Act
        pointer1 = pointer1.moveTo(
            10.milliseconds,
            x1e,
            y1e
        )
        pointer2 = pointer2.moveTo(
            10.milliseconds,
            x2e,
            y2e
        )
        pointer3 = pointer3.moveTo(
            10.milliseconds,
            x3e,
            y3e
        )
        filter::onPointerInput.invokeOverAllPasses(pointer1, pointer2, pointer3)

        assertThat(onScaleSlopExceededCount).isEqualTo(expectedCound)
    }
}