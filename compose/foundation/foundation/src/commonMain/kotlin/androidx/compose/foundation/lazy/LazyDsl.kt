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

package androidx.compose.foundation.lazy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@RequiresOptIn(
    "This is an experimental API for demonstrating how LazyColumn / LazyRow should work" +
            "using a DSL implementation. This is a prototype and its implementation is not suited" +
            " for PagedList or large lists."
)
annotation class ExperimentalLazyDsl

/**
 * Receiver scope which is used by [LazyColumn] and [LazyRow].
 */
interface LazyListScope {
    /**
     * Adds a list of items and their content to the scope.
     *
     * @param items the data list
     * @param itemContent the content displayed by a single item
     */
    fun <T : Any> items(
        items: List<T>,
        itemContent: @Composable LazyItemScope.(item: T) -> Unit
    )

    /**
     * Adds a single item to the scope.
     *
     * @param content the content of the item
     */
    fun item(content: @Composable LazyItemScope.() -> Unit)

    /**
     * Adds a list of items to the scope where the content of an item is aware of its index.
     *
     * @param items the data list
     * @param itemContent the content displayed by a single item
     */
    fun <T : Any> itemsIndexed(
        items: List<T>,
        itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit
    )
}

private class IntervalHolder(
    val startIndex: Int,
    val content: LazyItemScope.(Int) -> (@Composable () -> Unit)
)

private class LazyListScopeImpl : LazyListScope {
    val intervals = mutableListOf<IntervalHolder>()
    var totalSize = 0

    fun contentFor(index: Int, scope: LazyItemScope): @Composable () -> Unit {
        val intervalIndex = findIndexOfHighestValueLesserThan(intervals, index)

        val interval = intervals[intervalIndex]
        val localIntervalIndex = index - interval.startIndex

        return interval.content(scope, localIntervalIndex)
    }

    override fun <T : Any> items(
        items: List<T>,
        itemContent: @Composable LazyItemScope.(item: T) -> Unit
    ) {
        val interval = IntervalHolder(
            startIndex = totalSize,
            content = { index ->
                val item = items[index]

                { itemContent(item) }
            }
        )

        totalSize += items.size

        intervals.add(interval)
    }

    override fun item(content: @Composable LazyItemScope.() -> Unit) {
        val interval = IntervalHolder(
            startIndex = totalSize,
            content = { { content() } }
        )

        totalSize += 1

        intervals.add(interval)
    }

    override fun <T : Any> itemsIndexed(
        items: List<T>,
        itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit
    ) {
        val interval = IntervalHolder(
            startIndex = totalSize,
            content = { index ->
                val item = items[index]

                { itemContent(index, item) }
            }
        )

        totalSize += items.size

        intervals.add(interval)
    }

    /**
     * Finds the index of the [list] which contains the highest value of [IntervalHolder.startIndex]
     * that is less than or equal to the given [value].
     */
    private fun findIndexOfHighestValueLesserThan(list: List<IntervalHolder>, value: Int): Int {
        var left = 0
        var right = list.lastIndex

        while (left < right) {
            val middle = (left + right) / 2

            val middleValue = list[middle].startIndex
            if (middleValue == value) {
                return middle
            }

            if (middleValue < value) {
                left = middle + 1

                // Verify that the left will not be bigger than our value
                if (value < list[left].startIndex) {
                    return middle
                }
            } else {
                right = middle - 1
            }
        }

        return left
    }
}

/**
 * The DSL implementation of a horizontally scrolling list that only composes and lays out the
 * currently visible items.
 * This API is not stable yet, please consider using [LazyRowFor] instead.
 *
 * @param modifier the modifier to apply to this layout
 * @param contentPadding specify a padding around the whole content
 * @param verticalAlignment the vertical alignment applied to the items
 * @param content the [LazyListScope] which describes the content
 */
@Composable
@ExperimentalLazyDsl
fun LazyRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit
) {
    val scope = LazyListScopeImpl()
    scope.apply(content)

    LazyFor(
        itemsCount = scope.totalSize,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalAlignment = verticalAlignment,
        isVertical = false
    ) { index -> scope.contentFor(index, this) }
}

/**
 * The DSL implementation of a vertically scrolling list that only composes and lays out the
 * currently visible items.
 * This API is not stable yet, please consider using [LazyColumnFor] instead.
 *
 * @param modifier the modifier to apply to this layout
 * @param contentPadding specify a padding around the whole content
 * @param horizontalAlignment the horizontal alignment applied to the items
 * @param content the [LazyListScope] which describes the content
 */
@Composable
@ExperimentalLazyDsl
fun LazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit
) {
    val scope = LazyListScopeImpl()
    scope.apply(content)

    LazyFor(
        itemsCount = scope.totalSize,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = horizontalAlignment,
        isVertical = true
    ) {
        index -> scope.contentFor(index, this)
    }
}