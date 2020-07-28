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

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLifecycleObserver
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.compositionReference
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.emit
import androidx.compose.runtime.remember
import androidx.compose.ui.AlignmentLine
import androidx.compose.ui.Measurable
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.materialize
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutEmitHelper
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNode.LayoutState
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.platform.subcomposeInto
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEach

@RequiresOptIn(
    "This is an experimental API for being able to perform subcomposition during the " +
            "measuring. API is likely to change before becoming stable."
)
annotation class ExperimentalSubcomposeLayoutApi

/**
 * Analogue of [Layout] which allows to subcompose the actual content during the measuring stage
 * for example to use the values calculated during the measurement as params for the composition
 * of the children.
 *
 * Possible use cases:
 * * You need to know the constraints passed by the parent during the composition and can't solve
 * your use case with just custom [Layout] or [LayoutModifier]. See [WithConstraints].
 * * You want to use the size of one child during the composition of the second child. Example is
 * using the sizes of the tabs in TabRow as a input in tabs indicator composable
 * * You want to compose your items lazily based on the available size. For example you have a
 * list of 100 items and instead of composing all of them you only compose the ones which are
 * currently visible(say 5 of them) and compose next items when the component is scrolled.
 *
 * @sample androidx.compose.ui.samples.SubcomposeLayoutSample
 *
 * @param modifier [Modifier] to apply for the layout.
 * @param measureBlock Measure block which provides ability to subcompose during the measuring.
 */
@Composable
@OptIn(ExperimentalLayoutNodeApi::class, ExperimentalComposeApi::class, ComposeCompilerApi::class)
@ExperimentalSubcomposeLayoutApi
fun <T> SubcomposeLayout(
    modifier: Modifier = Modifier,
    measureBlock: SubcomposeMeasureScope<T>.(Constraints) -> MeasureScope.MeasureResult
) {
    val state = remember { SubcomposeLayoutState<T>() }
    // TODO(lelandr): refactor these APIs so that recomposer isn't necessary
    state.recomposer = currentComposer.recomposer
    state.compositionRef = compositionReference()

    val materialized = currentComposer.materialize(modifier)
    emit<LayoutNode, Applier<Any>>(
        ctor = LayoutEmitHelper.constructor,
        update = {
            set(Unit, state.setRoot)
            set(materialized, LayoutEmitHelper.setModifier)
            set(measureBlock, state.setMeasureBlock)
            set(LayoutDirectionAmbient.current, LayoutEmitHelper.setLayoutDirection)
        }
    )

    state.subcomposeIfRemeasureNotScheduled()
}

/**
 * The receiver scope of a [SubcomposeLayout]'s measure lambda which adds ability to dynamically
 * subcompose a content during the measuring on top of the features provided by [MeasureScope].
 */
@ExperimentalSubcomposeLayoutApi
abstract class SubcomposeMeasureScope<T> : MeasureScope() {
    /**
     * Performs subcomposition of the provided [content] with given [slotId].
     *
     * @param slotId unique id which represents the slot we are composing into. If you have fixed
     * amount or slots you can use enums as slot ids, or if you have a list of items maybe an
     * index in the list or some other unique key can work. To be able to correctly match the
     * content between remeasures you should provide the object which is equals to the one you
     * used during the previous measuring.
     * @param content the composable content which defines the slot. It could emit multiple
     * layouts, in this case the returned list of [Measurable]s will have multiple elements.
     */
    abstract fun subcompose(slotId: T, content: @Composable () -> Unit): List<Measurable>
}

