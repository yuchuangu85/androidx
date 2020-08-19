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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
@OptIn(InternalTextApi::class)
class TextFieldSelectionManagerTest {
    private val text = "Hello World"
    private val density = Density(density = 1f)
    private val offsetMap = OffsetMap.identityOffsetMap
    private var value = TextFieldValue(text)
    private val lambda: (TextFieldValue) -> Unit = { value = it }
    private val spyLambda = spy(lambda)
    private val state = TextFieldState(mock())

    private val dragBeginPosition = Offset.Zero
    private val dragDistance = Offset(300f, 15f)
    private val beginOffset = 0
    private val dragOffset = text.indexOf('r')
    private val fakeTextRange = TextRange(0, "Hello".length)
    private val dragTextRange = TextRange("Hello".length + 1, text.length)

    private val manager = TextFieldSelectionManager()

    private val clipboardManager = mock<ClipboardManager>()
    private val textToolbar = mock<TextToolbar>()
    private val hapticFeedback = mock<HapticFeedback>()

    @Before
    fun setup() {
        manager.offsetMap = offsetMap
        manager.onValueChange = lambda
        manager.state = state
        manager.value = value
        manager.clipboardManager = clipboardManager
        manager.textToolbar = textToolbar
        manager.hapticFeedBack = hapticFeedback

        state.layoutResult = mock()
        state.textDelegate = mock()
        whenever(state.textDelegate.density).thenReturn(density)
        whenever(state.layoutResult!!.layoutInput).thenReturn(
            TextLayoutInput(
                text = AnnotatedString(text),
                style = TextStyle.Default,
                placeholders = mock(),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                resourceLoader = mock(),
                constraints = Constraints()
            )
        )
        whenever(state.layoutResult!!.getOffsetForPosition(dragBeginPosition)).thenReturn(
            beginOffset
        )
        whenever(state.layoutResult!!.getOffsetForPosition(dragDistance)).thenReturn(dragOffset)
        whenever(state.layoutResult!!.getWordBoundary(beginOffset)).thenReturn(fakeTextRange)
        whenever(state.layoutResult!!.getWordBoundary(dragOffset)).thenReturn(dragTextRange)
        whenever(state.layoutResult!!.getBidiRunDirection(any()))
            .thenReturn(ResolvedTextDirection.Ltr)
        whenever(state.layoutResult!!.getBoundingBox(any())).thenReturn(Rect.Zero)
    }

    @Test
    fun TextFieldSelectionManager_init() {
        assertThat(manager.offsetMap).isEqualTo(offsetMap)
        assertThat(manager.onValueChange).isEqualTo(lambda)
        assertThat(manager.state).isEqualTo(state)
        assertThat(manager.value).isEqualTo(value)
    }

