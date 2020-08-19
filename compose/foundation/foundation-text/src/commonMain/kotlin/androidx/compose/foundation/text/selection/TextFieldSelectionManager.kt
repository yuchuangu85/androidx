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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.text.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.gesture.DragObserver
import androidx.compose.ui.gesture.LongPressDragObserver
import androidx.compose.ui.gesture.dragGestureFilter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.selection.SelectionHandle
import androidx.compose.ui.selection.SelectionHandleLayout
import androidx.compose.ui.selection.getAdjustedCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.input.getTextAfterSelection
import androidx.compose.ui.text.input.getTextBeforeSelection
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * A bridge class between user interaction to the text field selection.
 */
@OptIn(InternalTextApi::class)
internal class TextFieldSelectionManager() {

    /**
     * The current [OffsetMap] for text field.
     */
    internal var offsetMap: OffsetMap = OffsetMap.identityOffsetMap

    /**
     * Called when the input service updates the values in [TextFieldValue].
     */
    internal var onValueChange: (TextFieldValue) -> Unit = {}

    /**
     * The current [TextFieldState].
     */
    internal var state: TextFieldState? = null

    /**
     * The current [TextFieldValue].
     */
    internal var value: TextFieldValue = TextFieldValue()

    /**
     * [ClipboardManager] to perform clipboard features.
     */
    internal var clipboardManager: ClipboardManager? = null

    /**
     * [TextToolbar] to show floating toolbar(post-M) or primary toolbar(pre-M).
     */
    var textToolbar: TextToolbar? = null

    /**
     * [HapticFeedback] handle to perform haptic feedback.
     */
    var hapticFeedBack: HapticFeedback? = null

    /**
     * The beginning position of the drag gesture. Every time a new drag gesture starts, it wil be
     * recalculated.
     */
    private var dragBeginPosition = Offset.Zero

    /**
     * The total distance being dragged of the drag gesture. Every time a new drag gesture starts,
     * it will be zeroed out.
     */
    private var dragTotalDistance = Offset.Zero

    /**
     * The old [TextFieldValue]. Used to compare with the [value].
     */
    private var oldValue: TextFieldValue = TextFieldValue()

    /**
     * [LongPressDragObserver] for long press and drag to select in TextField.
     */
    internal val longPressDragObserver = object : LongPressDragObserver {
        override fun onLongPress(pxPosition: Offset) {
            state?.let {
                if (it.draggingHandle) return
            }

            oldValue = value

            // Long Press at the blank area, the cursor should show up at the end of the line.
            if (!isPositionOnText(pxPosition)) {
                state?.layoutResult?.let { layoutResult ->
                    val offset = offsetMap.transformedToOriginal(
                        layoutResult.getLineEnd(
                            layoutResult.getLineForVerticalPosition(pxPosition.y)
                        )
                    )
                    hapticFeedBack?.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    val newValue = TextFieldValue(
                        text = value.text,
                        selection = TextRange(offset, offset)
                    )
                    onValueChange(newValue)
                    state?.showFloatingToolbar = true
                    setSelectionStatus(true)
                    return
                }
            }

            // selection never started
            if (value.text == "") return
            setSelectionStatus(true)
            state?.layoutResult?.let { layoutResult ->
                val offset = offsetMap.transformedToOriginal(
                    layoutResult.getOffsetForPosition(pxPosition)
                )
                updateSelection(
                    value = value,
                    startOffset = offset,
                    endOffset = offset,
                    isStartHandle = true,
                    wordBasedSelection = true
                )
            }
            state?.showFloatingToolbar = true
            dragBeginPosition = pxPosition
            dragTotalDistance = Offset.Zero
        }

        override fun onDrag(dragDistance: Offset): Offset {
            // selection never started, did not consume any drag
            if (value.text == "") return Offset.Zero

            dragTotalDistance += dragDistance
            state?.layoutResult?.let { layoutResult ->
                val startOffset = layoutResult.getOffsetForPosition(dragBeginPosition)
                val endOffset =
                    layoutResult.getOffsetForPosition(dragBeginPosition + dragTotalDistance)
                updateSelection(
                    value = value,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    isStartHandle = true,
                    wordBasedSelection = true
                )
            }
            state?.showFloatingToolbar = false
            return dragDistance
        }

        override fun onStop(velocity: Offset) {
            super.onStop(velocity)
            state?.showFloatingToolbar = true
            showSelectionToolbar()
        }
    }

