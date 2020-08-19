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

package androidx.paging

import androidx.annotation.CheckResult
import androidx.paging.LoadState.NotLoading
import androidx.paging.LoadType.APPEND
import androidx.paging.LoadType.PREPEND
import androidx.paging.LoadType.REFRESH
import androidx.paging.PageEvent.Insert.Companion.Append
import androidx.paging.PageEvent.Insert.Companion.Prepend
import androidx.paging.PageEvent.Insert.Companion.Refresh
import androidx.paging.PagingConfig.Companion.MAX_SIZE_UNBOUNDED
import androidx.paging.PagingSource.LoadResult.Page
import androidx.paging.PagingSource.LoadResult.Page.Companion.COUNT_UNDEFINED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onStart

/**
 * Internal state of [PageFetcherSnapshot] whose updates can be consumed as a [Flow] of [PageEvent].
 */
internal class PageFetcherSnapshotState<Key : Any, Value : Any>(
    private val config: PagingConfig,
    hasRemoteState: Boolean
) {
    private val _pages = mutableListOf<Page<Key, Value>>()
    internal val pages: List<Page<Key, Value>> = _pages
    internal var initialPageIndex = 0
        private set

    internal val storageCount
        get() = pages.sumBy { it.data.size }

    private var _placeholdersBefore = 0

    /**
     * Always greater than or equal to 0.
     */
    internal var placeholdersBefore
        get() = when {
            config.enablePlaceholders -> _placeholdersBefore
            else -> 0
        }
        set(value) {
            _placeholdersBefore = when (value) {
                COUNT_UNDEFINED -> 0
                else -> value
            }
        }

    private var _placeholdersAfter = 0

    /**
     * Always greater than or equal to 0.
     */
    internal var placeholdersAfter
        get() = when {
            config.enablePlaceholders -> _placeholdersAfter
            else -> 0
        }
        set(value) {
            _placeholdersAfter = when (value) {
                COUNT_UNDEFINED -> 0
                else -> value
            }
        }

    internal var prependLoadId = 0
        private set
    internal var appendLoadId = 0
        private set
    private val prependLoadIdCh = Channel<Int>(Channel.CONFLATED)
    private val appendLoadIdCh = Channel<Int>(Channel.CONFLATED)

    /**
     * Cache previous ViewportHint which triggered any failed PagingSource APPEND / PREPEND that
     * we can later retry. This is so we always trigger loads based on hints, instead of having
     * two different ways to trigger.
     */
    internal val failedHintsByLoadType = mutableMapOf<LoadType, ViewportHint>()
    internal val loadStates = MutableLoadStateCollection(hasRemoteState)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumePrependGenerationIdAsFlow(): Flow<Int> {
        return prependLoadIdCh.consumeAsFlow()
            .onStart { prependLoadIdCh.offer(prependLoadId) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeAppendGenerationIdAsFlow(): Flow<Int> {
        return appendLoadIdCh.consumeAsFlow()
            .onStart { appendLoadIdCh.offer(appendLoadId) }
    }

    /**
     * Convert a loaded [Page] into a [PageEvent] for [PageFetcherSnapshot.pageEventCh].
     *
     * Note: This method should be called after state updated by [insert]
     *
     * TODO: Move this into Pager, which owns pageEventCh, since this logic is sensitive to its
     *  implementation.
     */
    internal fun Page<Key, Value>.toPageEvent(loadType: LoadType): PageEvent<Value> {
        val sourcePageIndex = when (loadType) {
            REFRESH -> 0
            PREPEND -> 0 - initialPageIndex
            APPEND -> pages.size - initialPageIndex - 1
        }
        val pages = listOf(TransformablePage(sourcePageIndex, data))
        return when (loadType) {
            REFRESH -> Refresh(
                pages = pages,
                placeholdersBefore = placeholdersBefore,
                placeholdersAfter = placeholdersAfter,
                combinedLoadStates = loadStates.snapshot()
            )
            PREPEND -> Prepend(
                pages = pages,
                placeholdersBefore = placeholdersBefore,
                combinedLoadStates = loadStates.snapshot()
            )
            APPEND -> Append(
                pages = pages,
                placeholdersAfter = placeholdersAfter,
                combinedLoadStates = loadStates.snapshot()
            )
        }
    }

    /**
     * @return true if insert was applied, false otherwise.
     */
    @CheckResult
    fun insert(loadId: Int, loadType: LoadType, page: Page<Key, Value>): Boolean {
        when (loadType) {
            REFRESH -> {
                check(pages.isEmpty()) { "cannot receive multiple init calls" }
                check(loadId == 0) { "init loadId must be the initial value, 0" }

                _pages.add(page)
                initialPageIndex = 0
                placeholdersAfter = page.itemsAfter
                placeholdersBefore = page.itemsBefore
            }
            PREPEND -> {
                check(pages.isNotEmpty()) { "should've received an init before prepend" }

                // Skip this insert if it is the result of a cancelled job due to page drop
                if (loadId != prependLoadId) return false

                _pages.add(0, page)
                initialPageIndex++
                placeholdersBefore = if (page.itemsBefore == COUNT_UNDEFINED) {
                    (placeholdersBefore - page.data.size).coerceAtLeast(0)
                } else {
                    page.itemsBefore
                }

                // Clear error on successful insert
                failedHintsByLoadType.remove(PREPEND)
            }
            APPEND -> {
                check(pages.isNotEmpty()) { "should've received an init before append" }

                // Skip this insert if it is the result of a cancelled job due to page drop
                if (loadId != appendLoadId) return false

                _pages.add(page)
                placeholdersAfter = if (page.itemsAfter == COUNT_UNDEFINED) {
                    (placeholdersAfter - page.data.size).coerceAtLeast(0)
                } else {
                    page.itemsAfter
                }

                // Clear error on successful insert
                failedHintsByLoadType.remove(APPEND)
            }
        }

        return true
    }

    fun drop(event: PageEvent.Drop<Value>) {
        check(event.pageCount <= pages.size) {
            "invalid drop count. have ${pages.size} but wanted to drop ${event.pageCount}"
        }

        // Reset load state to NotLoading(endOfPaginationReached = false).
        failedHintsByLoadType.remove(event.loadType)
        loadStates.set(event.loadType, false, NotLoading.Incomplete)

        when (event.loadType) {
            PREPEND -> {
                repeat(event.pageCount) { _pages.removeAt(0) }
                initialPageIndex -= event.pageCount

                placeholdersBefore = event.placeholdersRemaining

                prependLoadId++
                prependLoadIdCh.offer(prependLoadId)
            }
            APPEND -> {
                repeat(event.pageCount) { _pages.removeAt(pages.size - 1) }

                placeholdersAfter = event.placeholdersRemaining

                appendLoadId++
                appendLoadIdCh.offer(appendLoadId)
            }
            else -> throw IllegalArgumentException("cannot drop ${event.loadType}")
        }
    }

    /**
     * @return [PageEvent.Drop] for [loadType] that would allow this [PageFetcherSnapshotState] to
     * respect [PagingConfig.maxSize], `null` if no pages should be dropped for the provided
     * [loadType].
     */
    fun dropEventOrNull(loadType: LoadType, hint: ViewportHint): PageEvent.Drop<Value>? {
        if (config.maxSize == MAX_SIZE_UNBOUNDED) return null
        // Never drop below 2 pages as this can cause UI flickering with certain configs and it's
        // much more important to protect against this behaviour over respecting a config where
        // maxSize is set unusually (probably incorrectly) strict.
        if (pages.size <= 2) return null

        if (storageCount <= config.maxSize) return null

        require(loadType != REFRESH) {
            "Drop LoadType must be PREPEND or APPEND, but got $loadType"
        }

        // Compute pageCount and itemsToDrop
        var pagesToDrop = 0
        var itemsToDrop = 0
        while (pagesToDrop < pages.size && storageCount - itemsToDrop > config.maxSize) {
            val pageSize = when (loadType) {
                PREPEND -> pages[pagesToDrop].data.size
                else -> pages[pages.lastIndex - pagesToDrop].data.size
            }
            val itemsAfterDrop = when (loadType) {
                PREPEND -> hint.presentedItemsBefore - itemsToDrop - pageSize
                else -> hint.presentedItemsAfter - itemsToDrop - pageSize
            }
            // Do not drop pages that would fulfill prefetchDistance.
            if (itemsAfterDrop < config.prefetchDistance) break

            itemsToDrop += pageSize
            pagesToDrop++
        }

        return when (pagesToDrop) {
            0 -> null
            else -> PageEvent.Drop(
                loadType = loadType,
                minPageOffset = when (loadType) {
                    // originalPageOffset of the first page.
                    PREPEND -> -initialPageIndex
                    // maxPageOffset - pagesToDrop; We subtract one from pagesToDrop, since this
                    // value is inclusive.
                    else -> pages.lastIndex - initialPageIndex - (pagesToDrop - 1)
                },
                maxPageOffset = when (loadType) {
                    // minPageOffset + pagesToDrop; We subtract on from pagesToDrop, since this
                    // value is inclusive.
                    PREPEND -> (pagesToDrop - 1) - initialPageIndex
                    // originalPageOffset of the last page.
                    else -> pages.lastIndex - initialPageIndex
                },
                placeholdersRemaining = when {
                    !config.enablePlaceholders -> 0
                    loadType == PREPEND -> placeholdersBefore + itemsToDrop
                    else -> placeholdersAfter + itemsToDrop
                }
            )
        }
    }
}
