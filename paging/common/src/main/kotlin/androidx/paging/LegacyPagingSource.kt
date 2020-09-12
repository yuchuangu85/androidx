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

import androidx.paging.DataSource.KeyType.ITEM_KEYED
import androidx.paging.DataSource.KeyType.PAGE_KEYED
import androidx.paging.DataSource.KeyType.POSITIONAL
import androidx.paging.DataSource.Params
import androidx.paging.LoadType.APPEND
import androidx.paging.LoadType.PREPEND
import androidx.paging.LoadType.REFRESH
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A wrapper around [DataSource] which adapts it to the [PagingSource] API.
 */
internal class LegacyPagingSource<Key : Any, Value : Any>(
    private val fetchDispatcher: CoroutineDispatcher = DirectDispatcher,
    internal val dataSourceFactory: () -> DataSource<Key, Value>
) : PagingSource<Key, Value>() {
    // Lazily initialize because it must be created on fetchDispatcher, but PagingSourceFactory
    // passed to Pager is a non-suspending method.
    internal val dataSource by lazy {
        dataSourceFactory().also { dataSource ->
            dataSource.addInvalidatedCallback(::invalidate)
            // LegacyPagingSource registers invalidate callback after DataSource is created, so we
            // need to check for race condition here. If DataSource is already invalid, simply
            // propagate invalidation manually.
            if (dataSource.isInvalid && !invalid) {
                dataSource.removeInvalidatedCallback(::invalidate)
                // Note: Calling this.invalidate directly will re-evaluate dataSource's by lazy
                // init block, since we haven't returned a value for dataSource yet.
                super.invalidate()
            }
        }
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> {
        val type = when (params) {
            is LoadParams.Refresh -> REFRESH
            is LoadParams.Append -> APPEND
            is LoadParams.Prepend -> PREPEND
        }
        val dataSourceParams = Params(
            type,
            params.key,
            params.loadSize,
            params.placeholdersEnabled,
            @Suppress("DEPRECATION")
            params.pageSize
        )

        return withContext(fetchDispatcher) {
            dataSource.load(dataSourceParams).run {
                LoadResult.Page(
                    data,
                    @Suppress("UNCHECKED_CAST")
                    if (data.isEmpty() && params is LoadParams.Prepend) null else prevKey as Key?,
                    @Suppress("UNCHECKED_CAST")
                    if (data.isEmpty() && params is LoadParams.Append) null else nextKey as Key?,
                    itemsBefore,
                    itemsAfter
                )
            }
        }
    }

    override fun invalidate() {
        super.invalidate()
        dataSource.invalidate()
    }

    @OptIn(ExperimentalPagingApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun getRefreshKey(state: PagingState<Key, Value>): Key? {
        return when (dataSource.type) {
            POSITIONAL -> state.anchorPosition as Key
            PAGE_KEYED -> null
            ITEM_KEYED -> state.anchorPosition
                ?.let { anchorPosition -> state.closestItemToPosition(anchorPosition) }
                ?.let { item -> dataSource.getKeyInternal(item) }
        }
    }

    override val jumpingSupported: Boolean
        get() = dataSource.type == POSITIONAL
}