    @Test
    fun TextFieldSelectionManager_longPressDragObserver_onLongPress() {
        manager.longPressDragObserver.onLongPress(dragBeginPosition)

        assertThat(state.selectionIsOn).isTrue()
        assertThat(state.showFloatingToolbar).isTrue()
        assertThat(value.selection).isEqualTo(fakeTextRange)
        verify(
            hapticFeedback,
            times(1)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_longPressDragObserver_onLongPress_blank() {
        // Setup
        val fakeLineNumber = 0
        val fakeLineEnd = text.length
        whenever(state.layoutResult!!.getLineForVerticalPosition(dragBeginPosition.y))
            .thenReturn(fakeLineNumber)
        whenever(state.layoutResult!!.getLineLeft(fakeLineNumber))
            .thenReturn(dragBeginPosition.x + 1.0f)
        whenever(state.layoutResult!!.getLineEnd(fakeLineNumber)).thenReturn(fakeLineEnd)

        // Act
        manager.longPressDragObserver.onLongPress(dragBeginPosition)

        // Assert
        assertThat(state.selectionIsOn).isTrue()
        assertThat(state.showFloatingToolbar).isTrue()
        assertThat(value.selection).isEqualTo(TextRange(fakeLineEnd))
        verify(
            hapticFeedback,
            times(1)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_longPressDragObserver_onDrag() {
        manager.longPressDragObserver.onLongPress(dragBeginPosition)
        manager.longPressDragObserver.onDrag(dragDistance)

        assertThat(value.selection).isEqualTo(TextRange(0, text.length))
        assertThat(state.showFloatingToolbar).isFalse()
        verify(
            hapticFeedback,
            times(2)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_longPressDragObserver_onStop() {
        manager.longPressDragObserver.onLongPress(dragBeginPosition)
        manager.longPressDragObserver.onDrag(dragDistance)

        manager.longPressDragObserver.onStop(Offset.Zero)

        assertThat(state.showFloatingToolbar).isTrue()
    }

    @Test
    fun TextFieldSelectionManager_handleDragObserver_onStart_startHandle() {
        manager.handleDragObserver(isStartHandle = true).onStart(Offset.Zero)

        assertThat(state.draggingHandle).isTrue()
        assertThat(state.showFloatingToolbar).isFalse()
        verify(spyLambda, times(0)).invoke(any())
        verify(
            hapticFeedback,
            times(0)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_handleDragObserver_onStart_endHandle() {
        manager.handleDragObserver(isStartHandle = false).onStart(Offset.Zero)

        assertThat(state.draggingHandle).isTrue()
        assertThat(state.showFloatingToolbar).isFalse()
        verify(spyLambda, times(0)).invoke(any())
        verify(
            hapticFeedback,
            times(0)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_handleDragObserver_onDrag_startHandle() {
        manager.value = TextFieldValue(text = text, selection = TextRange(0, "Hello".length))

        val result = manager.handleDragObserver(isStartHandle = true).onDrag(dragDistance)

        assertThat(result).isEqualTo(dragDistance)
        assertThat(state.showFloatingToolbar).isFalse()
        assertThat(value.selection).isEqualTo(TextRange(dragOffset, "Hello".length))
        verify(
            hapticFeedback,
            times(1)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_handleDragObserver_onDrag_endHandle() {
        manager.value = TextFieldValue(text = text, selection = TextRange(0, "Hello".length))

        val result = manager.handleDragObserver(isStartHandle = false).onDrag(dragDistance)

        assertThat(result).isEqualTo(dragDistance)
        assertThat(state.showFloatingToolbar).isFalse()
        assertThat(value.selection).isEqualTo(TextRange(0, dragOffset))
        verify(
            hapticFeedback,
            times(1)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_handleDragObserver_onStop() {
        manager.handleDragObserver(false).onStart(Offset.Zero)
        manager.handleDragObserver(false).onDrag(Offset.Zero)

        manager.handleDragObserver(false).onStop(Offset.Zero)

        assertThat(state.draggingHandle).isFalse()
        assertThat(state.showFloatingToolbar).isTrue()
        verify(
            hapticFeedback,
            times(0)
        ).performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun TextFieldSelectionManager_deselect() {
        whenever(textToolbar.status).thenReturn(TextToolbarStatus.Shown)
        manager.value = TextFieldValue(text = text, selection = TextRange(0, "Hello".length))

        manager.deselect()

        verify(textToolbar, times(1)).hide()
        assertThat(value.selection).isEqualTo(TextRange.Zero)
        assertThat(state.selectionIsOn).isFalse()
    }

    @Test
    fun copy_selection_collapse() {
        manager.value = TextFieldValue(text = text, selection = TextRange(4, 4))

        manager.copy()

        verify(clipboardManager, times(0)).setText(any())
    }

    @Test
    fun copy_selection_not_null() {
        manager.value = TextFieldValue(text = text, selection = TextRange(0, "Hello".length))

        manager.copy()

        verify(clipboardManager, times(1)).setText(AnnotatedString("Hello"))
        assertThat(value.selection).isEqualTo(TextRange("Hello".length, "Hello".length))
        assertThat(state.selectionIsOn).isFalse()
    }

    @Test
    fun paste_clipBoardManager_null() {
        manager.clipboardManager = null

        manager.paste()

        verify(spyLambda, times(0)).invoke(any())
    }

    @Test
    fun paste_clipBoardManager_empty() {
        whenever(clipboardManager.getText()).thenReturn(null)

        manager.paste()

        verify(spyLambda, times(0)).invoke(any())
    }

    @Test
    fun paste_clipBoardManager_not_empty() {
        whenever(clipboardManager.getText()).thenReturn(AnnotatedString("Hello"))
        manager.value = TextFieldValue(
            text = text,
            selection = TextRange("Hel".length, "Hello Wo".length)
        )

        manager.paste()

        assertThat(value.text).isEqualTo("HelHellorld")
        assertThat(value.selection).isEqualTo(TextRange("Hello Wo".length, "Hello Wo".length))
        assertThat(state.selectionIsOn).isFalse()
    }

    @Test
    fun cut_selection_collapse() {
        manager.value = TextFieldValue(text = text, selection = TextRange(4, 4))

        manager.cut()

        verify(clipboardManager, times(0)).setText(any())
    }

    @Test
    fun cut_selection_not_null() {
        manager.value = TextFieldValue(
            text = text + text,
            selection = TextRange("Hello".length, text.length)
        )

        manager.cut()

        verify(clipboardManager, times(1)).setText(AnnotatedString(" World"))
        assertThat(value.text).isEqualTo("HelloHello World")
        assertThat(value.selection).isEqualTo(TextRange("Hello".length, "Hello".length))
        assertThat(state.selectionIsOn).isFalse()
    }

    @Test
    fun showSelectionToolbar_trigger_textToolbar_showMenu_Clipboard_empty_not_show_paste() {
        manager.value = TextFieldValue(
            text = text + text,
            selection = TextRange("Hello".length, text.length)
        )

        manager.showSelectionToolbar()

        verify(textToolbar, times(1)).showMenu(any(), any(), isNull(), any())
    }

    @Test
    fun showSelectionToolbar_trigger_textToolbar_showMenu_selection_collapse_not_show_copy_cut() {
        whenever(clipboardManager.getText()).thenReturn(AnnotatedString(text))
        manager.value = TextFieldValue(
            text = text + text,
            selection = TextRange(0, 0)
        )

        manager.showSelectionToolbar()

        verify(textToolbar, times(1)).showMenu(any(), isNull(), any(), isNull())
    }

    @Test
    fun isTextChanged_text_changed_return_true() {
        manager.longPressDragObserver.onLongPress(dragBeginPosition)
        manager.value = TextFieldValue(text + text)

        assertThat(manager.isTextChanged()).isTrue()
    }

    @Test
    fun isTextChanged_text_unchange_return_false() {
        manager.longPressDragObserver.onLongPress(dragBeginPosition)

        assertThat(manager.isTextChanged()).isFalse()
    }
}
