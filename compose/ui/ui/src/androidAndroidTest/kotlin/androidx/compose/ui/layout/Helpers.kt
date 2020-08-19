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

package androidx.compose.ui.layout

import androidx.compose.ui.HorizontalAlignmentLine
import androidx.compose.ui.LayoutModifier
import androidx.compose.ui.Measurable
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.MeasureAndLayoutDelegate
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlin.math.max
import kotlin.math.min

@Suppress("UNCHECKED_CAST")
@ExperimentalLayoutNodeApi
internal fun createDelegate(
    root: LayoutNode,
    firstMeasureCompleted: Boolean = true
): MeasureAndLayoutDelegate {
    val delegate = MeasureAndLayoutDelegate(root)
    root.attach(mock {
        on { measureIteration } doAnswer {
            delegate.measureIteration
        }
        on { onRequestMeasure(any()) } doAnswer {
            delegate.requestRemeasure(it.arguments[0] as LayoutNode)
            Unit
        }
        on { observeMeasureModelReads(any(), any()) } doAnswer {
            (it.arguments[1] as () -> Unit).invoke()
        }
        on { observeLayoutModelReads(any(), any()) } doAnswer {
            (it.arguments[1] as () -> Unit).invoke()
        }
        on { measureAndLayout() } doAnswer {
            delegate.measureAndLayout()
            Unit
        }
    })
    if (firstMeasureCompleted) {
        delegate.updateRootConstraints(
            defaultRootConstraints()
        )
        Truth.assertThat(delegate.measureAndLayout()).isTrue()
    }
    return delegate
}

internal fun defaultRootConstraints() = Constraints(maxWidth = 100, maxHeight = 100)

@ExperimentalLayoutNodeApi
internal fun assertNotRemeasured(node: LayoutNode, block: (LayoutNode) -> Unit) {
    val measuresCountBefore = node.measuresCount
    block(node)
    Truth.assertThat(node.measuresCount).isEqualTo(measuresCountBefore)
    assertMeasuredAndLaidOut(node)
}

@ExperimentalLayoutNodeApi
internal fun assertRemeasured(
    node: LayoutNode,
    times: Int = 1,
    withDirection: LayoutDirection? = null,
    block: (LayoutNode) -> Unit
) {
    val measuresCountBefore = node.measuresCount
    block(node)
    Truth.assertThat(node.measuresCount).isEqualTo(measuresCountBefore + times)
    if (withDirection != null) {
        Truth.assertThat(node.measuredWithLayoutDirection).isEqualTo(withDirection)
    }
    assertMeasuredAndLaidOut(node)
}

@ExperimentalLayoutNodeApi
internal fun assertRelaidOut(node: LayoutNode, times: Int = 1, block: (LayoutNode) -> Unit) {
    val layoutsCountBefore = node.layoutsCount
    block(node)
    Truth.assertThat(node.layoutsCount).isEqualTo(layoutsCountBefore + times)
    assertMeasuredAndLaidOut(node)
}

@ExperimentalLayoutNodeApi
internal fun assertNotRelaidOut(node: LayoutNode, block: (LayoutNode) -> Unit) {
    val layoutsCountBefore = node.layoutsCount
    block(node)
    Truth.assertThat(node.layoutsCount).isEqualTo(layoutsCountBefore)
    assertMeasuredAndLaidOut(node)
}

@ExperimentalLayoutNodeApi
internal fun assertMeasureRequired(node: LayoutNode) {
    Truth.assertThat(node.layoutState).isEqualTo(LayoutNode.LayoutState.NeedsRemeasure)
}

@ExperimentalLayoutNodeApi
internal fun assertMeasuredAndLaidOut(node: LayoutNode) {
    Truth.assertThat(node.layoutState).isEqualTo(LayoutNode.LayoutState.Ready)
}

@ExperimentalLayoutNodeApi
internal fun assertLayoutRequired(node: LayoutNode) {
    Truth.assertThat(node.layoutState).isEqualTo(LayoutNode.LayoutState.NeedsRelayout)
}

