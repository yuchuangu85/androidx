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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Layout
import androidx.compose.ui.LayoutModifier
import androidx.compose.ui.Measurable
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.enforce

/**
 * Declare the preferred width of the content to be the same as the min or max intrinsic width of
 * the content. The incoming measurement [Constraints] may override this value, forcing the content
 * to be either smaller or larger.
 *
 * See [preferredHeight] for options of sizing to intrinsic height.
 * Also see [preferredWidth] and [preferredWidthIn] for other options to set the preferred width.
 *
 * Example usage for min intrinsic:
 * @sample androidx.compose.foundation.layout.samples.SameWidthBoxes
 *
 * Example usage for max intrinsic:
 * @sample androidx.compose.foundation.layout.samples.SameWidthTextBoxes
 */
@ExperimentalLayout
@Stable
fun Modifier.preferredWidth(intrinsicSize: IntrinsicSize) = when (intrinsicSize) {
    IntrinsicSize.Min -> this.then(PreferredMinIntrinsicWidthModifier)
    IntrinsicSize.Max -> this.then(PreferredMaxIntrinsicWidthModifier)
}

/**
 * Declare the preferred height of the content to be the same as the min or max intrinsic height of
 * the content. The incoming measurement [Constraints] may override this value, forcing the content
 * to be either smaller or larger.
 *
 * See [preferredWidth] for other options of sizing to intrinsic width.
 * Also see [preferredHeight] and [preferredHeightIn] for other options to set the preferred height.
 *
 * Example usage for min intrinsic:
 * @sample androidx.compose.foundation.layout.samples.MatchParentDividerForText
 *
 * Example usage for max intrinsic:
 * @sample androidx.compose.foundation.layout.samples.MatchParentDividerForAspectRatio
 */
@ExperimentalLayout
@Stable
fun Modifier.preferredHeight(intrinsicSize: IntrinsicSize) = when (intrinsicSize) {
    IntrinsicSize.Min -> this.then(PreferredMinIntrinsicHeightModifier)
    IntrinsicSize.Max -> this.then(PreferredMaxIntrinsicHeightModifier)
}

/**
 * Intrinsic size used in [preferredWidth] or [preferredHeight] which can refer to width or height.
 */
enum class IntrinsicSize { Min, Max }

private object PreferredMinIntrinsicWidthModifier : PreferredIntrinsicSizeModifier {
    override fun MeasureScope.calculateContentConstraints(
        measurable: Measurable,
        constraints: Constraints
    ): Constraints {
        val width = measurable.minIntrinsicWidth(constraints.maxHeight)
        return Constraints.fixedWidth(width)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ) = measurable.minIntrinsicWidth(height)
}

private object PreferredMinIntrinsicHeightModifier : PreferredIntrinsicSizeModifier {
    override fun MeasureScope.calculateContentConstraints(
        measurable: Measurable,
        constraints: Constraints
    ): Constraints {
        val height = measurable.minIntrinsicHeight(constraints.maxWidth)
        return Constraints.fixedHeight(height)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ) = measurable.minIntrinsicHeight(width)
}

private object PreferredMaxIntrinsicWidthModifier : PreferredIntrinsicSizeModifier {
    override fun MeasureScope.calculateContentConstraints(
        measurable: Measurable,
        constraints: Constraints
    ): Constraints {
        val width = measurable.maxIntrinsicWidth(constraints.maxHeight)
        return Constraints.fixedWidth(width)
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ) = measurable.maxIntrinsicWidth(height)
}

private object PreferredMaxIntrinsicHeightModifier : PreferredIntrinsicSizeModifier {
    override fun MeasureScope.calculateContentConstraints(
        measurable: Measurable,
        constraints: Constraints
    ): Constraints {
        val height = measurable.maxIntrinsicHeight(constraints.maxWidth)
        return Constraints.fixedHeight(height)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ) = measurable.maxIntrinsicHeight(width)
}

private interface PreferredIntrinsicSizeModifier : LayoutModifier {
    fun MeasureScope.calculateContentConstraints(
        measurable: Measurable,
        constraints: Constraints
    ): Constraints

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(
            calculateContentConstraints(
                measurable,
                constraints
            ).enforce(constraints)
        )
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(IntOffset.Zero)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ) = measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ) = measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ) = measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ) = measurable.maxIntrinsicHeight(width)
}

