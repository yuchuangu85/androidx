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

package androidx.compose.ui

import androidx.compose.runtime.Immutable

/**
 * Defines an offset line that can be used by parent layouts to align and position their children.
 * When a layout provides a value for a particular [AlignmentLine], this can be read by the
 * parents of the layout after measuring, using the [Placeable.get] operator on the corresponding
 * [Placeable] instance. Based on the position of the [AlignmentLine], the parents can then decide
 * the positioning of the children.
 *
 * Note that when a layout provides a value for an [AlignmentLine], this will be automatically
 * inherited by the layout's parent, which will offset the value by the position of the child
 * within itself. This way, nested layout hierarchies are able to preserve the [AlignmentLine]s
 * defined for deeply nested children, making it possible for non-direct parents to use these for
 * positioning and alignment. When a layout inherits multiple values for the same [AlignmentLine]
 * from different children, the position of the line within the layout will be computed by merging
 * the children values using the provided [merger]. If a layout provides a value for an
 * [AlignmentLine], this will always be the position of the line, regardless of the values
 * provided by children for the same line.
 *
 * [AlignmentLine]s cannot be created directly, please create [VerticalAlignmentLine] or
 * [HorizontalAlignmentLine] instances instead.
 *
 * @sample androidx.compose.ui.samples.AlignmentLineSample
 *
 * @see VerticalAlignmentLine
 * @see HorizontalAlignmentLine
 *
 * @param merger Defines the position of an alignment line inherited from more than one child.
 */
@Immutable
sealed class AlignmentLine(
    internal val merger: (Int, Int) -> Int
) {
    companion object {
        /**
         * Constant representing that an [AlignmentLine] has not been provided.
         */
        const val Unspecified = Int.MIN_VALUE
    }
}

/**
 * Merges two values of the current [alignment line][AlignmentLine].
 */
fun AlignmentLine.merge(position1: Int, position2: Int) = merger(position1, position2)

/**
 * A vertical [AlignmentLine].
 *
 * @param merger How to merge two alignment line values defined by different children
 */
class VerticalAlignmentLine(merger: (Int, Int) -> Int) : AlignmentLine(merger)

/**
 * A horizontal [AlignmentLine].
 *
 * @param merger How to merge two alignment line values defined by different children
 */
class HorizontalAlignmentLine(merger: (Int, Int) -> Int) : AlignmentLine(merger)
