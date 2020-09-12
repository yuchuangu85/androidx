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

package androidx.compose.ui.input.pointer

import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import android.view.MotionEvent.TOOL_TYPE_UNKNOWN
import androidx.activity.ComponentActivity
import androidx.compose.ui.AlignmentLine
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.gesture.PointerCoords
import androidx.compose.ui.gesture.PointerProperties
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.milliseconds
import androidx.test.filters.SmallTest
import androidx.ui.test.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class PointerInteropFilterTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var pointerInteropFilter: PointerInteropFilter
    private val dispatchedMotionEvents = mutableListOf<MotionEvent>()
    private val disallowInterceptRequester = RequestDisallowInterceptTouchEvent()
    private var retVal = true

    @Before
    fun setup() {
        pointerInteropFilter = PointerInteropFilter()
        pointerInteropFilter.pointerInputFilter.layoutCoordinates = MockCoordinates()

        pointerInteropFilter.onTouchEvent = { motionEvent ->
            dispatchedMotionEvents.add(motionEvent)
            retVal
        }
        pointerInteropFilter.requestDisallowInterceptTouchEvent = disallowInterceptRequester
    }

    // Verification of correct MotionEvents being dispatched (when no events are cancel)

    @Test
    fun onPointerEvent_1PointerDown_correctMotionEventDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val expected =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
        assertThat(dispatchedMotionEvents[0]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_1PointerUp_correctMotionEventDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val downMotionEvent =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val up = down.up(5.milliseconds)
        val expected =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = downMotionEvent)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(up, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2PointersDown_correctMotionEventDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val downMotionEvent =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)

        val expected =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = downMotionEvent)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove, bDown, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2PointersDownAllPassesAltOrder_correctMotionEventDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val downMotionEvent =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)

        val expected =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                0,
                arrayOf(
                    PointerProperties(1),
                    PointerProperties(0)
                ),
                arrayOf(
                    PointerCoords(10f, 11f),
                    PointerCoords(3f, 4f)
                )
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = downMotionEvent)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bDown, aMove, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1Up_correctMotionEventDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUp = bDown.up(13.milliseconds)

        val expected =
            MotionEvent(
                13,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bUp, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
        assertThat(dispatchedMotionEvents[2]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1UpAllPassesAltOrder_correctMotionEventDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUp = bDown.up(13.milliseconds)
        val expected =
            MotionEvent(
                13,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(10f, 11f),
                    PointerCoords(3f, 4f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bUp, aMove2, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
        assertThat(dispatchedMotionEvents[2]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_1PointerMove_correctMotionEventDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val expected =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2PointersMove_correctMotionEventDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(13f, 14f),
                    PointerCoords(3f, 4f)
                )
            )

        val aMove2 = aMove1.moveTo(15.milliseconds, 8f, 9f)
        val bMove1 = bDown.moveTo(15.milliseconds, 18f, 19f)

        val expected =
            MotionEvent(
                15,
                ACTION_MOVE,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(8f, 9f),
                    PointerCoords(18f, 19f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bMove1, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
        assertThat(dispatchedMotionEvents[2]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2PointersMoveAltOrder_correctMotionEventDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(13f, 14f),
                    PointerCoords(3f, 4f)
                )
            )

        val aMove2 = aMove1.moveTo(15.milliseconds, 8f, 9f)
        val bMove1 = bDown.moveTo(15.milliseconds, 18f, 19f)
        val expected =
            MotionEvent(
                15,
                ACTION_MOVE,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(18f, 19f),
                    PointerCoords(8f, 9f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bMove1, aMove2, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
        assertThat(dispatchedMotionEvents[2]).isSameInstanceAs(expected)
    }

    // Verification of correct cancel events being dispatched

    @Test
    fun onPointerEvent_1PointerUpConsumed_correctCancelDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val upConsumed = down.up(5.milliseconds).consumeDownChange()
        val expected =
            MotionEvent(
                5,
                ACTION_CANCEL,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(upConsumed, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2PointersDown2ndDownConsumed_correctCancelDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDownConsumed = down(8, 7.milliseconds, 10f, 11f).consumeDownChange()
        val expected =
            MotionEvent(
                7,
                ACTION_CANCEL,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove, bDownConsumed, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1UpConsumed_correctCancelDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUpConsumed = bDown.up(13.milliseconds).consumeDownChange()
        val expected =
            MotionEvent(
                13,
                ACTION_CANCEL,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bUpConsumed, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
        assertThat(dispatchedMotionEvents[2]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_1PointerMoveConsumed_correctCancelDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val moveConsumed = down.moveTo(7.milliseconds, 8f, 9f).consume(1f, 0f)
        val expected =
            MotionEvent(
                7,
                ACTION_CANCEL,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(moveConsumed, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
        assertThat(dispatchedMotionEvents[1]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_2PointersMoveConsumed_correctCancelDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(13f, 14f)
                )
            )

        val aMove2 = aMove1.moveTo(15.milliseconds, 8f, 9f)
        val bMoveConsumed = bDown.moveTo(15.milliseconds, 18f, 19f).consume(1f, 0f)

        val expected =
            MotionEvent(
                15,
                ACTION_CANCEL,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(8f, 9f),
                    PointerCoords(18f, 19f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bMoveConsumed, motionEvent = expected)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
        assertThat(dispatchedMotionEvents[2]).isSameInstanceAs(expected)
    }

    // Verification of no longer dispatching to children once we have consumed events

    @Test
    fun onPointerEvent_downConsumed_nothingDispatched() {
        val downConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(downConsumed, motionEvent = motionEvent1)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_downConsumedThenMoveThenUp_nothingDispatched() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDownConsumed.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 7f))
            )

        val aUp = aMove.up(10.milliseconds)
        val motionEvent3 =
            MotionEvent(
                10,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 7f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent3)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenDown2ThenMove2ThenUp2_nothingDispatched() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDownConsumed.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        val aMove2 = aDownConsumed.moveTo(21.milliseconds, 6f, 7f)
        val bMove = bDown.moveTo(21.milliseconds, 22f, 23f)
        val motionEvent3 =
            MotionEvent(
                21,
                ACTION_MOVE,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(6f, 7f), PointerCoords(22f, 23f))
            )

        val aMove3 = aDownConsumed.moveTo(31.milliseconds, 6f, 7f)
        val bUp = bMove.up(31.milliseconds)
        val motionEvent4 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(6f, 7f), PointerCoords(22f, 23f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove3, bUp, motionEvent = motionEvent4)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenDown2ThenUp1ThenDown3_nothingDispatched() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDownConsumed.moveTo(22.milliseconds, 3f, 4f)
        val bDown = down(21, 22.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                22,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aUp = aMove1.up(31.milliseconds)
        val bMove1 = bDown.moveTo(31.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val bMove2 = bMove1.moveTo(41.milliseconds, 23f, 24f)
        val cDown = down(51, 41.milliseconds, 52f, 53f)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(1), PointerProperties(0)),
                arrayOf(PointerCoords(23f, 24f), PointerCoords(52f, 53f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, bMove1, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bMove2, cDown, motionEvent = motionEvent4)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_downThenMoveConsumedThenMoveThenUp_afterConsumeNoDispatch() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val move1Consumed = down.moveTo(5.milliseconds, 6f, 7f).consume(0f, 1f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 47f))
            )
        val move2 = move1Consumed.moveTo(10.milliseconds, 11f, 12f)
        val motionEvent3 =
            MotionEvent(
                10,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(11f, 12f))
            )
        val up = move2.up(15.milliseconds)
        val motionEvent4 =
            MotionEvent(
                15,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(11f, 12f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move1Consumed, motionEvent = motionEvent2)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move2, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(up, motionEvent = motionEvent4)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ThenDown2ConsumedThenMoveThenUp1ThenUp2_afterConsumeNoDispatch() {
        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDownConsumed = down(21, 11.milliseconds, 23f, 24f).consumeDownChange()
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aMove2 = aMove1.moveTo(31.milliseconds, 31f, 32f)
        val bMove = bDownConsumed.moveTo(31.milliseconds, 33f, 34f)
        val motionEvent3 =
            MotionEvent(
                11,
                ACTION_MOVE,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(31f, 32f), PointerCoords(33f, 34f))
            )

        val aMove3 = aMove2.moveTo(41.milliseconds, 31f, 32f)
        val bUp = bMove.up(41.milliseconds)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(31f, 32f), PointerCoords(33f, 34f))
            )

        val aUp = aMove3.up(51.milliseconds)
        val motionEvent5 =
            MotionEvent(
                51,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(31f, 32f))
            )

        // Act
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDownConsumed, motionEvent = motionEvent2)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove3, bUp, motionEvent = motionEvent4)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent5)
        )

        // Assert
        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenUp1ThenDown2_finalDownDispatched() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aUp = aDownConsumed.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(
                    PointerProperties(0)
                ),
                arrayOf(
                    PointerCoords(3f, 4f)
                )
            )

        val bDown = down(11, 12.milliseconds, 13f, 14f)
        val expected =
            MotionEvent(
                12,
                ACTION_DOWN,
                1,
                0,
                arrayOf(
                    PointerProperties(0)
                ),
                arrayOf(
                    PointerCoords(13f, 14f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bDown, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
        assertThat(dispatchedMotionEvents[0]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenDown2ThenUp1ThenUp2ThenDown3_finalDownDispatched() {

        // Arrange

        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDownConsumed.moveTo(22.milliseconds, 3f, 4f)
        val bDown = down(21, 22.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                22,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aUp = aMove1.up(31.milliseconds)
        val bMove1 = bDown.moveTo(31.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val bUp = bMove1.up(41.milliseconds)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(1)),
                arrayOf(PointerCoords(23f, 24f))
            )

        val cDown = down(51, 52.milliseconds, 53f, 54f)
        val expected =
            MotionEvent(
                52,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(53f, 54f))
            )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, bMove1, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bUp, motionEvent = motionEvent4)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(cDown, motionEvent = expected)
        )

        // Assert
        assertThat(dispatchedMotionEvents).hasSize(1)
        assertThat(dispatchedMotionEvents[0]).isSameInstanceAs(expected)
    }

    // Verification no longer dispatching to children due to the child returning false for
    // dispatchTouchEvent(...)

    @Test
    fun onPointerEvent_downViewRetsFalseThenMoveThenUp_noDispatchAfterRetFalse() {
        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                2,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 7f))
            )

        val aUp = aMove.up(10.milliseconds)
        val motionEvent3 =
            MotionEvent(
                10,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 7f))
            )

        retVal = false

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent3)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ViewRetsFalseThenDown2ThenMove2ThenUp2_noDispatchAfterRetFalse() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        val aMove2 = aDown.moveTo(21.milliseconds, 6f, 7f)
        val bMove = bDown.moveTo(21.milliseconds, 22f, 23f)
        val motionEvent3 =
            MotionEvent(
                21,
                ACTION_MOVE,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(6f, 7f), PointerCoords(22f, 23f))
            )

        val aMove3 = aDown.moveTo(31.milliseconds, 6f, 7f)
        val bUp = bMove.up(31.milliseconds)
        val motionEvent4 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(6f, 7f), PointerCoords(22f, 23f))
            )

        retVal = false

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove3, bUp, motionEvent = motionEvent4)
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ViewRetsFalseThenDown2ThenUp1ThenDown3_noDispatchAfterRetFalse() {
        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDown = down(21, 11.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                2,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aUp = aMove1.up(31.milliseconds)
        val bMove1 = bDown.moveTo(31.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val bMove2 = bMove1.moveTo(41.milliseconds, 23f, 24f)
        val cDown = down(51, 41.milliseconds, 52f, 53f)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(1), PointerProperties(0)),
                arrayOf(PointerCoords(23f, 24f), PointerCoords(52f, 53f))
            )

        retVal = false

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, bMove1, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bMove2, cDown, motionEvent = motionEvent4)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_downThenMoveViewRetsFalseThenMoveThenUp_noDispatchAfterRetFalse() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val move1 = down.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 47f))
            )

        val move2 = move1.moveTo(10.milliseconds, 11f, 12f)
        val motionEvent3 =
            MotionEvent(
                10,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(11f, 12f))
            )

        val up = move2.up(15.milliseconds)
        val motionEvent4 =
            MotionEvent(
                15,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(11f, 12f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move1, motionEvent = motionEvent2)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move2, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(up, motionEvent = motionEvent4)
        )

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ThenDown2ViewRetsFalseThenMoveThenUp1ThenUp2_noDispatchAfterRetFalse() {
        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDown = down(21, 11.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                2,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aMove2 = aMove1.moveTo(31.milliseconds, 31f, 32f)
        val bMove = bDown.moveTo(31.milliseconds, 33f, 34f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_MOVE,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(31f, 32f), PointerCoords(33f, 34f))
            )

        val aMove3 = aMove2.moveTo(41.milliseconds, 31f, 32f)
        val bUp = bMove.up(41.milliseconds)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(31f, 32f), PointerCoords(33f, 34f))
            )

        val aUp = aMove3.up(51.milliseconds)
        val motionEvent5 =
            MotionEvent(
                51,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(31f, 32f))
            )

        // Act
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove3, bUp, motionEvent = motionEvent4)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent5)
        )

        // Assert
        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onPointerEvent_down1ViewRetsFalseThenUp1ThenDown2_finalDownDispatched() {
        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aUp = aDown.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val bDown = down(11, 12.milliseconds, 13f, 14f)
        retVal = false
        val expected =
            MotionEvent(
                12,
                ACTION_DOWN,
                1,
                0,
                arrayOf(
                    PointerProperties(0)
                ),
                arrayOf(
                    PointerCoords(13f, 14f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bDown, motionEvent = expected)
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
        assertThat(dispatchedMotionEvents[0]).isSameInstanceAs(expected)
    }

    @Test
    fun onPointerEvent_down1ViewRetsFalseThenDown2ThenUp1ThenUp2ThenDown3_finalDownDispatched() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDown = down(21, 11.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aUp = aMove1.up(31.milliseconds)
        val bMove1 = bDown.moveTo(31.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val bUp = bMove1.up(41.milliseconds)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(1)),
                arrayOf(PointerCoords(23f, 24f))
            )

        val cDown = down(51, 52.milliseconds, 53f, 54f)
        val expected =
            MotionEvent(
                52,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(53f, 54f))
            )

        // Act

        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, bMove1, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bUp, motionEvent = motionEvent4)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(cDown, motionEvent = expected)
        )

        // Assert
        assertThat(dispatchedMotionEvents).hasSize(1)
        assertThat(dispatchedMotionEvents[0]).isSameInstanceAs(expected)
    }

    // Verification of correct consumption due to the return value of View.dispatchTouchEvent(...).
    // If a view returns false, nothing should be consumed.  If it returns true, everything that can
    // be consumed should be consumed.

    @Test
    fun onPointerEvent_1PointerDownViewRetsFalse_nothingConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        retVal = false

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(down, motionEvent = motionEvent1)
            )

        assertThat(actual.first()).isEqualTo(down)
    }

    @Test
    fun onPointerEvent_1PointerDownViewRetsTrue_everythingConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        retVal = true
        val expected = down.consumeAllChanges()

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(down, motionEvent = motionEvent1)
            )

        assertThat(actual.first()).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_1PointerUpViewRetsFalse_nothingConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val up = down.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        retVal = true
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        retVal = false
        val actual = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(up, motionEvent = motionEvent2)
        )

        assertThat(actual.first()).isEqualTo(up)
    }

    @Test
    fun onPointerEvent_1PointerUpViewRetsTrue_everythingConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val up = down.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val expected = up.consumeAllChanges()
        retVal = true
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(up, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2PointersDownViewRetsFalse_nothingConsumed() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        retVal = true

        val expected = listOf(aMove, bDown)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        // Act

        retVal = false
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove, bDown, motionEvent = motionEvent2)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2PointersDownViewRetsTrue_everythingConsumed() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        retVal = true

        val expected = listOf(aMove.consumeAllChanges(), bDown.consumeAllChanges())

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        // Act

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove, bDown, motionEvent = motionEvent2)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1UpViewRetsFalse_nothingConsumed() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUp = bDown.up(13.milliseconds)
        val motionEvent3 =
            MotionEvent(
                13,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        retVal = true

        val expected = listOf(aMove2, bUp)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        retVal = false
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bUp, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1UpViewRetsTrue_everythingConsumed() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUp = bDown.up(13.milliseconds)
        val motionEvent3 =
            MotionEvent(
                13,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        retVal = true

        val expected = listOf(aMove2.consumeAllChanges(), bUp.consumeAllChanges())

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bUp, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_1PointerMoveViewRetsFalse_nothingConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )
        retVal = true
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        retVal = false
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(move, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(move)
    }

    @Test
    fun onPointerEvent_1PointerMoveViewRetsTrue_everythingConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )
        retVal = true
        val expected = move.consumeAllChanges()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(move, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2PointersMoveViewRetsFalse_nothingConsumed() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(13f, 14f)
                )
            )

        val aMove2 = aMove1.moveBy(15.milliseconds, 8f, 9f)
        val bMove1 = bDown.moveBy(15.milliseconds, 18f, 19f)
        val motionEvent3 =
            MotionEvent(
                15,
                ACTION_MOVE,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(8f, 9f),
                    PointerCoords(18f, 19f)
                )
            )

        retVal = true

        val expected = listOf(aMove2, bMove1)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        retVal = false
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMove1, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2PointersMoveViewRetsTrue_everythingConsumed() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(13f, 14f)
                )
            )

        val aMove2 = aMove1.moveBy(15.milliseconds, 8f, 9f)
        val bMove1 = bDown.moveBy(15.milliseconds, 18f, 19f)
        val motionEvent3 =
            MotionEvent(
                15,
                ACTION_MOVE,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(8f, 9f),
                    PointerCoords(18f, 19f)
                )
            )

        retVal = true

        val expected = listOf(aMove2.consumeAllChanges(), bMove1.consumeAllChanges())

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMove1, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    // Verification of no further consumption after initial consumption (because if something was
    // consumed, we should prevent view from getting dispatched to and thus nothing additional
    // should be consumed).

    @Test
    fun onPointerEvent_downConsumedThenMove_noAdditionalConsumption() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val aMove = aDownConsumed.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 7f))
            )
        retVal = true

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(aMove)
    }

    @Test
    fun onPointerEvent_downConsumedThenUp_noAdditionalConsumption() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aUp = aDownConsumed.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(
                    PointerProperties(0)
                ),
                arrayOf(
                    PointerCoords(3f, 4f)
                )
            )
        retVal = true

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aUp, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(aUp)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenDown2_noAdditionalConsumption() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val aMove1 = aDownConsumed.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        val expected = listOf(aMove1, bDown)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
            )

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenDown2ThenMove_noAdditionalConsumption() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val aMove1 = aDownConsumed.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )
        val aMove2 = aDownConsumed.moveTo(21.milliseconds, 6f, 7f)
        val bMove = bDown.moveTo(21.milliseconds, 22f, 23f)
        val motionEvent3 =
            MotionEvent(
                5,
                ACTION_MOVE,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(6f, 7f), PointerCoords(22f, 23f))
            )
        val expected = listOf(aMove2, bMove)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
            )

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1MoveConsumed_noAdditionalConsumption() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(13f, 14f)
                )
            )

        val aMove2 = aMove1.moveTo(15.milliseconds, 8f, 9f)
        val bMoveConsumed = bDown.moveTo(15.milliseconds, 18f, 19f).consume(1f, 0f)
        val motionEvent3 =
            MotionEvent(
                7,
                ACTION_MOVE,
                2,
                1,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(8f, 9f),
                    PointerCoords(18f, 19f)
                )
            )

        val expected = listOf(aMove2, bMoveConsumed)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMoveConsumed, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_down1ThenDown2ConsumedThenMove_noAdditionalConsumption() {
        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDownConsumed = down(21, 11.milliseconds, 23f, 24f).consumeDownChange()
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aMove2 = aMove1.moveTo(31.milliseconds, 31f, 32f)
        val bMove = bDownConsumed.moveTo(31.milliseconds, 33f, 34f)
        val motionEvent3 =
            MotionEvent(
                11,
                ACTION_MOVE,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(31f, 32f), PointerCoords(33f, 34f))
            )

        val expected = listOf(aMove2, bMove)

        // Act
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDownConsumed, motionEvent = motionEvent2)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
            )

        // Assert
        assertThat(actual).isEqualTo(expected)
    }

    // Verifies resetting of consumption.

    @Test
    fun onPointerEvent_down1ConsumedThenUp1ThenDown2_finalDownConsumed() {
        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val aUp = aDownConsumed.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(
                    PointerProperties(0)
                ),
                arrayOf(
                    PointerCoords(3f, 4f)
                )
            )
        val bDown = down(11, 12.milliseconds, 13f, 14f)
        val motionEvent3 =
            MotionEvent(
                12,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(13f, 14f))
            )
        val expected = bDown.consumeAllChanges()

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, motionEvent = motionEvent2)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(bDown, motionEvent = motionEvent3)
            )

        assertThat(actual.first()).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_down1ConsumedThenDown2ThenUp1ThenUp2ThenDown3_finalDownConsumed() {

        // Arrange

        val aDownConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDownConsumed.moveTo(22.milliseconds, 3f, 4f)
        val bDown = down(21, 22.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                22,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aUp = aMove1.up(31.milliseconds)
        val bMove1 = bDown.moveTo(31.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val bUp = bMove1.up(41.milliseconds)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(1)),
                arrayOf(PointerCoords(23f, 24f))
            )

        val cDown = down(51, 52.milliseconds, 53f, 54f)
        val motionEvent5 =
            MotionEvent(
                52,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(53f, 54f))
            )

        val expected = cDown.consumeAllChanges()

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDownConsumed, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, bMove1, motionEvent = motionEvent3)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(bUp, motionEvent = motionEvent4)
        )
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(cDown, motionEvent = motionEvent5)
            )

        // Assert
        assertThat(actual.first()).isEqualTo(expected)
    }

    // Verification of consumption when the view rets false and then is set to return true.

    @Test
    fun onPointerEvent_viewRetsFalseDownThenViewRetsTrueMove_noConsumptionOfMove() {
        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val aMove = aDown.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                2,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 7f))
            )
        retVal = false

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(aMove)
    }

    @Test
    fun onPointerEvent_viewRetsFalseDownThenViewRetsTrueUp_noConsumptionOfUp() {
        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val aUp = aDown.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        retVal = false

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aUp, motionEvent = motionEvent2)
            )

        assertThat(actual.first()).isEqualTo(aUp)
    }

    @Test
    fun onPointerEvent_viewRestsFalseDown1ThenViewRetsTrueDown2_noConsumptionOfDown2() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        retVal = false

        val expected = listOf(aMove1, bDown)

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_viewRestsFalseDown1ThenViewRetsTrueDown2TheMove_noConsumptionOfMove() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        val aMove2 = aMove1.moveTo(21.milliseconds, 22f, 23f)
        val bMove1 = bDown.moveBy(21.milliseconds, 24f, 25f)
        val motionEvent3 =
            MotionEvent(
                5,
                ACTION_MOVE,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(22f, 23f), PointerCoords(24f, 25f))
            )

        retVal = false

        val expected = listOf(aMove2, bMove1)

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMove1, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_viewRestsFalseDown1ThenViewRetsTrueDown2TheUp2_noConsumptionOfUp() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(5.milliseconds, 3f, 4f)
        val bDown = down(11, 5.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        val aMove2 = aMove1.moveTo(21.milliseconds, 3f, 4f)
        val bUp = bDown.up(21.milliseconds)
        val motionEvent3 =
            MotionEvent(
                21,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        retVal = false

        val expected = listOf(aMove2, bUp)

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bUp, motionEvent = motionEvent3)
            )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_down1ViewRetsFalseThenViewRestsTrueDown2ThenUp1ThenDown3_down3NotConsumed() {
        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDown = down(21, 11.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                2,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aUp = aMove1.up(31.milliseconds)
        val bMove1 = bDown.moveTo(31.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val bMove2 = bMove1.moveTo(41.milliseconds, 23f, 24f)
        val cDown = down(51, 41.milliseconds, 52f, 53f)
        val motionEvent4 =
            MotionEvent(
                41,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(1), PointerProperties(0)),
                arrayOf(PointerCoords(23f, 24f), PointerCoords(52f, 53f))
            )

        retVal = false

        val expected = listOf(bMove2, cDown)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aUp, bMove1, motionEvent = motionEvent3)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(bMove2, cDown, motionEvent = motionEvent4)
            )

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_downThenMoveViewRetsFalseThenViewRetsTrueMove_moveNotConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move1 = down.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 47f))
            )
        val move2 = move1.moveTo(10.milliseconds, 11f, 12f)
        val motionEvent3 =
            MotionEvent(
                10,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(11f, 12f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move1, motionEvent = motionEvent2)
        )
        retVal = true
        val actual = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move2, motionEvent = motionEvent3)
        )

        assertThat(actual.first()).isEqualTo(move2)
    }

    @Test
    fun onPointerEvent_downThenMoveViewRetsFalseThenViewRetsTrueThenUp_UpNotConsumed() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move1 = down.moveTo(5.milliseconds, 6f, 7f)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 47f))
            )
        val up = move1.up(10.milliseconds)
        val motionEvent3 =
            MotionEvent(
                10,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(6f, 47f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(move1, motionEvent = motionEvent2)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(up, motionEvent = motionEvent3)
            )

        assertThat(actual.first()).isEqualTo(up)
    }

    @Test
    fun onPointerEvent_down1ThenDown2ViewRetsFalseThenViewRetsTrueMove_moveNotConsumed() {
        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDown = down(21, 11.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                2,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aMove2 = aMove1.moveTo(31.milliseconds, 31f, 32f)
        val bMove = bDown.moveTo(31.milliseconds, 33f, 34f)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_MOVE,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(31f, 32f), PointerCoords(33f, 34f))
            )

        val expected = listOf(aMove2, bMove)

        // Act
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bMove, motionEvent = motionEvent3)
            )

        // Assert
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_down1ThenDown2ViewRetsFalseThenViewRetsTrueUp2_moveNotConsumed() {
        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(11.milliseconds, 3f, 4f)
        val bDown = down(21, 11.milliseconds, 23f, 24f)
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_POINTER_DOWN,
                2,
                2,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val aMove2 = aMove1.moveTo(31.milliseconds, 31f, 32f)
        val bUp = bDown.up(31.milliseconds)
        val motionEvent3 =
            MotionEvent(
                31,
                ACTION_POINTER_UP,
                2,
                2,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(23f, 24f))
            )

        val expected = listOf(aMove2, bUp)

        // Act
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        retVal = false
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )
        retVal = true
        val actual =
            pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
                pointerEventOf(aMove2, bUp, motionEvent = motionEvent3)
            )

        // Assert
        assertThat(actual).isEqualTo(expected)
    }

    // Verification of correct passes being used

    @Test
    fun onPointerEvent_1PointerDown_dispatchedDuringInitialTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(down, motionEvent = motionEvent1),
            PointerEventPass.Initial
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onPointerEvent_1PointerUp_dispatchedDuringInitialTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val up = down.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(up, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
    }

    @Test
    fun onPointerEvent_2PointersDown_dispatchedDuringInitialTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove, bDown, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(2)
    }

    @Test
    fun onPointerEvent_2Pointers1Up_dispatchedDuringInitialTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUp = bDown.up(13.milliseconds)
        val motionEvent3 =
            MotionEvent(
                13,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove2, bUp, motionEvent = motionEvent3),
            PointerEventPass.Initial
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
    }

    @Test
    fun onPointerEvent_pointerMove_dispatchedDuringPostTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        assertThat(dispatchedMotionEvents).hasSize(0)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Final
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onPointerEvent_downDisallowInterceptRequestedMove_moveDispatchedDuringInitialTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()

        disallowInterceptRequester.invoke(true)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onPointerEvent_disallowInterceptRequestedUpDownMove_moveDispatchedDuringPostTunnel() {
        val downA = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val upA = downA.up(11.milliseconds)
        val motionEvent2 =
            MotionEvent(
                11,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val downB = down(21, 22.milliseconds, 23f, 24f)
        val motionEvent3 =
            MotionEvent(
                21,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(23f, 24f))
            )
        val moveB = downB.moveTo(31.milliseconds, 32f, 33f)
        val motionEvent4 =
            MotionEvent(
                31,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(32f, 33f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(downA, motionEvent = motionEvent1)
        )
        disallowInterceptRequester.invoke(true)
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(upA, motionEvent = motionEvent2)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(downB, motionEvent = motionEvent3)
        )
        dispatchedMotionEvents.clear()

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(moveB, motionEvent = motionEvent4),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        assertThat(dispatchedMotionEvents).hasSize(0)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(moveB, motionEvent = motionEvent4),
            PointerEventPass.Final
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onPointerEvent_disallowInterceptTrueThenFalseThenMove_moveDispatchedDuringPostTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        disallowInterceptRequester.invoke(true)
        disallowInterceptRequester.invoke(false)
        dispatchedMotionEvents.clear()

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        assertThat(dispatchedMotionEvents).hasSize(0)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Final
        )

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onPointerEvent_1PointerUpConsumed_dispatchDuringInitialTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val upConsumed = down.up(5.milliseconds).consumeDownChange()
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(upConsumed, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
    }

    @Test
    fun onPointerEvent_2PointersDown2ndDownConsumed_dispatchDuringInitialTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDownConsumed = down(8, 7.milliseconds, 10f, 11f).consumeDownChange()
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_CANCEL,
                2,
                0,
                arrayOf(
                    PointerProperties(0),
                    PointerProperties(1)
                ),
                arrayOf(
                    PointerCoords(3f, 4f),
                    PointerCoords(10f, 11f)
                )
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove, bDownConsumed, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(2)
    }

    @Test
    fun onPointerEvent_2Pointers1UpConsumed_dispatchDuringInitialTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUpConsumed = bDown.up(13.milliseconds).consumeDownChange()
        val motionEvent3 =
            MotionEvent(
                13,
                ACTION_CANCEL,
                2,
                0,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove2, bUpConsumed, motionEvent = motionEvent3),
            PointerEventPass.Initial
        )

        // Assert

        assertThat(dispatchedMotionEvents).hasSize(3)
    }

    @Test
    fun onPointerEvent_1PointerMoveConsumed_dispatchDuringPostTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val moveConsumed = down.moveTo(7.milliseconds, 8f, 9f).consume(1f, 0f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_CANCEL,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(moveConsumed, motionEvent = motionEvent2),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        assertThat(dispatchedMotionEvents).hasSize(1)

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(moveConsumed, motionEvent = motionEvent2),
            PointerEventPass.Final
        )

        assertThat(dispatchedMotionEvents).hasSize(2)
    }

    @Test
    fun onPointerEvent_2PointersMoveConsumed_dispatchDuringPostTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(11, 7.milliseconds, 13f, 14f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(13f, 14f))
            )

        val aMove2 = aMove1.moveTo(15.milliseconds, 8f, 9f)
        val bMoveConsumed = bDown.moveTo(15.milliseconds, 18f, 19f).consume(1f, 0f)
        val motionEvent3 =
            MotionEvent(
                7,
                ACTION_MOVE,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(8f, 9f), PointerCoords(18f, 19f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act 1

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove2, bMoveConsumed, motionEvent = motionEvent3),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        // Assert 1

        assertThat(dispatchedMotionEvents).hasSize(2)

        // Act 2

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove2, bMoveConsumed, motionEvent = motionEvent3),
            PointerEventPass.Final
        )

        // Assert 2

        assertThat(dispatchedMotionEvents).hasSize(3)
    }

    @Test
    fun onPointerEvent_1PointerDown_consumedDuringInitialTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val expected = down.consumeAllChanges()

        val actual = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(down, motionEvent = motionEvent1),
            PointerEventPass.Initial
        )

        assertThat(actual.first()).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_1PointerUp_consumedDuringInitialTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val up = down.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val expected = up.consumeAllChanges()
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        val actual = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(up, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        assertThat(actual.first()).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2PointersDown_consumedDuringInitialTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val expected = listOf(aMove, bDown).map { it.consumeAllChanges() }

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )

        // Act

        val actual = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove, bDown, motionEvent = motionEvent2),
            PointerEventPass.Initial
        )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_2Pointers1Up_consumedDuringInitialTunnel() {

        // Arrange

        val aDown = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        val aMove1 = aDown.moveTo(7.milliseconds, 3f, 4f)
        val bDown = down(8, 7.milliseconds, 10f, 11f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_POINTER_DOWN,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val aMove2 = aMove1.moveTo(13.milliseconds, 3f, 4f)
        val bUp = bDown.up(13.milliseconds)
        val motionEvent3 =
            MotionEvent(
                13,
                ACTION_POINTER_UP,
                2,
                1,
                arrayOf(PointerProperties(0), PointerProperties(1)),
                arrayOf(PointerCoords(3f, 4f), PointerCoords(10f, 11f))
            )

        val expected = listOf(aMove2, bUp).map { it.consumeAllChanges() }

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aDown, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(aMove1, bDown, motionEvent = motionEvent2)
        )

        // Act

        val actual = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(aMove2, bUp, motionEvent = motionEvent3),
            PointerEventPass.Initial
        )

        // Assert

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun onPointerEvent_pointerMove_consumedDuringPostTunnel() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val move = down.moveTo(7.milliseconds, 8f, 9f)
        val motionEvent2 =
            MotionEvent(
                7,
                ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(8f, 9f))
            )

        val expected2 = move.consumeAllChanges()

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        val actual1 = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Initial,
            PointerEventPass.Main
        )

        assertThat(actual1.first()).isEqualTo(move)

        val actual2 = pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverPasses(
            pointerEventOf(move, motionEvent = motionEvent2),
            PointerEventPass.Final
        )

        assertThat(actual2.first()).isEqualTo(expected2)
    }

    @Test
    fun onCancel_cancelEventIsCorrect() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )

        pointerInteropFilter.pointerInputFilter.onCancel()

        assertThat(dispatchedMotionEvents).hasSize(2)
        val cancelEvent = dispatchedMotionEvents[1]

        assertThat(cancelEvent.actionMasked).isEqualTo(ACTION_CANCEL)
        assertThat(cancelEvent.actionIndex).isEqualTo(0)
        assertThat(cancelEvent.pointerCount).isEqualTo(1)

        val actualPointerProperties = MotionEvent.PointerProperties()
        cancelEvent.getPointerProperties(0, actualPointerProperties)
        assertThat(actualPointerProperties.id).isEqualTo(0)
        assertThat(actualPointerProperties.toolType).isEqualTo(TOOL_TYPE_UNKNOWN)

        val actualPointerCoords = MotionEvent.PointerCoords()
        cancelEvent.getPointerCoords(0, actualPointerCoords)
        assertThat(actualPointerCoords.x).isEqualTo(0)
        assertThat(actualPointerCoords.y).isEqualTo(0)
    }

    @Test
    fun onCancel_noPointers_cancelNotDispatched() {
        pointerInteropFilter.pointerInputFilter.onCancel()
        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onCancel_downConsumedCancel_cancelNotDispatched() {
        val downConsumed = down(1, 2.milliseconds, 3f, 4f).consumeDownChange()
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(downConsumed, motionEvent = motionEvent1)
        )

        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onCancel_downViewRetsFalseThenCancel_cancelNotDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        retVal = false

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onCancel_downThenUpOnCancel_cancelNotDispatched() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val up = down.up(5.milliseconds)
        val motionEvent2 =
            MotionEvent(
                5,
                ACTION_UP,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(up, motionEvent = motionEvent2)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()

        assertThat(dispatchedMotionEvents).hasSize(0)
    }

    @Test
    fun onCancel_downThenOnCancel_cancelDispatchedOnce() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onCancel_downThenOnCancelThenOnCancel_cancelDispatchedOnce() {
        val down = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()
        pointerInteropFilter.pointerInputFilter.onCancel()

        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    @Test
    fun onCancel_downThenOnCancelThenDownThenOnCancel_cancelDispatchedTwice() {
        val down1 = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent1 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )
        val down2 = down(1, 2.milliseconds, 3f, 4f)
        val motionEvent2 =
            MotionEvent(
                2,
                ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(3f, 4f))
            )

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down1, motionEvent = motionEvent1)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()
        assertThat(dispatchedMotionEvents).hasSize(1)
        dispatchedMotionEvents.clear()

        pointerInteropFilter.pointerInputFilter::onPointerEvent.invokeOverAllPasses(
            pointerEventOf(down2, motionEvent = motionEvent2)
        )
        dispatchedMotionEvents.clear()
        pointerInteropFilter.pointerInputFilter.onCancel()
        assertThat(dispatchedMotionEvents).hasSize(1)
    }

    private class MockCoordinates : LayoutCoordinates {
        override val size: IntSize
            get() = IntSize.Zero
        override val providedAlignmentLines: Set<AlignmentLine>
            get() = emptySet()
        override val parentCoordinates: LayoutCoordinates?
            get() = null
        override val isAttached: Boolean
            get() = true
        override fun globalToLocal(global: Offset): Offset = Offset.Zero

        override fun localToGlobal(local: Offset): Offset = Offset.Zero

        override fun localToRoot(local: Offset): Offset = Offset.Zero

        override fun childToLocal(child: LayoutCoordinates, childLocal: Offset): Offset =
            Offset.Zero

        override fun childBoundingBox(child: LayoutCoordinates): Rect = Rect.Zero

        override fun get(line: AlignmentLine): Int = 0
    }
}