/**
 * Layout composable that forces its child to be as wide as its min intrinsic width.
 * If incoming constraints do not allow this, the closest possible width will be used.
 */
@Deprecated("This component is deprecated. " +
        "Please use the preferredWidth(IntrinsicSize.Min) modifier instead.")
@Composable
fun MinIntrinsicWidth(children: @Composable () -> Unit) {
    Layout(
        children,
        minIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.minIntrinsicWidth(h) ?: 0
        },
        minIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.minIntrinsicHeight(w) ?: 0
        },
        maxIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.minIntrinsicWidth(h) ?: 0
        },
        maxIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.maxIntrinsicHeight(w) ?: 0
        }
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        val width = measurable?.minIntrinsicWidth(constraints.maxHeight) ?: 0
        val placeable = measurable?.measure(
            Constraints.fixedWidth(width).enforce(constraints)
        )
        layout(placeable?.width ?: 0, placeable?.height ?: 0) {
            placeable?.place(0, 0)
        }
    }
}

/**
 * Layout composable that forces its child to be as tall as its min intrinsic height.
 * If incoming constraints do not allow this, the closest possible height will be used.
 */
@Deprecated("This component is deprecated. " +
        "Please use the preferredHeight(IntrinsicSize.Min) modifier instead.")
@Composable
fun MinIntrinsicHeight(children: @Composable () -> Unit) {
    Layout(
        children,
        minIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.minIntrinsicWidth(h) ?: 0
        },
        minIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.minIntrinsicHeight(w) ?: 0
        },
        maxIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.maxIntrinsicWidth(h) ?: 0
        },
        maxIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.minIntrinsicHeight(w) ?: 0
        }
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        val height = measurable?.minIntrinsicHeight(constraints.maxWidth) ?: 0
        val placeable = measurable?.measure(
            Constraints.fixedHeight(height).enforce(constraints)
        )
        layout(placeable?.width ?: 0, placeable?.height ?: 0) {
            placeable?.place(0, 0)
        }
    }
}

/**
 * Layout composable that forces its child to be as wide as its max intrinsic width.
 * If incoming constraints do not allow this, the closest possible width will be used.
 */
@Deprecated("This component is deprecated. " +
        "Please use the preferredWidth(IntrinsicSize.Max) modifier instead.")
@Composable
fun MaxIntrinsicWidth(children: @Composable () -> Unit) {
    Layout(
        children,
        minIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.maxIntrinsicWidth(h) ?: 0
        },
        minIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.minIntrinsicHeight(w) ?: 0
        },
        maxIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.maxIntrinsicWidth(h) ?: 0
        },
        maxIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.maxIntrinsicHeight(w) ?: 0
        }
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        val width = measurable?.maxIntrinsicWidth(constraints.maxHeight) ?: 0
        val placeable = measurable?.measure(
            Constraints.fixedWidth(width).enforce(constraints)
        )
        layout(placeable?.width ?: 0, placeable?.height ?: 0) {
            placeable?.place(0, 0)
        }
    }
}

/**
 * Layout composable that forces its child to be as tall as its max intrinsic height.
 * If incoming constraints do not allow this, the closest possible height will be used.
 */
@Deprecated("This component is deprecated. " +
        "Please use the preferredHeight(IntrinsicSize.Max) modifier instead.")
@Composable
fun MaxIntrinsicHeight(children: @Composable () -> Unit) {
    Layout(
        children,
        minIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.minIntrinsicWidth(h) ?: 0
        },
        minIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.maxIntrinsicHeight(w) ?: 0
        },
        maxIntrinsicWidthMeasureBlock = { measurables, h ->
            measurables.firstOrNull()?.maxIntrinsicWidth(h) ?: 0
        },
        maxIntrinsicHeightMeasureBlock = { measurables, w ->
            measurables.firstOrNull()?.maxIntrinsicHeight(w) ?: 0
        }
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        val height = measurable?.maxIntrinsicHeight(constraints.maxWidth) ?: 0
        val placeable = measurable?.measure(
            Constraints.fixedHeight(height).enforce(constraints)
        )
        layout(placeable?.width ?: 0, placeable?.height ?: 0) {
            placeable?.place(0, 0)
        }
    }
}
