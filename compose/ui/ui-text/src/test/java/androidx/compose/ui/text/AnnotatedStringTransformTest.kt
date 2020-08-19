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

import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Locale

@RunWith(JUnit4::class)
class AnnotatedStringTransformTest {

    private val spanStyle1 = SpanStyle(fontSize = 8.sp)
    private val spanStyle2 = SpanStyle(fontSize = 16.sp)
    private val spanStyle3 = SpanStyle(fontSize = 24.sp)

    private val paraStyle1 = ParagraphStyle(lineHeight = 10.sp)
    private val paraStyle2 = ParagraphStyle(lineHeight = 20.sp)

    /**
     * Helper function for creating AnnotatedString.Item with TextRange.
     */
    private fun <T> makeRange(style: T, range: TextRange) =
        AnnotatedString.Range(style, range.min, range.max)

    /**
     * Make AnnotatedString.Item with original string with using "(" and ")" characters.
     *
     * For example.
     *   val text = "aaa bbb ccc"
     *
     *   AnnotatedString.Item(STYLE, 4, 8)
     *
     * can be written as
     *
     *   val text = "aaa bbb ccc"
     *
     *   makeRange(STYLE, "aaa (bbb )ccc")
     */
    private fun <T> makeRange(style: T, rangeStr: String): AnnotatedString.Range<T> {
        val start = rangeStr.indexOf('(')
        val end = rangeStr.indexOf(')')

        if (start >= end) throw RuntimeException("Invalid range str: $rangeStr")
        return makeRange(style, TextRange(start, end - 1 /* subtract start marker */))
    }

    @Test
    fun `English uppercase plaintext`() {
        val input = AnnotatedString("aaa bbb ccc")

        val uppercase = input.toUpperCase()

        assertEquals(input.text.toUpperCase(), uppercase.text)
    }

    @Test
    fun `English uppercase sparse`() {
        val input = AnnotatedString("aaa bbb ccc",
            listOf(makeRange(spanStyle1, "aaa (bbb) ccc")))

        val uppercase = input.toUpperCase()

        assertEquals(input.text.toUpperCase(), uppercase.text)
    }

    @Test
    fun `English uppercase`() {
        val input = AnnotatedString("aaa bbb ccc",
            listOf(
                makeRange(spanStyle1, "(aaa bbb ccc)"),
                makeRange(spanStyle2, "(aaa )bbb ccc"),
                makeRange(spanStyle3, "aaa (bbb ccc)")
            ), listOf(
                makeRange(paraStyle1, "(aaa bbb )ccc"),
                makeRange(paraStyle2, "aaa bbb (ccc)")
            )
        )

        val uppercase = input.toUpperCase()

        assertEquals(input.text.toUpperCase(), uppercase.text)
        assertEquals(input.spanStyles, uppercase.spanStyles)
        assertEquals(input.paragraphStyles, uppercase.paragraphStyles)
    }

    @Test
    fun `English lowercase`() {
        val input = AnnotatedString("aaa bbb ccc",
            listOf(
                makeRange(spanStyle1, "(aaa bbb ccc)"),
                makeRange(spanStyle2, "(aaa )bbb ccc"),
                makeRange(spanStyle3, "aaa (bbb ccc)")
            ), listOf(
                makeRange(paraStyle1, "(aaa bbb )ccc"),
                makeRange(paraStyle2, "aaa bbb (ccc)")
            )
        )

        val lowercase = input.toLowerCase()

        assertEquals(input.text.toLowerCase(), lowercase.text)
        assertEquals(input.spanStyles, lowercase.spanStyles)
        assertEquals(input.paragraphStyles, lowercase.paragraphStyles)
    }

    @Test
    fun `English capitalize`() {
        val input = AnnotatedString("aaa bbb ccc",
            listOf(
                makeRange(spanStyle1, "(aaa bbb ccc)"),
                makeRange(spanStyle2, "(aaa )bbb ccc"),
                makeRange(spanStyle3, "aaa (bbb ccc)")
            ), listOf(
                makeRange(paraStyle1, "(aaa bbb )ccc"),
                makeRange(paraStyle2, "aaa bbb (ccc)")
            )
        )

        val capitalized = input.capitalize()

        assertEquals(input.text.capitalize(), capitalized.text)
        assertEquals(input.spanStyles, capitalized.spanStyles)
        assertEquals(input.paragraphStyles, capitalized.paragraphStyles)
    }

