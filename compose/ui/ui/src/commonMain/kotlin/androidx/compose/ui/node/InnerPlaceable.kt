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

package androidx.compose.ui.node

import androidx.compose.ui.AlignmentLine
import androidx.compose.ui.Placeable
import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach

@OptIn(ExperimentalLayoutNodeApi::class)
internal class InnerPlaceable(
    layoutNode: LayoutNode
) : LayoutNodeWrapper(layoutNode), Density by layoutNode.measureScope {

    override val providedAlignmentLines: Set<AlignmentLine>
        get() = layoutNode.providedAlignmentLines.keys
    override val isAttached: Boolean
        get() = layoutNode.isAttached()

    override val measureScope get() = layoutNode.measureScope

    override fun performMeasure(constraints: Constraints): Placeable {
        val measureResult = layoutNode.measureBlocks.measure(
            layoutNode.measureScope,
            layoutNode.children,
            constraints
        )
        layoutNode.handleMeasureResult(measureResult)
        return this
    }

    override val parentData: Any?
        get() = null

    override fun findPreviousFocusWrapper() = wrappedBy?.findPreviousFocusWrapper()

    override fun findNextFocusWrapper(): ModifiedFocusNode? = null

    override fun findLastFocusWrapper(): ModifiedFocusNode? = findPreviousFocusWrapper()

    @OptIn(ExperimentalFocus::class)
    override fun propagateFocusStateChange(focusState: FocusState) {
        wrappedBy?.propagateFocusStateChange(focusState)
    }

    override fun findPreviousKeyInputWrapper() = wrappedBy?.findPreviousKeyInputWrapper()

    override fun findNextKeyInputWrapper(): ModifiedKeyInputNode? = null

    override fun findLastKeyInputWrapper(): ModifiedKeyInputNode? = findPreviousKeyInputWrapper()

    override fun minIntrinsicWidth(height: Int): Int {
        return layoutNode.measureBlocks.minIntrinsicWidth(
            measureScope,
            layoutNode.children,
            height
        )
    }

    override fun minIntrinsicHeight(width: Int): Int {
        return layoutNode.measureBlocks.minIntrinsicHeight(
            measureScope,
            layoutNode.children,
            width
        )
    }

    override fun maxIntrinsicWidth(height: Int): Int {
        return layoutNode.measureBlocks.maxIntrinsicWidth(
            measureScope,
            layoutNode.children,
            height
        )
    }

    override fun maxIntrinsicHeight(width: Int): Int {
        return layoutNode.measureBlocks.maxIntrinsicHeight(
            measureScope,
            layoutNode.children,
            width
        )
    }

    override fun placeAt(position: IntOffset) {
        this.position = position

        // The wrapper only runs their placement block to obtain our position, which allows them
        // to calculate the offset of an alignment line we have already provided a position for.
        // No need to place our wrapped as well (we might have actually done this already in
        // get(line), to obtain the position of the alignment line the wrapper currently needs
        // our position in order ot know how to offset the value we provided).
        if (wrappedBy?.isShallowPlacing == true) return

        layoutNode.onNodePlaced()
    }

    override operator fun get(line: AlignmentLine): Int {
        return layoutNode.calculateAlignmentLines()[line] ?: AlignmentLine.Unspecified
    }

    override fun draw(canvas: Canvas) {
        withPositionTranslation(canvas) {
            val owner = layoutNode.requireOwner()
            layoutNode.zIndexSortedChildren.fastForEach { child ->
                if (child.isPlaced) {
                    require(child.layoutState == LayoutNode.LayoutState.Ready) {
                        "$child is not ready. layoutState is ${child.layoutState}"
                    }
                    child.draw(canvas)
                }
            }
            if (owner.showLayoutBounds) {
                drawBorder(canvas, innerBoundsPaint)
            }
        }
    }

    override fun hitTest(
        pointerPositionRelativeToScreen: Offset,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ) {
        // Any because as soon as true is returned, we know we have found a hit path and we must
        // not add PointerInputFilters on different paths so we should not even go looking.
        val originalSize = hitPointerInputFilters.size
        layoutNode.zIndexSortedChildren.reversed().fastAny { child ->
            callHitTest(child, pointerPositionRelativeToScreen, hitPointerInputFilters)
            hitPointerInputFilters.size > originalSize
        }
    }

    override fun attach() {
        // Do nothing. InnerPlaceable only is attached when the LayoutNode is attached.
    }

    override fun detach() {
        // Do nothing. InnerPlaceable only is detached when the LayoutNode is detached.
    }

    internal companion object {
        val innerBoundsPaint = Paint().also { paint ->
            paint.color = Color.Red
            paint.strokeWidth = 1f
            paint.style = PaintingStyle.Stroke
        }

        private fun callHitTest(
            node: LayoutNode,
            globalPoint: Offset,
            hitPointerInputFilters: MutableList<PointerInputFilter>
        ) {
            node.hitTest(globalPoint, hitPointerInputFilters)
        }
    }
}
