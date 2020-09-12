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

// TODO(b/160821157): Replace FocusDetailedState with FocusState2 DEPRECATION
@file:Suppress("DEPRECATION")

package androidx.compose.material

import androidx.compose.animation.ColorPropKey
import androidx.compose.animation.DpPropKey
import androidx.compose.animation.core.FloatPropKey
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TransitionSpec
import androidx.compose.animation.core.transitionDefinition
import androidx.compose.animation.core.tween
import androidx.compose.animation.transition
import androidx.compose.foundation.BaseTextField
import androidx.compose.foundation.Box
import androidx.compose.foundation.ContentColorAmbient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ProvideTextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberScrollableController
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredSizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Layout
import androidx.compose.ui.LayoutModifier
import androidx.compose.ui.Measurable
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Placeable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawOpacity
import androidx.compose.ui.focus.ExperimentalFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.isFocused
import androidx.compose.ui.focusObserver
import androidx.compose.ui.focusRequester
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.Ref
import androidx.compose.ui.text.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.annotation.VisibleForTesting
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class TextFieldType {
    Filled, Outlined
}

/**
 * Implementation of the [TextField] and [OutlinedTextField]
 */
@Composable
@OptIn(
    ExperimentalFocus::class,
    ExperimentalFoundationApi::class
)
internal fun TextFieldImpl(
    type: TextFieldType,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    textStyle: TextStyle,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    leading: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
    isErrorValue: Boolean,
    visualTransformation: VisualTransformation,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeActionPerformed: (ImeAction, SoftwareKeyboardController?) -> Unit,
    onTextInputStarted: (SoftwareKeyboardController) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    errorColor: Color,
    backgroundColor: Color,
    shape: Shape
) {
    val keyboardController: Ref<SoftwareKeyboardController> = remember { Ref() }

    var isFocused by remember { mutableStateOf(false) }
    val inputState = when {
        isFocused -> InputPhase.Focused
        value.text.isEmpty() -> InputPhase.UnfocusedEmpty
        else -> InputPhase.UnfocusedNotEmpty
    }

    val decoratedTextField = @Composable { tagModifier: Modifier ->
        Decoration(
            contentColor = inactiveColor,
            typography = MaterialTheme.typography.subtitle1,
            emphasis = EmphasisAmbient.current.high
        ) {
            TextFieldScroller(
                scrollerPosition = rememberSavedInstanceState(
                    saver = TextFieldScrollerPosition.Saver
                ) {
                    TextFieldScrollerPosition()
                },
                modifier = tagModifier
            ) {
                BaseTextField(
                    value = value,
                    textStyle = textStyle,
                    onValueChange = onValueChange,
                    cursorColor = if (isErrorValue) errorColor else activeColor,
                    visualTransformation = visualTransformation,
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    onImeActionPerformed = {
                        onImeActionPerformed(it, keyboardController.value)
                    },
                    onTextInputStarted = {
                        keyboardController.value = it
                        onTextInputStarted(it)
                    }
                )
            }
        }
    }

    val focusRequester = FocusRequester()
    val textFieldModifier = modifier
        .focusRequester(focusRequester)
        .focusObserver { isFocused = it.isFocused }
        .clickable(indication = null) {
            focusRequester.requestFocus()
            // TODO(b/163109449): Showing and hiding keyboard should be handled by BaseTextField.
            //  The requestFocus() call here should be enough to trigger the software keyboard.
            //  Investiate why this is needed here. If it is really needed, instead of doing
            //  this in the onClick callback, we should move this logic to the focusObserver
            //  so that it can show or hide the keyboard based on the focus state.
            keyboardController.value?.showSoftwareKeyboard()
        }

    val emphasisLevels = EmphasisAmbient.current

    TextFieldTransitionScope.transition(
        inputState = inputState,
        showLabel = label != null,
        activeColor = if (isErrorValue) {
            errorColor
        } else {
            emphasisLevels.high.applyEmphasis(activeColor)
        },
        labelInactiveColor = if (isErrorValue) {
            errorColor
        } else {
            emphasisLevels.medium.applyEmphasis(inactiveColor)
        },
        indicatorInactiveColor = when {
            isErrorValue -> errorColor
            type == TextFieldType.Filled -> inactiveColor.applyAlpha(alpha = IndicatorInactiveAlpha)
            else -> emphasisLevels.disabled.applyEmphasis(inactiveColor)
        }

    ) { labelProgress, animatedLabelColor, indicatorWidth, indicatorColor, placeholderOpacity ->

        val leadingColor = inactiveColor.applyAlpha(alpha = TrailingLeadingAlpha)
        val trailingColor = if (isErrorValue) errorColor else leadingColor

        val decoratedLabel: @Composable (() -> Unit)? =
            if (label != null) {
                {
                    val labelAnimatedStyle = lerp(
                        MaterialTheme.typography.subtitle1,
                        MaterialTheme.typography.caption,
                        labelProgress
                    )
                    Decoration(
                        contentColor = animatedLabelColor,
                        typography = labelAnimatedStyle,
                        children = label
                    )
                }
            } else null

        val decoratedPlaceholder: @Composable ((Modifier) -> Unit)? =
            if (placeholder != null && value.text.isEmpty()) {
                { modifier ->
                    Box(modifier.drawOpacity(placeholderOpacity)) {
                        Decoration(
                            contentColor = inactiveColor,
                            typography = MaterialTheme.typography.subtitle1,
                            emphasis = EmphasisAmbient.current.medium,
                            children = placeholder
                        )
                    }
                }
            } else null

        when (type) {
            TextFieldType.Filled -> {
                TextFieldLayout(
                    modifier = Modifier
                        .preferredSizeIn(
                            minWidth = TextFieldMinWidth,
                            minHeight = TextFieldMinHeight
                        )
                        .then(textFieldModifier),
                    decoratedTextField = decoratedTextField,
                    decoratedPlaceholder = decoratedPlaceholder,
                    decoratedLabel = decoratedLabel,
                    leading = leading,
                    trailing = trailing,
                    leadingColor = leadingColor,
                    trailingColor = trailingColor,
                    labelProgress = labelProgress,
                    indicatorWidth = indicatorWidth,
                    indicatorColor = indicatorColor,
                    backgroundColor = backgroundColor,
                    shape = shape
                )
            }
            TextFieldType.Outlined -> {
                OutlinedTextFieldLayout(
                    modifier = Modifier
                        .preferredSizeIn(
                            minWidth = TextFieldMinWidth,
                            minHeight = TextFieldMinHeight + OutlinedTextFieldTopPadding
                        )
                        .then(textFieldModifier)
                        .padding(top = OutlinedTextFieldTopPadding),
                    decoratedTextField = decoratedTextField,
                    decoratedPlaceholder = decoratedPlaceholder,
                    decoratedLabel = decoratedLabel,
                    leading = leading,
                    trailing = trailing,
                    leadingColor = leadingColor,
                    trailingColor = trailingColor,
                    labelProgress = labelProgress,
                    indicatorWidth = indicatorWidth,
                    indicatorColor = indicatorColor
                )
            }
        }
    }
}