    @Test
    fun `English decapitalize`() {
        val input = AnnotatedString("aaa bbb ccc",
            listOf(
                makeRange(spanStyle1, "(aaa bbb ccc)"),
                makeRange(spanStyle2, "(aaa )bbb ccc"),
                makeRange(spanStyle3, "aaa (bbb ccc)")
            ), listOf(
                makeRange(paraStyle1, "(aaa bbb )ccc"),
                makeRange(paraStyle2, "aaa bbb (ccc)")
            )
        )

        val decapitalized = input.decapitalize()

        assertEquals(input.text.decapitalize(), decapitalized.text)
        assertEquals(input.spanStyles, decapitalized.spanStyles)
        assertEquals(input.paragraphStyles, decapitalized.paragraphStyles)
    }

    @Test
    fun `locale dependent uppercase or lowercase (Turkish uppercase)`() {
        val input = AnnotatedString("hhh iii jjj",
            listOf(
                makeRange(spanStyle1, "(hhh iii jjj)"),
                makeRange(spanStyle2, "(hhh )iii jjj"),
                makeRange(spanStyle3, "hhh (iii jjj)")
            ), listOf(
                makeRange(paraStyle1, "(hhh iii )jjj"),
                makeRange(paraStyle2, "hhh iii (jjj)")
            )
        )

        val uppercase = input.toUpperCase(LocaleList("tr"))

        assertEquals(input.text.toUpperCase(Locale.forLanguageTag("tr")), uppercase.text)

        val upperI = "i".toUpperCase(Locale.forLanguageTag("tr"))

        assertEquals(
            listOf(
                makeRange(spanStyle1, "(HHH $upperI$upperI$upperI JJJ)"),
                makeRange(spanStyle2, "(HHH )$upperI$upperI$upperI JJJ"),
                makeRange(spanStyle3, "HHH ($upperI$upperI$upperI JJJ)")
            ),
            uppercase.spanStyles)
        assertEquals(
            listOf(
                makeRange(paraStyle1, "(HHH $upperI$upperI$upperI )JJJ"),
                makeRange(paraStyle2, "HHH $upperI$upperI$upperI (JJJ)")
            ),
            uppercase.paragraphStyles)
    }

    @Test
    fun `not 1-by-1 mapping uppercase or lowercase (Lithuanian lowercase)`() {
        val input = AnnotatedString("HHH ÌÌÌ YYY",
            listOf(
                makeRange(spanStyle1, "(HHH ÌÌÌ YYY)"),
                makeRange(spanStyle2, "(HHH )ÌÌÌ YYY"),
                makeRange(spanStyle3, "HHH (ÌÌÌ YYY)")
            ), listOf(
                makeRange(paraStyle1, "(HHH ÌÌÌ )YYY"),
                makeRange(paraStyle2, "HHH ÌÌÌ (YYY)")
            )
        )

        val lowercase = input.toLowerCase(LocaleList("lt"))

        assertEquals(input.text.toLowerCase(Locale.forLanguageTag("lt")), lowercase.text)

        // Usually generate U+0069 U+0307 U+0300
        val lowerIDot = "Ì".toLowerCase(Locale.forLanguageTag("lt"))
        assertEquals(
            listOf(
                makeRange(spanStyle1, "(hhh $lowerIDot$lowerIDot$lowerIDot yyy)"),
                makeRange(spanStyle2, "(hhh )$lowerIDot$lowerIDot$lowerIDot yyy"),
                makeRange(spanStyle3, "hhh ($lowerIDot$lowerIDot$lowerIDot yyy)")
            ),
            lowercase.spanStyles)
        assertEquals(
            listOf(
                makeRange(paraStyle1, "(hhh $lowerIDot$lowerIDot$lowerIDot )yyy"),
                makeRange(paraStyle2, "hhh $lowerIDot$lowerIDot$lowerIDot (yyy)")
            ),
            lowercase.paragraphStyles)
    }

    @Test
    fun `nothing happens for CJK uppercase or lowercase (Japanese uppercase)`() {
        val input = AnnotatedString("あああ いいい ううう",
            listOf(
                makeRange(spanStyle1, "(あああ いいい ううう)"),
                makeRange(spanStyle2, "(あああ )いいい ううう"),
                makeRange(spanStyle3, "あああ (いいい ううう)")
            ), listOf(
                makeRange(paraStyle1, "(あああ いいい )ううう"),
                makeRange(paraStyle2, "あああ いいい (ううう)")
            )
        )

        val uppercase = input.toUpperCase()

        // No upper case concept in Japanese, so should be the same
        assertEquals(input.text, uppercase.text)
        assertEquals(input.spanStyles, uppercase.spanStyles)
        assertEquals(input.paragraphStyles, uppercase.paragraphStyles)
    }
}