@OptIn(ExperimentalLayoutNodeApi::class, ExperimentalSubcomposeLayoutApi::class)
private class SubcomposeLayoutState<T> : SubcomposeMeasureScope<T>(),
    CompositionLifecycleObserver {
    // Values set during the composition
    var recomposer: Recomposer? = null
    var compositionRef: CompositionReference? = null

    // MeasureScope delegation
    override var layoutDirection: LayoutDirection = LayoutDirection.Rtl
    override var density: Float = 0f
    override var fontScale: Float = 0f

    // Pre-allocated lambdas to update LayoutNode
    val setRoot: LayoutNode.(Unit) -> Unit = { root = this }
    val setMeasureBlock:
            LayoutNode.(SubcomposeMeasureScope<T>.(Constraints) -> MeasureResult) -> Unit =
        { measureBlocks = createMeasureBlocks(it) }

    // inner state
    private var root: LayoutNode? = null
    private var currentIndex = 0
    private val nodeToNodeState = mutableMapOf<LayoutNode, NodeState<T>>()
    private val slodIdToNode = mutableMapOf<T, LayoutNode>()

    override fun subcompose(slotId: T, content: @Composable () -> Unit): List<Measurable> {
        val root = root!!
        val layoutState = root.layoutState
        check(layoutState == LayoutState.Measuring || layoutState == LayoutState.LayingOut) {
            "subcompose can only be used inside the measure or layout blocks"
        }

        val node = slodIdToNode.getOrPut(slotId) {
            LayoutNode(isVirtual = true).also {
                root.insertAt(currentIndex, it)
            }
        }

        val itemIndex = root.foldedChildren.indexOf(node)
        if (itemIndex < currentIndex) {
            throw IllegalArgumentException(
                "$slotId was already used with subcompose during this measuring pass"
            )
        }
        if (currentIndex != itemIndex) {
            root.move(itemIndex, currentIndex, 1)
        }
        currentIndex++

        val nodeState = nodeToNodeState.getOrPut(node) {
            NodeState(slotId, content)
        }
        nodeState.content = content
        subcompose(node, nodeState)
        return node.children
    }

    fun subcomposeIfRemeasureNotScheduled() {
        val root = root!!
        if (root.layoutState != LayoutState.NeedsRemeasure) {
            root.foldedChildren.fastForEach {
                subcompose(it, nodeToNodeState.getValue(it))
            }
        }
    }

    private fun subcompose(node: LayoutNode, nodeState: NodeState<T>) {
        node.ignoreModelReads {
            val content = nodeState.content
            nodeState.composition = subcomposeInto(node, recomposer!!, compositionRef!!) {
                content()
            }
        }
    }

    private fun disposeAfterIndex(currentIndex: Int) {
        val root = root!!
        for (i in currentIndex until root.foldedChildren.size) {
            val node = root.foldedChildren[i]
            val nodeState = nodeToNodeState.remove(node)!!
            nodeState.composition!!.dispose()
            slodIdToNode.remove(nodeState.slotId)
        }
        root.removeAt(currentIndex, root.foldedChildren.size - currentIndex)
    }

    private fun createMeasureBlocks(
        block: SubcomposeMeasureScope<T>.(Constraints) -> MeasureResult
    ): LayoutNode.MeasureBlocks = object : LayoutNode.NoIntrinsicsMeasureBlocks(
        error = "Intrinsic measurements are not currently supported by SubcomposeLayout"
    ) {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints
        ): MeasureResult {
            this@SubcomposeLayoutState.layoutDirection = measureScope.layoutDirection
            this@SubcomposeLayoutState.density = measureScope.density
            this@SubcomposeLayoutState.fontScale = measureScope.fontScale
            currentIndex = 0
            val result = block(constraints)
            val indexAfterMeasure = currentIndex
            return object : MeasureResult {
                override val width: Int
                    get() = result.width
                override val height: Int
                    get() = result.height
                override val alignmentLines: Map<AlignmentLine, Int>
                    get() = result.alignmentLines

                override fun placeChildren() {
                    currentIndex = indexAfterMeasure
                    result.placeChildren()
                    disposeAfterIndex(currentIndex)
                }
            }
        }
    }

    override fun onEnter() {
        // do nothing
    }

    override fun onLeave() {
        nodeToNodeState.values.forEach {
            it.composition!!.dispose()
        }
        nodeToNodeState.clear()
        slodIdToNode.clear()
    }

    private class NodeState<T>(
        val slotId: T,
        var content: @Composable () -> Unit,
        var composition: Composition? = null
    )
}
