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

package androidx.compose.ui.input.pointer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Uptime
import androidx.compose.ui.unit.milliseconds
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// TODO(shepshapard): Write the following tests when functionality is done.
// consumeDownChange_noChangeOccurred_throwsException
// consumeDownChange_alreadyConsumed_throwsException
// consumePositionChange_noChangeOccurred_throwsException
// consumePositionChange_changeOverConsumed_throwsException
// consumePositionChange_consumedInWrongDirection_throwsException

@SmallTest
@RunWith(JUnit4::class)
class PointerInputTest {

    @Test
    fun changedToDown_didNotChange_returnsFalse() {
        val pointerInputChange1 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, false, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, false)
        val pointerInputChange3 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, true, 0f, 0f, false)

        assertThat(pointerInputChange1.changedToDown(), `is`(false))
        assertThat(pointerInputChange2.changedToDown(), `is`(false))
        assertThat(pointerInputChange3.changedToDown(), `is`(false))
    }

    @Test
    fun changedToDown_changeNotConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, false)
        assertThat(pointerInputChange.changedToDown(), `is`(true))
    }

    @Test
    fun changedToDown_changeNotConsumed_returnsFalse() {
        val pointerInputChange = createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, true)
        assertThat(pointerInputChange.changedToDown(), `is`(false))
    }

    @Test
    fun changedToDownIgnoreConsumed_didNotChange_returnsFalse() {
        val pointerInputChange1 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, false, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, false)
        val pointerInputChange3 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, true, 0f, 0f, false)

        assertThat(
            pointerInputChange1.changedToDownIgnoreConsumed(),
            `is`(false)
        )
        assertThat(
            pointerInputChange2.changedToDownIgnoreConsumed(),
            `is`(false)
        )
        assertThat(
            pointerInputChange3.changedToDownIgnoreConsumed(),
            `is`(false)
        )
    }

    @Test
    fun changedToDownIgnoreConsumed_changedNotConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, false)
        assertThat(
            pointerInputChange.changedToDownIgnoreConsumed(),
            `is`(true)
        )
    }

    @Test
    fun changedToDownIgnoreConsumed_changedConsumed_returnsTrue() {
        val pointerInputChange = createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, true)
        assertThat(
            pointerInputChange.changedToDownIgnoreConsumed(),
            `is`(true)
        )
    }

    @Test
    fun changedToUp_didNotChange_returnsFalse() {
        val pointerInputChange1 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, false, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, false)
        val pointerInputChange3 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, true, 0f, 0f, false)

        assertThat(pointerInputChange1.changedToUp(), `is`(false))
        assertThat(pointerInputChange2.changedToUp(), `is`(false))
        assertThat(pointerInputChange3.changedToUp(), `is`(false))
    }

    @Test
    fun changedToUp_changeNotConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, false)
        assertThat(pointerInputChange.changedToUp(), `is`(true))
    }

    @Test
    fun changedToUp_changeNotConsumed_returnsFalse() {
        val pointerInputChange = createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, true)
        assertThat(pointerInputChange.changedToUp(), `is`(false))
    }

    @Test
    fun changedToUpIgnoreConsumed_didNotChange_returnsFalse() {
        val pointerInputChange1 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, false, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, false)
        val pointerInputChange3 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, true, 0f, 0f, false)

        assertThat(
            pointerInputChange1.changedToUpIgnoreConsumed(),
            `is`(false)
        )
        assertThat(
            pointerInputChange2.changedToUpIgnoreConsumed(),
            `is`(false)
        )
        assertThat(
            pointerInputChange3.changedToUpIgnoreConsumed(),
            `is`(false)
        )
    }

    @Test
    fun changedToUpIgnoreConsumed_changedNotConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.changedToUpIgnoreConsumed(),
            `is`(true)
        )
    }

    @Test
    fun changedToUpIgnoreConsumed_changedConsumed_returnsTrue() {
        val pointerInputChange = createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, true)
        assertThat(
            pointerInputChange.changedToUpIgnoreConsumed(),
            `is`(true)
        )
    }

    // TODO(shepshapard): Test more variations of positions?

    @Test
    fun positionChange_didNotChange_returnsZeroOffset() {
        val pointerInputChange =
            createPointerInputChange(11f, 13f, true, 11f, 13f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.positionChange(),
            `is`(equalTo(Offset.Zero))
        )
    }

    @Test
    fun positionChange_changedNotConsumed_returnsFullOffset() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.positionChange(),
            `is`(equalTo(Offset(6f, 12f)))
        )
    }

    @Test
    fun positionChange_changedPartiallyConsumed_returnsRemainder() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 9f, false)
        assertThat(
            pointerInputChange.positionChange(),
            `is`(equalTo(Offset(1f, 3f)))
        )
    }

    @Test
    fun positionChange_changedFullConsumed_returnsZeroOffset() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 12f, false)
        assertThat(
            pointerInputChange.positionChange(),
            `is`(equalTo(Offset.Zero))
        )
    }

    @Test
    fun positionChangeIgnoreConsumed_didNotChange_returnsZeroOffset() {
        val pointerInputChange =
            createPointerInputChange(11f, 13f, true, 11f, 13f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.positionChangeIgnoreConsumed(),
            `is`(equalTo(Offset.Zero))
        )
    }

    @Test
    fun positionChangeIgnoreConsumed_changedNotConsumed_returnsFullOffset() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.positionChangeIgnoreConsumed(),
            `is`(equalTo(Offset(6f, 12f)))
        )
    }

    @Test
    fun positionChangeIgnoreConsumed_changedPartiallyConsumed_returnsFullOffset() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 9f, false)
        assertThat(
            pointerInputChange.positionChangeIgnoreConsumed(),
            `is`(equalTo(Offset(6f, 12f)))
        )
    }

    @Test
    fun positionChangeIgnoreConsumed_changedFullConsumed_returnsFullOffset() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 12f, false)
        assertThat(
            pointerInputChange.positionChangeIgnoreConsumed(),
            `is`(equalTo(Offset(6f, 12f)))
        )
    }

    @Test
    fun positionChanged_didNotChange_returnsFalse() {
        val pointerInputChange =
            createPointerInputChange(11f, 13f, true, 11f, 13f, true, 0f, 0f, false)
        assertThat(pointerInputChange.positionChanged(), `is`(false))
    }

    @Test
    fun positionChanged_changedNotConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)
        assertThat(pointerInputChange.positionChanged(), `is`(true))
    }

    @Test
    fun positionChanged_changedPartiallyConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 9f, false)
        assertThat(pointerInputChange.positionChanged(), `is`(true))
    }

    @Test
    fun positionChanged_changedFullConsumed_returnsFalse() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 12f, false)
        assertThat(pointerInputChange.positionChanged(), `is`(false))
    }

    @Test
    fun positionChangedIgnoreConsumed_didNotChange_returnsFalse() {
        val pointerInputChange =
            createPointerInputChange(11f, 13f, true, 11f, 13f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.positionChangedIgnoreConsumed(),
            `is`(false)
        )
    }

    @Test
    fun positionChangedIgnoreConsumed_changedNotConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.positionChangedIgnoreConsumed(),
            `is`(true)
        )
    }

    @Test
    fun positionChangedIgnoreConsumed_changedPartiallyConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 9f, false)
        assertThat(
            pointerInputChange.positionChangedIgnoreConsumed(),
            `is`(true)
        )
    }

    @Test
    fun positionChangedIgnoreConsumed_changedFullConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 12f, false)
        assertThat(
            pointerInputChange.positionChangedIgnoreConsumed(),
            `is`(true)
        )
    }

    @Test
    fun anyPositionChangeConsumed_changedNotConsumed_returnsFalse() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)
        assertThat(
            pointerInputChange.anyPositionChangeConsumed(),
            `is`(false)
        )
    }

    @Test
    fun anyPositionChangeConsumed_changedPartiallyConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 9f, false)
        assertThat(
            pointerInputChange.anyPositionChangeConsumed(),
            `is`(true)
        )
    }

    @Test
    fun anyPositionChangeConsumed_changedFullConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 12f, false)
        assertThat(
            pointerInputChange.anyPositionChangeConsumed(),
            `is`(true)
        )
    }

    @Test
    fun anyChangeConsumed_noChangeConsumed_returnsFalse() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, false, 0f, 0f, false)
        assertThat(pointerInputChange.anyChangeConsumed()).isFalse()
    }

    @Test
    fun anyChangeConsumed_downConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, false, 0f, 0f, true)
        assertThat(pointerInputChange.anyChangeConsumed()).isTrue()
    }

    @Test
    fun anyChangeConsumed_movementConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, false, 1f, 3f, false)
        assertThat(pointerInputChange.anyChangeConsumed()).isTrue()
    }

    @Test
    fun anyChangeConsumed_allConsumed_returnsTrue() {
        val pointerInputChange =
            createPointerInputChange(8f, 16f, true, 2f, 4f, false, 1f, 3f, true)
        assertThat(pointerInputChange.anyChangeConsumed()).isTrue()
    }

    @Test
    fun consumeDownChange_changeOccurred_consumes() {
        val pointerInputChange1 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, true, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, false, 0f, 0f, false)

        val (_, _, _, consumed) = pointerInputChange1.consumeDownChange()
        val (_, _, _, consumed1) = pointerInputChange2.consumeDownChange()

        assertThat(consumed.downChange, `is`(true))
        assertThat(consumed1.downChange, `is`(true))
    }

    @Test
    fun consumeDownChange_changeDidntOccur_doesNotConsume() {
        val pointerInputChange1 =
            createPointerInputChange(0f, 0f, true, 0f, 0f, true, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(0f, 0f, false, 0f, 0f, false, 0f, 0f, false)

        val (_, _, _, consumed) = pointerInputChange1.consumeDownChange()
        val (_, _, _, consumed1) = pointerInputChange2.consumeDownChange()

        assertThat(consumed.downChange, `is`(false))
        assertThat(consumed1.downChange, `is`(false))
    }

    @Test
    fun consumePositionChange_consumesNone_consumesCorrectly() {
        val pointerInputChange1 =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)

        val pointerInputChangeResult1 = pointerInputChange1.consumePositionChange(0f, 0f)

        assertThat(pointerInputChangeResult1, `is`(equalTo(pointerInputChange1)))
    }

    @Test
    fun consumePositionChange_consumesPart_consumes() {
        val pointerInputChange1 =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)

        val pointerInputChangeResult1 = pointerInputChange1.consumePositionChange(5f, 0f)
        val pointerInputChangeResult2 = pointerInputChange1.consumePositionChange(0f, 3f)
        val pointerInputChangeResult3 = pointerInputChange1.consumePositionChange(5f, 3f)

        assertThat(
            pointerInputChangeResult1,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 0f, false)))
        )
        assertThat(
            pointerInputChangeResult2,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 3f, false)))
        )
        assertThat(
            pointerInputChangeResult3,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 5f, 3f, false)))
        )
    }

    @Test
    fun consumePositionChange_consumesAll_consumes() {
        val pointerInputChange1 =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 0f, false)

        val pointerInputChangeResult1 = pointerInputChange1.consumePositionChange(6f, 0f)
        val pointerInputChangeResult2 = pointerInputChange1.consumePositionChange(0f, 12f)
        val pointerInputChangeResult3 = pointerInputChange1.consumePositionChange(6f, 12f)

        assertThat(
            pointerInputChangeResult1,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 0f, false)))
        )
        assertThat(
            pointerInputChangeResult2,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 0f, 12f, false)))
        )
        assertThat(
            pointerInputChangeResult3,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 6f, 12f, false)))
        )
    }

    @Test
    fun consumePositionChange_alreadyPartiallyConsumed_consumptionAdded() {
        val pointerInputChange1 =
            createPointerInputChange(8f, 16f, true, 2f, 4f, true, 1f, 5f, false)

        val pointerInputChangeResult1 = pointerInputChange1.consumePositionChange(2f, 0f)
        val pointerInputChangeResult2 = pointerInputChange1.consumePositionChange(0f, 3f)
        val pointerInputChangeResult3 = pointerInputChange1.consumePositionChange(2f, 3f)

        assertThat(
            pointerInputChangeResult1,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 3f, 5f, false)))
        )
        assertThat(
            pointerInputChangeResult2,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 1f, 8f, false)))
        )
        assertThat(
            pointerInputChangeResult3,
            `is`(equalTo(createPointerInputChange(8f, 16f, true, 2f, 4f, true, 3f, 8f, false)))
        )
    }

    @Test
    fun consumeAllChanges_nothingChanged_nothingConsumed() {
        val pointerInputChange1 =
            createPointerInputChange(1f, 2f, false, 1f, 2f, false, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(2f, 1f, true, 2f, 1f, true, 0f, 0f, false)

        val actual1 = pointerInputChange1.consumeAllChanges()
        val actual2 = pointerInputChange2.consumeAllChanges()

        assertThat(actual1).isEqualTo(pointerInputChange1)
        assertThat(actual2).isEqualTo(pointerInputChange2)
    }

    @Test
    fun consumeAllChanges_downChanged_downChangeConsumed() {
        val pointerInputChange1 =
            createPointerInputChange(1f, 2f, true, 1f, 2f, false, 0f, 0f, false)
        val pointerInputChange2 =
            createPointerInputChange(2f, 1f, false, 2f, 1f, true, 0f, 0f, false)

        val actual1 = pointerInputChange1.consumeAllChanges()
        val actual2 = pointerInputChange2.consumeAllChanges()

        assertThat(actual1).isEqualTo(
            createPointerInputChange(1f, 2f, true, 1f, 2f, false, 0f, 0f, true)
        )
        assertThat(actual2).isEqualTo(
            createPointerInputChange(2f, 1f, false, 2f, 1f, true, 0f, 0f, true)
        )
    }

    @Test
    fun consumeAllChanges_movementChanged_movementFullyConsumed() {
        val pointerInputChange =
            createPointerInputChange(1f, 2f, true, 11f, 21f, true, 0f, 0f, false)

        val actual = pointerInputChange.consumeAllChanges()

        assertThat(actual).isEqualTo(
            createPointerInputChange(1f, 2f, true, 11f, 21f, true, -10f, -19f, false)
        )
    }

    @Test
    fun consumeAllChanges_movementChangedAndPartiallyConsumed_movementFullyConsumed() {
        val pointerInputChange =
            createPointerInputChange(1f, 2f, true, 11f, 21f, true, -3f, -5f, false)

        val actual = pointerInputChange.consumeAllChanges()

        assertThat(actual).isEqualTo(
            createPointerInputChange(1f, 2f, true, 11f, 21f, true, -10f, -19f, false)
        )
    }

    @Test
    fun consumeAllChanges_allChanged_movementFullyConsumed() {
        val pointerInputChange1 =
            createPointerInputChange(1f, 2f, true, 11f, 21f, false, -3f, -5f, false)
        val pointerInputChange2 =
            createPointerInputChange(1f, 2f, false, 11f, 21f, true, -7f, -11f, false)

        val actual1 = pointerInputChange1.consumeAllChanges()
        val actual2 = pointerInputChange2.consumeAllChanges()

        assertThat(actual1).isEqualTo(
            createPointerInputChange(1f, 2f, true, 11f, 21f, false, -10f, -19f, true)
        )
        assertThat(actual2).isEqualTo(
            createPointerInputChange(1f, 2f, false, 11f, 21f, true, -10f, -19f, true)
        )
    }

    // Private Helper

    private fun createPointerInputChange(
        currentX: Float,
        currentY: Float,
        currentDown: Boolean,
        previousX: Float,
        previousY: Float,
        previousDown: Boolean,
        consumedX: Float,
        consumedY: Float,
        consumedDown: Boolean
    ): PointerInputChange {
        return PointerInputChange(
            PointerId(0),
            PointerInputData(
                Uptime.Boot + 100.milliseconds,
                Offset(currentX, currentY),
                currentDown
            ),
            PointerInputData(
                Uptime.Boot + 0.milliseconds,
                Offset(previousX, previousY),
                previousDown
            ),
            ConsumedData(
                Offset(
                    consumedX,
                    consumedY
                ), consumedDown
            )
        )
    }
}