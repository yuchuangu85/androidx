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

package androidx.compose.ui

import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastForEach

@OptIn(ExperimentalLayoutNodeApi::class)
internal object RootMeasureBlocks : LayoutNode.NoIntrinsicsMeasureBlocks(
    "Undefined intrinsics block and it is required"
) {
    override fun measure(
        measureScope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        return when {
            measurables.isEmpty() -> measureScope.layout(0, 0) {}
            measurables.size == 1 -> {
                val placeable = measurables[0].measure(constraints)
                measureScope.layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
            else -> {
                val placeables = measurables.map {
                    it.measure(constraints)
                }
                var maxWidth = 0
                var maxHeight = 0
                placeables.fastForEach { placeable ->
                    maxWidth = maxOf(placeable.width, maxWidth)
                    maxHeight = maxOf(placeable.height, maxHeight)
                }
                measureScope.layout(maxWidth, maxHeight) {
                    placeables.fastForEach { placeable ->
                        placeable.placeRelative(0, 0)
                    }
                }
            }
        }
    }
}