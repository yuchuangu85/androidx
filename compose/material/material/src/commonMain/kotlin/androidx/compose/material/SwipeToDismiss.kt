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

package androidx.compose.material

import androidx.compose.animation.asDisposableClock
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationEndReason.Interrupted
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.offsetPx
import androidx.compose.material.DismissDirection.EndToStart
import androidx.compose.material.DismissDirection.StartToEnd
import androidx.compose.material.DismissValue.Default
import androidx.compose.material.DismissValue.DismissedToEnd
import androidx.compose.material.DismissValue.DismissedToStart
import androidx.compose.material.SwipeableConstants.StandardResistanceFactor
import androidx.compose.material.SwipeableConstants.StiffResistanceFactor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.WithConstraints
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.platform.AnimationClockAmbient
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.unit.LayoutDirection

/**
 * The directions in which a [SwipeToDismiss] can be dismissed.
 */
enum class DismissDirection {
    /**
     * Can be dismissed by swiping in the reading direction.
     */
    StartToEnd,

    /**
     * Can be dismissed by swiping in the reverse of the reading direction.
     */
    EndToStart
}

/**
 * Possible values of [DismissState].
 */
enum class DismissValue {
    /**
     * Indicates the component has not been dismissed yet.
     */
    Default,

    /**
     * Indicates the component has been dismissed in the reading direction.
     */
    DismissedToEnd,

    /**
     * Indicates the component has been dismissed in the reverse of the reading direction.
     */
    DismissedToStart
}

/**
 * State of the [SwipeToDismiss] composable.
 *
 * @param initialValue The initial value of the state.
 * @param clock The animation clock that will be used to drive the animations.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@ExperimentalMaterialApi
class DismissState(
    initialValue: DismissValue,
    clock: AnimationClockObservable,
    confirmStateChange: (DismissValue) -> Boolean = { true }
) : SwipeableState<DismissValue>(initialValue, clock, confirmStateChange = confirmStateChange) {
    /**
     * The direction (if any) in which the composable has been or is being dismissed.
     *
     * If the composable is settled at the default state, then this will be null. Use this to
     * change the background of the [SwipeToDismiss] if you want different actions on each side.
     */
    val dismissDirection: DismissDirection?
        get() = if (offset.value == 0f) null else if (offset.value > 0f) StartToEnd else EndToStart

    /**
     * Whether the component has been dismissed in the given [direction].
     *
     * @param direction The dismiss direction.
     */
    fun isDismissed(direction: DismissDirection): Boolean {
        return value == if (direction == StartToEnd) DismissedToEnd else DismissedToStart
    }

    /**
     * Reset the component to the default position, with an animation.
     *
     * @param onReset Optional callback invoked when the component has been reset.
     */
    fun reset(onReset: (() -> Unit)? = null) {
        animateTo(targetValue = Default, onEnd = { endReason, endValue ->
            if (endReason != Interrupted && endValue == Default) {
                onReset?.invoke()
            }
        })
    }

    /**
     * Dismiss the component in the given [direction], with an animation.
     *
     * @param direction The dismiss direction.
     * @param onDismissed Optional callback invoked when the component has been dismissed.
     */
    fun dismiss(direction: DismissDirection, onDismissed: (() -> Unit)? = null) {
        val targetValue = if (direction == StartToEnd) DismissedToEnd else DismissedToStart
        animateTo(targetValue = targetValue, onEnd = { endReason, endValue ->
            if (endReason != Interrupted && endValue == targetValue) {
                onDismissed?.invoke()
            }
        })
    }

    companion object {
        /**
         * The default [Saver] implementation for [DismissState].
         */
        fun Saver(
            clock: AnimationClockObservable,
            confirmStateChange: (DismissValue) -> Boolean
        ) = Saver<DismissState, DismissValue>(
            save = { it.value },
            restore = { DismissState(it, clock, confirmStateChange) }
        )
    }
}

/**
 * Create and [remember] a [DismissState] with the default animation clock.
 *
 * @param initialValue The initial value of the state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@Composable
@ExperimentalMaterialApi
fun rememberDismissState(
    initialValue: DismissValue = Default,
    confirmStateChange: (DismissValue) -> Boolean = { true }
): DismissState {
    val clock = AnimationClockAmbient.current.asDisposableClock()
    return rememberSavedInstanceState(
        clock,
        saver = DismissState.Saver(clock, confirmStateChange)
    ) {
        DismissState(initialValue, clock, confirmStateChange)
    }
}

/**
 * A composable that can be dismissed by swiping left or right.
 *
 * @sample androidx.compose.material.samples.SwipeToDismissListItems
 *
 * @param state The state of this component.
 * @param modifier Optional [Modifier] for this component.
 * @param directions The set of directions in which the component can be dismissed.
 * @param dismissThresholds The thresholds the item needs to be swiped in order to be dismissed.
 * @param background A composable that is stacked behind the content and is exposed when the
 * content is swiped. You can/should use the [state] to have different backgrounds on each side.
 * @param dismissContent The content that can be dismissed.
 */
@Composable
@ExperimentalMaterialApi
fun SwipeToDismiss(
    state: DismissState,
    modifier: Modifier = Modifier,
    directions: Set<DismissDirection> = setOf(EndToStart, StartToEnd),
    dismissThresholds: (DismissDirection) -> ThresholdConfig = { FractionalThreshold(0.5f) },
    background: @Composable RowScope.() -> Unit,
    dismissContent: @Composable RowScope.() -> Unit
) = WithConstraints(modifier) {
    val width = constraints.maxWidth.toFloat()
    val isRtl = LayoutDirectionAmbient.current == LayoutDirection.Rtl

    val anchors = mutableMapOf(0f to Default)
    if (StartToEnd in directions) anchors += width to DismissedToEnd
    if (EndToStart in directions) anchors += -width to DismissedToStart

    val thresholds = { from: DismissValue, to: DismissValue ->
        dismissThresholds(getDismissDirection(from, to)!!)
    }

    Stack(Modifier.swipeable(
        state = state,
        anchors = anchors,
        thresholds = thresholds,
        orientation = Orientation.Horizontal,
        enabled = state.value == Default,
        reverseDirection = isRtl,
        resistanceFactorAtMin =
            if (EndToStart in directions) StandardResistanceFactor else StiffResistanceFactor,
        resistanceFactorAtMax =
            if (StartToEnd in directions) StandardResistanceFactor else StiffResistanceFactor
    )) {
        Row(
            children = background,
            modifier = Modifier.matchParentSize()
        )
        Row(
            children = dismissContent,
            modifier = Modifier.offsetPx(x = state.offset)
        )
    }
}

private fun getDismissDirection(from: DismissValue, to: DismissValue): DismissDirection? {
    return when {
        // settled at the default state
        from == to && from == Default -> null
        // has been dismissed to the end
        from == to && from == DismissedToEnd -> StartToEnd
        // has been dismissed to the start
        from == to && from == DismissedToStart -> EndToStart
        // is currently being dismissed to the end
        from == Default && to == DismissedToEnd -> StartToEnd
        // is currently being dismissed to the start
        from == Default && to == DismissedToStart -> EndToStart
        // has been dismissed to the end but is now animated back to default
        from == DismissedToEnd && to == Default -> StartToEnd
        // has been dismissed to the start but is now animated back to default
        from == DismissedToStart && to == Default -> EndToStart
        else -> null
    }
}