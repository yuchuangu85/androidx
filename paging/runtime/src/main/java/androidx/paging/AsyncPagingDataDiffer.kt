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

import androidx.annotation.IntRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.paging.LoadType.REFRESH
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper class for mapping a [PagingData] into a
 * [RecyclerView.Adapter][androidx.recyclerview.widget.RecyclerView.Adapter].
 *
 * For simplicity, [PagingDataAdapter] can often be used in place of this class.
 * [AsyncPagingDataDiffer] is exposed for complex cases, and where overriding [PagingDataAdapter] to
 * support paging isn't convenient.
 */
class AsyncPagingDataDiffer<T : Any> @JvmOverloads constructor(
    private val diffCallback: DiffUtil.ItemCallback<T>,
    @Suppress("ListenerLast") // have to suppress for each, due to defaults / JvmOverloads
    private val updateCallback: ListUpdateCallback,
    @Suppress("ListenerLast") // have to suppress for each, due to defaults / JvmOverloads
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    @Suppress("ListenerLast") // have to suppress for each, due to defaults / JvmOverloads
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    @Suppress("MemberVisibilityCanBePrivate") // synthetic access
    internal val differCallback = object : DifferCallback {
        override fun onInserted(position: Int, count: Int) {
            updateCallback.onInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) =
            updateCallback.onRemoved(position, count)

        override fun onChanged(position: Int, count: Int) {
            // NOTE: pass a null payload to convey null -> item, or item -> null
            updateCallback.onChanged(position, count, null)
        }
    }

    /** True if we're currently executing [getItem] */
    @Suppress("MemberVisibilityCanBePrivate") // synthetic access
    internal var inGetItem: Boolean = false

    private val differBase = object : PagingDataDiffer<T>(differCallback, mainDispatcher) {
        override suspend fun presentNewList(
            previousList: NullPaddedList<T>,
            newList: NullPaddedList<T>,
            newCombinedLoadStates: CombinedLoadStates,
            lastAccessedIndex: Int
        ) = when {
            // fast path for no items -> some items
            previousList.size == 0 -> {
                differCallback.onInserted(0, newList.size)
                null
            }
            // fast path for some items -> no items
            newList.size == 0 -> {
                differCallback.onRemoved(0, previousList.size)
                null
            }
            else -> {
                val diffResult = withContext(workerDispatcher) {
                    previousList.computeDiff(newList, diffCallback)
                }
                previousList.dispatchDiff(updateCallback, newList, diffResult)
                previousList.transformAnchorIndex(
                    diffResult = diffResult,
                    newList = newList,
                    oldPosition = lastAccessedIndex
                )
            }
        }

        /**
         * Return if [getItem] is running to post any data modifications.
         *
         * This must be done because RecyclerView can't be modified during an onBind, when
         * [getItem] is generally called.
         */
        override fun postEvents(): Boolean {
            return inGetItem
        }
    }

    private val submitDataId = AtomicInteger(0)

    /**
     * Present a [PagingData] until it is invalidated by a call to [refresh] or
     * [PagingSource.invalidate].
     *
     * [submitData] should be called on the same [CoroutineDispatcher] where updates will be
     * dispatched to UI, typically [Dispatchers.Main]. (this is done for you if you use
     * `lifecycleScope.launch {}`).
     *
     * This method is typically used when collecting from a [Flow][kotlinx.coroutines.flow.Flow]
     * produced by [Pager]. For RxJava or LiveData support, use the non-suspending overload of
     * [submitData], which accepts a [Lifecycle].
     *
     * Note: This method suspends while it is actively presenting page loads from a [PagingData],
     * until the [PagingData] is invalidated. Although cancellation will propagate to this call
     * automatically, collecting from a [Pager.flow] with the intention of presenting the most
     * up-to-date representation of your backing dataset should typically be done using
     * [collectLatest][kotlinx.coroutines.flow.collectLatest].
     *
     *
     * @see [Pager]
     */
    suspend fun submitData(pagingData: PagingData<T>) {
        submitDataId.incrementAndGet()
        differBase.collectFrom(pagingData)
    }

    /**
     * Present a [PagingData] until it is either invalidated or another call to [submitData] is
     * made.
     *
     * This method is typically used when observing a RxJava or LiveData stream produced by [Pager].
     * For [Flow][kotlinx.coroutines.flow.Flow] support, use the suspending overload of
     * [submitData], which automates cancellation via
     * [CoroutineScope][kotlinx.coroutines.CoroutineScope] instead of relying of [Lifecycle].
     *
     * @see submitData
     * @see [Pager]
     */
    fun submitData(lifecycle: Lifecycle, pagingData: PagingData<T>) {
        val id = submitDataId.incrementAndGet()
        lifecycle.coroutineScope.launch {
            // Check id when this job runs to ensure the last synchronous call submitData always
            // wins.
            if (submitDataId.get() == id) {
                differBase.collectFrom(pagingData)
            }
        }
    }

    /**
     * Retry any failed load requests that would result in a [LoadState.Error] update to this
     * [AsyncPagingDataDiffer].
     *
     * Unlike [refresh], this does not invalidate [PagingSource], it only retries failed loads
     * within the same generation of [PagingData].
     *
     * [LoadState.Error] can be generated from two types of load requests:
     *  * [PagingSource.load] returning [PagingSource.LoadResult.Error]
     *  * [RemoteMediator.load] returning [RemoteMediator.MediatorResult.Error]
     */
    fun retry() {
        differBase.retry()
    }

    /**
     * Refresh the data presented by this [AsyncPagingDataDiffer].
     *
     * [refresh] triggers the creation of a new [PagingData] with a new instance of [PagingSource]
     * to represent an updated snapshot of the backing dataset. If a [RemoteMediator] is set,
     * calling [refresh] will also trigger a call to [RemoteMediator.load] with [LoadType] [REFRESH]
     * to allow [RemoteMediator] to check for updates to the dataset backing [PagingSource].
     *
     * Note: This API is intended for UI-driven refresh signals, such as swipe-to-refresh.
     * Invalidation due repository-layer signals, such as DB-updates, should instead use
     * [PagingSource.invalidate].
     *
     * @see PagingSource.invalidate
     *
     * @sample androidx.paging.samples.refreshSample
     */
    fun refresh() {
        differBase.refresh()
    }

    /**
     * Get the item from the current PagedList at the specified index.
     *
     * Note that this operates on both loaded items and null padding within the PagedList.
     *
     * @param index Index of item to get, must be >= 0, and < [itemCount]
     * @return The item, or `null`, if a `null` placeholder is at the specified position.
     */
    fun getItem(@IntRange(from = 0) index: Int): T? {
        try {
            inGetItem = true
            return differBase[index]
        } finally {
            inGetItem = false
        }
    }

    /**
     * Returns the presented item at the specified position, without notifying Paging of the item
     * access that would normally trigger page loads.
     *
     * @param index Index of the presented item to return, including placeholders.
     * @return The presented item at position [index], `null` if it is a placeholder
     */
    fun peek(@IntRange(from = 0) index: Int): T? {
        return differBase.peek(index)
    }

    /**
     * Returns a new [ItemSnapshotList] representing the currently presented items, including any
     * placeholders if they are enabled.
     */
    fun snapshot(): ItemSnapshotList<T> = differBase.snapshot()

    /**
     * Get the number of items currently presented by this Differ. This value can be directly
     * returned to [androidx.recyclerview.widget.RecyclerView.Adapter.getItemCount].
     *
     * @return Number of items being presented.
     */
    val itemCount: Int
        get() = differBase.size

    /**
     * A hot [Flow] of [CombinedLoadStates] that emits a snapshot whenever the loading state of the
     * current [PagingData] changes.
     *
     * This flow is conflated, so it buffers the last update to [CombinedLoadStates] and
     * immediately delivers the current load states on collection.
     *
     * @sample androidx.paging.samples.loadStateFlowSample
     */
    @OptIn(FlowPreview::class)
    val loadStateFlow: Flow<CombinedLoadStates> = differBase.loadStateFlow

    /**
     * Add a [CombinedLoadStates] listener to observe the loading state of the current [PagingData].
     *
     * As new [PagingData] generations are submitted and displayed, the listener will be notified to
     * reflect the current [CombinedLoadStates].
     *
     * @param listener [LoadStates] listener to receive updates.
     *
     * @see removeLoadStateListener
     *
     * @sample androidx.paging.samples.addLoadStateListenerSample
     */
    fun addLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        differBase.addLoadStateListener(listener)
    }

    /**
     * Remove a previously registered [CombinedLoadStates] listener.
     *
     * @param listener Previously registered listener.
     * @see addLoadStateListener
     */
    fun removeLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        differBase.removeLoadStateListener(listener)
    }

    /**
     * A [Flow] of [Boolean] that is emitted when new [PagingData] generations are submitted and
     * displayed. The [Boolean] that is emitted is `true` if the new [PagingData] is empty,
     * `false` otherwise.
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        "dataRefreshFlow is now redundant with the information passed from loadStateFlow and " +
                "getItemCount, and will be removed in a future alpha version"
    )
    @ExperimentalPagingApi
    val dataRefreshFlow: Flow<Boolean> = differBase.dataRefreshFlow

    /**
     * Add a listener to observe new [PagingData] generations.
     *
     * @param listener called whenever a new [PagingData] is submitted and displayed. `true` is
     * passed to the [listener] if the new [PagingData] is empty, `false` otherwise.
     *
     * @see removeDataRefreshListener
     */
    @Deprecated(
        "dataRefreshListener is now redundant with the information passed from loadStateListener " +
                "and getItemCount, and will be removed in a future alpha version"
    )
    @ExperimentalPagingApi
    fun addDataRefreshListener(listener: (isEmpty: Boolean) -> Unit) {
        @Suppress("DEPRECATION")
        differBase.addDataRefreshListener(listener)
    }

    /**
     * Remove a previously registered listener for new [PagingData] generations.
     *
     * @param listener Previously registered listener.
     *
     * @see addDataRefreshListener
     */
    @Deprecated(
        "dataRefreshListener is now redundant with the information passed from loadStateListener " +
                "and getItemCount, and will be removed in a future alpha version"
    )
    @ExperimentalPagingApi
    fun removeDataRefreshListener(listener: (isEmpty: Boolean) -> Unit) {
        @Suppress("DEPRECATION")
        differBase.removeDataRefreshListener(listener)
    }
}