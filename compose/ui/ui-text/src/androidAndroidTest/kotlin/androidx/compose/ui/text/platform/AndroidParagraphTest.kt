package androidx.compose.ui.text.platform

import android.graphics.Paint
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LocaleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ScaleXSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.FontTestData.Companion.BASIC_MEASURE_FONT
import androidx.compose.ui.text.ParagraphConstraints
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TestFontResourceLoader
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.android.InternalPlatformTextApi
import androidx.compose.ui.text.android.TextLayout
import androidx.compose.ui.text.android.style.BaselineShiftSpan
import androidx.compose.ui.text.android.style.FontFeatureSpan
import androidx.compose.ui.text.android.style.FontSpan
import androidx.compose.ui.text.android.style.LetterSpacingSpanEm
import androidx.compose.ui.text.android.style.LetterSpacingSpanPx
import androidx.compose.ui.text.android.style.ShadowSpan
import androidx.compose.ui.text.android.style.SkewXSpan
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.asFontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.matchers.assertThat
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.ceil

@OptIn(InternalPlatformTextApi::class)
@RunWith(JUnit4::class)
@SmallTest
class AndroidParagraphTest {
    // This sample font provides the following features:
    // 1. The width of most of visible characters equals to font size.
    // 2. The LTR/RTL characters are rendered as ▶/◀.
    // 3. The fontMetrics passed to TextPaint has descend - ascend equal to 1.2 * fontSize.
    private val basicFontFamily = BASIC_MEASURE_FONT.asFontFamily()
    private val defaultDensity = Density(density = 1f)
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun draw_with_newline_and_line_break_default_values() {
        with(defaultDensity) {
            val fontSize = 50.sp
            for (text in arrayOf("abc\ndef", "\u05D0\u05D1\u05D2\n\u05D3\u05D4\u05D5")) {
                val paragraphAndroid = simpleParagraph(
                    text = text,
                    style = TextStyle(
                        fontSize = fontSize,
                        fontFamily = basicFontFamily
                    ),
                    // 2 chars width
                    constraints = ParagraphConstraints(width = 2 * fontSize.toPx())
                )

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
                textPaint.textSize = fontSize.toPx()
                textPaint.typeface = TypefaceAdapter().create(basicFontFamily)

                val layout = TextLayout(
                    charSequence = text,
                    width = ceil(paragraphAndroid.width),
                    textPaint = textPaint
                )

                assertThat(paragraphAndroid.bitmap()).isEqualToBitmap(layout.bitmap())
            }
        }
    }

    @Test
    fun testAnnotatedString_setColorOnWholeText() {
        val text = "abcde"
        val spanStyle = SpanStyle(color = Color(0xFF0000FF))

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(ForegroundColorSpan::class, 0, text.length)
    }

    @Test
    fun testAnnotatedString_setColorOnPartOfText() {
        val text = "abcde"
        val spanStyle = SpanStyle(color = Color(0xFF0000FF))

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(ForegroundColorSpan::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setColorTwice_lastOneOverwrite() {
        val text = "abcde"
        val spanStyle = SpanStyle(color = Color(0xFF0000FF))
        val spanStyleOverwrite = SpanStyle(color = Color(0xFF00FF00))

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, 0, text.length),
                AnnotatedString.Range(spanStyleOverwrite, 0, "abc".length)
            ),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(ForegroundColorSpan::class, 0, text.length)
        assertThat(paragraph.charSequence).hasSpan(ForegroundColorSpan::class, 0, "abc".length)
        assertThat(paragraph.charSequence).hasSpanOnTop(ForegroundColorSpan::class, 0, "abc".length)
    }

    @Test
    fun testStyle_setTextDecorationOnWholeText_withLineThrough() {
        val text = "abcde"
        val spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(StrikethroughSpan::class, 0, text.length)
    }

    @Test
    fun testStyle_setTextDecorationOnWholeText_withUnderline() {
        val text = "abcde"
        val spanStyle = SpanStyle(textDecoration = TextDecoration.Underline)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(UnderlineSpan::class, 0, text.length)
    }

    @Test
    fun testStyle_setTextDecorationOnPartText_withLineThrough() {
        val text = "abcde"
        val spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(StrikethroughSpan::class, 0, "abc".length)
    }

    @Test
    fun testStyle_setTextDecorationOnPartText_withUnderline() {
        val text = "abcde"
        val spanStyle = SpanStyle(textDecoration = TextDecoration.Underline)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(UnderlineSpan::class, 0, "abc".length)
    }

