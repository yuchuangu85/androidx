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

package androidx.compose.ui.selection

import androidx.compose.ui.AlignmentLine
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize

class MockCoordinates(
    override var size: IntSize = IntSize.Zero,
    var localOffset: Offset = Offset.Zero,
    var globalOffset: Offset = Offset.Zero,
    var rootOffset: Offset = Offset.Zero,
    var childToLocalOffset: Offset = Offset.Zero,
    override var isAttached: Boolean = true
) : LayoutCoordinates {
    val globalToLocalParams = mutableListOf<Offset>()
    val localToGlobalParams = mutableListOf<Offset>()
    val localToRootParams = mutableListOf<Offset>()
    val childToLocalParams = mutableListOf<Pair<LayoutCoordinates, Offset>>()

    override val providedAlignmentLines: Set<AlignmentLine>
        get() = emptySet()
    override val parentCoordinates: LayoutCoordinates?
        get() = null
    override fun globalToLocal(global: Offset): Offset {
        globalToLocalParams += global
        return localOffset
    }

    override fun localToGlobal(local: Offset): Offset {
        localToGlobalParams += local
        return globalOffset
    }

    override fun localToRoot(local: Offset): Offset {
        localToRootParams += local
        return rootOffset
    }

    override fun childToLocal(child: LayoutCoordinates, childLocal: Offset): Offset {
        childToLocalParams += child to childLocal
        return childToLocalOffset
    }

    override fun childBoundingBox(child: LayoutCoordinates): Rect = Rect.Zero

    override fun get(line: AlignmentLine): Int = 0
}