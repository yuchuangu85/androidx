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

import androidx.compose.animation.core.AnimatedValue
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.Interaction
import androidx.compose.ui.unit.Dp

/**
 * Animates the [Dp] value of [this] between [from] and [to] [Interaction]s, to [target]. The
 * [AnimationSpec] used depends on the values for [from] and [to], see
 * [ElevationConstants.incomingAnimationSpecForInteraction] and
 * [ElevationConstants.outgoingAnimationSpecForInteraction] for more details.
 *
 * @param from the previous [Interaction] that was used to calculate elevation. `null` if there
 * was no previous [Interaction], such as when the component is in its default state.
 * @param to the [Interaction] that this component is moving to, such as [Interaction.Pressed]
 * when this component is being pressed. `null` if this component is moving back to its default
 * state.
 * @param target the [Dp] target elevation for this component, corresponding to the elevation
 * desired for the [to] state.
 */
fun AnimatedValue<Dp, *>.animateElevation(
    from: Interaction? = null,
    to: Interaction? = null,
    target: Dp
) {
    val spec = when {
        // Moving to a new state
        to != null -> ElevationConstants.incomingAnimationSpecForInteraction(to)
        // Moving to default, from a previous state
        from != null -> ElevationConstants.outgoingAnimationSpecForInteraction(from)
        // Loading the initial state, or moving back to the baseline state from a disabled /
        // unknown state, so just snap to the final value.
        else -> null
    }
    if (spec != null) animateTo(target, spec) else snapTo(target)
}

/**
 * Contains default [AnimationSpec]s used for animating elevation between different [Interaction]s.
 *
 * Typically you should use [animateElevation] instead, which uses these [AnimationSpec]s
 * internally. [animateElevation] in turn is used by the defaults for [Button] and
 * [FloatingActionButton] - inside [ButtonConstants.defaultAnimatedElevation] and
 * [FloatingActionButtonConstants.defaultAnimatedElevation] respectively.
 *
 * @see animateElevation
 */
object ElevationConstants {
    /**
     * Returns the [AnimationSpec]s used when animating elevation to [interaction], either from a
     * previous [Interaction], or from the default state. If [interaction] is unknown, then
     * returns `null`.
     *
     * @param interaction the [Interaction] that is being animated to
     */
    fun incomingAnimationSpecForInteraction(interaction: Interaction): AnimationSpec<Dp>? {
        return when (interaction) {
            is Interaction.Pressed -> DefaultIncomingSpec
            // TODO: b/161522042 - clarify specs for dragged state transitions
            is Interaction.Dragged -> DefaultIncomingSpec
            else -> null
        }
    }

    /**
     * Returns the [AnimationSpec]s used when animating elevation away from [interaction], to the
     * default state. If [interaction] is unknown, then returns `null`.
     *
     * @param interaction the [Interaction] that is being animated away from
     */
    fun outgoingAnimationSpecForInteraction(interaction: Interaction): AnimationSpec<Dp>? {
        return when (interaction) {
            is Interaction.Pressed -> DefaultOutgoingSpec
            // TODO: b/161522042 - clarify specs for dragged state transitions
            is Interaction.Dragged -> DefaultOutgoingSpec
            // TODO: use [HoveredOutgoingSpec] when hovered
            else -> null
        }
    }
}

private val DefaultIncomingSpec = TweenSpec<Dp>(
    durationMillis = 120,
    easing = FastOutSlowInEasing
)

private val DefaultOutgoingSpec = TweenSpec<Dp>(
    durationMillis = 150,
    easing = CubicBezierEasing(0.40f, 0.00f, 0.60f, 1.00f)
)

@Suppress("unused")
private val HoveredOutgoingSpec = TweenSpec<Dp>(
    durationMillis = 120,
    easing = CubicBezierEasing(0.40f, 0.00f, 0.60f, 1.00f)
)
