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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.lerp
import androidx.compose.ui.unit.TextUnit

/**
 * Paragraph styling configuration for a paragraph. The difference between [SpanStyle] and
 * `ParagraphStyle` is that, `ParagraphStyle` can be applied to a whole [Paragraph] while
 * [SpanStyle] can be applied at the character level.
 * Once a portion of the text is marked with a `ParagraphStyle`, that portion will be separated from
 * the remaining as if a line feed character was added.
 *
 * @sample androidx.compose.ui.text.samples.ParagraphStyleSample
 * @sample androidx.compose.ui.text.samples.ParagraphStyleAnnotatedStringsSample
 *
 * @param textAlign The alignment of the text within the lines of the paragraph.
 * @param textDirection The algorithm to be used to resolve the final text direction:
 * Left To Right or Right To Left.
 * @param textIndent The indentation of the paragraph.
 * @param lineHeight Line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM.
 *
 * @see Paragraph
 * @see AnnotatedString
 * @see SpanStyle
 * @see TextStyle
 */
@Immutable
data class ParagraphStyle constructor(
    val textAlign: TextAlign? = null,
    val textDirection: TextDirection? = null,
    val lineHeight: TextUnit = TextUnit.Inherit,
    val textIndent: TextIndent? = null
) {
    init {
        if (lineHeight != TextUnit.Inherit) {
            // Since we are checking if it's negative, no need to convert Sp into Px at this point.
            check(lineHeight.value >= 0f) {
                "lineHeight can't be negative (${lineHeight.value})"
            }
        }
    }

    /**
     * Returns a new paragraph style that is a combination of this style and the given [other]
     * style.
     *
     * If the given paragraph style is null, returns this paragraph style.
     */
    @Stable
    fun merge(other: ParagraphStyle? = null): ParagraphStyle {
        if (other == null) return this

        return ParagraphStyle(
            lineHeight = if (other.lineHeight == TextUnit.Inherit) {
                this.lineHeight
            } else {
                other.lineHeight
            },
            textIndent = other.textIndent ?: this.textIndent,
            textAlign = other.textAlign ?: this.textAlign,
            textDirection = other.textDirection ?: this.textDirection
        )
    }

    /**
     * Plus operator overload that applies a [merge].
     */
    @Stable
    operator fun plus(other: ParagraphStyle): ParagraphStyle = this.merge(other)
}

/**
 * Interpolate between two [ParagraphStyle]s.
 *
 * This will not work well if the styles don't set the same fields.
 *
 * The [fraction] argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning [start] (or something
 * equivalent to [start]), 1.0 meaning that the interpolation has finished,
 * returning [stop] (or something equivalent to [stop]), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between [start] and [stop]. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid.
 */
@Stable
fun lerp(start: ParagraphStyle, stop: ParagraphStyle, fraction: Float): ParagraphStyle {
    return ParagraphStyle(
        textAlign = lerpDiscrete(start.textAlign, stop.textAlign, fraction),
        textDirection = lerpDiscrete(
            start.textDirection,
            stop.textDirection,
            fraction
        ),
        lineHeight = lerpTextUnitInheritable(start.lineHeight, stop.lineHeight, fraction),
        textIndent = lerp(
            start.textIndent ?: TextIndent(),
            stop.textIndent ?: TextIndent(),
            fraction
        )
    )
}