    /**
     * [DragObserver] for dragging the selection handles to change the selection in TextField.
     */
    internal fun handleDragObserver(isStartHandle: Boolean): DragObserver {
        return object : DragObserver {
            override fun onStart(downPosition: Offset) {
                // The position of the character where the drag gesture should begin. This is in
                // the composable coordinates.
                dragBeginPosition = getAdjustedCoordinates(getHandlePosition(isStartHandle))
                // Zero out the total distance that being dragged.
                dragTotalDistance = Offset.Zero
                state?.draggingHandle = true
                state?.showFloatingToolbar = false
            }

            override fun onDrag(dragDistance: Offset): Offset {
                dragTotalDistance += dragDistance

                state?.layoutResult?.let { layoutResult ->
                    val startOffset =
                        if (isStartHandle)
                            layoutResult.getOffsetForPosition(dragBeginPosition + dragTotalDistance)
                        else
                            value.selection.start

                    val endOffset =
                        if (isStartHandle)
                            value.selection.end
                        else
                            layoutResult.getOffsetForPosition(dragBeginPosition + dragTotalDistance)

                    updateSelection(
                        value = value,
                        startOffset = startOffset,
                        endOffset = endOffset,
                        isStartHandle = isStartHandle,
                        wordBasedSelection = false
                    )
                }
                state?.showFloatingToolbar = false
                return dragDistance
            }

            override fun onStop(velocity: Offset) {
                super.onStop(velocity)
                state?.draggingHandle = false
                state?.showFloatingToolbar = true
                showSelectionToolbar()
            }
        }
    }

    internal fun deselect() {
        val newValue = TextFieldValue(text = value.text, selection = TextRange.Zero)
        onValueChange(newValue)
        setSelectionStatus(false)
        hideSelectionToolbar()
    }

    /**
     * The method for copying text.
     *
     * If there is no selection, return.
     * Put the selected text into the [ClipboardManager], and cancel the selection.
     * The text in the text field should be unchanged.
     * The new cursor offset should be at the end of the previous selected text.
     */
    internal fun copy() {
        if (value.selection.collapsed) return

        clipboardManager?.setText(AnnotatedString(value.getSelectedText()))

        val newCursorOffset = value.selection.end
        val newValue = TextFieldValue(
            text = value.text,
            selection = TextRange(newCursorOffset, newCursorOffset)
        )
        onValueChange(newValue)
        setSelectionStatus(false)
    }

