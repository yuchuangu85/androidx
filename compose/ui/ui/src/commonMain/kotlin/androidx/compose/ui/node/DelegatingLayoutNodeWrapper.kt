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
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Placeable
import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.nativeClass

/**
 * [LayoutNodeWrapper] with default implementations for methods.
 */
@OptIn(ExperimentalLayoutNodeApi::class)
internal open class DelegatingLayoutNodeWrapper<T : Modifier.Element>(
    override var wrapped: LayoutNodeWrapper,
    open var modifier: T
) : LayoutNodeWrapper(wrapped.layoutNode) {
    override val providedAlignmentLines: Set<AlignmentLine>
        get() = wrapped.providedAlignmentLines

    private var _isAttached = true
    override val isAttached: Boolean
        get() = _isAttached && layoutNode.isAttached()

    override val measureScope: MeasureScope get() = wrapped.measureScope

    /**
     * Indicates that this modifier is used in [wrappedBy] also.
     */
    var isChained = false

    init {
        wrapped.wrappedBy = this
    }

    /**
     * Sets the modifier instance to the new modifier. [modifier] must be the
     * same type as the current modifier.
     */
    fun setModifierTo(modifier: Modifier.Element) {
        if (modifier !== this.modifier) {
            require(modifier.nativeClass() == this.modifier.nativeClass())
            @Suppress("UNCHECKED_CAST")
            this.modifier = modifier as T
        }
    }

    override fun draw(canvas: Canvas) {
        withPositionTranslation(canvas) {
            wrapped.draw(canvas)
        }
    }

    override fun hitTest(
        pointerPositionRelativeToScreen: Offset,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ) {
        wrapped.hitTest(pointerPositionRelativeToScreen, hitPointerInputFilters)
    }

    override fun get(line: AlignmentLine): Int = wrapped[line]

    override fun placeAt(position: IntOffset) {
        this.position = position

        // The wrapper only runs their placement block to obtain our position, which allows them
        // to calculate the offset of an alignment line we have already provided a position for.
        // No need to place our wrapped as well (we might have actually done this already in
        // get(line), to obtain the position of the alignment line the wrapper currently needs
        // our position in order ot know how to offset the value we provided).
        if (wrappedBy?.isShallowPlacing == true) return

        with(InnerPlacementScope) {
            val previousParentWidth = this.parentWidth
            val previousLayoutDirection = this.parentLayoutDirection
            updateValuesForRtlMirroring(measureScope.layoutDirection, measuredSize.width)
            measureResult.placeChildren()
            updateValuesForRtlMirroring(previousLayoutDirection, previousParentWidth)
        }
    }

    override fun performMeasure(constraints: Constraints): Placeable {
        val placeable = wrapped.measure(constraints)
        measureResult = object : MeasureScope.MeasureResult {
            override val width: Int = wrapped.measureResult.width
            override val height: Int = wrapped.measureResult.height
            override val alignmentLines: Map<AlignmentLine, Int> = emptyMap()
            override fun placeChildren() {
                with(InnerPlacementScope) {
                    placeable.place(-apparentToRealOffset)
                }
            }
        }
        return this
    }

    override fun findPreviousFocusWrapper() = wrappedBy?.findPreviousFocusWrapper()

    override fun findNextFocusWrapper() = wrapped.findNextFocusWrapper()

    override fun findLastFocusWrapper(): ModifiedFocusNode? {
        var lastFocusWrapper: ModifiedFocusNode? = null

        // Find last focus wrapper for the current layout node.
        var next: ModifiedFocusNode? = findNextFocusWrapper()
        while (next != null) {
            lastFocusWrapper = next
            next = next.wrapped.findNextFocusWrapper()
        }
        return lastFocusWrapper
    }

    @OptIn(ExperimentalFocus::class)
    override fun propagateFocusStateChange(focusState: FocusState) {
        wrappedBy?.propagateFocusStateChange(focusState)
    }

    override fun findPreviousKeyInputWrapper() = wrappedBy?.findPreviousKeyInputWrapper()

    override fun findNextKeyInputWrapper() = wrapped.findNextKeyInputWrapper()

    override fun findLastKeyInputWrapper(): ModifiedKeyInputNode? {
        val wrapper = layoutNode.innerLayoutNodeWrapper.findPreviousKeyInputWrapper()
        return if (wrapper !== this) wrapper else null
    }

    override fun minIntrinsicWidth(height: Int) = wrapped.minIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int) = wrapped.maxIntrinsicWidth(height)

    override fun minIntrinsicHeight(width: Int) = wrapped.minIntrinsicHeight(width)

    override fun maxIntrinsicHeight(width: Int) = wrapped.maxIntrinsicHeight(width)

    override val parentData: Any? get() = wrapped.parentData

    override fun attach() {
        _isAttached = true
    }

    override fun detach() {
        _isAttached = false
    }
}

internal object InnerPlacementScope : Placeable.PlacementScope() {
    override var parentLayoutDirection = LayoutDirection.Ltr
        private set
    override var parentWidth = 0
        private set

    fun updateValuesForRtlMirroring(parentLayoutDirection: LayoutDirection, parentWidth: Int) {
        this.parentLayoutDirection = parentLayoutDirection
        this.parentWidth = parentWidth
    }
}
