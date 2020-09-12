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

package androidx.ui.test.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.util.VelocityTracker
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputData
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.input.pointer.PointerInputModifier
import androidx.compose.ui.unit.Duration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Uptime
import com.google.common.truth.Truth.assertThat

data class DataPoint(val id: PointerId, val data: PointerInputData) {
    val timestamp get() = data.uptime!!
    val position get() = data.position!!
    val x get() = data.position!!.x
    val y get() = data.position!!.y
    val down get() = data.down
}

class SinglePointerInputRecorder : PointerInputModifier {
    private val _events = mutableListOf<DataPoint>()
    val events get() = _events as List<DataPoint>

    private val velocityTracker = VelocityTracker()
    val recordedVelocity get() = velocityTracker.calculateVelocity()

    override val pointerInputFilter = RecordingFilter { changes ->
        changes.forEach {
            _events.add(DataPoint(it.id, it.current))
            velocityTracker.addPosition(it.current.uptime!!, it.current.position!!)
        }
    }
}

class MultiPointerInputRecorder : PointerInputModifier {
    data class Event(val pointers: List<DataPoint>) {
        val pointerCount: Int get() = pointers.size
        fun getPointer(index: Int) = pointers[index]
    }

    private val _events = mutableListOf<Event>()
    val events get() = _events as List<Event>

    override val pointerInputFilter = RecordingFilter { changes ->
        _events.add(Event(changes.map { DataPoint(it.id, it.current) }))
    }
}

class RecordingFilter(
    private val record: (List<PointerInputChange>) -> Unit
) : PointerInputFilter() {
    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntSize
    ): List<PointerInputChange> {
        if (pass == PointerEventPass.Initial) {
            record(changes)
        }
        return changes
    }

    override fun onCancel() {
        // Do nothing
    }
}

val SinglePointerInputRecorder.downEvents get() = events.filter { it.down }

val SinglePointerInputRecorder.recordedDuration: Duration
    get() {
        check(events.isNotEmpty()) { "No events recorded" }
        return events.last().timestamp - events.first().timestamp
    }

fun SinglePointerInputRecorder.assertTimestampsAreIncreasing() {
    check(events.isNotEmpty()) { "No events recorded" }
    events.reduce { prev, curr ->
        assertThat(curr.timestamp).isAtLeast(prev.timestamp)
        curr
    }
}

fun MultiPointerInputRecorder.assertTimestampsAreIncreasing() {
    check(events.isNotEmpty()) { "No events recorded" }
    // Check that each event has the same timestamp
    events.forEach { event ->
        assertThat(event.pointerCount).isAtLeast(1)
        val currTime = event.pointers[0].timestamp
        for (i in 1 until event.pointerCount) {
            assertThat(event.pointers[i].timestamp).isEqualTo(currTime)
        }
    }
    // Check that the timestamps are ordered
    assertThat(events.map { it.pointers[0].timestamp }).isInOrder()
}

fun SinglePointerInputRecorder.assertOnlyLastEventIsUp() {
    check(events.isNotEmpty()) { "No events recorded" }
    assertThat(events.last().down).isFalse()
    assertThat(events.count { !it.down }).isEqualTo(1)
}

fun DataPoint.verify(
    expectedTimestamp: Uptime?,
    expectedId: PointerId?,
    expectedDown: Boolean,
    expectedPosition: Offset
) {
    if (expectedTimestamp != null) {
        assertThat(timestamp).isEqualTo(expectedTimestamp)
    }
    if (expectedId != null) {
        assertThat(id).isEqualTo(expectedId)
    }
    assertThat(down).isEqualTo(expectedDown)
    assertThat(position).isEqualTo(expectedPosition)
}

/**
 * Checks that the coordinates are progressing in a monotonous direction
 */
fun List<DataPoint>.isMonotonicBetween(start: Offset, end: Offset) {
    map { it.x }.isMonotonicBetween(start.x, end.x, 1e-3f)
    map { it.y }.isMonotonicBetween(start.y, end.y, 1e-3f)
}