/**
 * Similar to [androidx.compose.foundation.ScrollableColumn] but does not lose the minWidth constraints.
 */
@VisibleForTesting
@Composable
internal fun TextFieldScroller(
    scrollerPosition: TextFieldScrollerPosition,
    modifier: Modifier = Modifier,
    textField: @Composable () -> Unit
) {
    Layout(
        modifier = modifier
            .clipToBounds()
            .scrollable(
                orientation = Orientation.Vertical,
                canScroll = { scrollerPosition.maximum != 0f },
                controller = rememberScrollableController { delta ->
                    val newPosition = scrollerPosition.current + delta
                    val consumedDelta = when {
                        newPosition > scrollerPosition.maximum ->
                            scrollerPosition.maximum - scrollerPosition.current // too much down
                        newPosition < 0f -> -scrollerPosition.current // scrolled too much up
                        else -> delta
                    }
                    scrollerPosition.current += consumedDelta
                    consumedDelta
                }
            ),
        children = textField,
        measureBlock = { measurables, constraints ->
            val childConstraints = constraints.copy(maxHeight = Constraints.Infinity)
            val placeable = measurables.first().measure(childConstraints)
            val height = min(placeable.height, constraints.maxHeight)
            val diff = placeable.height.toFloat() - height.toFloat()
            layout(placeable.width, height) {
                // update current and maximum positions to correctly calculate delta in scrollable
                scrollerPosition.maximum = diff
                if (scrollerPosition.current > diff) scrollerPosition.current = diff

                val yOffset = scrollerPosition.current - diff
                placeable.placeRelative(0, yOffset.roundToInt())
            }
        }
    )
}

