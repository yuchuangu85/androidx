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

package androidx.compose.ui.text

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.TextDelegate.Companion.paint
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.util.annotation.VisibleForTesting
import kotlin.math.ceil

/**
 * An object that paints text onto a [Canvas].
 *
 * To use a [TextDelegate], follow these steps:
 *
 * 1. Create an [AnnotatedString] and pass it to the [TextDelegate] constructor.
 *
 * 2. Call [layout] to prepare the paragraph.
 *
 * 3. Call [paint] as often as desired to paint the paragraph.
 *
 *  If the width of the area into which the text is being painted changes, return to step 2. If the
 *  text to be painted changes, return to step 1.
 *
 * @param text the text to paint.
 *
 * @param style The text style specified to render the text. Notice that you can also set text
 * style on the given [AnnotatedString], and the style set on [text] always has higher priority
 * than this setting. But if only one global text style is needed, passing it to [TextDelegate]
 * is always preferred.
 *
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it is truncated such that subsequent
 * lines are dropped.
 *
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 *
 * @param overflow How visual overflow should be handled. Specifically, the ellipsis is applied
 * to the last line before the line truncated by [maxLines], if [maxLines] is non-null and that
 * line overflows the width constraint.
 *
 * @param density The [Density] object that provides pixel density information of the device
 *
 * @param placeholders a list of [Placeholder]s that specify ranges of text where the original
 * text is replaced empty spaces. It's typically used to embed images into text.
 *
 * @suppress
 */
@InternalTextApi
@Stable
class TextDelegate(
    val text: AnnotatedString,
    val style: TextStyle,
    val maxLines: Int = Int.MAX_VALUE,
    val softWrap: Boolean = true,
    val overflow: TextOverflow = TextOverflow.Clip,
    val density: Density,
    val resourceLoader: Font.ResourceLoader,
    val placeholders: List<AnnotatedString.Range<Placeholder>> = listOf()
) {
    @VisibleForTesting
    internal var paragraphIntrinsics: MultiParagraphIntrinsics? = null
    internal var intrinsicsLayoutDirection: LayoutDirection? = null

    private inline fun <T> assumeIntrinsics(block: (MultiParagraphIntrinsics) -> T) =
        block(paragraphIntrinsics
            ?: throw IllegalStateException("layoutForIntrinsics must be called first")
        )

    /**
     * The width for text if all soft wrap opportunities were taken.
     *
     * Valid only after [layout] has been called.
     */
    val minIntrinsicWidth: Int get() = assumeIntrinsics {
        ceil(it.minIntrinsicWidth).toInt()
    }

    /**
     * The width at which increasing the width of the text no longer decreases the height.
     *
     * Valid only after [layout] has been called.
     */
    val maxIntrinsicWidth: Int get() = assumeIntrinsics {
        ceil(it.maxIntrinsicWidth).toInt()
    }

    init {
        check(maxLines > 0)
    }

    fun layoutIntrinsics(layoutDirection: LayoutDirection) {
        val intrinsics = if (
            paragraphIntrinsics == null ||
            layoutDirection != intrinsicsLayoutDirection
        ) {
            intrinsicsLayoutDirection = layoutDirection
            MultiParagraphIntrinsics(
                annotatedString = text,
                style = resolveDefaults(style, layoutDirection),
                density = density,
                resourceLoader = resourceLoader,
                placeholders = placeholders
            )
        } else {
            paragraphIntrinsics
        }

        paragraphIntrinsics = intrinsics
    }

    /**
     * Computes the visual position of the glyphs for painting the text.
     *
     * The text will layout with a width that's as close to its max intrinsic width as possible
     * while still being greater than or equal to `minWidth` and less than or equal to `maxWidth`.
     */
    private fun layoutText(minWidth: Float, maxWidth: Float, layoutDirection: LayoutDirection):
            MultiParagraph {
        layoutIntrinsics(layoutDirection)
        assumeIntrinsics { paragraphIntrinsics ->
            // if minWidth == maxWidth the width is fixed.
            //    therefore we can pass that value to our paragraph and use it
            // if minWidth != maxWidth there is a range
            //    then we should check if the max intrinsic width is in this range to decide the
            //    width to be passed to Paragraph
            //        if max intrinsic width is between minWidth and maxWidth
            //           we can use it to layout
            //        else if max intrinsic width is greater than maxWidth, we can only use maxWidth
            //        else if max intrinsic width is less than minWidth, we should use minWidth
            val width = if (minWidth == maxWidth) {
                maxWidth
            } else {
                paragraphIntrinsics.maxIntrinsicWidth.coerceIn(minWidth, maxWidth)
            }

            return MultiParagraph(
                intrinsics = paragraphIntrinsics,
                maxLines = maxLines,
                ellipsis = overflow == TextOverflow.Ellipsis,
                constraints = ParagraphConstraints(width = width)
            )
        }
    }

    fun layout(
        constraints: Constraints,
        layoutDirection: LayoutDirection,
        prevResult: TextLayoutResult? = null
    ): TextLayoutResult {
        val minWidth = constraints.minWidth.toFloat()
        val widthMatters = softWrap || overflow == TextOverflow.Ellipsis
        val maxWidth = if (widthMatters && constraints.hasBoundedWidth) {
            constraints.maxWidth.toFloat()
        } else {
            Float.POSITIVE_INFINITY
        }

        if (prevResult != null && prevResult.canReuse(
                text, style, maxLines, softWrap, overflow, density, layoutDirection,
                resourceLoader, constraints)) {
            return with(prevResult) {
                copy(
                    layoutInput = layoutInput.copy(
                        style = style,
                        constraints = constraints
                    ),
                    size = constraints.constrain(
                        IntSize(
                            ceil(multiParagraph.width).toInt(),
                            ceil(multiParagraph.height).toInt()
                        )
                    )
                )
            }
        }

        val multiParagraph = layoutText(
            minWidth,
            maxWidth,
            layoutDirection
        )

        val size = constraints.constrain(
            IntSize(
                ceil(multiParagraph.width).toInt(),
                ceil(multiParagraph.height).toInt()
            )
        )

        return TextLayoutResult(
            TextLayoutInput(
                text,
                style,
                placeholders,
                maxLines,
                softWrap,
                overflow,
                density,
                layoutDirection,
                resourceLoader,
                constraints
            ),
            multiParagraph,
            size
        )
    }

    companion object {
        /**
         * Paints the text onto the given canvas.
         *
         * Valid only after [layout] has been called.
         *
         * If you cannot see the text being painted, check that your text color does not conflict
         * with the background on which you are drawing. The default text color is white (to
         * contrast with the default black background color), so if you are writing an
         * application with a white background, the text will not be visible by default.
         *
         * To set the text style, specify a [SpanStyle] when creating the [AnnotatedString] that
         * you pass to the [TextDelegate] constructor or to the [text] property.
         */
        fun paint(canvas: Canvas, textLayoutResult: TextLayoutResult) {
            TextPainter.paint(canvas, textLayoutResult)
        }

        /**
         * Draws text background of the given range.
         *
         * If the given range is empty, do nothing.
         *
         * @param start inclusive start character offset of the drawing range.
         * @param end exclusive end character offset of the drawing range.
         * @param paint used to draw background.
         * @param canvas the target canvas.
         */
        fun paintBackground(
            start: Int,
            end: Int,
            paint: Paint,
            canvas: Canvas,
            textLayoutResult: TextLayoutResult
        ) {
            if (start == end) return
            val selectionPath = textLayoutResult.multiParagraph.getPathForRange(start, end)
            canvas.drawPath(selectionPath, paint)
        }
    }
}