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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.HorizontalAlignmentLine
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.util.annotation.FloatRange

/**
 * A layout composable that places its children in a horizontal sequence. For a layout composable
 * that places its children in a vertical sequence, see [Column].
 *
 * The layout model is able to assign children widths according to their weights provided
 * using the [RowScope.weight] modifier. If a child is not provided a weight, it will be
 * asked for its preferred width before the sizes of the children with weights are calculated
 * proportionally to their weight based on the remaining available space.
 *
 * When none of its children have weights, a [Row] will be as small as possible to fit its
 * children one next to the other. In order to change the width of the [Row], use the
 * [Modifier.width] modifiers; e.g. to make it fill the available width [Modifier.fillMaxWidth]
 * can be used. If at least one child of a [Row] has a [weight][RowScope.weight], the [Row] will
 * fill the available width, so there is no need for [Modifier.fillMaxWidth]. However, if [Row]'s
 * size should be limited, the [Modifier.width] or [Modifier.size] layout modifiers should be
 * applied.
 *
 * When the size of the [Row] is larger than the sum of its children sizes, a
 * [horizontalArrangement] can be specified to define the positioning of the children inside
 * the [Row]. See [Arrangement] for available positioning behaviors; a custom arrangement can
 * also be defined using the constructor of [Arrangement].
 *
 * Example usage:
 *
 * @sample androidx.compose.foundation.layout.samples.SimpleRow
 *
 * @param modifier The modifier to be applied to the Row.
 * @param horizontalArrangement The horizontal arrangement of the layout's children.
 * @param verticalAlignment The vertical alignment of the layout's children.
 *
 * @see Column
 */
@Composable
@OptIn(ExperimentalLayoutNodeApi::class, InternalLayoutApi::class)
inline fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    children: @Composable RowScope.() -> Unit
) {
    val measureBlocks = rowMeasureBlocks(
        horizontalArrangement,
        verticalAlignment
    )
    Layout(
        children = { RowScope.children() },
        measureBlocks = measureBlocks,
        modifier = modifier
    )
}

/**
 * MeasureBlocks to use when horizontalArrangement and verticalAlignment are not provided.
 */
@PublishedApi
@OptIn(InternalLayoutApi::class)
internal val DefaultRowMeasureBlocks = rowColumnMeasureBlocks(
    orientation = LayoutOrientation.Horizontal,
    arrangement = { totalSize, size, layoutDirection, density, outPosition ->
        Arrangement.Start.arrange(totalSize, size, layoutDirection, density, outPosition)
    },
    arrangementSpacing = Arrangement.Start.spacing,
    crossAxisAlignment = CrossAxisAlignment.vertical(Alignment.Top),
    crossAxisSize = SizeMode.Wrap
)

@PublishedApi
@Composable
@OptIn(InternalLayoutApi::class)
internal fun rowMeasureBlocks(
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical
) = remember(horizontalArrangement, verticalAlignment) {
    if (horizontalArrangement == Arrangement.Start && verticalAlignment == Alignment.Top) {
        DefaultRowMeasureBlocks
    } else {
        rowColumnMeasureBlocks(
            orientation = LayoutOrientation.Horizontal,
            arrangement = { totalSize, size, layoutDirection, density, outPosition ->
                horizontalArrangement
                    .arrange(totalSize, size, layoutDirection, density, outPosition)
            },
            arrangementSpacing = horizontalArrangement.spacing,
            crossAxisAlignment = CrossAxisAlignment.vertical(verticalAlignment),
            crossAxisSize = SizeMode.Wrap
        )
    }
}

/**
 * Scope for the children of [Row].
 */
@LayoutScopeMarker
@Immutable
interface RowScope {
    /**
     * Align the element vertically within the [Row]. This alignment will have priority over the
     * [Row]'s `verticalAlignment` parameter.
     *
     * Example usage:
     * @sample androidx.compose.foundation.layout.samples.SimpleAlignInRow
     */
    @Stable
    fun Modifier.align(alignment: Alignment.Vertical) = this.then(VerticalAlignModifier(alignment))

    @Stable
    @Deprecated("gravity has been renamed to align.", ReplaceWith("align(align)"))
    fun Modifier.gravity(align: Alignment.Vertical) = this.then(VerticalAlignModifier(align))

    /**
     * Position the element vertically such that its [alignmentLine] aligns with sibling elements
     * also configured to [alignWithSiblings]. [alignWithSiblings] is a form of [align],
     * so both modifiers will not work together if specified for the same layout.
     * [alignWithSiblings] can be used to align two layouts by baseline inside a [Row],
     * using `alignWithSiblings(FirstBaseline)`.
     * Within a [Row], all components with [alignWithSiblings] will align vertically using
     * the specified [HorizontalAlignmentLine]s or values provided using the other
     * [alignWithSiblings] overload, forming a sibling group.
     * At least one element of the sibling group will be placed as it had [Alignment.Top] align
     * in [Row], and the alignment of the other siblings will be then determined such that
     * the alignment lines coincide. Note that if only one element in a [Row] has the
     * [alignWithSiblings] modifier specified the element will be positioned
     * as if it had [Alignment.Top] align.
     *
     * Example usage:
     * @sample androidx.compose.foundation.layout.samples.SimpleRelativeToSiblingsInRow
     */
    @Stable
    fun Modifier.alignWithSiblings(alignmentLine: HorizontalAlignmentLine) =
        this.then(SiblingsAlignedModifier.WithAlignmentLine(alignmentLine))

    /**
     * Size the element's width proportional to its [weight] relative to other weighted sibling
     * elements in the [Row]. The parent will divide the horizontal space remaining after measuring
     * unweighted child elements and distribute it according to this weight.
     * When [fill] is true, the element will be forced to occupy the whole width allocated to it.
     * Otherwise, the element is allowed to be smaller - this will result in [Row] being smaller,
     * as the unused allocated width will not be redistributed to other siblings.
     */
    @Stable
    fun Modifier.weight(
        @FloatRange(from = 0.0, to = 3.4e38 /* POSITIVE_INFINITY */, fromInclusive = false)
        weight: Float,
        fill: Boolean = true
    ): Modifier {
        require(weight > 0.0) { "invalid weight $weight; must be greater than zero" }
        return this.then(LayoutWeightImpl(weight, fill))
    }

    /**
     * Position the element vertically such that the alignment line for the content as
     * determined by [alignmentLineBlock] aligns with sibling elements also configured to
     * [alignWithSiblings]. [alignWithSiblings] is a form of [align], so both modifiers
     * will not work together if specified for the same layout.
     * Within a [Row], all components with [alignWithSiblings] will align vertically using
     * the specified [HorizontalAlignmentLine]s or values obtained from [alignmentLineBlock],
     * forming a sibling group.
     * At least one element of the sibling group will be placed as it had [Alignment.Top] align
     * in [Row], and the alignment of the other siblings will be then determined such that
     * the alignment lines coincide. Note that if only one element in a [Row] has the
     * [alignWithSiblings] modifier specified the element will be positioned
     * as if it had [Alignment.Top] align.
     *
     * Example usage:
     * @sample androidx.compose.foundation.layout.samples.SimpleRelativeToSiblings
     */
    @Stable
    fun Modifier.alignWithSiblings(
        alignmentLineBlock: (Measured) -> Int
    ) = this.then(SiblingsAlignedModifier.WithAlignmentLineBlock(alignmentLineBlock))

    companion object : RowScope
}