@VisibleForTesting
@Stable
internal class TextFieldScrollerPosition(private val initial: Float = 0f) {
    var current by mutableStateOf(initial, structuralEqualityPolicy())
    var maximum by mutableStateOf(Float.POSITIVE_INFINITY, structuralEqualityPolicy())

    companion object {
        val Saver = Saver<TextFieldScrollerPosition, Float>(
            save = { it.current },
            restore = { restored ->
                TextFieldScrollerPosition(
                    initial = restored
                )
            }
        )
    }
}

/**
 * Set alpha if the color is not translucent
 */
internal fun Color.applyAlpha(alpha: Float): Color {
    return if (this.alpha != 1f) this else this.copy(alpha = alpha)
}

/**
 * Set content color, typography and emphasis for [children] composable
 */
@Composable
internal fun Decoration(
    contentColor: Color,
    typography: TextStyle? = null,
    emphasis: Emphasis? = null,
    children: @Composable () -> Unit
) {
    val colorAndEmphasis = @Composable {
        Providers(ContentColorAmbient provides contentColor) {
            if (emphasis != null) ProvideEmphasis(
                emphasis,
                children
            ) else children()
        }
    }
    if (typography != null) ProvideTextStyle(typography, colorAndEmphasis) else colorAndEmphasis()
}

private val Placeable.nonZero: Boolean get() = this.width != 0 || this.height != 0
internal fun widthOrZero(placeable: Placeable?) = placeable?.width ?: 0
internal fun heightOrZero(placeable: Placeable?) = placeable?.height ?: 0

/**
 * A modifier that applies padding only if the size of the element is not zero
 */
internal fun Modifier.iconPadding(start: Dp = 0.dp, end: Dp = 0.dp) =
    this.then(object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureScope.MeasureResult {
            val horizontal = start.toIntPx() + end.toIntPx()
            val placeable = measurable.measure(constraints.offset(-horizontal))
            val width = if (placeable.nonZero) {
                constraints.constrainWidth(placeable.width + horizontal)
            } else {
                0
            }
            return layout(width, placeable.height) {
                placeable.placeRelative(start.toIntPx(), 0)
            }
        }
    })

private object TextFieldTransitionScope {
    private val LabelColorProp = ColorPropKey()
    private val LabelProgressProp = FloatPropKey()
    private val IndicatorColorProp = ColorPropKey()
    private val IndicatorWidthProp = DpPropKey()
    private val PlaceholderOpacityProp = FloatPropKey()

    @Composable
    fun transition(
        inputState: InputPhase,
        showLabel: Boolean,
        activeColor: Color,
        labelInactiveColor: Color,
        indicatorInactiveColor: Color,
        children: @Composable (
            labelProgress: Float,
            labelColor: Color,
            indicatorWidth: Dp,
            indicatorColor: Color,
            placeholderOpacity: Float
        ) -> Unit
    ) {
        val definition = remember(
            showLabel,
            activeColor,
            labelInactiveColor,
            indicatorInactiveColor
        ) {
            generateLabelTransitionDefinition(
                showLabel,
                activeColor,
                labelInactiveColor,
                indicatorInactiveColor
            )
        }
        val state = transition(definition = definition, toState = inputState)
        children(
            state[LabelProgressProp],
            state[LabelColorProp],
            state[IndicatorWidthProp],
            state[IndicatorColorProp],
            state[PlaceholderOpacityProp]
        )
    }

