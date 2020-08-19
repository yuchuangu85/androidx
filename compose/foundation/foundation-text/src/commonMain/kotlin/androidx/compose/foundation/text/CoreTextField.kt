/*
 * Copyright 2019 The Android Open Source Project
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

@file:Suppress("DEPRECATION_ERROR")

package androidx.compose.foundation.text

import androidx.compose.foundation.text.selection.SelectionHandle
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.emptyContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onDispose
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.state
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.drawBehind
import androidx.compose.ui.focus
import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.isFocused
import androidx.compose.ui.focusObserver
import androidx.compose.ui.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.DragObserver
import androidx.compose.ui.gesture.LongPressDragObserver
import androidx.compose.ui.gesture.dragGestureFilter
import androidx.compose.ui.gesture.longPressDragGestureFilter
import androidx.compose.ui.gesture.pressIndicatorGestureFilter
import androidx.compose.ui.graphics.drawscope.drawCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.ClipboardManagerAmbient
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.platform.FontLoaderAmbient
import androidx.compose.ui.platform.HapticFeedBackAmbient
import androidx.compose.ui.platform.TextInputServiceAmbient
import androidx.compose.ui.platform.TextToolbarAmbient
import androidx.compose.ui.selection.SelectionLayout
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.SoftwareKeyboardController
import androidx.compose.ui.text.TextDelegate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.NO_SESSION
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Base composable that enables users to edit text via hardware or software keyboard.
 *
 * This composable provides basic text editing functionality, however does not include any
 * decorations such as borders, hints/placeholder.
 *
 * Whenever the user edits the text, [onValueChange] is called with the most up to date state
 * represented by [TextFieldValue]. [TextFieldValue] contains the text entered by user, as well
 * as selection, cursor and text composition information. Please check [TextFieldValue] for the
 * description of its contents.
 *
 * It is crucial that the value provided in the [onValueChange] is fed back into [CoreTextField] in
 * order to have the final state of the text being displayed. Example usage:
 * @sample androidx.compose.foundation.text.samples.CoreTextFieldSample
 *
 * Please keep in mind that [onValueChange] is useful to be informed about the latest state of the
 * text input by users, however it is generally not recommended to modify the values in the
 * [TextFieldValue] that you get via [onValueChange] callback. Any change to the values in
 * [TextFieldValue] may result in a context reset and end up with input session restart. Such
 * a scenario would cause glitches in the UI or text input experience for users.
 *
 * @param value The [androidx.compose.ui.text.input.TextFieldValue] to be shown in the [CoreTextField].
 * @param onValueChange Called when the input service updates the values in [TextFieldValue].
 * @param modifier optional [Modifier] for this text field.
 * @param textStyle Style configuration that applies at character level such as color, font etc.
 * @param keyboardType The keyboard type to be used in this text field. Note that this input type
 * is honored by IME and shows corresponding keyboard but this is not guaranteed. For example,
 * some IME may send non-ASCII character even if you set [KeyboardType.Ascii].
 * @param imeAction The IME action. This IME action is honored by IME and may show specific icons
 * on the keyboard. For example, search icon may be shown if [ImeAction.Search] is specified.
 * Then, when user tap that key, the [onImeActionPerformed] callback is called with specified
 * ImeAction.
 * @param onImeActionPerformed Called when the input service requested an IME action. When the
 * input service emitted an IME action, this callback is called with the emitted IME action. Note
 * that this IME action may be different from what you specified in [imeAction].
 * @param visualTransformation The visual transformation filter for changing the visual
 * representation of the input. By default no visual transformation is applied.
 * @param onTextLayout Callback that is executed when a new text layout is calculated.
 * @param onTextInputStarted Callback that is executed when the initialization has done for
 * communicating with platform text input service, e.g. software keyboard on Android. Called with
 * [SoftwareKeyboardController] instance which can be used for requesting input show/hide software
 * keyboard.
 */
