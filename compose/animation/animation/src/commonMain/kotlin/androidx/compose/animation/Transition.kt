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

package androidx.compose.animation

import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.InternalAnimationApi
import androidx.compose.animation.core.PropKey
import androidx.compose.animation.core.TransitionAnimation
import androidx.compose.animation.core.TransitionDefinition
import androidx.compose.animation.core.TransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onCommit
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AnimationClockAmbient

/**
 * [transition] composable creates a state-based transition using the animation configuration
 * defined in [TransitionDefinition]. This can be especially useful when animating multiple
 * values from a predefined set of values to another. For animating a single value, consider using
 * [animatedValue], [animatedFloat], [animatedColor] or the more light-weight [animate] APIs.
 *
 * [transition] starts a new animation or changes the on-going animation when the [toState]
 * parameter is changed to a different value. It dutifully ensures that the animation will head
 * towards new [toState] regardless of what state (or in-between state) it’s currently in: If the
 * transition is not currently animating, having a new [toState] value will start a new animation,
 * otherwise the in-flight animation will correct course and animate towards the new [toState]
 * based on the interruption handling logic.
 *
 * [transition] takes a transition definition, a target state and child composables.
 * These child composables will be receiving a [TransitionState] object as an argument, which
 * captures all the current values of the animation. Child composables should read the animation
 * values from the [TransitionState] object, and apply the value wherever necessary.
 *
 * @sample androidx.compose.animation.samples.TransitionSample
 *
 * @param definition Transition definition that defines states and transitions
 * @param toState New state to transition to
 * @param clock Optional animation clock that pulses animations when time changes. By default,
 *              the system uses a choreographer based clock read from the [AnimationClockAmbient].
 *              A custom implementation of the [AnimationClockObservable] (such as a
 *              [androidx.compose.animation.core.ManualAnimationClock]) can be supplied here if there’s a need to
 *              manually control the clock (for example in tests).
 * @param initState Optional initial state for the transition. When undefined, the initial state
 *                  will be set to the first [toState] seen in the transition.
 * @param label Optional label for distinguishing different transitions in Android Studio.
 * @param onStateChangeFinished An optional listener to get notified when state change animation
 *                              has completed
 *
 * @return a [TransitionState] instance, from which the animation values can be read
 *
 * @see [TransitionDefinition]
 */
// TODO: The list of params is getting a bit long. Consider grouping them.
@OptIn(InternalAnimationApi::class)
@Composable
fun <T> transition(
    definition: TransitionDefinition<T>,
    toState: T,
    clock: AnimationClockObservable = AnimationClockAmbient.current,
    initState: T = toState,
    label: String? = null,
    onStateChangeFinished: ((T) -> Unit)? = null
): TransitionState {
    if (transitionsEnabled) {
        val disposableClock = clock.asDisposableClock()
        val model = remember(definition, disposableClock) {
            TransitionModel(definition, initState, disposableClock, label)
        }

        model.anim.onStateChangeFinished = onStateChangeFinished
        // TODO(b/150674848): Should be onCommit, but that posts to the Choreographer. Until that
        //  callback is executed, nothing is aware that the animation is kicked off, so if
        //  Espresso checks for idleness between now and then, it will think all is idle.
        onCommit(model, toState) {
            model.anim.toState(toState)
        }
        return model
    } else {
        return remember(definition, toState) { definition.getStateFor(toState) }
    }
}

/**
 * Stores the enabled state for [transition] animations. Useful for tests to disable
 * animations and have reliable screenshot tests.
 */
var transitionsEnabled = true

// TODO(Doris): Use Clock idea instead of TransitionModel with pulse
/**
 * This class is marked as internal animation API to allow access from tools
 * @suppress
 */
@Stable
@InternalAnimationApi
class TransitionModel<T>(
    transitionDef: TransitionDefinition<T>,
    initState: T,
    clock: AnimationClockObservable,
    label: String?
) : TransitionState {

    private var animationPulse by mutableStateOf(0L)
    @InternalAnimationApi
    val anim: TransitionAnimation<T> =
        TransitionAnimation(transitionDef, clock, initState, label).apply {
            onUpdate = {
                animationPulse++
            }
        }

    override fun <T, V : AnimationVector> get(propKey: PropKey<T, V>): T {
        // we need to access the animationPulse so Compose will record this state values usage.
        @Suppress("UNUSED_VARIABLE")
        val pulse = animationPulse
        return anim[propKey]
    }
}