// Private helper functions

private fun MotionEvent(
    eventTime: Int,
    action: Int,
    numPointers: Int,
    actionIndex: Int,
    pointerProperties: Array<MotionEvent.PointerProperties>,
    pointerCoords: Array<MotionEvent.PointerCoords>,
    downTime: Long = 0
) = MotionEvent.obtain(
    downTime,
    eventTime.toLong(),
    action + (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
    numPointers,
    pointerProperties,
    pointerCoords,
    0,
    0,
    0f,
    0f,
    0,
    0,
    0,
    0
)

private typealias PointerEventHandler =
            (PointerEvent, PointerEventPass, IntSize) -> List<PointerInputChange>

private fun PointerEventHandler.invokeOverAllPasses(
    pointerEvent: PointerEvent,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(
    pointerEvent,
    listOf(
        PointerEventPass.Initial,
        PointerEventPass.Main,
        PointerEventPass.Final
    ),
    size = size
)

private fun PointerEventHandler.invokeOverPasses(
    pointerEvent: PointerEvent,
    vararg pointerEventPasses: PointerEventPass,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(pointerEvent, pointerEventPasses.toList(), size)

private fun PointerEventHandler.invokeOverPasses(
    pointerEvent: PointerEvent,
    pointerEventPasses: List<PointerEventPass>,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
): List<PointerInputChange> {
    require(pointerEventPasses.isNotEmpty())
    var localChanges: List<PointerInputChange> = pointerEvent.changes
    pointerEventPasses.forEach {
        localChanges = this.invoke(
            PointerEvent(
                localChanges,
                pointerEvent.motionEvent
            ), it, size)
    }
    return localChanges
}

private fun pointerEventOf(vararg changes: PointerInputChange, motionEvent: MotionEvent? = null) =
    PointerEvent(
        changes.toList(),
        motionEvent = motionEvent
    )