@Composable
@OptIn(
    ExperimentalFocus::class,
    InternalTextApi::class
)
fun CoreTextField(
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit,
    textStyle: TextStyle = TextStyle.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Unspecified,
    onImeActionPerformed: (ImeAction) -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onTextInputStarted: (SoftwareKeyboardController) -> Unit = {}
) {
    // If developer doesn't pass new value to TextField, recompose won't happen but internal state
    // and IME may think it is updated. To fix this inconsistent state, enforce recompose by
    // incrementing generation counter when we callback to the developer and reset the state with
    // the latest state.

    // TODO(b/162464429): Remove deprecated state {} function. This can throw, "Expected a group"
    //  exceptions if changed to the suggested equivalent for the deprecated state {} function.
    @Suppress("DEPRECATION")
    val generation = state { 0 }

    val Wrapper: @Composable (Int, @Composable () -> Unit) -> Unit = { _, child -> child() }
    val onValueChangeWrapper: (TextFieldValue) -> Unit = { onValueChange(it); generation.value++ }
    val focusRequester = FocusRequester()

    Wrapper(generation.value) {
        // Ambients
        val textInputService = TextInputServiceAmbient.current
        val density = DensityAmbient.current
        val resourceLoader = FontLoaderAmbient.current

        // State
        val (visualText, offsetMap) = remember(value, visualTransformation) {
            val transformed = visualTransformation.filter(AnnotatedString(value.text))
            value.composition?.let {
                TextFieldDelegate.applyCompositionDecoration(it, transformed)
            } ?: transformed
        }
        val state = remember {
            TextFieldState(
                TextDelegate(
                    text = visualText,
                    style = textStyle,
                    density = density,
                    resourceLoader = resourceLoader
                )
            )
        }
        state.textDelegate = updateTextDelegate(
            current = state.textDelegate,
            text = visualText,
            style = textStyle,
            density = density,
            resourceLoader = resourceLoader,
            placeholders = emptyList()
        )

        state.processor.onNewState(value, textInputService, state.inputSession)

        val manager = remember { TextFieldSelectionManager() }
        manager.offsetMap = offsetMap
        manager.onValueChange = onValueChangeWrapper
        manager.state = state
        manager.value = value
        manager.clipboardManager = ClipboardManagerAmbient.current
        manager.textToolbar = TextToolbarAmbient.current
        manager.hapticFeedBack = HapticFeedBackAmbient.current

        val focusObserver = Modifier.focusObserver {
            if (state.hasFocus == it.isFocused) {
                return@focusObserver
            }

            state.hasFocus = it.isFocused

            if (it.isFocused) {
                state.inputSession = TextFieldDelegate.onFocus(
                    textInputService,
                    value,
                    state.processor,
                    keyboardType,
                    imeAction,
                    onValueChangeWrapper,
                    onImeActionPerformed
                )
                if (state.inputSession != NO_SESSION && textInputService != null) {
                    onTextInputStarted(
                        SoftwareKeyboardController(
                            textInputService,
                            state.inputSession
                        )
                    )
                }
                state.layoutCoordinates?.let { coords ->
                    textInputService?.let { textInputService ->
                        state.layoutResult?.let { layoutResult ->
                            TextFieldDelegate.notifyFocusedRect(
                                value,
                                state.textDelegate,
                                layoutResult,
                                coords,
                                textInputService,
                                state.inputSession,
                                state.hasFocus,
                                offsetMap
                            )
                        }
                    }
                }
            } else {
                TextFieldDelegate.onBlur(
                    textInputService,
                    state.inputSession,
                    state.processor,
                    false,
                    onValueChangeWrapper
                )
                manager.deselect()
            }
        }

        val dragPositionGestureModifier = Modifier.dragPositionGestureFilter(
            onPress = {
                state.selectionIsOn = false
                manager.hideSelectionToolbar()
                if (!state.hasFocus) focusRequester.requestFocus()
            },
            onRelease = {
                if (state.selectionIsOn == false) {
                    state.layoutResult?.let { layoutResult ->
                        TextFieldDelegate.onRelease(
                            it,
                            layoutResult,
                            state.processor,
                            offsetMap,
                            onValueChangeWrapper,
                            textInputService,
                            state.inputSession,
                            state.hasFocus
                        )
                    }
                }
            },
            longPressDragObserver = manager.longPressDragObserver
        )

        val drawModifier = Modifier.drawBehind {
            state.layoutResult?.let { layoutResult ->
                drawCanvas { canvas, _ ->
                    TextFieldDelegate.draw(
                        canvas,
                        value,
                        offsetMap,
                        layoutResult,
                        DefaultSelectionColor
                    )
                }
            }
        }

        val onPositionedModifier = Modifier.onPositioned {
            if (textInputService != null) {
                state.layoutCoordinates = it
                if (state.selectionIsOn) {
                    if (state.showFloatingToolbar) manager.showSelectionToolbar()
                    else manager.hideSelectionToolbar()
                }
                state.layoutResult?.let { layoutResult ->
                    TextFieldDelegate.notifyFocusedRect(
                        value,
                        state.textDelegate,
                        layoutResult,
                        it,
                        textInputService,
                        state.inputSession,
                        state.hasFocus,
                        offsetMap
                    )
                }
            }
        }

        val semanticsModifier = Modifier.semantics {
            this.imeAction = imeAction
            this.supportsInputMethods()
            this.text = AnnotatedString(value.text)
            this.textSelectionRange = value.selection
            this.focused = state.hasFocus
            getTextLayoutResult {
                if (state.layoutResult != null) {
                    it.add(state.layoutResult!!)
                    true
                } else {
                    false
                }
            }
            setText {
                onValueChange(TextFieldValue(it.text, TextRange(it.text.length)))
                true
            }
            // TODO: startSelectionActionModeAsync
            setSelection { start, end, startSelectionActionMode ->
                if (start == value.selection.start && end == value.selection.end) {
                    false
                } else if (start.coerceAtMost(end) >= 0 &&
                    start.coerceAtLeast(end) <= value.text.length
                ) {
                    onValueChange(TextFieldValue(value.text, TextRange(start, end)))
                    if (startSelectionActionMode) {
                        // startSelectionActionModeAsync
                        // See TextView.java
                    } else {
                        // stop SelectionActionMode
                    }
                    true
                } else {
                    false
                }
            }
            onClick {
                if (!state.hasFocus) focusRequester.requestFocus()
                true
            }
        }

        onDispose { manager.hideSelectionToolbar() }

        SelectionLayout(
            modifier.focusRequester(focusRequester)
                .then(focusObserver)
                .then(dragPositionGestureModifier)
                .then(drawModifier)
                .then(onPositionedModifier)
                .then(semanticsModifier)
                .focus()
        ) {
            Layout(emptyContent()) { _, constraints ->
                TextFieldDelegate.layout(
                    state.textDelegate,
                    constraints,
                    layoutDirection,
                    state.layoutResult
                ).let { (width, height, result) ->
                    if (state.layoutResult != result) {
                        state.layoutResult = result
                        onTextLayout(result)
                    }
                    layout(
                        width,
                        height,
                        mapOf(
                            FirstBaseline to result.firstBaseline.roundToInt(),
                            LastBaseline to result.lastBaseline.roundToInt()
                        )
                    ) {}
                }
            }

            if (state.hasFocus) {
                if (state.selectionIsOn) {
                    manager.state?.layoutResult?.let {
                        if (!value.selection.collapsed) {
                            val startDirection = it.getBidiRunDirection(value.selection.start)
                            val endDirection =
                                it.getBidiRunDirection(max(value.selection.end - 1, 0))
                            val directions = Pair(startDirection, endDirection)
                            SelectionHandle(
                                isStartHandle = true,
                                directions = directions,
                                manager = manager
                            )
                            SelectionHandle(
                                isStartHandle = false,
                                directions = directions,
                                manager = manager
                            )
                        }

                        manager.state?.let {
                            if (manager.isTextChanged()) it.showFloatingToolbar = false
                            if (it.hasFocus) {
                                if (it.showFloatingToolbar) manager.showSelectionToolbar()
                                else manager.hideSelectionToolbar()
                            }
                        }
                    }
                }
            } else manager.hideSelectionToolbar()
        }
    }
}

