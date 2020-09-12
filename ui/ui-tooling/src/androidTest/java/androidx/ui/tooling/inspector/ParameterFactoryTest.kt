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

package androidx.ui.tooling.inspector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.colorspace.ColorModel
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class ParameterFactoryTest {
    private val node = MutableInspectorNode()
    private val factory = ParameterFactory()

    @Before
    fun before() {
        factory.density = Density(2.0f)
        node.width = 1000
        node.height = 500
    }

    @Test
    fun testAbsoluteAlignment() {
        assertThat(lookup(AbsoluteAlignment.TopLeft))
            .isEqualTo(ParameterType.String to "TopLeft")
        assertThat(lookup(AbsoluteAlignment.TopRight))
            .isEqualTo(ParameterType.String to "TopRight")
        assertThat(lookup(AbsoluteAlignment.CenterLeft))
            .isEqualTo(ParameterType.String to "CenterLeft")
        assertThat(lookup(AbsoluteAlignment.CenterRight))
            .isEqualTo(ParameterType.String to "CenterRight")
        assertThat(lookup(AbsoluteAlignment.BottomLeft))
            .isEqualTo(ParameterType.String to "BottomLeft")
        assertThat(lookup(AbsoluteAlignment.BottomRight))
            .isEqualTo(ParameterType.String to "BottomRight")
        assertThat(lookup(AbsoluteAlignment.Left))
            .isEqualTo(ParameterType.String to "Left")
        assertThat(lookup(AbsoluteAlignment.Right))
            .isEqualTo(ParameterType.String to "Right")
    }

    @Test
    fun testAlignment() {
        assertThat(lookup(Alignment.Top)).isEqualTo(ParameterType.String to "Top")
        assertThat(lookup(Alignment.Bottom)).isEqualTo(ParameterType.String to "Bottom")
        assertThat(lookup(Alignment.CenterVertically))
            .isEqualTo(ParameterType.String to "CenterVertically")

        assertThat(lookup(Alignment.Start)).isEqualTo(ParameterType.String to "Start")
        assertThat(lookup(Alignment.End)).isEqualTo(ParameterType.String to "End")
        assertThat(lookup(Alignment.CenterHorizontally))
            .isEqualTo(ParameterType.String to "CenterHorizontally")

        assertThat(lookup(Alignment.TopStart)).isEqualTo(ParameterType.String to "TopStart")
        assertThat(lookup(Alignment.TopCenter)).isEqualTo(ParameterType.String to "TopCenter")
        assertThat(lookup(Alignment.TopEnd)).isEqualTo(ParameterType.String to "TopEnd")
        assertThat(lookup(Alignment.CenterStart)).isEqualTo(ParameterType.String to "CenterStart")
        assertThat(lookup(Alignment.Center)).isEqualTo(ParameterType.String to "Center")
        assertThat(lookup(Alignment.CenterEnd)).isEqualTo(ParameterType.String to "CenterEnd")
        assertThat(lookup(Alignment.BottomStart)).isEqualTo(ParameterType.String to "BottomStart")
        assertThat(lookup(Alignment.BottomCenter)).isEqualTo(ParameterType.String to "BottomCenter")
        assertThat(lookup(Alignment.BottomEnd)).isEqualTo(ParameterType.String to "BottomEnd")
    }

    @Test
    fun testAnnotatedString() {
        assertThat(lookup(AnnotatedString("Hello"))).isEqualTo(ParameterType.String to "Hello")
    }

    @Test
    fun testArrangement() {
        assertThat(lookup(Arrangement.Top)).isEqualTo(ParameterType.String to "Top")
        assertThat(lookup(Arrangement.Bottom)).isEqualTo(ParameterType.String to "Bottom")
        assertThat(lookup(Arrangement.Center)).isEqualTo(ParameterType.String to "Center")
        assertThat(lookup(Arrangement.Start)).isEqualTo(ParameterType.String to "Start")
        assertThat(lookup(Arrangement.End)).isEqualTo(ParameterType.String to "End")
        assertThat(lookup(Arrangement.SpaceEvenly)).isEqualTo(ParameterType.String to "SpaceEvenly")
        assertThat(lookup(Arrangement.SpaceBetween))
            .isEqualTo(ParameterType.String to "SpaceBetween")
        assertThat(lookup(Arrangement.SpaceAround)).isEqualTo(ParameterType.String to "SpaceAround")
    }

    @Test
    fun testBaselineShift() {
        assertThat(lookup(BaselineShift.None)).isEqualTo(ParameterType.String to "None")
        assertThat(lookup(BaselineShift.Subscript)).isEqualTo(ParameterType.String to "Subscript")
        assertThat(lookup(BaselineShift.Superscript))
            .isEqualTo(ParameterType.String to "Superscript")
        assertThat(lookup(BaselineShift(0.0f))).isEqualTo(ParameterType.String to "None")
        assertThat(lookup(BaselineShift(-0.5f))).isEqualTo(ParameterType.String to "Subscript")
        assertThat(lookup(BaselineShift(0.5f))).isEqualTo(ParameterType.String to "Superscript")
        assertThat(lookup(BaselineShift(0.1f))).isEqualTo(ParameterType.Float to 0.1f)
        assertThat(lookup(BaselineShift(0.75f))).isEqualTo(ParameterType.Float to 0.75f)
    }

    @Test
    fun testBoolean() {
        assertThat(lookup(true)).isEqualTo(ParameterType.Boolean to true)
        assertThat(lookup(false)).isEqualTo(ParameterType.Boolean to false)
    }

    @Test
    fun testBorder() {
        validate(factory.create(node, "borderstroke", BorderStroke(2.0.dp, Color.Magenta))!!) {
            parameter("borderstroke", ParameterType.String, "BorderStroke") {
                parameter("width", ParameterType.DimensionDp, 2.0f)
                parameter("brush", ParameterType.Color, Color.Magenta.toArgb())
            }
        }
    }

    @Test
    fun testBrush() {
        assertThat(lookup(SolidColor(Color.Red)))
            .isEqualTo(ParameterType.Color to Color.Red.toArgb())
        assertThat(lookup(LinearGradient(listOf(Color.Red, Color.Blue), 0.0f, 0.5f, 5.0f, 10.0f)))
            .isEqualTo(ParameterType.String to "LinearGradient")
        // TODO: add tests for RadialGradient & ShaderBrush
    }

    @Test
    fun testColor() {
        assertThat(lookup(Color.Blue)).isEqualTo(ParameterType.Color to 0xff0000ff.toInt())
        assertThat(lookup(Color.Red)).isEqualTo(ParameterType.Color to 0xffff0000.toInt())
        assertThat(lookup(Color.Transparent)).isEqualTo(ParameterType.Color to 0x00000000)
        assertThat(lookup(Color.Unset)).isEqualTo(ParameterType.String to "Unset")
    }

    @Test
    fun testCornerBasedShape() {
        validate(factory.create(node, "corner",
            RoundedCornerShape(2.0.dp, 0.5.dp, 2.5.dp, 0.7.dp))!!) {
            parameter("corner", ParameterType.String, RoundedCornerShape::class.java.simpleName) {
                parameter("topLeft", ParameterType.DimensionDp, 2.0f)
                parameter("topRight", ParameterType.DimensionDp, 0.5f)
                parameter("bottomLeft", ParameterType.DimensionDp, 0.7f)
                parameter("bottomRight", ParameterType.DimensionDp, 2.5f)
            }
        }
        validate(factory.create(node, "corner", CutCornerShape(2))!!) {
            parameter("corner", ParameterType.String, CutCornerShape::class.java.simpleName) {
                parameter("topLeft", ParameterType.DimensionDp, 5.0f)
                parameter("topRight", ParameterType.DimensionDp, 5.0f)
                parameter("bottomLeft", ParameterType.DimensionDp, 5.0f)
                parameter("bottomRight", ParameterType.DimensionDp, 5.0f)
            }
        }
        validate(factory.create(node, "corner", RoundedCornerShape(1.0f, 10.0f, 2.0f, 3.5f))!!) {
            parameter("corner", ParameterType.String, RoundedCornerShape::class.java.simpleName) {
                parameter("topLeft", ParameterType.DimensionDp, 0.5f)
                parameter("topRight", ParameterType.DimensionDp, 5.0f)
                parameter("bottomLeft", ParameterType.DimensionDp, 1.75f)
                parameter("bottomRight", ParameterType.DimensionDp, 1.0f)
            }
        }
    }

    @Test
    fun testCornerSize() {
        assertThat(lookup(ZeroCornerSize)).isEqualTo(ParameterType.DimensionDp to 0.0f)
        assertThat(lookup(CornerSize(2.4.dp))).isEqualTo(ParameterType.DimensionDp to 2.4f)
        assertThat(lookup(CornerSize(2.4f))).isEqualTo(ParameterType.DimensionDp to 1.2f)
        assertThat(lookup(CornerSize(3))).isEqualTo(ParameterType.DimensionDp to 7.5f)
    }

    @Test
    fun testDouble() {
        assertThat(lookup(3.1428)).isEqualTo(ParameterType.Double to 3.1428)
    }

    @Test
    fun testDp() {
        assertThat(lookup(2.0.dp)).isEqualTo(ParameterType.DimensionDp to 2.0f)
        assertThat(lookup(Dp.Hairline)).isEqualTo(ParameterType.DimensionDp to 0.0f)
        assertThat(lookup(Dp.Unspecified)).isEqualTo(ParameterType.DimensionDp to Float.NaN)
        assertThat(lookup(Dp.Infinity))
            .isEqualTo(ParameterType.DimensionDp to Float.POSITIVE_INFINITY)
    }

    @Test
    fun testEnum() {
        assertThat(lookup(ColorModel.Lab)).isEqualTo(ParameterType.String to "Lab")
        assertThat(lookup(ColorModel.Rgb)).isEqualTo(ParameterType.String to "Rgb")
        assertThat(lookup(ColorModel.Cmyk)).isEqualTo(ParameterType.String to "Cmyk")
    }

    @Test
    fun testFloat() {
        assertThat(lookup(3.1428f)).isEqualTo(ParameterType.Float to 3.1428f)
    }

    @Test
    fun testFontFamily() {
        assertThat(lookup(FontFamily.Cursive)).isEqualTo(ParameterType.String to "Cursive")
        assertThat(lookup(FontFamily.Default)).isEqualTo(ParameterType.String to "Default")
        assertThat(lookup(FontFamily.SansSerif)).isEqualTo(ParameterType.String to "SansSerif")
        assertThat(lookup(FontFamily.Serif)).isEqualTo(ParameterType.String to "Serif")
        assertThat(lookup(FontFamily.Monospace)).isEqualTo(ParameterType.String to "Monospace")
    }

    @Test
    fun testFontListFontFamily() {
        val family = FontListFontFamily(listOf(
            ResourceFont(1234, FontWeight.Normal, FontStyle.Italic),
            ResourceFont(1235, FontWeight.Normal, FontStyle.Normal),
            ResourceFont(1236, FontWeight.Bold, FontStyle.Italic),
            ResourceFont(1237, FontWeight.Bold, FontStyle.Normal)
        ))
        assertThat(lookup(family)).isEqualTo(ParameterType.Resource to 1235)
    }

    @Test
    fun testFontWeight() {
        assertThat(lookup(FontWeight.Thin)).isEqualTo(ParameterType.String to "Thin")
        assertThat(lookup(FontWeight.ExtraLight)).isEqualTo(ParameterType.String to "ExtraLight")
        assertThat(lookup(FontWeight.Light)).isEqualTo(ParameterType.String to "Light")
        assertThat(lookup(FontWeight.Normal)).isEqualTo(ParameterType.String to "Normal")
        assertThat(lookup(FontWeight.Medium)).isEqualTo(ParameterType.String to "Medium")
        assertThat(lookup(FontWeight.SemiBold)).isEqualTo(ParameterType.String to "SemiBold")
        assertThat(lookup(FontWeight.Bold)).isEqualTo(ParameterType.String to "Bold")
        assertThat(lookup(FontWeight.ExtraBold)).isEqualTo(ParameterType.String to "ExtraBold")
        assertThat(lookup(FontWeight.Black)).isEqualTo(ParameterType.String to "Black")
        assertThat(lookup(FontWeight.W100)).isEqualTo(ParameterType.String to "Thin")
        assertThat(lookup(FontWeight.W200)).isEqualTo(ParameterType.String to "ExtraLight")
        assertThat(lookup(FontWeight.W300)).isEqualTo(ParameterType.String to "Light")
        assertThat(lookup(FontWeight.W400)).isEqualTo(ParameterType.String to "Normal")
        assertThat(lookup(FontWeight.W500)).isEqualTo(ParameterType.String to "Medium")
        assertThat(lookup(FontWeight.W600)).isEqualTo(ParameterType.String to "SemiBold")
        assertThat(lookup(FontWeight.W700)).isEqualTo(ParameterType.String to "Bold")
        assertThat(lookup(FontWeight.W800)).isEqualTo(ParameterType.String to "ExtraBold")
        assertThat(lookup(FontWeight.W900)).isEqualTo(ParameterType.String to "Black")
    }

    @Test
    fun testPaddingValues() {
        validate(factory.create(node, "padding", PaddingValues(2.0.dp, 0.5.dp, 2.5.dp, 0.7.dp))!!) {
            parameter("padding", ParameterType.String, PaddingValues::class.java.simpleName) {
                parameter("start", ParameterType.DimensionDp, 2.0f)
                parameter("end", ParameterType.DimensionDp, 2.5f)
                parameter("top", ParameterType.DimensionDp, 0.5f)
                parameter("bottom", ParameterType.DimensionDp, 0.7f)
            }
        }
    }

    @Test
    fun testInt() {
        assertThat(lookup(12345)).isEqualTo(ParameterType.Int32 to 12345)
    }

    @Test
    fun testLocale() {
        assertThat(lookup(Locale("fr-CA"))).isEqualTo(ParameterType.String to "fr-CA")
        assertThat(lookup(Locale("fr-BE"))).isEqualTo(ParameterType.String to "fr-BE")
    }

    @Test
    fun testLocaleList() {
        assertThat(lookup(LocaleList(Locale("fr-ca"), Locale("fr-be"))))
            .isEqualTo(ParameterType.String to "fr-CA, fr-BE")
    }

    @Test
    fun testLong() {
        assertThat(lookup(12345L)).isEqualTo(ParameterType.Int64 to 12345L)
    }

    @Test
    fun testModifier() {
        validate(factory.create(node, "modifier",
            Modifier
                .background(Color.Blue)
                // TODO(b/163494569) uncomment this and code below when bug is fixed
                // .border(width = 5.dp, color = Color.Red)
                .padding(2.0.dp)
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Bottom)
                .preferredWidth(30.0.dp)
                .paint(TestPainter(10f, 20f)))!!) {
            parameter("modifier", ParameterType.String, "") {
                parameter("background", ParameterType.Color, Color.Blue.toArgb()) {
                    parameter("color", ParameterType.Color, Color.Blue.toArgb())
                    parameter("alpha", ParameterType.Float, 1.0f)
                    parameter("shape", ParameterType.String, "Shape")
                }
                // TODO(b/163494569)
                /*parameter("border", ParameterType.Color, Color.Red.toArgb()) {
                    parameter("color", ParameterType.Color, Color.Red.toArgb())
                    parameter("width", ParameterType.DimensionDp, 5.0f)
                    parameter("shape", ParameterType.String, "Shape")
                }*/
                parameter("padding", ParameterType.DimensionDp, 2.0f) {
                    parameter("start", ParameterType.DimensionDp, 2.0f)
                    parameter("top", ParameterType.DimensionDp, 2.0f)
                    parameter("end", ParameterType.DimensionDp, 2.0f)
                    parameter("bottom", ParameterType.DimensionDp, 2.0f)
                }
                parameter("fillMaxWidth", ParameterType.String, "")
                parameter("wrapContentHeight", ParameterType.String, "") {
                    parameter("alignment", ParameterType.String, "Bottom")
                }
                parameter("preferredWidth", ParameterType.DimensionDp, 30.0f) {
                    parameter("width", ParameterType.DimensionDp, 30.0f)
                }
                // TODO: Map Painter, ContentScale, ColorFilter
                parameter("paint", ParameterType.String, "") {
                    parameter("sizeToIntrinsics", ParameterType.Boolean, true)
                    parameter("alignment", ParameterType.String, "Center")
                    parameter("alpha", ParameterType.Float, 1.0f)
                }
            }
        }
    }

    @Test
    fun testSingleModifier() {
        validate(factory.create(node, "modifier", Modifier.padding(2.0.dp))!!) {
            parameter("modifier", ParameterType.String, "") {
                parameter("padding", ParameterType.DimensionDp, 2.0f) {
                    parameter("start", ParameterType.DimensionDp, 2.0f)
                    parameter("top", ParameterType.DimensionDp, 2.0f)
                    parameter("end", ParameterType.DimensionDp, 2.0f)
                    parameter("bottom", ParameterType.DimensionDp, 2.0f)
                }
            }
        }
    }

    @Test
    fun testOffset() {
        validate(factory.create(node, "offset", Offset(1.0f, 5.0f))!!) {
            parameter("offset", ParameterType.String, Offset::class.java.simpleName) {
                parameter("x", ParameterType.DimensionDp, 0.5f)
                parameter("y", ParameterType.DimensionDp, 2.5f)
            }
        }
        validate(factory.create(node, "offset", Offset.Zero)!!) {
            parameter("offset", ParameterType.String, Offset::class.java.simpleName) {
                parameter("x", ParameterType.DimensionDp, 0.0f)
                parameter("y", ParameterType.DimensionDp, 0.0f)
            }
        }
    }

    @Test
    fun testShadow() {
        assertThat(lookup(Shadow.None)).isEqualTo(ParameterType.String to "None")
        validate(factory.create(node, "shadow", Shadow(Color.Cyan, Offset.Zero, 2.5f))!!) {
            parameter("shadow", ParameterType.String, Shadow::class.java.simpleName) {
                parameter("color", ParameterType.Color, Color.Cyan.toArgb())
                parameter("offset", ParameterType.String, Offset::class.java.simpleName) {
                    parameter("x", ParameterType.DimensionDp, 0.0f)
                    parameter("y", ParameterType.DimensionDp, 0.0f)
                }
                parameter("blurRadius", ParameterType.DimensionDp, 1.25f)
            }
        }
        validate(factory.create(node, "shadow", Shadow(Color.Blue, Offset(1.0f, 4.0f), 1.5f))!!) {
            parameter("shadow", ParameterType.String, Shadow::class.java.simpleName) {
                parameter("color", ParameterType.Color, Color.Blue.toArgb())
                parameter("offset", ParameterType.String, Offset::class.java.simpleName) {
                    parameter("x", ParameterType.DimensionDp, 0.5f)
                    parameter("y", ParameterType.DimensionDp, 2.0f)
                }
                parameter("blurRadius", ParameterType.DimensionDp, 0.75f)
            }
        }
    }

    @Test
    fun testShape() {
        assertThat(lookup(RectangleShape)).isEqualTo(ParameterType.String to "Shape")
    }

    @Test
    fun testString() {
        assertThat(lookup("Hello")).isEqualTo(ParameterType.String to "Hello")
    }

    @Test
    fun testTextDecoration() {
        assertThat(lookup(TextDecoration.None)).isEqualTo(ParameterType.String to "None")
        assertThat(lookup(TextDecoration.LineThrough))
            .isEqualTo(ParameterType.String to "LineThrough")
        assertThat(lookup(TextDecoration.Underline))
            .isEqualTo(ParameterType.String to "Underline")

        // TODO: Return a representation of lineThrough & Underline:
        assertThat(lookup(TextDecoration.LineThrough + TextDecoration.Underline)).isNull()
    }

    @Test
    fun testTextGeometricTransform() {
        validate(factory.create(node, "transform", TextGeometricTransform(2.0f, 1.5f))!!) {
            parameter("transform", ParameterType.String,
                TextGeometricTransform::class.java.simpleName) {
                parameter("scaleX", ParameterType.Float, 2.0f)
                parameter("skewX", ParameterType.Float, 1.5f)
            }
        }
    }

    @Test
    fun testTextIndent() {
        assertThat(lookup(TextIndent.None)).isEqualTo(ParameterType.String to "None")

        validate(factory.create(node, "textIndent", TextIndent(4.0.sp, 0.5.sp))!!) {
            parameter("textIndent", ParameterType.String, "TextIndent") {
                parameter("firstLine", ParameterType.DimensionSp, 4.0f)
                parameter("restLine", ParameterType.DimensionSp, 0.5f)
            }
        }
    }

    @Test
    fun testTextStyle() {
        val style = TextStyle(
            color = Color.Red,
            textDecoration = TextDecoration.Underline
        )
        validate(factory.create(node, "style", style)!!) {
            parameter("style", ParameterType.String, TextStyle::class.java.simpleName) {
                parameter("color", ParameterType.Color, Color.Red.toArgb())
                parameter("fontSize", ParameterType.String, "Inherit")
                parameter("letterSpacing", ParameterType.String, "Inherit")
                parameter("background", ParameterType.String, "Unset")
                parameter("textDecoration", ParameterType.String, "Underline")
                parameter("lineHeight", ParameterType.String, "Inherit")
            }
        }
    }

    @Test
    fun testTextUnit() {
        assertThat(lookup(TextUnit.Inherit)).isEqualTo(ParameterType.String to "Inherit")
        assertThat(lookup(12.0.sp)).isEqualTo(ParameterType.DimensionSp to 12.0f)
        assertThat(lookup(2.0.em)).isEqualTo(ParameterType.DimensionEm to 2.0f)
        assertThat(lookup(TextUnit.Sp(9.0f))).isEqualTo(ParameterType.DimensionSp to 9.0f)
        assertThat(lookup(TextUnit.Sp(10))).isEqualTo(ParameterType.DimensionSp to 10.0f)
        assertThat(lookup(TextUnit.Sp(26.0))).isEqualTo(ParameterType.DimensionSp to 26.0f)
        assertThat(lookup(TextUnit.Em(2.0f))).isEqualTo(ParameterType.DimensionEm to 2.0f)
        assertThat(lookup(TextUnit.Em(1))).isEqualTo(ParameterType.DimensionEm to 1.0f)
        assertThat(lookup(TextUnit.Em(3.0))).isEqualTo(ParameterType.DimensionEm to 3.0f)
    }

    private fun lookup(value: Any): Pair<ParameterType, Any?>? {
        val parameter = factory.create(node, "property", value) ?: return null
        assertThat(parameter.elements).isEmpty()
        return Pair(parameter.type, parameter.value)
    }

    private fun validate(
        parameter: NodeParameter,
        expected: ParameterValidationReceiver.() -> Unit = {}
    ) {
        val elements = ParameterValidationReceiver(listOf(parameter).listIterator())
        elements.expected()
    }
}