    private fun generateLabelTransitionDefinition(
        showLabel: Boolean,
        activeColor: Color,
        labelInactiveColor: Color,
        indicatorInactiveColor: Color
    ) = transitionDefinition<InputPhase> {
        state(InputPhase.Focused) {
            this[LabelColorProp] = activeColor
            this[IndicatorColorProp] = activeColor
            this[LabelProgressProp] = 1f
            this[IndicatorWidthProp] = IndicatorFocusedWidth
            this[PlaceholderOpacityProp] = 1f
        }
        state(InputPhase.UnfocusedEmpty) {
            this[LabelColorProp] = labelInactiveColor
            this[IndicatorColorProp] = indicatorInactiveColor
            this[LabelProgressProp] = 0f
            this[IndicatorWidthProp] = IndicatorUnfocusedWidth
            this[PlaceholderOpacityProp] = if (showLabel) 0f else 1f
        }
        state(InputPhase.UnfocusedNotEmpty) {
            this[LabelColorProp] = labelInactiveColor
            this[IndicatorColorProp] = indicatorInactiveColor
            this[LabelProgressProp] = 1f
            this[IndicatorWidthProp] = 1.dp
            this[PlaceholderOpacityProp] = 0f
        }

        transition(fromState = InputPhase.Focused, toState = InputPhase.UnfocusedEmpty) {
            labelTransition()
            indicatorTransition()
            placeholderDisappearTransition()
        }
        transition(fromState = InputPhase.Focused, toState = InputPhase.UnfocusedNotEmpty) {
            indicatorTransition()
        }
        transition(fromState = InputPhase.UnfocusedNotEmpty, toState = InputPhase.Focused) {
            indicatorTransition()
        }
        transition(fromState = InputPhase.UnfocusedEmpty, toState = InputPhase.Focused) {
            labelTransition()
            indicatorTransition()
            placeholderAppearTransition()
        }
        // below states are needed to support case when a single state is used to control multiple
        // text fields.
        transition(fromState = InputPhase.UnfocusedNotEmpty, toState = InputPhase.UnfocusedEmpty) {
            labelTransition()
            placeholderAppearTransition()
        }
        transition(fromState = InputPhase.UnfocusedEmpty, toState = InputPhase.UnfocusedNotEmpty) {
            labelTransition()
        }
    }

    private fun TransitionSpec<InputPhase>.indicatorTransition() {
        IndicatorColorProp using tween(durationMillis = AnimationDuration)
        IndicatorWidthProp using tween(durationMillis = AnimationDuration)
    }

    private fun TransitionSpec<InputPhase>.labelTransition() {
        LabelColorProp using tween(durationMillis = AnimationDuration)
        LabelProgressProp using tween(durationMillis = AnimationDuration)
    }

    private fun TransitionSpec<InputPhase>.placeholderAppearTransition() {
        PlaceholderOpacityProp using tween(
            durationMillis = PlaceholderAnimationDuration,
            delayMillis = PlaceholderAnimationDelayOrDuration,
            easing = LinearEasing
        )
    }

    private fun TransitionSpec<InputPhase>.placeholderDisappearTransition() {
        PlaceholderOpacityProp using tween(
            durationMillis = PlaceholderAnimationDelayOrDuration,
            easing = LinearEasing
        )
    }
}

/**
 * An internal state used to animate a label and an indicator.
 */
private enum class InputPhase {
    // Text field is focused
    Focused,

    // Text field is not focused and input text is empty
    UnfocusedEmpty,

    // Text field is not focused but input text is not empty
    UnfocusedNotEmpty
}

internal const val TextFieldId = "TextField"
internal const val PlaceholderId = "Hint"
internal const val LabelId = "Label"

private const val AnimationDuration = 150
private const val PlaceholderAnimationDuration = 83
private const val PlaceholderAnimationDelayOrDuration = 67

private val IndicatorUnfocusedWidth = 1.dp
private val IndicatorFocusedWidth = 2.dp
private const val TrailingLeadingAlpha = 0.54f
private val TextFieldMinHeight = 56.dp
private val TextFieldMinWidth = 280.dp
internal val TextFieldPadding = 16.dp
internal val HorizontalIconPadding = 12.dp

// Filled text field uses 42% opacity to meet the contrast requirements for accessibility reasons
private const val IndicatorInactiveAlpha = 0.42f

/*
This padding is used to allow label not overlap with the content above it. This 8.dp will work
for default cases when developers do not override the label's font size. If they do, they will
need to add additional padding themselves
*/
private val OutlinedTextFieldTopPadding = 8.dp