    /**
     * The method for pasting text.
     *
     * Get the text from [ClipboardManager]. If it's null, return.
     * The new text should be the text before the selected text, plus the text from the
     * [ClipboardManager], and plus the text after the selected text.
     * Then the selection should collapse, and the new cursor offset should be the end of the
     * newly added text.
     */
    internal fun paste() {
        val text = clipboardManager?.getText()?.text ?: return

        val newText = value.getTextBeforeSelection(value.text.length) +
                text +
                value.getTextAfterSelection(value.text.length)
        val newCursorOffset = value.selection.start + text.length

        val newValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorOffset, newCursorOffset)
        )
        onValueChange(newValue)
        setSelectionStatus(false)
    }

    /**
     * The method for cutting text.
     *
     * If there is no selection, return.
     * Put the selected text into the [ClipboardManager].
     * The new text should be the text before the selection plus the text after the selection.
     * And the new cursor offset should be between the text before the selection, and the text
     * after the selection.
     */
    internal fun cut() {
        if (value.selection.collapsed) return

        clipboardManager?.setText(AnnotatedString(value.getSelectedText()))

        val newText = value.getTextBeforeSelection(value.text.length) +
                value.getTextAfterSelection(value.text.length)
        val newCursorOffset = value.selection.start

        val newValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorOffset, newCursorOffset)
        )
        onValueChange(newValue)
        setSelectionStatus(false)
    }

    internal fun getHandlePosition(isStartHandle: Boolean): Offset {
        return if (isStartHandle)
            getSelectionHandleCoordinates(
                textLayoutResult = state?.layoutResult!!,
                offset = value.selection.start,
                isStart = true,
                areHandlesCrossed = value.selection.reversed
            )
        else
            getSelectionHandleCoordinates(
                textLayoutResult = state?.layoutResult!!,
                offset = value.selection.end,
                isStart = false,
                areHandlesCrossed = value.selection.reversed
            )
    }

    /**
     * This function get the selected region as a Rectangle region, and pass it to [TextToolbar]
     * to make the FloatingToolbar show up in the proper place. In addition, this function passes
     * the copy, paste and cut method as callbacks when "copy", "cut" or "paste" is clicked.
     */
    internal fun showSelectionToolbar() {
        val copy: (() -> Unit)? = if (!value.selection.collapsed) {
            {
                copy()
                hideSelectionToolbar()
            }
        } else null

        val cut: (() -> Unit)? = if (!value.selection.collapsed) {
            {
                cut()
                hideSelectionToolbar()
            }
        } else null

        val paste: (() -> Unit)? = if (clipboardManager?.getText() != null) {
            {
                paste()
                hideSelectionToolbar()
            }
        } else null

        textToolbar?.showMenu(
            rect = getContentRect(),
            onCopyRequested = copy,
            onPasteRequested = paste,
            onCutRequested = cut
        )
    }

    internal fun hideSelectionToolbar() {
        if (textToolbar?.status == TextToolbarStatus.Shown) {
            textToolbar?.hide()
        }
    }

    /**
     * Check if the text in the text field changed.
     * When the content in the text field is modified, this method returns true.
     */
    internal fun isTextChanged(): Boolean {
        return oldValue.text != value.text
    }

    /**
     * Calculate selected region as [Rect]. The top is the top of the first selected
     * line, and the bottom is the bottom of the last selected line. The left is the leftmost
     * handle's horizontal coordinates, and the right is the rightmost handle's coordinates.
     */
    private fun getContentRect(): Rect {
        state?.let {
            val startOffset =
                state?.layoutCoordinates?.localToRoot(getHandlePosition(true)) ?: Offset.Zero
            val endOffset =
                state?.layoutCoordinates?.localToRoot(getHandlePosition(false)) ?: Offset.Zero
            val startTop =
                state?.layoutCoordinates?.localToRoot(
                    Offset(
                        0f,
                        it.layoutResult?.getCursorRect(
                            value.selection.start.coerceIn(
                                0,
                                max(0, value.text.length - 1)
                            )
                        )?.top ?: 0f
                    )
                )?.y ?: 0f
            val endTop =
                state?.layoutCoordinates?.localToRoot(
                    Offset(
                        0f,
                        it.layoutResult?.getCursorRect(
                            value.selection.end.coerceIn(
                                0,
                                max(0, value.text.length - 1)
                            )
                        )?.top ?: 0f
                    )
                )?.y ?: 0f

            val left = min(startOffset.x, endOffset.x)
            val right = max(startOffset.x, endOffset.x)
            val top = min(startTop, endTop)
            val bottom = max(startOffset.y, endOffset.y) +
                    25.dp.value * it.textDelegate.density.density

            return Rect(left, top, right, bottom)
        }

        return Rect.Zero
    }

    private fun updateSelection(
        value: TextFieldValue,
        startOffset: Int,
        endOffset: Int,
        isStartHandle: Boolean,
        wordBasedSelection: Boolean
    ) {
        val range = getTextFieldSelection(
            textLayoutResult = state?.layoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = if (value.selection.collapsed) null else value.selection,
            previousHandlesCrossed = value.selection.reversed,
            isStartHandle = isStartHandle,
            wordBasedSelection = wordBasedSelection
        )

        if (range == value.selection) return

        hapticFeedBack?.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        val newValue = TextFieldValue(
            text = value.text,
            selection = range
        )
        onValueChange(newValue)
    }

    private fun setSelectionStatus(on: Boolean) {
        state?.let {
            it.selectionIsOn = on
        }
    }

    /** Returns true if the screen coordinates position (x,y) corresponds to a character displayed
     * in the view. Returns false when the position is in the empty space of left/right of text.
     */
    private fun isPositionOnText(offset: Offset): Boolean {
        state?.layoutResult?.let {
            val line = it.getLineForVerticalPosition(offset.y)
            if (offset.x < it.getLineLeft(line) || offset.x > it.getLineRight(line)) return false
            return true
        }
        return false
    }
}

@Composable
@OptIn(InternalTextApi::class)
internal fun SelectionHandle(
    isStartHandle: Boolean,
    directions: Pair<ResolvedTextDirection, ResolvedTextDirection>,
    manager: TextFieldSelectionManager
) {
    SelectionHandleLayout(
        startHandlePosition = manager.getHandlePosition(true),
        endHandlePosition = manager.getHandlePosition(false),
        isStartHandle = isStartHandle,
        directions = directions,
        handlesCrossed = manager.value.selection.reversed
    ) {
        SelectionHandle(
            modifier =
            Modifier.dragGestureFilter(manager.handleDragObserver(isStartHandle)),
            isStartHandle = isStartHandle,
            directions = directions,
            handlesCrossed = manager.value.selection.reversed
        )
    }
}
