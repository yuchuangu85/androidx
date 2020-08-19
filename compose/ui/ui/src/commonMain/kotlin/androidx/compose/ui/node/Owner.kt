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

import androidx.compose.ui.DrawLayerModifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection

/**
 * Owner implements the connection to the underlying view system. On Android, this connects
 * to Android [views][android.view.View] and all layout, draw, input, and accessibility is hooked
 * through them.
 */
@OptIn(ExperimentalLayoutNodeApi::class)
interface Owner {

    /**
     * The root layout node in the component tree.
     */
    val root: LayoutNode

    /**
     * Provide haptic feedback to the user. Use the Android version of haptic feedback.
     */
    val hapticFeedBack: HapticFeedback

    /**
     * Provide clipboard manager to the user. Use the Android version of clipboard manager.
     */
    val clipboardManager: ClipboardManager

    /**
     * Provide toolbar for text-related actions, such as copy, paste, cut etc.
     */
    val textToolbar: TextToolbar

    /**
     *  A data structure used to store autofill information. It is used by components that want to
     *  provide autofill semantics.
     *  TODO(ralu): Replace with SemanticsTree. This is a temporary hack until we have a semantics
     *  tree implemented.
     */
    val autofillTree: AutofillTree

    /**
     * The [Autofill] class can be used to perform autofill operations. It is used as an ambient.
     */
    val autofill: Autofill?

    val density: Density

    val semanticsOwner: SemanticsOwner

    val textInputService: TextInputService

    val fontLoader: Font.ResourceLoader

    val layoutDirection: LayoutDirection

    /**
     * `true` when layout should draw debug bounds.
     */
    var showLayoutBounds: Boolean
        /** @suppress */
        @InternalCoreApi
        set

    /**
     * Called from a [LayoutNode], this registers with the underlying view system that a
     * redraw of the given [layoutNode] is required. It may cause other nodes to redraw, if
     * necessary. Note that [LayoutNode]s are able to draw due to draw modifiers applied to them.
     */
    fun onInvalidate(layoutNode: LayoutNode)

    /**
     * Called by [LayoutNode] to request the Owner a new measurement+layout.
     */
    fun onRequestMeasure(layoutNode: LayoutNode)

    /**
     * Called by [LayoutNode] to request the Owner a new measurement+layout.
     */
    fun onRequestRelayout(layoutNode: LayoutNode)

    /**
     * Called by [LayoutNode] when it is attached to the view system and now has an owner.
     * This is used by [Owner] to track which nodes are associated with it. It will only be
     * called when [node] is not already attached to an owner.
     */
    fun onAttach(node: LayoutNode)

    /**
     * Called by [LayoutNode] when it is detached from the view system, such as during
     * [LayoutNode.removeAt]. This will only be called for [node]s that are already
     * [LayoutNode.attach]ed.
     */
    fun onDetach(node: LayoutNode)

    /**
     * Returns the most global position of the owner that Compose can access (such as the device
     * screen).
     */
    fun calculatePosition(): IntOffset

    /**
     * Ask the system to provide focus to this owner.
     *
     * @return true if the system granted focus to this owner. False otherwise.
     */
    fun requestFocus(): Boolean

    /**
     * Send this [KeyEvent] to the focused component in this [Owner].
     *
     * @return true if the event was consumed. False otherwise.
     */
    @ExperimentalKeyInput
    fun sendKeyEvent(keyEvent: KeyEvent): Boolean

    /**
     * Observing the model reads are temporary disabled during the [block] execution.
     * For example if we are currently within the measure stage and we want some code block to
     * be skipped from the observing we disable if before calling the block, execute block and
     * then enable it again.
     */
    fun pauseModelReadObserveration(block: () -> Unit)

    /**
     * Observe model reads during layout of [node], executed in [block].
     */
    fun observeLayoutModelReads(node: LayoutNode, block: () -> Unit)

    /**
     * Observe model reads during measure of [node], executed in [block].
     */
    fun observeMeasureModelReads(node: LayoutNode, block: () -> Unit)

    /**
     * Iterates through all LayoutNodes that have requested layout and measures and lays them out
     */
    fun measureAndLayout()

    /**
     * Creates and returns an [OwnedLayer] for the given [drawLayerModifier].
     */
    fun createLayer(
        drawLayerModifier: DrawLayerModifier,
        drawBlock: (Canvas) -> Unit,
        invalidateParentLayer: () -> Unit
    ): OwnedLayer

    /**
     * The semantics have changed. This function will be called when a SemanticsNode is added to
     * or deleted from the Semantics tree. It will also be called when a SemanticsNode in the
     * Semantics tree has some property change.
     */
    fun onSemanticsChange()

    val measureIteration: Long

    companion object {
        /**
         * Enables additional (and expensive to do in production) assertions. Useful to be set
         * to true during the tests covering our core logic.
         */
        var enableExtraAssertions: Boolean = false
    }
}
