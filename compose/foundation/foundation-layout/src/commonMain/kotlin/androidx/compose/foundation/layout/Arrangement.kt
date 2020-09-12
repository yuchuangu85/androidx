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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Used to specify the arrangement of the layout's children in [Row] or [Column] in the main axis
 * direction (horizontal and vertical, respectively).
 */
@Immutable
@OptIn(InternalLayoutApi::class)
object Arrangement {
    /**
     * Used to specify the horizontal arrangement of the layout's children in a [Row].
     */
    @InternalLayoutApi
    interface Horizontal {
        /**
         * Spacing that should be added between any two adjacent layout children.
         */
        val spacing get() = 0.dp

        /**
         * Horizontally places the layout children inside the [Row].
         *
         * @param totalSize Available space that can be occupied by the children.
         * @param size A list of sizes of all children.
         * @param layoutDirection A layout direction, left-to-right or right-to-left, of the parent
         * layout that should be taken into account when determining positions of the children.
         * @param density The current density.
         * @param outPosition A preallocated list that should return the calculated positions.
         */
        fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        )

        @Deprecated("Custom arrangements will not be supported anymore. Please use a provided " +
                "one instead, or Spacers.")
        fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection
        ): List<Int> {
            val result = MutableList(size.size) { 0 }
            arrange(totalSize, size, layoutDirection, Density(1f), result)
            return result
        }
    }

    /**
     * Used to specify the vertical arrangement of the layout's children in a [Column].
     */
    @InternalLayoutApi
    interface Vertical {
        /**
         * Spacing that should be added between any two adjacent layout children.
         */
        val spacing get() = 0.dp

        /**
         * Vertically places the layout children inside the [Column].
         *
         * @param totalSize Available space that can be occupied by the children.
         * @param size A list of sizes of all children.
         * @param density The current density.
         * @param outPosition A preallocated list that should return the calculated positions.
         */
        fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        )

        @Deprecated("Custom arrangements will not be supported anymore. Please use a provided " +
                "one instead, or Spacers.")
        fun arrange(
            totalSize: Int,
            size: List<Int>
        ): List<Int> {
            val result = MutableList(size.size) { 0 }
            arrange(totalSize, size, Density(1f), result)
            return result
        }
    }

    /**
     * Used to specify the horizontal arrangement of the layout's children in a [Row], or
     * the vertical arrangement of the layout's children in a [Column].
     */
    @InternalLayoutApi
    interface HorizontalOrVertical : Horizontal, Vertical {
        /**
         * Spacing that should be added between any two adjacent layout children.
         */
        override val spacing: Dp get() = 0.dp
    }

    /**
     * Place children horizontally such that they are as close as possible to the beginning of the
     * main axis.
     */
    val Start = object : Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeLeftOrTop(size, outPosition)
        } else {
            placeRightOrBottom(totalSize, size.asReversed(), outPosition)
            outPosition.reverse()
        }
    }

    /**
     * Place children horizontally such that they are as close as possible to the end of the main
     * axis.
     */
    val End = object : Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeRightOrBottom(totalSize, size, outPosition)
        } else {
            placeLeftOrTop(size.asReversed(), outPosition)
            outPosition.reverse()
        }
    }

    /**
     * Place children vertically such that they are as close as possible to the top of the main
     * axis.
     */
    val Top = object : Vertical {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = placeLeftOrTop(size, outPosition)
    }

    /**
     * Place children vertically such that they are as close as possible to the bottom of the main
     * axis.
     */
    val Bottom = object : Vertical {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = placeRightOrBottom(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are as close as possible to the middle of the main axis.
     */
    val Center = object : HorizontalOrVertical {
        override val spacing = 0.dp

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeCenter(totalSize, size, outPosition)
        } else {
            placeCenter(totalSize, size.asReversed(), outPosition)
            outPosition.reverse()
        }

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = placeCenter(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child.
     */
    val SpaceEvenly = object : HorizontalOrVertical {
        override val spacing = 0.dp

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeSpaceEvenly(totalSize, size, outPosition)
        } else {
            placeSpaceEvenly(totalSize, size.asReversed(), outPosition)
            outPosition.reverse()
        }

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = placeSpaceEvenly(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are spaced evenly across the main axis, without free
     * space before the first child or after the last child.
     */
    val SpaceBetween = object : HorizontalOrVertical {
        override val spacing = 0.dp

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeSpaceBetween(totalSize, size, outPosition)
        } else {
            placeSpaceBetween(totalSize, size.asReversed(), outPosition)
            outPosition.reverse()
        }

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = placeSpaceBetween(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child, but half the amount of space
     * existing otherwise between two consecutive children.
     */
    val SpaceAround = object : HorizontalOrVertical {
        override val spacing = 0.dp

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeSpaceAround(totalSize, size, outPosition)
        } else {
            placeSpaceAround(totalSize, size.asReversed(), outPosition)
            outPosition.reverse()
        }

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = placeSpaceAround(totalSize, size, outPosition)
    }

    /**
     * Place children such that each two adjacent ones are spaced by a fixed [space] distance across
     * the main axis. The spacing will be subtracted from the available space that the children
     * can occupy.
     *
     * @param space The space between adjacent children.
     */
    fun spacedBy(space: Dp): HorizontalOrVertical =
        SpacedAligned(space, true, null)

    /**
     * Place children horizontally such that each two adjacent ones are spaced by a fixed [space]
     * distance. The spacing will be subtracted from the available width that the children
     * can occupy. An [alignment] can be specified to align the spaced children horizontally
     * inside the parent, in case there is empty width remaining.
     *
     * @param space The space between adjacent children.
     * @param alignment The alignment of the spaced children inside the parent.
     */
    fun spacedBy(space: Dp, alignment: Alignment.Horizontal): Horizontal =
        SpacedAligned(space, true) { size, layoutDirection ->
            alignment.align(size, layoutDirection)
        }

    /**
     * Place children vertically such that each two adjacent ones are spaced by a fixed [space]
     * distance. The spacing will be subtracted from the available height that the children
     * can occupy. An [alignment] can be specified to align the spaced children vertically
     * inside the parent, in case there is empty height remaining.
     *
     * @param space The space between adjacent children.
     * @param alignment The alignment of the spaced children inside the parent.
     */
    fun spacedBy(space: Dp, alignment: Alignment.Vertical): Vertical =
        SpacedAligned(space, false) { size, _ -> alignment.align(size) }

    /**
     * Place children horizontally one next to the other and align the obtained group
     * according to an [alignment].
     *
     * @param alignment The alignment of the children inside the parent.
     */
    fun aligned(alignment: Alignment.Horizontal): Horizontal =
        SpacedAligned(0.dp, true) { size, layoutDirection ->
            alignment.align(size, layoutDirection)
        }

    /**
     * Place children vertically one next to the other and align the obtained group
     * according to an [alignment].
     *
     * @param alignment The alignment of the children inside the parent.
     */
    fun aligned(alignment: Alignment.Vertical): Vertical =
        SpacedAligned(0.dp, false) { size, _ -> alignment.align(size) }

    /**
     * Arrangement with spacing between adjacent children and alignment for the spaced group.
     * Should not be instantiated directly, use [spacedBy] instead.
     */
    internal data class SpacedAligned(
        val space: Dp,
        val rtlMirror: Boolean,
        val alignment: ((Int, LayoutDirection) -> Int)?
    ) : HorizontalOrVertical {
        override val spacing = space

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) {
            if (size.isEmpty()) return
            val spacePx = with(density) { space.toIntPx() }

            var occupied = 0
            var lastSpace = 0
            (if (layoutDirection == LayoutDirection.Ltr || !rtlMirror) size else size.asReversed())
                .fastForEachIndexed { index, it ->
                    outPosition[index] = min(occupied, totalSize - it)
                    lastSpace = min(spacePx, totalSize - outPosition[index] - it)
                    occupied = outPosition[index] + it + lastSpace
                }
            occupied -= lastSpace

            if (alignment != null && occupied < totalSize) {
                val groupPosition = alignment.invoke(totalSize - occupied, layoutDirection)
                for (index in outPosition.indices) {
                    outPosition[index] += groupPosition
                }
            }

            if (layoutDirection == LayoutDirection.Rtl && rtlMirror) outPosition.reverse()
        }

        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            density: Density,
            outPosition: MutableList<Int>
        ) = arrange(totalSize, size, LayoutDirection.Ltr, density, outPosition)
    }

    internal fun placeRightOrBottom(
        totalSize: Int,
        size: List<Int>,
        outPosition: MutableList<Int>
    ) {
        val consumedSize = size.fold(0) { a, b -> a + b }
        var current = totalSize - consumedSize
        size.fastForEachIndexed { index, it ->
            outPosition[index] = current
            current += it
        }
    }

    internal fun placeLeftOrTop(size: List<Int>, outPosition: MutableList<Int>) {
        var current = 0
        size.fastForEachIndexed { index, it ->
            outPosition[index] = current
            current += it
        }
    }

    internal fun placeCenter(totalSize: Int, size: List<Int>, outPosition: MutableList<Int>) {
        val consumedSize = size.fold(0) { a, b -> a + b }
        var current = (totalSize - consumedSize).toFloat() / 2
        size.fastForEachIndexed { index, it ->
            outPosition[index] = current.roundToInt()
            current += it.toFloat()
        }
    }

    internal fun placeSpaceEvenly(totalSize: Int, size: List<Int>, outPosition: MutableList<Int>) {
        val consumedSize = size.fold(0) { a, b -> a + b }
        val gapSize = (totalSize - consumedSize).toFloat() / (size.size + 1)
        var current = gapSize
        size.fastForEachIndexed { index, it ->
            outPosition[index] = current.roundToInt()
            current += it.toFloat() + gapSize
        }
    }

    internal fun placeSpaceBetween(totalSize: Int, size: List<Int>, outPosition: MutableList<Int>) {
        val consumedSize = size.fold(0) { a, b -> a + b }
        val gapSize = if (size.size > 1) {
            (totalSize - consumedSize).toFloat() / (size.size - 1)
        } else {
            0f
        }
        var current = 0f
        size.fastForEachIndexed { index, it ->
            outPosition[index] = current.roundToInt()
            current += it.toFloat() + gapSize
        }
    }

    internal fun placeSpaceAround(totalSize: Int, size: List<Int>, outPosition: MutableList<Int>) {
        val consumedSize = size.fold(0) { a, b -> a + b }
        val gapSize = if (size.isNotEmpty()) {
            (totalSize - consumedSize).toFloat() / size.size
        } else {
            0f
        }
        var current = gapSize / 2
        size.fastForEachIndexed { index, it ->
            outPosition[index] = current.roundToInt()
            current += it.toFloat() + gapSize
        }
    }
}

@Immutable
@OptIn(InternalLayoutApi::class)
object AbsoluteArrangement {
    /**
     * Place children horizontally such that they are as close as possible to the left edge of
     * the [Row].
     *
     * Unlike [Arrangement.Start], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     */
    val Left = object : Arrangement.Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = Arrangement.placeLeftOrTop(size, outPosition)
    }

    /**
     * Place children such that they are as close as possible to the middle of the [Row].
     *
     * Unlike [Arrangement.Center], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     */
    val Center = object : Arrangement.Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = Arrangement.placeCenter(totalSize, size, outPosition)
    }

    /**
     * Place children horizontally such that they are as close as possible to the right edge of
     * the [Row].
     *
     * Unlike [Arrangement.End], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     */
    val Right = object : Arrangement.Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = Arrangement.placeRightOrBottom(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are spaced evenly across the main axis, without free
     * space before the first child or after the last child.
     *
     * Unlike [Arrangement.SpaceBetween], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     */
    val SpaceBetween = object : Arrangement.Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = Arrangement.placeSpaceBetween(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child.
     *
     * Unlike [Arrangement.SpaceEvenly], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     */
    val SpaceEvenly = object : Arrangement.Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = Arrangement.placeSpaceEvenly(totalSize, size, outPosition)
    }

    /**
     * Place children such that they are spaced evenly horizontally, including free
     * space before the first child and after the last child, but half the amount of space
     * existing otherwise between two consecutive children.
     *
     * Unlike [Arrangement.SpaceAround], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     */
    val SpaceAround = object : Arrangement.Horizontal {
        override fun arrange(
            totalSize: Int,
            size: List<Int>,
            layoutDirection: LayoutDirection,
            density: Density,
            outPosition: MutableList<Int>
        ) = Arrangement.placeSpaceAround(totalSize, size, outPosition)
    }

    /**
     * Place children such that each two adjacent ones are spaced by a fixed [space] distance across
     * the main axis. The spacing will be subtracted from the available space that the children
     * can occupy.
     *
     * Unlike [Arrangement.spacedBy], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     *
     * @param space The space between adjacent children.
     */
    fun spacedBy(space: Dp): Arrangement.HorizontalOrVertical =
        Arrangement.SpacedAligned(space, false, null)

    /**
     * Place children horizontally such that each two adjacent ones are spaced by a fixed [space]
     * distance. The spacing will be subtracted from the available width that the children
     * can occupy. An [alignment] can be specified to align the spaced children horizontally
     * inside the parent, in case there is empty width remaining.
     *
     * Unlike [Arrangement.spacedBy], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     *
     * @param space The space between adjacent children.
     * @param alignment The alignment of the spaced children inside the parent.
     */
    fun spacedBy(space: Dp, alignment: Alignment.Horizontal): Arrangement.Horizontal =
        Arrangement.SpacedAligned(space, false) { size, layoutDirection ->
            alignment.align(size, layoutDirection)
        }

    /**
     * Place children vertically such that each two adjacent ones are spaced by a fixed [space]
     * distance. The spacing will be subtracted from the available height that the children
     * can occupy. An [alignment] can be specified to align the spaced children vertically
     * inside the parent, in case there is empty height remaining.
     *
     * Unlike [Arrangement.spacedBy], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     *
     * @param space The space between adjacent children.
     * @param alignment The alignment of the spaced children inside the parent.
     */
    fun spacedBy(space: Dp, alignment: Alignment.Vertical): Arrangement.Vertical =
        Arrangement.SpacedAligned(space, false) { size, _ -> alignment.align(size) }

    /**
     * Place children horizontally one next to the other and align the obtained group
     * according to an [alignment].
     *
     * Unlike [Arrangement.aligned], when the layout direction is RTL, the children will not be
     * mirrored and as such children will appear in the order they are composed inside the [Row].
     *
     * @param alignment The alignment of the children inside the parent.
     */
    fun aligned(alignment: Alignment.Horizontal): Arrangement.Horizontal =
        Arrangement.SpacedAligned(0.dp, false) { size, layoutDirection ->
            alignment.align(size, layoutDirection)
        }
}