internal fun assertRemeasured(
    modifier: SpyLayoutModifier,
    block: () -> Unit
) {
    val measuresCountBefore = modifier.measuresCount
    block()
    Truth.assertThat(modifier.measuresCount).isEqualTo(measuresCountBefore + 1)
}

internal fun assertNotRemeasured(
    modifier: SpyLayoutModifier,
    block: () -> Unit
) {
    val measuresCountBefore = modifier.measuresCount
    block()
    Truth.assertThat(modifier.measuresCount).isEqualTo(measuresCountBefore)
}

internal fun assertRelaidOut(
    modifier: SpyLayoutModifier,
    block: () -> Unit
) {
    val layoutsCountBefore = modifier.layoutsCount
    block()
    Truth.assertThat(modifier.layoutsCount).isEqualTo(layoutsCountBefore + 1)
}

@ExperimentalLayoutNodeApi
internal fun root(block: LayoutNode.() -> Unit = {}): LayoutNode {
    return node(block).apply {
        @OptIn(ExperimentalLayoutNodeApi::class)
        isPlaced = true
    }
}

@ExperimentalLayoutNodeApi
internal fun node(block: LayoutNode.() -> Unit = {}): LayoutNode {
    return LayoutNode().apply {
        measureBlocks = MeasureInMeasureBlock()
        block.invoke(this)
    }
}

@ExperimentalLayoutNodeApi
internal fun LayoutNode.add(child: LayoutNode) = insertAt(children.count(), child)

@ExperimentalLayoutNodeApi
internal fun LayoutNode.measureInLayoutBlock() {
    measureBlocks = MeasureInLayoutBlock()
}

@ExperimentalLayoutNodeApi
internal fun LayoutNode.doNotMeasure() {
    measureBlocks = NoMeasureBlock()
}

@ExperimentalLayoutNodeApi
internal fun LayoutNode.queryAlignmentLineDuringMeasure() {
    (measureBlocks as SmartMeasureBlock).queryAlignmentLinesDuringMeasure = true
}

@ExperimentalLayoutNodeApi
internal fun LayoutNode.runDuringMeasure(block: () -> Unit) {
    (measureBlocks as SmartMeasureBlock).preMeasureCallback = block
}

@ExperimentalLayoutNodeApi
internal fun LayoutNode.runDuringLayout(block: () -> Unit) {
    (measureBlocks as SmartMeasureBlock).preLayoutCallback = block
}

@ExperimentalLayoutNodeApi
internal val LayoutNode.first: LayoutNode get() = children.first()
@ExperimentalLayoutNodeApi
internal val LayoutNode.second: LayoutNode get() = children[1]
@ExperimentalLayoutNodeApi
internal val LayoutNode.measuresCount: Int
    get() = (measureBlocks as SmartMeasureBlock).measuresCount
@ExperimentalLayoutNodeApi
internal val LayoutNode.layoutsCount: Int
    get() = (measureBlocks as SmartMeasureBlock).layoutsCount
@ExperimentalLayoutNodeApi
internal var LayoutNode.wrapChildren: Boolean
    get() = (measureBlocks as SmartMeasureBlock).wrapChildren
    set(value) {
        (measureBlocks as SmartMeasureBlock).wrapChildren = value
    }
@ExperimentalLayoutNodeApi
internal val LayoutNode.measuredWithLayoutDirection: LayoutDirection
    get() = (measureBlocks as SmartMeasureBlock).measuredLayoutDirection!!
@ExperimentalLayoutNodeApi
internal var LayoutNode.size: Int?
    get() = (measureBlocks as SmartMeasureBlock).size
    set(value) {
        (measureBlocks as SmartMeasureBlock).size = value
    }
@ExperimentalLayoutNodeApi
internal var LayoutNode.childrenDirection: LayoutDirection?
    get() = (measureBlocks as SmartMeasureBlock).childrenLayoutDirection
    set(value) {
        (measureBlocks as SmartMeasureBlock).childrenLayoutDirection = value
    }

internal val TestAlignmentLine = HorizontalAlignmentLine(::min)

