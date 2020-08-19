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

package androidx.ui.test.android

import android.os.SystemClock
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import androidx.compose.runtime.dispatch.AndroidUiDispatcher
import androidx.compose.ui.geometry.Offset
import androidx.ui.test.AndroidBaseInputDispatcher
import androidx.ui.test.InputDispatcher
import androidx.ui.test.PartialGesture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class AndroidInputDispatcher(
    private val sendEvent: (MotionEvent) -> Unit
) : AndroidBaseInputDispatcher() {

    private val batchLock = Any()
    private var batchedEvents = mutableListOf<MotionEvent>()
    private var acceptEvents = true

    override val now: Long get() = SystemClock.uptimeMillis()

    override fun PartialGesture.enqueueDown(pointerId: Int) {
        batchMotionEvent(
            if (lastPositions.size() == 1) ACTION_DOWN else ACTION_POINTER_DOWN,
            lastPositions.indexOfKey(pointerId)
        )
    }

    override fun PartialGesture.enqueueMove() {
        batchMotionEvent(ACTION_MOVE, 0)
    }

    override fun PartialGesture.enqueueUp(pointerId: Int) {
        batchMotionEvent(
            if (lastPositions.size() == 1) ACTION_UP else ACTION_POINTER_UP,
            lastPositions.indexOfKey(pointerId)
        )
    }

    override fun PartialGesture.enqueueCancel() {
        batchMotionEvent(ACTION_CANCEL, 0)
    }

    /**
     * Generates a MotionEvent with the given [action] and [actionIndex], adding all pointers that
     * are currently in the gesture, and adds the MotionEvent to the batch.
     *
     * @see MotionEvent.getAction
     * @see MotionEvent.getActionIndex
     */
    private fun PartialGesture.batchMotionEvent(action: Int, actionIndex: Int) {
        batchMotionEvent(
            downTime,
            lastEventTime,
            action,
            actionIndex,
            List(lastPositions.size()) { lastPositions.valueAt(it) },
            List(lastPositions.size()) { lastPositions.keyAt(it) }
        )
    }

    /**
     * Generates an event with the given parameters.
     */
    private fun batchMotionEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        actionIndex: Int,
        coordinates: List<Offset>,
        pointerIds: List<Int>
    ) {
        synchronized(batchLock) {
            check(acceptEvents) {
                "Can't enqueue event (" +
                        "downTime=$downTime, " +
                        "eventTime=$eventTime, " +
                        "action=$action, " +
                        "actionIndex=$actionIndex, " +
                        "pointerIds=$pointerIds, " +
                        "coordinates=$coordinates" +
                        "), events have already been (or are being) dispatched or disposed"
            }
            batchedEvents.add(
                MotionEvent.obtain(
                    /* downTime = */ downTime,
                    /* eventTime = */ eventTime,
                    /* action = */ action + (actionIndex shl ACTION_POINTER_INDEX_SHIFT),
                    /* pointerCount = */ coordinates.size,
                    /* pointerProperties = */ Array(coordinates.size) {
                        MotionEvent.PointerProperties().apply { id = pointerIds[it] }
                    },
                    /* pointerCoords = */ Array(coordinates.size) {
                        MotionEvent.PointerCoords().apply {
                            x = coordinates[it].x
                            y = coordinates[it].y
                        }
                    },
                    /* metaState = */ 0,
                    /* buttonState = */ 0,
                    /* xPrecision = */ 0f,
                    /* yPrecision = */ 0f,
                    /* deviceId = */ 0,
                    /* edgeFlags = */ 0,
                    /* source = */ 0,
                    /* flags = */ 0
                )
            )
        }
    }

    override fun sendAllSynchronous() {
        runBlocking {
            withContext(AndroidUiDispatcher.Main) {
                checkAndStopAcceptingEvents()
                val copy = batchedEvents.toList()
                batchedEvents.clear()
                val iterator = copy.iterator()
                try {
                    while (iterator.hasNext()) {
                        val event = iterator.next()
                        sendAndRecycleEvent(event)
                    }
                } finally {
                    // In case we were cancelled, or an exception was thrown,
                    // stop injecting and recycle all left over events
                    while (iterator.hasNext()) {
                        try {
                            iterator.next().recycle()
                        } catch (ignore: Throwable) {
                            // ignore all errors, just continue recycling
                        }
                    }
                }
            }
        }
    }

    override fun dispose() {
        if (stopAcceptingEvents()) {
            batchedEvents.forEach { it.recycle() }
            batchedEvents.clear()
        }
    }

    private fun checkAndStopAcceptingEvents() {
        synchronized(batchLock) {
            check(acceptEvents) { "Events have already been (or are being) dispatched or disposed" }
            acceptEvents = false
        }
    }

    private fun stopAcceptingEvents(): Boolean {
        synchronized(batchLock) {
            return acceptEvents.also { acceptEvents = false }
        }
    }

    /**
     * Sends and recycles the given [event]. If [InputDispatcher.dispatchInRealTime] is `true`,
     * suspends until [now] is equal to the event's `eventTime`. Doesn't suspend otherwise, or if
     * the event's `eventTime` is before [now].
     */
    private suspend fun sendAndRecycleEvent(event: MotionEvent) {
        try {
            if (dispatchInRealTime) {
                val delayMs = event.eventTime - now
                if (delayMs > 0) {
                    delay(delayMs)
                }
            }
            sendEvent(event)
        } finally {
            event.recycle()
        }
    }
}
