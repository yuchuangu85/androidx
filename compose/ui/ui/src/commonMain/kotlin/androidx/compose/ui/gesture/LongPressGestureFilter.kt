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

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.customevents.LongPressFiredEvent
import androidx.compose.ui.input.pointer.CustomEvent
import androidx.compose.ui.input.pointer.CustomEventDispatcher
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.input.pointer.anyPositionChangeConsumed
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.inMilliseconds
import androidx.compose.ui.util.annotation.VisibleForTesting
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// TODO(b/137569202): This bug tracks the note below regarding the need to eventually
//  improve LongPressGestureDetector.
// TODO(b/139020678): Probably has shared functionality with other press based detectors.
/**
 * Responds to a pointer being "down" for an extended amount of time.
 *
 * Note: this is likely a temporary, naive, and flawed approach. It is not necessarily guaranteed
 * to interoperate well with forthcoming behavior related to disambiguation between multi-tap
 * (double tap, triple tap) and tap.
 */
fun Modifier.longPressGestureFilter(
    onLongPress: (Offset) -> Unit
): Modifier = composed {
    @Suppress("DEPRECATION")
    val scope = rememberCoroutineScope()
    val filter = remember { LongPressGestureFilter(scope) }
    filter.onLongPress = onLongPress
    PointerInputModifierImpl(filter)
}

internal class LongPressGestureFilter(
    private val coroutineScope: CoroutineScope
) : PointerInputFilter() {
    lateinit var onLongPress: (Offset) -> Unit

    @VisibleForTesting
    internal var longPressTimeout = LongPressTimeout

    private enum class State {
        Idle, Primed, Fired
    }

    private var state = State.Idle
    private val pointerPositions = linkedMapOf<PointerId, Offset>()
    private var job: Job? = null
    private lateinit var customEventDispatcher: CustomEventDispatcher

    override fun onInit(customEventDispatcher: CustomEventDispatcher) {
        this.customEventDispatcher = customEventDispatcher
    }

    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntSize
    ): List<PointerInputChange> {

            var changesToReturn = changes

            if (pass == PointerEventPass.Initial && state == State.Fired) {
                // If we fired and have not reset, we should prevent other pointer input nodes from
                // responding to up, so consume it early on.
                changesToReturn = changesToReturn.map {
                    if (it.changedToUp()) {
                        it.consumeDownChange()
                    } else {
                        it
                    }
                }
            }

            if (pass == PointerEventPass.Main) {
                if (state == State.Idle && changes.all { it.changedToDown() }) {
                    // If we are idle and all of the changes changed to down, we are prime to fire
                    // the event.
                    primeToFire()
                } else if (state != State.Idle && changes.all { it.changedToUpIgnoreConsumed() }) {
                    // If we have started and all of the changes changed to up, reset to idle.
                    resetToIdle()
                } else if (!changesToReturn.anyPointersInBounds(bounds)) {
                    // If all pointers have gone out of bounds, reset to idle.
                    resetToIdle()
                }

                if (state == State.Primed) {
                    // If we are primed, keep track of all down pointer positions so we can pass
                    // pointer position information to the event we will fire.
                    changes.forEach {
                        if (it.current.down) {
                            pointerPositions[it.id] = it.current.position!!
                        } else {
                            pointerPositions.remove(it.id)
                        }
                    }
                }
            }

            if (
                pass == PointerEventPass.Final &&
                state != State.Idle &&
                changes.fastAny { it.anyPositionChangeConsumed() }
            ) {
                // If we are anything but Idle and something consumed movement, reset.
                resetToIdle()
            }

            return changesToReturn
        }

    override fun onCustomEvent(customEvent: CustomEvent, pass: PointerEventPass) {
        if (
            state == State.Primed &&
            customEvent is LongPressFiredEvent &&
            pass == PointerEventPass.Initial
        ) {
            // If we are primed but something else fired long press, we should reset.
            // Doesn't matter what pass we are on, just choosing one so we only reset once.
            resetToIdle()
        }
    }

    override fun onCancel() {
        resetToIdle()
    }

    private fun fireLongPress() {
        state = State.Fired
        onLongPress.invoke(pointerPositions.asIterable().first().value)
        customEventDispatcher.dispatchCustomEvent(LongPressFiredEvent)
    }

    private fun primeToFire() {
        state = State.Primed
        job = coroutineScope.launch {
            delay(longPressTimeout.inMilliseconds())
            fireLongPress()
        }
    }

    private fun resetToIdle() {
        state = State.Idle
        job?.cancel()
        pointerPositions.clear()
    }
}