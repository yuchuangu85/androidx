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
package androidx.compose.ui.platform

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.input.BackspaceKeyEditOp
import androidx.compose.ui.text.input.CommitTextEditOp
import androidx.compose.ui.text.input.DeleteSurroundingTextInCodePointsEditOp
import androidx.compose.ui.text.input.EditOperation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.MoveCursorEditOp
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetComposingTextEditOp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.substring
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.FocusEvent
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.font.TextHitInfo
import java.awt.im.InputMethodRequests
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import java.text.CharacterIterator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal class DesktopPlatformInput(val component: Component) : PlatformTextInputService {
    data class CurrentInput(
        var value: TextFieldValue,
        val onEditCommand: ((List<EditOperation>) -> Unit),
        val onImeActionPerformed: ((ImeAction) -> Unit),
        val imeAction: ImeAction,
        var focusedRect: Rect? = null
    )

    var currentInput: CurrentInput? = null

    var charKeyPressed: Boolean = false
    var needToDeletePreviousChar: Boolean = false

    override fun startInput(
        value: TextFieldValue,
        keyboardType: KeyboardType,
        imeAction: ImeAction,
        onEditCommand: (List<EditOperation>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        currentInput = CurrentInput(
            value, onEditCommand, onImeActionPerformed, imeAction
        )

        triggerFocus()
    }

    private fun triggerFocus() {
        val focusGainedEvent = FocusEvent(component, FocusEvent.FOCUS_GAINED)
        component.inputContext.dispatchEvent(focusGainedEvent)
    }

    override fun stopInput() {
        currentInput = null
    }

    override fun showSoftwareKeyboard() {
        println("DesktopPlatformInput.showSoftwareKeyboard")
    }

    override fun hideSoftwareKeyboard() {
        println("DesktopPlatformInput.hideSoftwareKeyboard")
    }

    override fun onStateUpdated(value: TextFieldValue) {
        currentInput?.let { input ->
            input.value = value
        }
    }

    override fun notifyFocusedRect(rect: Rect) {
        currentInput?.let { input ->
            input.focusedRect = rect
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onKeyPressed(keyCode: Int, char: Char) {
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
            charKeyPressed = true
        }
        currentInput?.let { input ->
            val command = input.onEditCommand
            when (keyCode) {
                KeyEvent.VK_LEFT -> {
                    command.invoke(listOf(MoveCursorEditOp(-1)))
                }
                KeyEvent.VK_RIGHT -> {
                    command.invoke(listOf(MoveCursorEditOp(1)))
                }
                KeyEvent.VK_BACK_SPACE -> {
                    command.invoke(listOf(BackspaceKeyEditOp()))
                }
                KeyEvent.VK_ENTER -> {
                    if (input.imeAction == ImeAction.Unspecified) {
                        command.invoke(listOf(CommitTextEditOp("\n", 1)))
                    } else
                        input.onImeActionPerformed.invoke(input.imeAction)
                }
                else -> Unit
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onKeyReleased(keyCode: Int, char: Char) {
        charKeyPressed = false
    }

    private fun Char.isPrintable(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return (!Character.isISOControl(this)) &&
                this != KeyEvent.CHAR_UNDEFINED &&
                block != null &&
                block != Character.UnicodeBlock.SPECIALS
    }

    fun onKeyTyped(char: Char) {
        needToDeletePreviousChar = false
        currentInput?.onEditCommand?.let {
            if (char.isPrintable()) {
                it.invoke(listOf(CommitTextEditOp(char.toString(), 1)))
            }
        }
    }

    fun onInputMethodTextChanged(event: InputMethodEvent) {
        currentInput?.let { input ->
            if (event.caret == null) {
                return
            }
            val committed = event.text.toStringUntil(event.committedCharacterCount)
            val composing = event.text.toStringFrom(event.committedCharacterCount)
            val ops = mutableListOf<EditOperation>()

            if (needToDeletePreviousChar && isMac) {
                // This is required to support input of accented characters using press-and-hold method (http://support.apple.com/kb/PH11264).
                // JDK currently properly supports this functionality only for TextComponent/JTextComponent descendants.
                // For our editor component we need this workaround.
                // After https://bugs.openjdk.java.net/browse/JDK-8074882 is fixed, this workaround should be replaced with a proper solution.
                needToDeletePreviousChar = false
                ops.add(DeleteSurroundingTextInCodePointsEditOp(1, 0))
            }

            if (committed.isNotEmpty()) {
                ops.add(CommitTextEditOp(committed, committed.length))
            }
            if (composing.isNotEmpty()) {
                ops.add(SetComposingTextEditOp(composing, composing.length))
            }
            input.onEditCommand.invoke(ops)
        }
    }

    fun getInputMethodRequests(): InputMethodRequests? {
        return currentInput?.let { input ->
            object : InputMethodRequests {
                override fun getLocationOffset(x: Int, y: Int): TextHitInfo? {
                    println("InputMethodRequests.getLocationOffset")
                    return null
                }

                override fun cancelLatestCommittedText(
                    attributes: Array<AttributedCharacterIterator.Attribute>?
                ): AttributedCharacterIterator? {
                    println("InputMethodRequests.cancelLatestCommittedText")
                    return null
                }

                override fun getInsertPositionOffset(): Int {
                    val composedStartIndex = input.value.composition?.start ?: 0
                    val composedEndIndex = input.value.composition?.end ?: 0

                    val caretIndex = input.value.selection.start
                    if (caretIndex < composedStartIndex) {
                        return caretIndex
                    }
                    if (caretIndex < composedEndIndex) {
                        return composedStartIndex
                    }
                    return caretIndex - (composedEndIndex - composedStartIndex)
                }

                override fun getCommittedTextLength() =
                    input.value.text.length - (input.value.composition?.length ?: 0)

                override fun getSelectedText(
                    attributes: Array<AttributedCharacterIterator.Attribute>?
                ): AttributedCharacterIterator {
                    if (charKeyPressed) {
                        needToDeletePreviousChar = true
                    }
                    val str = input.value.text.substring(input.value.selection)
                    return AttributedString(str).iterator
                }

                override fun getTextLocation(offset: TextHitInfo) =
                    input.focusedRect?.let {
                        Rectangle(
                            it.right.toInt() + component.locationOnScreen.x,
                            it.bottom.toInt() + component.locationOnScreen.y,
                            0, 0)
                    }

                override fun getCommittedText(
                    beginIndex: Int,
                    endIndex: Int,
                    attributes: Array<AttributedCharacterIterator.Attribute>?
                ): AttributedCharacterIterator {

                    val comp = input.value.composition
                    val text = input.value.text
                    if (comp == null) {
                        val res = text.substring(beginIndex, endIndex)
                        return AttributedString(res).iterator
                    }

                    val composedStartIndex = comp.start
                    val composedEndIndex = comp.end

                    val committed: String
                    if (beginIndex < composedStartIndex) {
                        val end = min(endIndex, composedStartIndex)
                        committed = text.substring(beginIndex, end)
                    } else if (composedEndIndex <= endIndex) {
                        val begin = max(composedEndIndex, beginIndex)
                        committed = text.substring(begin, endIndex)
                    } else {
                        committed = ""
                    }
                    return AttributedString(committed).iterator
                }
            }
        }
    }
}

private fun AttributedCharacterIterator.toStringUntil(index: Int): String {
    val strBuf = StringBuffer()
    var i = index
    if (i > 0) {
        var c: Char = setIndex(0)
        while (i > 0) {
            strBuf.append(c)
            c = next()
            i--
        }
    }
    return String(strBuf)
}

private fun AttributedCharacterIterator.toStringFrom(index: Int): String {
    val strBuf = StringBuffer()
    var c: Char = setIndex(index)
    while (c != CharacterIterator.DONE) {
        strBuf.append(c)
        c = next()
    }
    return String(strBuf)
}

private val isMac =
    System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac")