private class TestPainter(
    val width: Float,
    val height: Float
) : Painter() {

    var color = Color.Red

    override val intrinsicSize: Size
        get() = Size(width, height)

    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
        color = if (layoutDirection == LayoutDirection.Rtl) Color.Blue else Color.Red
        return true
    }

    override fun DrawScope.onDraw() {
        drawRect(color = color)
    }
}

class ParameterValidationReceiver(val parameterIterator: Iterator<NodeParameter>) {
    fun parameter(
        name: String,
        type: ParameterType,
        value: Any?,
        children: ParameterValidationReceiver.() -> Unit = {}
    ) {
        assertWithMessage("No such element found: $name").that(parameterIterator.hasNext()).isTrue()
        val parameter = parameterIterator.next()
        assertThat(parameter.name).isEqualTo(name)
        assertWithMessage(name).that(parameter.type).isEqualTo(type)
        assertWithMessage(name).that(parameter.value).isEqualTo(value)
        val elements = ParameterValidationReceiver(parameter.elements.listIterator())
        elements.children()
        if (elements.parameterIterator.hasNext()) {
            val elementNames = mutableListOf<String>()
            elements.parameterIterator.forEachRemaining { elementNames.add(it.name) }
            error("$name: has more elements like: ${elementNames.joinToString()}")
        }
    }
}