    @Test
    fun testStyle_setTextDecoration_withLineThroughAndUnderline() {
        val text = "abcde"
        val spanStyle = SpanStyle(
            textDecoration = TextDecoration.LineThrough + TextDecoration.Underline
        )

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(UnderlineSpan::class, 0, "abc".length)
        assertThat(paragraph.charSequence).hasSpan(StrikethroughSpan::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setFontSizeOnWholeText() {
        with(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val paragraphWidth = text.length * fontSize.toPx()
            val spanStyle = SpanStyle(fontSize = fontSize)

            val paragraph = simpleParagraph(
                text = text,
                spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
                constraints = ParagraphConstraints(width = paragraphWidth)
            )

            assertThat(paragraph.charSequence).hasSpan(AbsoluteSizeSpan::class, 0, text.length)
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeOnPartText() {
        with(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val paragraphWidth = text.length * fontSize.toPx()
            val spanStyle = SpanStyle(fontSize = fontSize)

            val paragraph = simpleParagraph(
                text = text,
                spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
                constraints = ParagraphConstraints(width = paragraphWidth)
            )

            assertThat(paragraph.charSequence).hasSpan(AbsoluteSizeSpan::class, 0, "abc".length)
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeTwice_lastOneOverwrite() {
        with(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeOverwrite = 30.sp
            val paragraphWidth = text.length * fontSizeOverwrite.toPx()
            val spanStyle = SpanStyle(fontSize = fontSize)
            val spanStyleOverwrite = SpanStyle(fontSize = fontSizeOverwrite)

            val paragraph = simpleParagraph(
                text = text,
                spanStyles = listOf(
                    AnnotatedString.Range(spanStyle, 0, text.length),
                    AnnotatedString.Range(spanStyleOverwrite, 0, "abc".length)
                ),
                constraints = ParagraphConstraints(width = paragraphWidth)
            )

            assertThat(paragraph.charSequence).hasSpan(AbsoluteSizeSpan::class, 0, text.length)
            assertThat(paragraph.charSequence).hasSpan(AbsoluteSizeSpan::class, 0, "abc".length)
            assertThat(paragraph.charSequence)
                .hasSpanOnTop(AbsoluteSizeSpan::class, 0, "abc".length)
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeScaleOnWholeText() {
        val text = "abcde"
        val fontSizeScale = 2.0.em
        val spanStyle = SpanStyle(fontSize = fontSizeScale)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(RelativeSizeSpan::class, 0, text.length) {
            it.sizeChange == fontSizeScale.value
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeScaleOnPartText() {
        val text = "abcde"
        val fontSizeScale = 2.0f.em
        val spanStyle = SpanStyle(fontSize = fontSizeScale)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(RelativeSizeSpan::class, 0, "abc".length) {
            it.sizeChange == fontSizeScale.value
        }
    }

    @Test
    fun testAnnotatedString_setLetterSpacingOnWholeText() {
        val text = "abcde"
        val letterSpacing = 2.0f
        val spanStyle = SpanStyle(letterSpacing = letterSpacing.em)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(LetterSpacingSpanEm::class, 0, text.length)
    }

    @Test
    fun testAnnotatedString_setLetterSpacingOnPartText() {
        val text = "abcde"
        val spanStyle = SpanStyle(letterSpacing = 2.em)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(LetterSpacingSpanEm::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setLetterSpacingTwice_lastOneOverwrite() {
        val text = "abcde"
        val spanStyle = SpanStyle(letterSpacing = 2.em)
        val spanStyleOverwrite = SpanStyle(letterSpacing = 3.em)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, 0, text.length),
                AnnotatedString.Range(spanStyleOverwrite, 0, "abc".length)
            ),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence).hasSpan(LetterSpacingSpanEm::class, 0, text.length)
        assertThat(paragraph.charSequence).hasSpan(LetterSpacingSpanEm::class, 0, "abc".length)
        assertThat(paragraph.charSequence).hasSpanOnTop(LetterSpacingSpanEm::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setBackgroundOnWholeText() {
        val text = "abcde"
        val color = Color(0xFF0000FF)
        val spanStyle = SpanStyle(background = color)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence)
            .hasSpan(BackgroundColorSpan::class, 0, text.length) { span ->
                span.backgroundColor == color.toArgb()
            }
    }

    @Test
    fun testAnnotatedString_setBackgroundOnPartText() {
        val text = "abcde"
        val color = Color(0xFF0000FF)
        val spanStyle = SpanStyle(background = color)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence)
            .hasSpan(BackgroundColorSpan::class, 0, "abc".length) { span ->
                span.backgroundColor == color.toArgb()
            }
    }

    @Test
    fun testAnnotatedString_setBackgroundTwice_lastOneOverwrite() {
        val text = "abcde"
        val color = Color(0xFF0000FF)
        val spanStyle = SpanStyle(background = color)
        val colorOverwrite = Color(0xFF00FF00)
        val spanStyleOverwrite = SpanStyle(background = colorOverwrite)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, 0, text.length),
                AnnotatedString.Range(spanStyleOverwrite, 0, "abc".length)
            ),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence)
            .hasSpan(BackgroundColorSpan::class, 0, text.length) { span ->
                span.backgroundColor == color.toArgb()
            }
        assertThat(paragraph.charSequence)
            .hasSpan(BackgroundColorSpan::class, 0, "abc".length) { span ->
                span.backgroundColor == colorOverwrite.toArgb()
            }
        assertThat(paragraph.charSequence)
            .hasSpanOnTop(BackgroundColorSpan::class, 0, "abc".length) { span ->
                span.backgroundColor == colorOverwrite.toArgb()
            }
    }

    @Test
    fun testAnnotatedString_setLocaleOnWholeText() {
        val text = "abcde"
        val localeList = LocaleList("en-US")
        val spanStyle = SpanStyle(localeList = localeList)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(LocaleSpan::class, 0, text.length)
    }

    @Test
    fun testAnnotatedString_setLocaleOnPartText() {
        val text = "abcde"
        val localeList = LocaleList("en-US")
        val spanStyle = SpanStyle(localeList = localeList)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(LocaleSpan::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setLocaleTwice_lastOneOverwrite() {
        val text = "abcde"
        val spanStyle = SpanStyle(localeList = LocaleList("en-US"))
        val spanStyleOverwrite = SpanStyle(localeList = LocaleList("ja-JP"))

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, 0, text.length),
                AnnotatedString.Range(spanStyleOverwrite, 0, "abc".length)
            ),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence).hasSpan(LocaleSpan::class, 0, text.length)
        assertThat(paragraph.charSequence).hasSpan(LocaleSpan::class, 0, "abc".length)
        assertThat(paragraph.charSequence).hasSpanOnTop(LocaleSpan::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setBaselineShiftOnWholeText() {
        val text = "abcde"
        val spanStyle = SpanStyle(baselineShift = BaselineShift.Subscript)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).hasSpan(BaselineShiftSpan::class, 0, text.length)
    }

    @Test
    fun testAnnotatedString_setBaselineShiftOnPartText() {
        val text = "abcde"
        val spanStyle = SpanStyle(baselineShift = BaselineShift.Superscript)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).hasSpan(BaselineShiftSpan::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setBaselineShiftTwice_LastOneOnTop() {
        val text = "abcde"
        val spanStyle = SpanStyle(baselineShift = BaselineShift.Subscript)
        val spanStyleOverwrite =
            SpanStyle(baselineShift = BaselineShift.Superscript)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, 0, text.length),
                AnnotatedString.Range(spanStyleOverwrite, 0, "abc".length)
            ),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).hasSpan(BaselineShiftSpan::class, 0, text.length)
        assertThat(paragraph.charSequence).hasSpan(BaselineShiftSpan::class, 0, "abc".length)
        assertThat(paragraph.charSequence).hasSpanOnTop(BaselineShiftSpan::class, 0, "abc".length)
    }

    @Test
    fun testAnnotatedString_setTextGeometricTransformWithNull_noSpanSet() {
        val text = "abcde"
        val spanStyle = SpanStyle(textGeometricTransform = TextGeometricTransform())

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).spans(ScaleXSpan::class).isEmpty()
        assertThat(paragraph.charSequence).spans(SkewXSpan::class).isEmpty()
    }

    @Test
    fun testAnnotatedString_setTextGeometricTransformWithScaleX() {
        val text = "abcde"
        val scaleX = 0.5f
        val spanStyle = SpanStyle(
            textGeometricTransform = TextGeometricTransform(
                scaleX = scaleX
            )
        )

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).hasSpan(ScaleXSpan::class, 0, text.length) {
            it.scaleX == scaleX
        }
        assertThat(paragraph.charSequence).spans(SkewXSpan::class).isEmpty()
    }

    @Test
    fun testAnnotatedString_setTextGeometricTransformWithSkewX() {
        val text = "aa"
        val skewX = 1f
        val spanStyle = SpanStyle(textGeometricTransform = TextGeometricTransform(skewX = skewX))

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, text.length)),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).hasSpan(SkewXSpan::class, 0, text.length) {
            it.skewX == skewX
        }
        assertThat(paragraph.charSequence).spans(ScaleXSpan::class).isEmpty()
    }

    @Test
    fun textIndent_onWholeParagraph() {
        val text = "abc\ndef"
        val firstLine = 40
        val restLine = 20

        val paragraph = simpleParagraph(
            text = text,
            textIndent = TextIndent(firstLine.sp, restLine.sp),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence)
            .hasSpan(LeadingMarginSpan.Standard::class, 0, text.length) {
                it.getLeadingMargin(true) == firstLine && it.getLeadingMargin(false) == restLine
            }
    }

    @Test
    fun testAnnotatedString_setShadow() {
        val text = "abcde"
        val color = Color(0xFF00FF00)
        val offset = Offset(1f, 2f)
        val radius = 3.0f
        val spanStyle = SpanStyle(shadow = Shadow(color, offset, radius))

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, start = 0, end = text.length)
            ),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence)
            .hasSpan(ShadowSpan::class, start = 0, end = text.length) {
                return@hasSpan it.color == color.toArgb() &&
                        it.offsetX == offset.x &&
                        it.offsetY == offset.y &&
                        it.radius == radius
            }
    }

    @Test
    fun testAnnotatedString_setShadowTwice_lastOnTop() {
        val text = "abcde"
        val color = Color(0xFF00FF00)
        val offset = Offset(1f, 2f)
        val radius = 3.0f
        val spanStyle = SpanStyle(shadow = Shadow(color, offset, radius))

        val colorOverwrite = Color(0xFF0000FF)
        val offsetOverwrite = Offset(3f, 2f)
        val radiusOverwrite = 1.0f
        val spanStyleOverwrite = SpanStyle(
            shadow = Shadow(colorOverwrite, offsetOverwrite, radiusOverwrite)
        )

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(spanStyle, start = 0, end = text.length),
                AnnotatedString.Range(spanStyleOverwrite, start = 0, end = "abc".length)
            ),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence)
            .hasSpan(ShadowSpan::class, start = 0, end = text.length) {
                return@hasSpan it.color == color.toArgb() &&
                        it.offsetX == offset.x &&
                        it.offsetY == offset.y &&
                        it.radius == radius
            }
        assertThat(paragraph.charSequence)
            .hasSpanOnTop(ShadowSpan::class, start = 0, end = "abc".length) {
                return@hasSpanOnTop it.color == colorOverwrite.toArgb() &&
                        it.offsetX == offsetOverwrite.x &&
                        it.offsetY == offsetOverwrite.y &&
                        it.radius == radiusOverwrite
            }
    }

    @Test
    fun testAnnotatedString_fontFamily_addsTypefaceSpanWithCorrectTypeface() {
        val text = "abcde"
        val spanStyle = SpanStyle(
            fontFamily = basicFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold
        )
        val expectedTypeface = TypefaceAdapter().create(
            fontFamily = basicFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold
        )
        val expectedStart = 0
        val expectedEnd = "abc".length

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(
                    spanStyle,
                    expectedStart,
                    expectedEnd
                )
            ),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence)
            .hasSpan(FontSpan::class, expectedStart, expectedEnd) { span ->
                span.getTypeface(FontWeight.Bold.weight, true) == expectedTypeface
            }
    }

    @Test
    fun testAnnotatedString_fontFamily_whenFontSynthesizeTurnedOff() {
        val text = "abcde"
        val spanStyle = SpanStyle(
            fontFamily = basicFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            fontSynthesis = FontSynthesis.None
        )
        val expectedTypeface = TypefaceAdapter().create(
            fontFamily = basicFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            fontSynthesis = FontSynthesis.None
        )
        val expectedStart = 0
        val expectedEnd = "abc".length

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(
                AnnotatedString.Range(
                    spanStyle,
                    expectedStart,
                    expectedEnd
                )
            ),
            constraints = ParagraphConstraints(width = 100.0f)
        )

        assertThat(paragraph.charSequence.toString()).isEqualTo(text)
        assertThat(paragraph.charSequence)
            .hasSpan(FontSpan::class, expectedStart, expectedEnd) { span ->
                span.getTypeface(FontWeight.Bold.weight, true) == expectedTypeface
            }
    }

    @Test
    fun testAnnotatedString_fontFeatureSetting_setSpanOnText() {
        val text = "abc"
        val fontFeatureSettings = "\"kern\" 0"
        val spanStyle = SpanStyle(fontFeatureSettings = fontFeatureSettings)

        val paragraph = simpleParagraph(
            text = text,
            spanStyles = listOf(AnnotatedString.Range(spanStyle, 0, "abc".length)),
            constraints = ParagraphConstraints(width = 100.0f) // width is not important
        )

        assertThat(paragraph.charSequence).hasSpan(FontFeatureSpan::class, 0, "abc".length) {
            it.fontFeatureSettings == fontFeatureSettings
        }
    }

    @Test
    fun testEmptyFontFamily() {
        val typefaceAdapter = mock<TypefaceAdapter>()
        val paragraph = simpleParagraph(
            text = "abc",
            typefaceAdapter = typefaceAdapter,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        verify(typefaceAdapter, never()).create(
            fontFamily = any(),
            fontWeight = any(),
            fontStyle = any(),
            fontSynthesis = any()
        )
        assertThat(paragraph.textPaint.typeface).isNull()
    }

    @Test
    fun testEmptyFontFamily_withBoldFontWeightSelection() {
        val typefaceAdapter = spy(TypefaceAdapter())

        val paragraph = simpleParagraph(
            text = "abc",
            style = TextStyle(
                fontFamily = null,
                fontWeight = FontWeight.Bold
            ),
            typefaceAdapter = typefaceAdapter,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(null),
            fontWeight = eq(FontWeight.Bold),
            fontStyle = eq(FontStyle.Normal),
            fontSynthesis = eq(FontSynthesis.All)
        )

        val typeface = paragraph.textPaint.typeface
        assertThat(typeface).isNotNull()
        assertThat(typeface.isBold).isTrue()
        assertThat(typeface.isItalic).isFalse()
    }

    @Test
    fun testEmptyFontFamily_withFontStyleSelection() {
        val typefaceAdapter = spy(TypefaceAdapter())
        val paragraph = simpleParagraph(
            text = "abc",
            style = TextStyle(
                fontFamily = null,
                fontStyle = FontStyle.Italic
            ),
            typefaceAdapter = typefaceAdapter,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(null),
            fontWeight = eq(FontWeight.Normal),
            fontStyle = eq(FontStyle.Italic),
            fontSynthesis = eq(FontSynthesis.All)
        )

        val typeface = paragraph.textPaint.typeface
        assertThat(typeface).isNotNull()
        assertThat(typeface.isBold).isFalse()
        assertThat(typeface.isItalic).isTrue()
    }

    @Test
    fun testFontFamily_withGenericFamilyName() {
        val typefaceAdapter = spy(TypefaceAdapter())
        val fontFamily = FontFamily.SansSerif

        val paragraph = simpleParagraph(
            text = "abc",
            style = TextStyle(
                fontFamily = fontFamily
            ),
            typefaceAdapter = typefaceAdapter,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(fontFamily),
            fontWeight = eq(FontWeight.Normal),
            fontStyle = eq(FontStyle.Normal),
            fontSynthesis = eq(FontSynthesis.All)
        )

        val typeface = paragraph.textPaint.typeface
        assertThat(typeface).isNotNull()
        assertThat(typeface.isBold).isFalse()
        assertThat(typeface.isItalic).isFalse()
    }

    @Test
    fun testFontFamily_withCustomFont() {
        val typefaceAdapter = spy(TypefaceAdapter())
        val paragraph = simpleParagraph(
            text = "abc",
            style = TextStyle(
                fontFamily = basicFontFamily
            ),
            typefaceAdapter = typefaceAdapter,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        verify(typefaceAdapter, atLeastOnce()).create(
            fontFamily = eq(basicFontFamily),
            fontWeight = eq(FontWeight.Normal),
            fontStyle = eq(FontStyle.Normal),
            fontSynthesis = eq(FontSynthesis.All)
        )
        val typeface = paragraph.textPaint.typeface
        assertThat(typeface.isBold).isFalse()
        assertThat(typeface.isItalic).isFalse()
    }

    @Test
    fun testFontFamily_appliedAsSpan() {
        val text = "abc"
        val typefaceAdapter = spy(TypefaceAdapter())
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(
                fontFamily = basicFontFamily
            ),
            typefaceAdapter = typefaceAdapter,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        val charSequence = paragraph.charSequence
        assertThat(charSequence).hasSpan(FontSpan::class, 0, text.length)
    }

    @Test
    fun testEllipsis_withMaxLineEqualsNull_doesNotEllipsis() {
        with(defaultDensity) {
            val text = "abc"
            val fontSize = 20.sp
            val paragraphWidth = (text.length - 1) * fontSize.toPx()
            val paragraph = simpleParagraph(
                text = text,
                style = TextStyle(
                    fontFamily = basicFontFamily,
                    fontSize = fontSize
                ),
                ellipsis = true,
                constraints = ParagraphConstraints(width = paragraphWidth)
            )

            for (i in 0 until paragraph.lineCount) {
                assertThat(paragraph.isEllipsisApplied(i)).isFalse()
            }
        }
    }

    @Test
    fun testEllipsis_withMaxLinesLessThanTextLines_doesEllipsis() {
        with(defaultDensity) {
            val text = "abcde"
            val fontSize = 100.sp
            // Note that on API 21, if the next line only contains 1 character, ellipsis won't work
            val paragraphWidth = (text.length - 1.5f) * fontSize.toPx()
            val paragraph = simpleParagraph(
                text = text,
                ellipsis = true,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = basicFontFamily,
                    fontSize = fontSize
                ),
                constraints = ParagraphConstraints(width = paragraphWidth)
            )

            assertThat(paragraph.isEllipsisApplied(0)).isTrue()
        }
    }

    @Test
    fun testEllipsis_withMaxLinesMoreThanTextLines_doesNotEllipsis() {
        with(defaultDensity) {
            val text = "abc"
            val fontSize = 100.sp
            val paragraphWidth = (text.length - 1) * fontSize.toPx()
            val maxLines = ceil(text.length * fontSize.toPx() / paragraphWidth).toInt()
            val paragraph = simpleParagraph(
                text = text,
                ellipsis = true,
                maxLines = maxLines,
                style = TextStyle(
                    fontFamily = basicFontFamily,
                    fontSize = fontSize
                ),
                constraints = ParagraphConstraints(width = paragraphWidth)
            )

            for (i in 0 until paragraph.lineCount) {
                assertThat(paragraph.isEllipsisApplied(i)).isFalse()
            }
        }
    }

    @Test
    fun testSpanStyle_fontSize_appliedOnTextPaint() {
        with(defaultDensity) {
            val fontSize = 100.sp
            val paragraph = simpleParagraph(
                text = "",
                style = TextStyle(fontSize = fontSize),
                constraints = ParagraphConstraints(width = 0.0f)
            )

            assertThat(paragraph.textPaint.textSize).isEqualTo(fontSize.toPx())
        }
    }

    @Test
    fun testSpanStyle_locale_appliedOnTextPaint() {
        val platformLocale = java.util.Locale.JAPANESE
        val localeList = LocaleList(platformLocale.toLanguageTag())

        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(localeList = localeList),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.textLocale.language).isEqualTo(platformLocale.language)
        assertThat(paragraph.textPaint.textLocale.country).isEqualTo(platformLocale.country)
    }

    @Test
    fun testSpanStyle_color_appliedOnTextPaint() {
        val color = Color(0x12345678)
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(color = color),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.color).isEqualTo(color.toArgb())
    }

    @Test
    fun testTextStyle_letterSpacingInEm_appliedOnTextPaint() {
        val letterSpacing = 2
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(letterSpacing = letterSpacing.em),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.letterSpacing).isEqualTo((letterSpacing))
    }

    @Test
    fun testTextStyle_letterSpacingInSp_appliedAsSpan() {
        val letterSpacing = 5f
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(letterSpacing = letterSpacing.sp),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.charSequence)
            .hasSpan(LetterSpacingSpanPx::class, 0, text.length) {
                it.letterSpacing == letterSpacing
            }
    }

    @Test
    fun testSpanStyle_fontFeatureSettings_appliedOnTextPaint() {
        val fontFeatureSettings = "\"kern\" 0"
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(fontFeatureSettings = fontFeatureSettings),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.fontFeatureSettings).isEqualTo(fontFeatureSettings)
    }

    @Test
    fun testSpanStyle_scaleX_appliedOnTextPaint() {
        val scaleX = 0.5f
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(
                textGeometricTransform = TextGeometricTransform(
                    scaleX = scaleX
                )
            ),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.textScaleX).isEqualTo(scaleX)
    }

    @Test
    fun testSpanStyle_skewX_appliedOnTextPaint() {
        val skewX = 0.5f
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(
                textGeometricTransform = TextGeometricTransform(
                    skewX = skewX
                )
            ),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.textSkewX).isEqualTo(skewX)
    }

    @Test
    fun testSpanStyle_textDecoration_underline_appliedOnTextPaint() {
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(textDecoration = TextDecoration.Underline),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.isUnderlineText).isTrue()
    }

    @Test
    fun testSpanStyle_textDecoration_lineThrough_appliedOnTextPaint() {
        val paragraph = simpleParagraph(
            text = "",
            style = TextStyle(textDecoration = TextDecoration.LineThrough),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.textPaint.isStrikeThruText).isTrue()
    }

    @Test
    fun testSpanStyle_background_appliedAsSpan() {
        // bgColor is reset in the Android Layout constructor.
        // therefore we cannot apply them on paint, have to use spans.
        val text = "abc"
        val color = Color(0x12345678)
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(background = color),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.charSequence)
            .hasSpan(BackgroundColorSpan::class, 0, text.length) { span ->
                span.backgroundColor == color.toArgb()
            }
    }

    @Test
    fun testSpanStyle_baselineShift_appliedAsSpan() {
        // baselineShift is reset in the Android Layout constructor.
        // therefore we cannot apply them on paint, have to use spans.
        val text = "abc"
        val baselineShift = BaselineShift.Subscript
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(baselineShift = baselineShift),
            constraints = ParagraphConstraints(width = 0.0f)
        )

        assertThat(paragraph.charSequence)
            .hasSpan(BaselineShiftSpan::class, 0, text.length) { span ->
                span.multiplier == BaselineShift.Subscript.multiplier
            }
    }

    @Test
    fun locale_isDefaultLocaleIfNotProvided() {
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        assertThat(paragraph.textLocale.toLanguageTag())
            .isEqualTo(java.util.Locale.getDefault().toLanguageTag())
    }

    @Test
    fun locale_isSetOnParagraphImpl_enUS() {
        val localeList = LocaleList("en-US")
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(localeList = localeList),
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        assertThat(paragraph.textLocale.toLanguageTag()).isEqualTo("en-US")
    }

    @Test
    fun locale_isSetOnParagraphImpl_jpJP() {
        val localeList = LocaleList("ja-JP")
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(localeList = localeList),
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        assertThat(paragraph.textLocale.toLanguageTag()).isEqualTo("ja-JP")
    }

    @Test
    fun locale_noCountryCode_isSetOnParagraphImpl() {
        val localeList = LocaleList("ja")
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            style = TextStyle(localeList = localeList),
            constraints = ParagraphConstraints(width = Float.MAX_VALUE)
        )

        assertThat(paragraph.textLocale.toLanguageTag()).isEqualTo("ja")
    }

    @Test
    fun floatingWidth() {
        val floatWidth = 1.3f
        val paragraph = simpleParagraph(
            text = "Hello, World",
            constraints = ParagraphConstraints(floatWidth)
        )

        assertThat(floatWidth).isEqualTo(paragraph.width)
    }

    private fun simpleParagraph(
        text: String = "",
        spanStyles: List<AnnotatedString.Range<SpanStyle>> = listOf(),
        textIndent: TextIndent? = null,
        textAlign: TextAlign? = null,
        ellipsis: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
        constraints: ParagraphConstraints,
        style: TextStyle? = null,
        typefaceAdapter: TypefaceAdapter = TypefaceAdapter()
    ): AndroidParagraph {
        return AndroidParagraph(
            text = text,
            spanStyles = spanStyles,
            placeholders = listOf(),
            typefaceAdapter = typefaceAdapter,
            style = TextStyle(
                textAlign = textAlign,
                textIndent = textIndent
            ).merge(style),
            maxLines = maxLines,
            ellipsis = ellipsis,
            constraints = constraints,
            density = Density(density = 1f)
        )
    }

    private fun TypefaceAdapter() = TypefaceAdapter(
        resourceLoader = TestFontResourceLoader(context)
    )
}