@OptIn(InternalTextApi::class)
internal class TextFieldState(
    var textDelegate: TextDelegate
) {
    val processor = EditProcessor()
    var inputSession = NO_SESSION

    /**
     * This should be a state as every time we update the value we need to redraw it.
     * state observation during onDraw callback will make it work.
     */
    var hasFocus by mutableStateOf(false)

    /** The last layout coordinates for the Text's layout, used by selection */
    var layoutCoordinates: LayoutCoordinates? = null

    /** The latest TextLayoutResult calculated in the measure block */
    var layoutResult: TextLayoutResult? = null

    /**
     * The gesture detector status, to indicate whether current status is selection or editing.
     *
     * In the editing mode, there is no selection shown, only cursor is shown. To enter the editing
     * mode from selection mode, just tap on the screen.
     *
     * In the selection mode, there is no cursor shown, only selection is shown. To enter
     * the selection mode, just long press on the screen. In this mode, finger movement on the
     * screen changes selection instead of moving the cursor.
     */
    var selectionIsOn: Boolean = false

    /**
     * A flag to check if the selection start or end handle is being dragged.
     * If this value is true, then onPress will not select any text.
     * This value will be set to true when either handle is being dragged, and be reset to false
     * when the dragging is stopped.
     */
    var draggingHandle = false

    /**
     * A flag to check if the floating toolbar should show.
     */
    var showFloatingToolbar = false
}

/**
 * Helper class for tracking dragging event.
 */
internal class DragEventTracker {
    private var origin = Offset.Zero
    private var distance = Offset.Zero

    /**
     * Restart the tracking from given origin.
     *
     * @param origin The origin of the drag gesture.
     */
    fun init(origin: Offset) {
        this.origin = origin
    }

    /**
     * Pass distance parameter called by DragGestureDetector$onDrag callback
     *
     * @param distance The distance from the origin of the drag origin.
     */
    fun onDrag(distance: Offset) {
        this.distance = distance
    }

    /**
     * Returns the current position.
     *
     * @return The position of the current drag point.
     */
    fun getPosition(): Offset {
        return origin + distance
    }
}

/**
 * Helper composable for tracking drag position.
 */
@Composable
private fun Modifier.dragPositionGestureFilter(
    onPress: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
    longPressDragObserver: LongPressDragObserver
): Modifier {
    val tracker = remember { DragEventTracker() }
    // TODO(shepshapard): PressIndicator doesn't seem to be the right thing to use here.  It
    //  actually may be functionally correct, but might mostly suggest that it should not
    //  actually be called PressIndicator, but instead something else.

    return this
        .pressIndicatorGestureFilter(
            onStart = {
                tracker.init(it)
                onPress(it)
            }, onStop = {
                onRelease(tracker.getPosition())
            })
        .dragGestureFilter(dragObserver = object :
            DragObserver {
            override fun onDrag(dragDistance: Offset): Offset {
                tracker.onDrag(dragDistance)
                return Offset.Zero
            }
        })
        .longPressDragGestureFilter(longPressDragObserver)
}