@ExperimentalLayoutNodeApi
internal abstract class SmartMeasureBlock : LayoutNode.NoIntrinsicsMeasureBlocks("") {
    var measuresCount = 0
        protected set
    var layoutsCount = 0
        protected set
    open var wrapChildren = false
    open var queryAlignmentLinesDuringMeasure = false
    var preMeasureCallback: (() -> Unit)? = null
    var preLayoutCallback: (() -> Unit)? = null
    var measuredLayoutDirection: LayoutDirection? = null
        protected set
    var childrenLayoutDirection: LayoutDirection? = null
    // child size is used when null
    var size: Int? = null
}

@OptIn(ExperimentalLayoutNodeApi::class)
internal class MeasureInMeasureBlock : SmartMeasureBlock() {
    override fun measure(
        measureScope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        measuresCount++
        preMeasureCallback?.invoke()
        preMeasureCallback = null
        val childConstraints = if (size == null) {
            constraints
        } else {
            val size = size!!
            constraints.copy(maxWidth = size, maxHeight = size)
        }
        val placeables = measurables.map {
            it.measure(childConstraints)
        }
        if (queryAlignmentLinesDuringMeasure) {
            placeables.forEach { it[TestAlignmentLine] }
        }
        var maxWidth = 0
        var maxHeight = 0
        if (!wrapChildren) {
            maxWidth = childConstraints.maxWidth
            maxHeight = childConstraints.maxHeight
        } else {
            placeables.forEach { placeable ->
                maxWidth = max(placeable.width, maxWidth)
                maxHeight = max(placeable.height, maxHeight)
            }
        }
        return measureScope.layout(maxWidth, maxHeight) {
            layoutsCount++
            preLayoutCallback?.invoke()
            preLayoutCallback = null
            placeables.forEach { placeable ->
                placeable.placeRelative(0, 0)
            }
        }
    }
}

@OptIn(ExperimentalLayoutNodeApi::class)
internal class MeasureInLayoutBlock : SmartMeasureBlock() {

    override var wrapChildren: Boolean
        get() = false
        set(value) {
            if (value) {
                throw IllegalArgumentException("MeasureInLayoutBlock always fills the parent size")
            }
        }

    override var queryAlignmentLinesDuringMeasure: Boolean
        get() = false
        set(value) {
            if (value) {
                throw IllegalArgumentException("MeasureInLayoutBlock cannot query alignment " +
                        "lines during measure")
            }
        }

    override fun measure(
        measureScope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        measuresCount++
        preMeasureCallback?.invoke()
        preMeasureCallback = null
        val childConstraints = if (size == null) {
            constraints
        } else {
            val size = size!!
            constraints.copy(maxWidth = size, maxHeight = size)
        }
        return measureScope.layout(childConstraints.maxWidth, childConstraints.maxHeight) {
            preLayoutCallback?.invoke()
            preLayoutCallback = null
            layoutsCount++
            measurables.forEach {
                it.measure(childConstraints)
                    .placeRelative(0, 0)
            }
        }
    }
}

@OptIn(ExperimentalLayoutNodeApi::class)
internal class NoMeasureBlock : SmartMeasureBlock() {

    override var queryAlignmentLinesDuringMeasure: Boolean
        get() = false
        set(value) {
            if (value) {
                throw IllegalArgumentException("MeasureInLayoutBlock cannot query alignment " +
                        "lines during measure")
            }
        }

    override fun measure(
        measureScope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        measuresCount++
        preMeasureCallback?.invoke()
        preMeasureCallback = null

        val width = size ?: if (!wrapChildren) constraints.maxWidth else constraints.minWidth
        val height = size ?: if (!wrapChildren) constraints.maxHeight else constraints.minHeight
        return measureScope.layout(width, height) {
            layoutsCount++
            preLayoutCallback?.invoke()
            preLayoutCallback = null
        }
    }
}

internal class SpyLayoutModifier : LayoutModifier {
    var measuresCount = 0
    var layoutsCount = 0

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        measuresCount++
        return layout(constraints.maxWidth, constraints.maxHeight) {
            layoutsCount++
            measurable.measure(constraints).placeRelative(0, 0)
        }
    }
}
