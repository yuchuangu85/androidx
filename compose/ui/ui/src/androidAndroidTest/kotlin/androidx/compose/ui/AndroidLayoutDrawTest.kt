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

@file:Suppress("Deprecation")

package androidx.compose.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Providers
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.emptyContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.id
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.AndroidOwnerExtraAssertionsRule
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.test.TestActivity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Corresponds to ContainingViewTest, but tests single composition measure, layout and draw.
 * It also tests that layouts with both Layout and MeasureBox work.
 */
@SmallTest
@RunWith(JUnit4::class)
class AndroidLayoutDrawTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val activityTestRule = androidx.test.rule.ActivityTestRule<TestActivity>(
        TestActivity::class.java
    )
    @get:Rule
    val excessiveAssertions = AndroidOwnerExtraAssertionsRule()
    private lateinit var activity: TestActivity
    private lateinit var drawLatch: CountDownLatch
    private lateinit var density: Density

    @Before
    fun setup() {
        activity = activityTestRule.activity
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
        drawLatch = CountDownLatch(1)
        density = Density(activity)
    }

    // Tests that simple drawing works with layered squares
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun simpleDrawTest() {
        val yellow = Color(0xFFFFFF00)
        val red = Color(0xFF800000)
        val model = SquareModel(outerColor = yellow, innerColor = red, size = 10)
        composeSquares(model)

        validateSquareColors(outerColor = yellow, innerColor = red, size = 10)
    }

    // Tests that simple drawing works with draw with nested children
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun nestedDrawTest() {
        val yellow = Color(0xFFFFFF00)
        val red = Color(0xFF800000)
        val model = SquareModel(outerColor = yellow, innerColor = red, size = 10)
        composeNestedSquares(model)

        validateSquareColors(outerColor = yellow, innerColor = red, size = 10)
    }

    // Tests that recomposition works with models used within Draw components
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun recomposeDrawTest() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        composeSquares(model)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        drawLatch = CountDownLatch(1)
        val red = Color(0xFF800000)
        val yellow = Color(0xFFFFFF00)
        activityTestRule.runOnUiThreadIR {
            model.outerColor = red
            model.innerColor = yellow
        }

        validateSquareColors(outerColor = red, innerColor = yellow, size = 10)
    }

    // Tests that recomposition of nested repaint boundaries work
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun recomposeNestedRepaintBoundariesColorChange() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        composeSquaresWithNestedRepaintBoundaries(model)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        drawLatch = CountDownLatch(1)
        val yellow = Color(0xFFFFFF00)
        activityTestRule.runOnUiThreadIR {
            model.innerColor = yellow
        }

        validateSquareColors(outerColor = blue, innerColor = yellow, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun recomposeNestedRepaintBoundariesSizeChange() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        composeSquaresWithNestedRepaintBoundaries(model)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)
        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.size = 20
        }

        validateSquareColors(outerColor = blue, innerColor = white, size = 20)
    }

    // When there is a repaint boundary around a moving child, the child move
    // should be reflected in the repainted bitmap
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun recomposeRepaintBoundariesMove() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        val offset = mutableStateOf(10)
        composeMovingSquaresWithRepaintBoundary(model, offset)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        positionLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            offset.value = 20
        }

        assertTrue(positionLatch!!.await(1, TimeUnit.SECONDS))
        validateSquareColors(outerColor = blue, innerColor = white, offset = 10, size = 10)
    }

    // When there is no repaint boundary around a moving child, the child move
    // should be reflected in the repainted bitmap
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun recomposeMove() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        val offset = mutableStateOf(10)
        composeMovingSquares(model, offset)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            // there isn't going to be a normal draw because we are just moving the repaint
            // boundary, but we should have a draw cycle
            activityTestRule.findAndroidComposeView().viewTreeObserver.addOnDrawListener(object :
                ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    drawLatch.countDown()
                }
            })
            offset.value = 20
        }

        validateSquareColors(outerColor = blue, innerColor = white, offset = 10, size = 10)
    }

    // Tests that recomposition works with models used within Layout components
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun recomposeSizeTest() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        composeSquares(model)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { model.size = 20 }
        validateSquareColors(outerColor = blue, innerColor = white, size = 20)
    }

    // The size and color are both changed in a simpler single-color square.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun simpleSquareColorAndSizeTest() {
        val green = Color(0xFF00FF00)
        val model = SquareModel(size = 20, outerColor = green, innerColor = green)

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Padding(size = (model.size * 3), modifier = fillColor(model, isInner = false)) {
                }
            }
        }
        validateSquareColors(outerColor = green, innerColor = green, size = 20)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.size = 30
        }
        validateSquareColors(outerColor = green, innerColor = green, size = 30)

        drawLatch = CountDownLatch(1)
        val blue = Color(0xFF0000FF)

        activityTestRule.runOnUiThreadIR {
            model.innerColor = blue
            model.outerColor = blue
        }
        validateSquareColors(outerColor = blue, innerColor = blue, size = 30)
    }

    // Components that aren't placed shouldn't be drawn.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun noPlaceNoDraw() {
        val green = Color(0xFF00FF00)
        val white = Color(0xFFFFFFFF)
        val model = SquareModel(size = 20, outerColor = green, innerColor = white)

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(children = {
                    Padding(size = (model.size * 3), modifier = fillColor(model, isInner = false)) {
                    }
                    Padding(size = model.size, modifier = fillColor(model, isInner = true)) {
                    }
                }, measureBlock = { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints) }
                    layout(placeables[0].width, placeables[0].height) {
                        placeables[0].place(0, 0)
                    }
                })
            }
        }
        validateSquareColors(outerColor = green, innerColor = green, size = 20)
    }

    // Make sure that draws intersperse properly with sub-layouts
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawOrderWithChildren() {
        val green = Color(0xFF00FF00)
        val white = Color(0xFFFFFFFF)
        val model = SquareModel(size = 20, outerColor = green, innerColor = white)

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val contentDrawing = object : DrawModifier {
                    override fun ContentDrawScope.draw() {
                        // Fill the space with the outerColor
                        drawRect(model.outerColor)
                        val offset = size.width / 3
                        // clip drawing to the inner rectangle
                        clipRect(offset, offset, offset * 2, offset * 2) {
                            this@draw.drawContent()

                            // Fill bottom half with innerColor -- should be clipped
                            drawRect(
                                model.innerColor,
                                topLeft = Offset(0f, size.height / 2f),
                                size = Size(size.width, size.height / 2f)
                            )
                        }
                    }
                }

                val paddingContent = Modifier.drawBehind {
                    // Fill top half with innerColor -- should be clipped
                    drawLatch.countDown()
                    drawRect(
                        model.innerColor,
                        size = Size(size.width, size.height / 2f)
                    )
                }
                Padding(size = (model.size * 3), modifier = contentDrawing.then(paddingContent)) {
                }
            }
        }
        validateSquareColors(outerColor = green, innerColor = white, size = 20)
    }

    // Tests that calling measure multiple times on the same Measurable causes an exception
    @Test
    fun multipleMeasureCall() {
        val latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                TwoMeasureLayout(50, latch) {
                    AtLeastSize(50) {
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun multiChildLayoutTest() {
        val childrenCount = 3
        val childConstraints = arrayOf(
            Constraints(),
            Constraints.fixedWidth(50),
            Constraints.fixedHeight(50)
        )
        val headerChildrenCount = 1
        val footerChildrenCount = 2

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val header = @Composable {
                    Layout(measureBlock = { _, constraints ->
                        assertEquals(childConstraints[0], constraints)
                        layout(0, 0) {}
                    }, children = emptyContent(), modifier = Modifier.layoutId("header"))
                }
                val footer = @Composable {
                    Layout(measureBlock = { _, constraints ->
                        assertEquals(childConstraints[1], constraints)
                        layout(0, 0) {}
                    }, children = emptyContent(), modifier = Modifier.layoutId("footer"))
                    Layout(measureBlock = { _, constraints ->
                        assertEquals(childConstraints[2], constraints)
                        layout(0, 0) {}
                    }, children = emptyContent(), modifier = Modifier.layoutId("footer"))
                }

                Layout({ header(); footer() }) { measurables, _ ->
                    assertEquals(childrenCount, measurables.size)
                    measurables.forEachIndexed { index, measurable ->
                        measurable.measure(childConstraints[index])
                    }
                    val measurablesHeader = measurables.filter { it.id == "header" }
                    val measurablesFooter = measurables.filter { it.id == "footer" }
                    assertEquals(headerChildrenCount, measurablesHeader.size)
                    assertSame(measurables[0], measurablesHeader[0])
                    assertEquals(footerChildrenCount, measurablesFooter.size)
                    assertSame(measurables[1], measurablesFooter[0])
                    assertSame(measurables[2], measurablesFooter[1])
                    layout(0, 0) {}
                }
            }
        }
    }

    // When a child's measure() is done within the layout, it should not affect the parent's
    // size. The parent's layout shouldn't be called when the child's size changes
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun measureInLayoutDoesNotAffectParentSize() {
        val white = Color(0xFFFFFFFF)
        val blue = Color(0xFF000080)
        val model = SquareModel(outerColor = blue, innerColor = white)
        var measureCalls = 0
        var layoutCalls = 0

        val layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(
                    modifier = Modifier.drawBehind {
                        drawRect(model.outerColor)
                    },
                    children = {
                        AtLeastSize(
                            size = model.size,
                            modifier = Modifier.drawBehind {
                                drawLatch.countDown()
                                drawRect(model.innerColor)
                            }
                        )
                    }, measureBlock = { measurables, constraints ->
                        measureCalls++
                        layout(30, 30) {
                            layoutCalls++
                            layoutLatch.countDown()
                            val placeable = measurables[0].measure(constraints)
                            placeable.place(
                                (30 - placeable.width) / 2,
                                (30 - placeable.height) / 2
                            )
                        }
                    })
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))

        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        layoutCalls = 0
        measureCalls = 0
        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.size = 20
        }

        validateSquareColors(outerColor = blue, innerColor = white, size = 20, totalSize = 30)
        assertEquals(0, measureCalls)
        assertEquals(1, layoutCalls)
    }

    @Test
    fun testLayout_whenMeasuringIsDoneDuringPlacing() {
        @Composable
        fun FixedSizeRow(
            width: Int,
            height: Int,
            children: @Composable () -> Unit
        ) {
            Layout(children = children, measureBlock = { measurables, constraints ->
                val resolvedWidth = constraints.constrainWidth(width)
                val resolvedHeight = constraints.constrainHeight(height)
                layout(resolvedWidth, resolvedHeight) {
                    val childConstraints = Constraints(
                        0,
                        Constraints.Infinity,
                        resolvedHeight,
                        resolvedHeight
                    )
                    var left = 0
                    for (measurable in measurables) {
                        val placeable = measurable.measure(childConstraints)
                        if (left + placeable.width > width) {
                            break
                        }
                        placeable.place(left, 0)
                        left += placeable.width
                    }
                }
            })
        }

        @Composable
        fun FixedWidthBox(
            width: Int,
            measured: Ref<Boolean?>,
            laidOut: Ref<Boolean?>,
            drawn: Ref<Boolean?>,
            latch: CountDownLatch
        ) {
            Layout(
                children = {},
                modifier = Modifier.drawBehind {
                    drawn.value = true
                    latch.countDown()
                },
                measureBlock = { _, constraints ->
                    measured.value = true
                    val resolvedWidth = constraints.constrainWidth(width)
                    val resolvedHeight = constraints.minHeight
                    layout(resolvedWidth, resolvedHeight) { laidOut.value = true }
                })
        }

        val childrenCount = 5
        val measured = Array(childrenCount) { Ref<Boolean?>() }
        val laidOut = Array(childrenCount) { Ref<Boolean?>() }
        val drawn = Array(childrenCount) { Ref<Boolean?>() }
        val latch = CountDownLatch(3)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Align {
                    FixedSizeRow(width = 90, height = 40) {
                        for (i in 0 until childrenCount) {
                            FixedWidthBox(
                                width = 30,
                                measured = measured[i],
                                laidOut = laidOut[i],
                                drawn = drawn[i],
                                latch = latch
                            )
                        }
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        for (i in 0 until childrenCount) {
            assertEquals(i <= 3, measured[i].value ?: false)
            assertEquals(i <= 2, laidOut[i].value ?: false)
            assertEquals(i <= 2, drawn[i].value ?: false)
        }
    }

    // When a new child is added, the parent must be remeasured because we don't know
    // if it affects the size and the child's measure() must be called as well.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testRelayoutOnNewChild() {
        val drawChild = mutableStateOf(false)

        val outerColor = Color(0xFF000080)
        val innerColor = Color(0xFFFFFFFF)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(size = 30, modifier = fillColor(outerColor)) {
                    if (drawChild.value) {
                        Padding(size = 20) {
                            AtLeastSize(size = 20, modifier = fillColor(innerColor)) {
                            }
                        }
                    }
                }
            }
        }

        // The padded area doesn't draw
        validateSquareColors(outerColor = outerColor, innerColor = outerColor, size = 10)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { drawChild.value = true }

        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 20)
    }

    // When we change a position of one LayoutNode up the tree it automatically
    // changes the position of all the children. RepaintBoundary with few intermediate
    // LayoutNode parents should be drawn on a correct position
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun moveRootLayoutRedrawsLeafRepaintBoundary() {
        val offset = mutableStateOf(0)
        drawLatch = CountDownLatch(2)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(
                    modifier = fillColor(Color.Green),
                    children = {
                        AtLeastSize(size = 10) {
                            AtLeastSize(
                                size = 10,
                                modifier = Modifier.drawLayer().then(fillColor(Color.Cyan))
                            ) {
                            }
                        }
                    }
                ) { measurables, constraints ->
                    layout(width = 20, height = 20) {
                        measurables.first().measure(constraints)
                            .place(offset.value, offset.value)
                    }
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        activityTestRule.waitAndScreenShot().apply {
            assertRect(Color.Cyan, size = 10, centerX = 5, centerY = 5)
            assertRect(Color.Green, size = 10, centerX = 15, centerY = 15)
        }

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 10 }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        activityTestRule.waitAndScreenShot().apply {
            assertRect(Color.Green, size = 10, centerX = 5, centerY = 5)
            assertRect(Color.Cyan, size = 10, centerX = 15, centerY = 15)
        }
    }

    // When a child is removed, the parent must be remeasured and redrawn.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testRedrawOnRemovedChild() {
        val drawChild = mutableStateOf(true)

        val outerColor = Color(0xFF000080)
        val innerColor = Color(0xFFFFFFFF)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(size = 30, modifier = Modifier.drawBehind {
                    drawLatch.countDown()
                    drawRect(outerColor)
                }) {
                    AtLeastSize(size = 30) {
                        if (drawChild.value) {
                            Padding(size = 10) {
                                AtLeastSize(
                                    size = 10,
                                    modifier = Modifier.drawBehind {
                                        drawLatch.countDown()
                                        drawRect(innerColor)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 10)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { drawChild.value = false }

        // The padded area doesn't draw
        validateSquareColors(outerColor = outerColor, innerColor = outerColor, size = 10)
    }

    // When a child is removed, the parent must be remeasured.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testRelayoutOnRemovedChild() {
        val drawChild = mutableStateOf(true)

        val outerColor = Color(0xFF000080)
        val innerColor = Color(0xFFFFFFFF)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(size = 30, modifier = Modifier.drawBehind {
                    drawLatch.countDown()
                    drawRect(outerColor)
                }) {
                    Padding(size = 20) {
                        if (drawChild.value) {
                            AtLeastSize(
                                size = 20,
                                modifier = Modifier.drawBehind {
                                    drawLatch.countDown()
                                    drawRect(innerColor)
                                }
                            )
                        }
                    }
                }
            }
        }

        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 20)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { drawChild.value = false }

        // The padded area doesn't draw
        validateSquareColors(outerColor = outerColor, innerColor = outerColor, size = 10)
    }

    @Test
    fun testAlignmentLines() {
        val TestVerticalLine = VerticalAlignmentLine(::min)
        val TestHorizontalLine = HorizontalAlignmentLine(::max)
        val layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child1 = @Composable {
                    Wrap {
                        Layout(children = {}) { _, _ ->
                            layout(
                                0,
                                0,
                                mapOf(
                                    TestVerticalLine to 10,
                                    TestHorizontalLine to 20
                                )
                            ) { }
                        }
                    }
                }
                val child2 = @Composable {
                    Wrap {
                        Layout(children = {}) { _, _ ->
                            layout(
                                0,
                                0,
                                mapOf(
                                    TestVerticalLine to 20,
                                    TestHorizontalLine to 10
                                )
                            ) { }
                        }
                    }
                }
                val inner = @Composable {
                    Layout({ child1(); child2() }) { measurables, constraints ->
                        val placeable1 = measurables[0].measure(constraints)
                        val placeable2 = measurables[1].measure(constraints)
                        assertEquals(10, placeable1[TestVerticalLine])
                        assertEquals(20, placeable1[TestHorizontalLine])
                        assertEquals(20, placeable2[TestVerticalLine])
                        assertEquals(10, placeable2[TestHorizontalLine])
                        layout(0, 0) {
                            placeable1.place(0, 0)
                            placeable2.place(0, 0)
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    assertEquals(10, placeable[TestVerticalLine])
                    assertEquals(20, placeable[TestHorizontalLine])
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testAlignmentLines_areNotInheritedFromInvisibleChildren() {
        val TestLine1 = VerticalAlignmentLine(::min)
        val TestLine2 = VerticalAlignmentLine(::min)
        val layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child1 = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(0, 0, mapOf(TestLine1 to 10)) {}
                    }
                }
                val child2 = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(0, 0, mapOf(TestLine2 to 20)) { }
                    }
                }
                val inner = @Composable {
                    Layout({ child1(); child2() }) { measurables, constraints ->
                        val placeable1 = measurables[0].measure(constraints)
                        measurables[1].measure(constraints)
                        layout(0, 0) {
                            // Only place the first child.
                            placeable1.place(0, 0)
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    assertEquals(10, placeable[TestLine1])
                    assertEquals(AlignmentLine.Unspecified, placeable[TestLine2])
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testAlignmentLines_doNotCauseMultipleMeasuresOrLayouts() {
        val TestLine1 = VerticalAlignmentLine(::min)
        val TestLine2 = VerticalAlignmentLine(::min)
        var child1Measures = 0
        var child2Measures = 0
        var child1Layouts = 0
        var child2Layouts = 0
        val layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child1 = @Composable {
                    Layout(children = {}) { _, _ ->
                        ++child1Measures
                        layout(0, 0, mapOf(TestLine1 to 10)) {
                            ++child1Layouts
                        }
                    }
                }
                val child2 = @Composable {
                    Layout(children = {}) { _, _ ->
                        ++child2Measures
                        layout(0, 0, mapOf(TestLine2 to 20)) {
                            ++child2Layouts
                        }
                    }
                }
                val inner = @Composable {
                    Layout({ child1(); child2() }) { measurables, constraints ->
                        val placeable1 = measurables[0].measure(constraints)
                        val placeable2 = measurables[1].measure(constraints)
                        layout(0, 0) {
                            placeable1.place(0, 0)
                            placeable2.place(0, 0)
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    assertEquals(10, placeable[TestLine1])
                    assertEquals(20, placeable[TestLine2])
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, child1Measures)
        assertEquals(1, child2Measures)
        assertEquals(1, child1Layouts)
        assertEquals(1, child2Layouts)
    }

    @Test
    fun testAlignmentLines_onlyLayoutEarlyWhenNeeded() {
        val TestLine1 = VerticalAlignmentLine(::min)
        val TestLine2 = VerticalAlignmentLine(::min)
        var child1Measures = 0
        var child2Measures = 0
        var child1Layouts = 0
        var child2Layouts = 0
        val layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child1 = @Composable {
                    Layout(children = {}) { _, _ ->
                        ++child1Measures
                        layout(0, 0, mapOf(TestLine1 to 10)) {
                            ++child1Layouts
                        }
                    }
                }
                val child2 = @Composable {
                    Layout(children = {}) { _, _ ->
                        ++child2Measures
                        layout(0, 0, mapOf(TestLine2 to 20)) {
                            ++child2Layouts
                        }
                    }
                }
                val inner = @Composable {
                    Layout({ child1(); child2() }) { measurables, constraints ->
                        val placeable1 = measurables[0].measure(constraints)
                        assertEquals(10, placeable1[TestLine1])
                        val placeable2 = measurables[1].measure(constraints)
                        layout(0, 0) {
                            placeable1.place(0, 0)
                            placeable2.place(0, 0)
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, child1Measures)
        assertEquals(1, child2Measures)
        assertEquals(1, child1Layouts)
        assertEquals(0, child2Layouts)
    }

    @Test
    fun testAlignmentLines_canBeQueriedInThePositioningBlock() {
        val TestLine = VerticalAlignmentLine(::min)
        val layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child1 = @Composable {
                    Layout(children = { }) { _, _ ->
                        layout(0, 0, mapOf(TestLine to 10)) { }
                    }
                }
                val child2 = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(
                            0,
                            0,
                            mapOf(TestLine to 20)
                        ) { }
                    }
                }
                val inner = @Composable {
                    Layout({ child1(); child2() }) { measurables, constraints ->
                        val placeable1 = measurables[0].measure(constraints)
                        layout(0, 0) {
                            assertEquals(10, placeable1[TestLine])
                            val placeable2 = measurables[1].measure(constraints)
                            assertEquals(20, placeable2[TestLine])
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testAlignmentLines_doNotCauseExtraLayout_whenQueriedAfterPositioning() {
        val TestLine = VerticalAlignmentLine(::min)
        val layoutLatch = CountDownLatch(1)
        var childLayouts = 0
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = { }) { _, _ ->
                        layout(0, 0, mapOf(TestLine to 10)) {
                            ++childLayouts
                        }
                    }
                }
                val inner = @Composable {
                    Layout({ child() }) { measurables, constraints ->
                        val placeable = measurables[0].measure(constraints)
                        layout(0, 0) {
                            assertEquals(10, placeable[TestLine])
                            placeable.place(0, 0)
                            assertEquals(10, placeable[TestLine])
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, childLayouts)
    }

    @Test
    fun testAlignmentLines_recomposeCorrectly() {
        val TestLine = VerticalAlignmentLine(::min)
        var layoutLatch = CountDownLatch(1)
        val offset = mutableStateOf(10)
        var measure = 0
        var layout = 0
        var linePosition: Int? = null
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(0, 0, mapOf(TestLine to offset.value)) {}
                    }
                }
                Layout(child) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    linePosition = placeable[TestLine]
                    ++measure
                    layout(placeable.width, placeable.height) {
                        ++layout
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, measure)
        assertEquals(1, layout)
        assertEquals(10, linePosition)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            offset.value = 20
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(2, measure)
        assertEquals(2, layout)
        assertEquals(20, linePosition)
    }

    @Test
    fun testAlignmentLines_recomposeCorrectly_whenQueriedInLayout() {
        val TestLine = VerticalAlignmentLine(::min)
        var layoutLatch = CountDownLatch(1)
        val offset = mutableStateOf(10)
        var measure = 0
        var layout = 0
        var linePosition: Int? = null
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(
                            0,
                            0,
                            mapOf(TestLine to offset.value)
                        ) {}
                    }
                }
                Layout(child) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    ++measure
                    layout(placeable.width, placeable.height) {
                        linePosition = placeable[TestLine]
                        ++layout
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, measure)
        assertEquals(1, layout)
        assertEquals(10, linePosition)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 20 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, measure)
        assertEquals(2, layout)
        assertEquals(20, linePosition)
    }

    @Test
    fun testAlignmentLines_recomposeCorrectly_whenMeasuredAndQueriedInLayout() {
        val TestLine = VerticalAlignmentLine(::min)
        var layoutLatch = CountDownLatch(1)
        val offset = mutableStateOf(10)
        var measure = 0
        var layout = 0
        var linePosition: Int? = null
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(0, 0, mapOf(TestLine to offset.value)) { }
                    }
                }
                Layout(child) { measurables, constraints ->
                    ++measure
                    layout(1, 1) {
                        val placeable = measurables.first().measure(constraints)
                        linePosition = placeable[TestLine]
                        ++layout
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, measure)
        assertEquals(1, layout)
        assertEquals(10, linePosition)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 20 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, measure)
        assertEquals(2, layout)
        assertEquals(20, linePosition)
    }

    @Test
    fun testAlignmentLines_onlyComputesAlignmentLinesWhenNeeded() {
        var layoutLatch = CountDownLatch(1)
        val offset = mutableStateOf(10)
        var alignmentLinesCalculations = 0
        val TestLine = VerticalAlignmentLine { _, _ ->
            ++alignmentLinesCalculations
            0
        }
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val innerChild = @Composable {
                    offset.value // Artificial remeasure.
                    Layout(children = {}) { _, _ ->
                        layout(0, 0, mapOf(TestLine to 10)) { }
                    }
                }
                val child = @Composable {
                    Layout({ innerChild(); innerChild() }) { measurables, constraints ->
                        offset.value // Artificial remeasure.
                        val placeable1 = measurables[0].measure(constraints)
                        val placeable2 = measurables[1].measure(constraints)
                        layout(0, 0) {
                            placeable1.place(0, 0)
                            placeable2.place(0, 0)
                        }
                    }
                }
                Layout(child) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    if (offset.value < 15) {
                        placeable[TestLine]
                    }
                    layout(0, 0) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, alignmentLinesCalculations)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 20 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, alignmentLinesCalculations)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 10 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(2, alignmentLinesCalculations)
    }

    @Test
    fun testAlignmentLines_providedLinesOverrideInherited() {
        val layoutLatch = CountDownLatch(1)
        val TestLine = VerticalAlignmentLine(::min)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val innerChild = @Composable {
                    Layout(children = {}) { _, _ ->
                        layout(0, 0, mapOf(TestLine to 10)) { }
                    }
                }
                val child = @Composable {
                    Layout({ innerChild() }) { measurables, constraints ->
                        val placeable = measurables.first().measure(constraints)
                        layout(0, 0, mapOf(TestLine to 20)) {
                            placeable.place(0, 0)
                        }
                    }
                }
                Layout(child) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    assertEquals(20, placeable[TestLine])
                    layout(0, 0) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testAlignmentLines_areRecalculatedCorrectlyOnRelayout_withNoRemeasure() {
        val TestLine = VerticalAlignmentLine(::min)
        var layoutLatch = CountDownLatch(1)
        var innerChildMeasures = 0
        var innerChildLayouts = 0
        var outerChildMeasures = 0
        var outerChildLayouts = 0
        val offset = mutableStateOf(0)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = {}) { _, _ ->
                        ++innerChildMeasures
                        layout(0, 0, mapOf(TestLine to 10)) { ++innerChildLayouts }
                    }
                }
                val inner = @Composable {
                    Layout({ Wrap { Wrap { child() } } }) { measurables, constraints ->
                        ++outerChildMeasures
                        val placeable = measurables[0].measure(constraints)
                        layout(0, 0) {
                            ++outerChildLayouts
                            placeable.place(offset.value, 0)
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    val width = placeable.width.coerceAtLeast(10)
                    val height = placeable.height.coerceAtLeast(10)
                    layout(width, height) {
                        assertEquals(offset.value + 10, placeable[TestLine])
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, innerChildMeasures)
        assertEquals(1, innerChildLayouts)
        assertEquals(1, outerChildMeasures)
        assertEquals(1, outerChildLayouts)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            offset.value = 10
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, innerChildMeasures)
        assertEquals(1, innerChildLayouts)
        assertEquals(1, outerChildMeasures)
        assertEquals(2, outerChildLayouts)
    }

    @Test
    fun testAlignmentLines_whenQueriedAfterPlacing() {
        val TestLine = VerticalAlignmentLine(::min)
        val layoutLatch = CountDownLatch(1)
        var childLayouts = 0
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = {}) { _, constraints ->
                        layout(
                            constraints.minWidth,
                            constraints.minHeight,
                            mapOf(TestLine to 10)
                        ) { ++childLayouts }
                    }
                }
                val inner = @Composable {
                    Layout({ Wrap { Wrap { child() } } }) { measurables, constraints ->
                        val placeable = measurables[0].measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                            assertEquals(10, placeable[TestLine])
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        // Two layouts as the alignment line was only queried after the child was placed.
        assertEquals(2, childLayouts)
    }

    @Test
    fun testAlignmentLines_whenQueriedAfterPlacing_haveCorrectNumberOfLayouts() {
        val TestLine = VerticalAlignmentLine(::min)
        var layoutLatch = CountDownLatch(1)
        var childLayouts = 0
        val offset = mutableStateOf(10)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Layout(children = {}) { _, constraints ->
                        layout(
                            constraints.minWidth,
                            constraints.minHeight,
                            mapOf(TestLine to 10)
                        ) {
                            offset.value // To ensure relayout.
                            ++childLayouts
                        }
                    }
                }
                val inner = @Composable {
                    Layout({
                        WrapForceRelayout(offset) { child() }
                    }) { measurables, constraints ->
                        val placeable = measurables[0].measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (offset.value > 15) assertEquals(10, placeable[TestLine])
                            placeable.place(0, 0)
                            if (offset.value > 5) assertEquals(10, placeable[TestLine])
                        }
                    }
                }
                Layout(inner) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    val width = placeable.width.coerceAtLeast(10)
                    val height = placeable.height.coerceAtLeast(10)
                    layout(width, height) {
                        offset.value // To ensure relayout.
                        placeable.place(0, 0)
                        layoutLatch.countDown()
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        // Two layouts as the alignment line was only queried after the child was placed.
        assertEquals(2, childLayouts)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 12 }
        assertTrue(layoutLatch.await(5, TimeUnit.SECONDS))
        // Just one more layout as the alignment lines were speculatively calculated this time.
        assertEquals(3, childLayouts)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 17 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        // One layout as the alignment lines are queried before.
        assertEquals(4, childLayouts)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 12 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        // One layout as the alignment lines are still calculated speculatively.
        assertEquals(5, childLayouts)

        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 1 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertEquals(6, childLayouts)
        layoutLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR { offset.value = 10 }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        // Two layouts again, since alignment lines were not queried during last layout,
        // so we did not calculate them speculatively anymore.
        assertEquals(8, childLayouts)
    }

    @Test
    fun testAlignmentLines_readFromModifier_duringMeasurement() = with(density) {
        val testVerticalLine = VerticalAlignmentLine(::min)
        val testHorizontalLine = HorizontalAlignmentLine(::max)

        val assertLines: Modifier.(Int, Int) -> Modifier = { vertical, horizontal ->
            this.then(object : LayoutModifier {
                override fun MeasureScope.measure(
                    measurable: Measurable,
                    constraints: Constraints
                ): MeasureScope.MeasureResult {
                    val placeable = measurable.measure(constraints)
                    assertEquals(vertical, placeable[testVerticalLine])
                    assertEquals(horizontal, placeable[testHorizontalLine])
                    return layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
            })
        }

        testAlignmentLinesReads(testVerticalLine, testHorizontalLine, assertLines)
    }

    @Test
    fun testAlignmentLines_readFromModifier_duringPositioning_before() = with(density) {
        val testVerticalLine = VerticalAlignmentLine(::min)
        val testHorizontalLine = HorizontalAlignmentLine(::max)

        val assertLines: Modifier.(Int, Int) -> Modifier = { vertical, horizontal ->
            this.then(object : LayoutModifier {
                override fun MeasureScope.measure(
                    measurable: Measurable,
                    constraints: Constraints
                ): MeasureScope.MeasureResult {
                    val placeable = measurable.measure(constraints)
                    return layout(placeable.width, placeable.height) {
                        assertEquals(vertical, placeable[testVerticalLine])
                        assertEquals(horizontal, placeable[testHorizontalLine])
                        placeable.place(0, 0)
                    }
                }
            })
        }

        testAlignmentLinesReads(testVerticalLine, testHorizontalLine, assertLines)
    }

    @Test
    fun testAlignmentLines_readFromModifier_duringPositioning_after() = with(density) {
        val testVerticalLine = VerticalAlignmentLine(::min)
        val testHorizontalLine = HorizontalAlignmentLine(::max)

        val assertLines: Modifier.(Int, Int) -> Modifier = { vertical, horizontal ->
            this.then(object : LayoutModifier {
                override fun MeasureScope.measure(
                    measurable: Measurable,
                    constraints: Constraints
                ): MeasureScope.MeasureResult {
                    val placeable = measurable.measure(constraints)
                    return layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                        assertEquals(vertical, placeable[testVerticalLine])
                        assertEquals(horizontal, placeable[testHorizontalLine])
                    }
                }
            })
        }

        testAlignmentLinesReads(testVerticalLine, testHorizontalLine, assertLines)
    }

    private fun Density.testAlignmentLinesReads(
        testVerticalLine: VerticalAlignmentLine,
        testHorizontalLine: HorizontalAlignmentLine,
        assertLines: Modifier.(Int, Int) -> Modifier
    ) {
        val layoutLatch = CountDownLatch(7)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val layout = @Composable { modifier: Modifier ->
                    Layout(modifier = modifier, children = {}) { _, _ ->
                        layout(
                            0,
                            0,
                            mapOf(
                                testVerticalLine to 10,
                                testHorizontalLine to 20
                            )
                        ) {
                            layoutLatch.countDown()
                        }
                    }
                }

                layout(Modifier.assertLines(10, 20))
                layout(Modifier.assertLines(30, 30).offset(20.toDp(), 10.toDp()))
                layout(
                    Modifier
                        .assertLines(30, 30)
                        .drawLayer()
                        .offset(20.toDp(), 10.toDp())
                )
                layout(
                    Modifier
                        .assertLines(30, 30)
                        .background(Color.Blue)
                        .drawLayer()
                        .offset(20.toDp(), 10.toDp())
                        .drawLayer()
                        .background(Color.Blue)
                )
                layout(
                    Modifier
                        .background(Color.Blue)
                        .assertLines(30, 30)
                        .background(Color.Blue)
                        .drawLayer()
                        .offset(20.toDp(), 10.toDp())
                        .drawLayer()
                        .background(Color.Blue)
                )
                Wrap(
                    Modifier
                        .background(Color.Blue)
                        .assertLines(30, 30)
                        .background(Color.Blue)
                        .drawLayer()
                        .offset(20.toDp(), 10.toDp())
                        .drawLayer()
                        .background(Color.Blue)
                ) {
                    layout(Modifier)
                }
                Wrap(
                    Modifier
                        .background(Color.Blue)
                        .assertLines(40, 50)
                        .background(Color.Blue)
                        .drawLayer()
                        .offset(20.toDp(), 10.toDp())
                        .drawLayer()
                        .background(Color.Blue)
                ) {
                    layout(Modifier.offset(10.toDp(), 20.toDp()))
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testLayoutBeforeDraw_forRecomposingNodesNotAffectingRootSize() {
        val offset = mutableStateOf(0)
        var latch = CountDownLatch(1)
        var laidOut = false
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val container = @Composable { children: @Composable () -> Unit ->
                    // This simulates a Container optimisation, when the child does not
                    // affect parent size.
                    Layout(children) { measurables, constraints ->
                        layout(30, 30) {
                            measurables[0].measure(constraints).place(0, 0)
                        }
                    }
                }
                val recomposingChild = @Composable { children: @Composable (Int) -> Unit ->
                    // This simulates a child that recomposes, for example due to a transition.
                    children(offset.value)
                }
                val assumeLayoutBeforeDraw = @Composable { _: Int ->
                    // This assumes a layout was done before the draw pass.
                    Layout(
                        children = {},
                        modifier = Modifier.drawBehind {
                            assertTrue(laidOut)
                            latch.countDown()
                        }
                    ) { _, _ ->
                        laidOut = true
                        layout(0, 0) {}
                    }
                }

                container {
                    recomposingChild {
                        assumeLayoutBeforeDraw(it)
                    }
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            offset.value = 10
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testDrawWithLayoutNotPlaced() {
        val latch = CountDownLatch(1)
        var drawn = false
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(children = {
                    AtLeastSize(30, modifier = Modifier.drawBehind { drawn = true })
                }, modifier = Modifier.drawLatchModifier()) { _, _ ->
                    // don't measure or place the AtLeastSize
                    latch.countDown()
                    layout(20, 20) {}
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        activityTestRule.runOnUiThreadIR {
            assertFalse(drawn)
        }
    }

    /**
     * Because we use invalidate() to cause relayout when children
     * are laid out, we want to ensure that when the View is 0-sized
     * that it gets a relayout when it needs to change to non-0
     */
    @Test
    fun testZeroSizeCanRelayout() {
        var latch = CountDownLatch(1)
        val model = SquareModel(size = 0)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(children = { }) { _, _ ->
                    latch.countDown()
                    layout(model.size, model.size) {}
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.size = 10
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testZeroSizeCanRelayout_child() {
        var latch = CountDownLatch(1)
        val model = SquareModel(size = 0)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(children = {
                    Layout(children = emptyContent()) { _, _ ->
                        latch.countDown()
                        layout(model.size, model.size) {}
                    }
                }) { measurables, constraints ->
                    val placeable = measurables[0].measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.size = 10
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testZeroSizeCanRelayout_childRepaintBoundary() {
        var latch = CountDownLatch(1)
        val model = SquareModel(size = 0)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(children = {
                    Layout(modifier = Modifier.drawLayer(), children = emptyContent()) { _, _ ->
                        latch.countDown()
                        layout(model.size, model.size) {}
                    }
                }) { measurables, constraints ->
                    val placeable = measurables[0].measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.size = 10
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun parentSizeForDrawIsProvidedWithoutPadding() {
        val latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val drawnContent = Modifier.drawBehind {
                    assertEquals(100.0f, size.width)
                    assertEquals(100.0f, size.height)
                    latch.countDown()
                }
                AtLeastSize(100, PaddingModifier(10).then(drawnContent)) {
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun parentSizeForDrawInsideRepaintBoundaryIsProvidedWithoutPadding() {
        val latch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    100,
                    PaddingModifier(10).drawLayer()
                        .drawBehind {
                            assertEquals(100.0f, size.width)
                            assertEquals(100.0f, size.height)
                            latch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun alignmentLinesInheritedCorrectlyByParents_withModifiedPosition() {
        val testLine = HorizontalAlignmentLine(::min)
        val latch = CountDownLatch(1)
        val alignmentLinePosition = 10
        val padding = 20
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val child = @Composable {
                    Wrap {
                        Layout(children = {}, modifier = PaddingModifier(padding)) { _, _ ->
                            layout(0, 0, mapOf(testLine to alignmentLinePosition)) { }
                        }
                    }
                }

                Layout(child) { measurables, constraints ->
                    assertEquals(
                        padding + alignmentLinePosition,
                        measurables[0].measure(constraints)[testLine]
                    )
                    latch.countDown()
                    layout(0, 0) { }
                }
            }
        }
    }

    @Test
    fun modifiers_validateCorrectSizes() {
        val layoutModifier = object : LayoutModifier {
            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints
            ): MeasureScope.MeasureResult {
                val placeable = measurable.measure(constraints)
                return layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
        }
        val parentDataModifier = object : ParentDataModifier {
            override fun Density.modifyParentData(parentData: Any?) = parentData
        }
        val size = 50

        val latch = CountDownLatch(2)
        val childSizes = arrayOfNulls<IntSize>(2)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(
                    children = {
                        FixedSize(size, layoutModifier)
                        FixedSize(size, parentDataModifier)
                    },
                    measureBlock = { measurables, constraints ->
                        for (i in 0 until measurables.size) {
                            val child = measurables[i]
                            val placeable = child.measure(constraints)
                            childSizes[i] = IntSize(placeable.width, placeable.height)
                            latch.countDown()
                        }
                        layout(0, 0) { }
                    }
                )
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(IntSize(size, size), childSizes[0])
        assertEquals(IntSize(size, size), childSizes[1])
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_drawPositioning() {
        val outerColor = Color.Blue
        val innerColor = Color.White
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(30, Modifier.background(outerColor)) {
                    FixedSize(
                        10,
                        PaddingModifier(10).background(innerColor).drawLatchModifier()
                    )
                }
            }
        }
        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 10)
    }

    @Test
    fun drawModifier_testLayoutDirection() {
        val drawLatch = CountDownLatch(1)
        val layoutDirection = Ref<LayoutDirection>()
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    FixedSize(
                        size = 50,
                        modifier = Modifier.drawBehind {
                            layoutDirection.value = this.layoutDirection
                            drawLatch.countDown()
                        }
                    )
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertEquals(LayoutDirection.Rtl, layoutDirection.value)
    }

    @Test
    fun layoutModifier_testLayoutDirection() {
        val latch = CountDownLatch(1)
        val layoutDirection = Ref<LayoutDirection>()

        val layoutModifier = object : LayoutModifier {
            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints
            ): MeasureScope.MeasureResult {
                layoutDirection.value = this.layoutDirection
                latch.countDown()
                return layout(0, 0) {}
            }
        }
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    FixedSize(
                        size = 50,
                        modifier = layoutModifier
                    )
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(LayoutDirection.Rtl, layoutDirection.value)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_modelChangesOnRoot() {
        val model = SquareModel(innerColor = Color.White, outerColor = Color.Green)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(30, Modifier.background(model, false)) {
                    FixedSize(
                        10,
                        PaddingModifier(10).background(model, true).drawLatchModifier()
                    )
                }
            }
        }
        validateSquareColors(outerColor = Color.Green, innerColor = Color.White, size = 10)
        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.innerColor = Color.Yellow
        }
        validateSquareColors(outerColor = Color.Green, innerColor = Color.Yellow, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_modelChangesOnRepaintBoundary() {
        val model = SquareModel(innerColor = Color.White, outerColor = Color.Green)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(30, Modifier.background(Color.Green)) {
                    FixedSize(
                        10,
                        Modifier.drawLayer()
                            .then(PaddingModifier(10))
                            .background(model, true)
                            .drawLatchModifier()
                    )
                }
            }
        }
        validateSquareColors(outerColor = Color.Green, innerColor = Color.White, size = 10)
        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            model.innerColor = Color.Yellow
        }
        validateSquareColors(outerColor = Color.Green, innerColor = Color.Yellow, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_oneModifier() {
        val outerColor = Color.Blue
        val innerColor = Color.White
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val colorModifier = Modifier.drawBehind {
                    drawRect(outerColor)
                    drawRect(
                        innerColor,
                        topLeft = Offset(10f, 10f),
                        size = Size(10f, 10f)
                    )
                    drawLatch.countDown()
                }
                FixedSize(30, colorModifier)
            }
        }
        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_nestedModifiers() {
        val outerColor = Color.Blue
        val innerColor = Color.White
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val countDownModifier = Modifier.drawBehind {
                    drawLatch.countDown()
                }
                FixedSize(30, countDownModifier.background(color = outerColor)) {
                    Padding(10) {
                        FixedSize(10, Modifier.background(color = innerColor))
                    }
                }
            }
        }
        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_withLayoutModifier() {
        val outerColor = Color.Blue
        val innerColor = Color.White
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(30, Modifier.background(color = outerColor)) {
                    FixedSize(
                        size = 10,
                        modifier = PaddingModifier(10)
                            .background(color = innerColor)
                            .drawLatchModifier()
                    )
                }
            }
        }
        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun drawModifier_withLayout() {
        val outerColor = Color.Blue
        val innerColor = Color.White
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val drawAndOffset = Modifier.drawWithContent {
                    drawRect(outerColor)
                    translate(10f, 10f) {
                        this@drawWithContent.drawContent()
                    }
                }
                FixedSize(30, drawAndOffset) {
                    FixedSize(
                        size = 10,
                        modifier = AlignTopLeft.background(innerColor).drawLatchModifier()
                    )
                }
            }
        }
        validateSquareColors(outerColor = outerColor, innerColor = innerColor, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun layoutModifier_redrawsCorrectlyWhenOnlyNonModifiedSizeChanges() {
        val blue = Color(0xFF000080)
        val green = Color(0xFF00FF00)
        val offset = mutableStateOf(10)

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(30, modifier = Modifier.drawBehind {
                    drawRect(green)
                }) {
                    FixedSize(
                        offset.value,
                        modifier = AlignTopLeft.drawLayer()
                            .drawBehind {
                                drawLatch.countDown()
                                drawRect(blue)
                            }
                    ) {
                    }
                }
            }
        }
        validateSquareColors(outerColor = green, innerColor = blue, size = 10, offset = -10)

        drawLatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            offset.value = 20
        }
        validateSquareColors(
            outerColor = green,
            innerColor = blue,
            size = 20,
            offset = -5,
            totalSize = 30
        )
    }

    @Test
    fun layoutModifier_convenienceApi() {
        val size = 100
        val offset = 15f
        val latch = CountDownLatch(1)
        var resultCoordinates: LayoutCoordinates? = null

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(
                    size = size,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.place(Offset(offset, offset))
                            }
                        }.onPositioned {
                            resultCoordinates = it
                            latch.countDown()
                        }
                )
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        activity.runOnUiThread {
            assertEquals(size, resultCoordinates?.size?.height)
            assertEquals(size, resultCoordinates?.size?.width)
            assertEquals(Offset(offset, offset), resultCoordinates?.positionInRoot)
        }
    }

    @Test
    fun layoutModifier_convenienceApi_equivalent() {
        val size = 100
        val offset = 15f
        val latch = CountDownLatch(2)

        var convenienceCoordinates: LayoutCoordinates? = null
        var coordinates: LayoutCoordinates? = null

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(
                    size = size,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.place(Offset(offset, offset))
                            }
                        }.onPositioned {
                            convenienceCoordinates = it
                            latch.countDown()
                        }
                )

                val layoutModifier = object : LayoutModifier {
                    override fun MeasureScope.measure(
                        measurable: Measurable,
                        constraints: Constraints
                    ): MeasureScope.MeasureResult {
                        val placeable = measurable.measure(constraints)
                        return layout(placeable.width, placeable.height) {
                            placeable.place(Offset(offset, offset))
                        }
                    }
                }
                FixedSize(
                    size = size,
                    modifier = layoutModifier.plus(
                        onPositioned {
                            coordinates = it
                            latch.countDown()
                        }
                    )
                )
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        activity.runOnUiThread {
            assertEquals(coordinates?.size?.height, convenienceCoordinates?.size?.height)
            assertEquals(coordinates?.size?.width, convenienceCoordinates?.size?.width)
            assertEquals(coordinates?.positionInRoot, convenienceCoordinates?.positionInRoot)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun modifier_combinedModifiers() {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(30, Modifier.background(Color.Blue).drawLatchModifier()) {
                    JustConstraints(LayoutAndDrawModifier(Color.White)) {
                    }
                }
            }
        }
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.White, size = 10)
    }

    // Tests that show layout bounds draws outlines around content and modifiers
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    @OptIn(InternalCoreApi::class)
    fun showLayoutBounds_content() {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                FixedSize(size = 30, modifier = Modifier.background(Color.White)) {
                    FixedSize(
                        size = 10,
                        modifier = PaddingModifier(5)
                            .then(PaddingModifier(5))
                            .drawLatchModifier()
                    )
                }
            }
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val owner = content.getChildAt(0) as Owner
            owner.showLayoutBounds = true
        }
        activityTestRule.waitAndScreenShot().apply {
            assertRect(Color.White, size = 8)
            assertRect(Color.Red, size = 10, holeSize = 8)
            assertRect(Color.White, size = 18, holeSize = 10)
            assertRect(Color.Blue, size = 20, holeSize = 18)
            assertRect(Color.White, size = 28, holeSize = 20)
            assertRect(Color.Red, size = 30, holeSize = 28)
        }
    }

    @Test
    fun requestRemeasureForAlreadyMeasuredChildWhileTheParentIsStillMeasuring() {
        val drawlatch = CountDownLatch(1)
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Layout(children = {
                    val state = remember { mutableStateOf(false) }
                    var lastLayoutValue: Boolean = false
                    Layout(children = {}, modifier = Modifier.drawBehind {
                        // this verifies the layout was remeasured before being drawn
                        assertTrue(lastLayoutValue)
                        drawlatch.countDown()
                    }) { _, _ ->
                        lastLayoutValue = state.value
                        // this registers the value read
                        if (!state.value) {
                            // change the value right inside the measure block
                            // it will cause one more remeasure pass as we also read this value
                            state.value = true
                        }
                        layout(100, 100) {}
                    }
                    FixedSize(30, children = emptyContent())
                }) { measurables, constraints ->
                    val (first, second) = measurables
                    val firstPlaceable = first.measure(constraints)
                    // switch frame, as inside the measure block we changed the model value
                    // this will trigger requestRemeasure on this first layout
                    @OptIn(ExperimentalComposeApi::class)
                    Snapshot.sendApplyNotifications()
                    val secondPlaceable = second.measure(constraints)
                    layout(30, 30) {
                        firstPlaceable.place(0, 0)
                        secondPlaceable.place(0, 0)
                    }
                }
            }
        }
        assertTrue(drawlatch.await(1, TimeUnit.SECONDS))
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun layerModifier_scaleDraw() {
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(
                    size = 30,
                    modifier = Modifier.background(Color.Blue)
                ) {
                    FixedSize(
                        size = 20,
                        modifier = AlignTopLeft
                            .then(PaddingModifier(5))
                            .scale(0.5f)
                            .background(Color.Red)
                            .latch(drawLatch)
                    ) {}
                }
            }
        }
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.Red, size = 10)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun layerModifier_scaleChange() {
        val scale = mutableStateOf(1f)
        val layerModifier = object : DrawLayerModifier {
            override val scaleX: Float get() = scale.value
            override val scaleY: Float get() = scale.value
        }
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(
                    size = 30,
                    modifier = Modifier.background(Color.Blue)
                ) {
                    FixedSize(
                        size = 10,
                        modifier = PaddingModifier(10)
                            .then(layerModifier)
                            .background(Color.Red)
                            .latch(drawLatch)
                    ) {}
                }
            }
        }
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.Red, size = 10)

        activityTestRule.runOnUiThread {
            scale.value = 2f
        }

        activityTestRule.waitAndScreenShot().apply {
            assertRect(Color.Red, size = 20, centerX = 15, centerY = 15)
        }
    }

    // Test that when no clip to outline is set that it still draws properly.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun layerModifier_noClip() {
        val triangleShape = object : Shape {
            override fun createOutline(size: Size, density: Density): Outline =
                Outline.Generic(
                    Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                )
        }
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(
                    size = 30
                ) {
                    FixedSize(
                        size = 10,
                        modifier = PaddingModifier(10)
                            .drawLayer(shape = triangleShape)
                            .drawBehind {
                                drawRect(
                                    Color.Blue,
                                    topLeft = Offset(-10f, -10f),
                                    size = Size(30.0f, 30.0f)
                                )
                            }
                            .background(Color.Red)
                            .latch(drawLatch)
                    ) {}
                }
            }
        }
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.Red, size = 10)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun testInvalidationMultipleLayers() {
        val innerColor = mutableStateOf(Color.Red)
        activityTestRule.runOnUiThread {
            activity.setContent {
                val children: @Composable () -> Unit = remember {
                    @Composable {
                        FixedSize(
                            size = 10,
                            modifier = Modifier.drawLayer()
                                .then(PaddingModifier(10))
                                .background(innerColor.value)
                                .latch(drawLatch)
                        ) {}
                    }
                }
                FixedSize(
                    size = 30,
                    modifier = Modifier.drawLayer().background(Color.Blue)
                ) {
                    FixedSize(
                        size = 30,
                        modifier = Modifier.drawLayer(),
                        children = children
                    )
                }
            }
        }
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.Red, size = 10)

        drawLatch = CountDownLatch(1)

        activityTestRule.runOnUiThread {
            innerColor.value = Color.White
        }

        validateSquareColors(outerColor = Color.Blue, innerColor = Color.White, size = 10)
    }

    @Test
    fun doubleDraw() {
        val offset = mutableStateOf(0)
        var outerLatch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(
                    30,
                    Modifier.drawBehind { outerLatch.countDown() }.drawLayer()
                ) {
                    FixedSize(10, Modifier.drawBehind {
                        drawLine(
                            Color.Blue,
                            Offset(offset.value.toFloat(), 0f),
                            Offset(0f, offset.value.toFloat()),
                            strokeWidth = Stroke.HairlineWidth
                        )
                        drawLatch.countDown()
                    })
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(outerLatch.await(1, TimeUnit.SECONDS))

        activityTestRule.runOnUiThread {
            drawLatch = CountDownLatch(1)
            outerLatch = CountDownLatch(1)
            offset.value = 10
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertFalse(outerLatch.await(200, TimeUnit.MILLISECONDS))
    }

    // When a child with a layer is removed with its children, it shouldn't crash.
    @Test
    fun detachChildWithLayer() {
        activityTestRule.runOnUiThread {
            val composition = activity.setContent {
                FixedSize(10, Modifier.drawLayer()) {
                    FixedSize(8)
                }
            }
            composition.dispose()
        }
    }

    // When a layer moves, it should redraw properly
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun drawOnLayerMove() {
        val offset = mutableStateOf(10)
        var placeLatch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            activity.setContent {
                val yellowSquare = @Composable {
                    FixedSize(
                        10, Modifier.drawLayer().background(Color.Yellow).drawLatchModifier()
                    ) {
                    }
                }
                Layout(
                    modifier = Modifier.background(Color.Red),
                    children = yellowSquare
                ) { measurables, _ ->
                    val childConstraints = Constraints.fixed(10, 10)
                    val p = measurables[0].measure(childConstraints)
                    layout(30, 30) {
                        p.place(offset.value, offset.value)
                        placeLatch.countDown()
                    }
                }
            }
        }

        validateSquareColors(outerColor = Color.Red, innerColor = Color.Yellow, size = 10)

        placeLatch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            offset.value = 5
        }

        // Wait for layout to complete
        assertTrue(placeLatch.await(1, TimeUnit.SECONDS))

        activityTestRule.runOnUiThread {
        }

        activityTestRule.waitAndScreenShot(forceInvalidate = false).apply {
            // just test that it is red around the Yellow
            assertRect(Color.Red, size = 20, centerX = 10, centerY = 10, holeSize = 10)
            // now test that it is red in the lower-right
            assertRect(Color.Red, size = 10, centerX = 25, centerY = 25)
            assertRect(Color.Yellow, size = 10, centerX = 10, centerY = 10)
        }
    }

    // When a layer property changes, it should redraw properly
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun drawOnLayerPropertyChange() {
        val offset = mutableStateOf(0f)
        var translationLatch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(30, Modifier.background(Color.Red).drawLatchModifier()) {
                    FixedSize(
                        10,
                        PaddingModifier(10).then(
                            object : DrawLayerModifier {
                                override val translationX: Float
                                    get() {
                                        translationLatch.countDown()
                                        return offset.value
                                    }
                                override val translationY: Float
                                    get() = offset.value
                            }
                        ).background(Color.Yellow)
                    ) {
                    }
                }
            }
        }

        validateSquareColors(outerColor = Color.Red, innerColor = Color.Yellow, size = 10)

        translationLatch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            offset.value = -5f
        }
        // Wait for translation to complete
        assertTrue(translationLatch.await(1, TimeUnit.SECONDS))

        activityTestRule.runOnUiThread {
        }

        activityTestRule.waitAndScreenShot(forceInvalidate = false).apply {
            // just test that it is red around the Yellow
            assertRect(Color.Red, size = 20, centerX = 10, centerY = 10, holeSize = 10)
            // now test that it is red in the lower-right
            assertRect(Color.Red, size = 10, centerX = 25, centerY = 25)
            assertRect(Color.Yellow, size = 10, centerX = 10, centerY = 10)
        }
    }

    // Delegates don't change when the modifier types remain the same
    @Test
    fun instancesKeepDelegates() {
        var color by mutableStateOf(Color.Red)
        var m: Measurable? = null
        val layoutCaptureModifier = object : LayoutModifier {
            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints
            ): MeasureScope.MeasureResult {
                m = measurable
                val p = measurable.measure(constraints)
                drawLatch.countDown()
                return layout(p.width, p.height) {
                    p.place(0, 0)
                }
            }
        }
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(30, layoutCaptureModifier.background(color)) {}
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        var firstMeasurable = m
        drawLatch = CountDownLatch(1)

        activityTestRule.runOnUiThread {
            m = null
            color = Color.Blue
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertNotNull(m)
        assertSame(firstMeasurable, m)
    }

    // LayoutNodeWrappers remain even when there are multiple for a modifier
    @Test
    fun replaceMultiImplementationModifier() {
        var color by mutableStateOf(Color.Red)
        var m: Measurable? = null

        var layerLatch = CountDownLatch(1)

        class SpecialModifier(
            val drawLatch: CountDownLatch,
            val layerLatch: CountDownLatch
        ) : DrawModifier, DrawLayerModifier {
            override fun ContentDrawScope.draw() {
                drawContent()
                drawLatch.countDown()
            }

            override val translationX: Float
                get() {
                    layerLatch.countDown()
                    return super.translationX
                }
        }

        val layoutCaptureModifier = object : LayoutModifier {
            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints
            ): MeasureScope.MeasureResult {
                m = measurable
                val p = measurable.measure(constraints)
                return layout(p.width, p.height) {
                    p.place(0, 0)
                }
            }
        }
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(30,
                    layoutCaptureModifier
                        .then(SpecialModifier(drawLatch, layerLatch))
                        .background(color)
                ) {}
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(layerLatch.await(1, TimeUnit.SECONDS))
        var firstMeasurable = m
        drawLatch = CountDownLatch(1)
        layerLatch = CountDownLatch(1)

        activityTestRule.runOnUiThread {
            m = null
            color = Color.Blue
        }

        // The latches are triggered in the new instance
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(layerLatch.await(1, TimeUnit.SECONDS))
        // The new instance's measurable is the same.
        assertNotNull(m)
        assertSame(firstMeasurable, m)
    }

    // When some content is drawn on the parent's layer through a modifier, when the modifier
    // changes, it should invalidate the parent layer, not layer of the LayoutNode.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun invalidateParentLayer() {
        var color by mutableStateOf(Color.Red)
        activityTestRule.runOnUiThread {
            activity.setContent {
                FixedSize(
                    size = 10,
                    modifier = Modifier.background(color = color).drawLatchModifier().then(
                        PaddingModifier(10)
                            .drawLayer()
                            .background(Color.White)
                    )
                )
            }
        }

        validateSquareColors(outerColor = Color.Red, innerColor = Color.White, size = 10)
        drawLatch = CountDownLatch(1)
        color = Color.Blue
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.White, size = 10)
    }

    // When zindex has changed, the parent should be invalidated, even if all drawing is done
    // within a modifier layer.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun invalidateParentLayerZIndex() {
        var zIndex by mutableStateOf(0f)
        activityTestRule.runOnUiThread {
            activity.setContent {
                with(DensityAmbient.current) {
                    FixedSize(
                        size = 30,
                        modifier = Modifier.background(color = Color.Blue).drawLatchModifier()
                    ) {
                        FixedSize(
                            size = 10,
                            modifier = Modifier
                                .drawLayer()
                                .zIndex(zIndex)
                                .padding(10.toDp())
                                .background(Color.White)
                        )
                        FixedSize(
                            size = 10,
                            modifier = Modifier
                                .drawLayer()
                                .zIndex(0f)
                                .padding(10.toDp())
                                .background(Color.Yellow)
                        )
                    }
                }
            }
        }

        validateSquareColors(outerColor = Color.Blue, innerColor = Color.Yellow, size = 10)
        drawLatch = CountDownLatch(1)
        zIndex = 1f
        validateSquareColors(outerColor = Color.Blue, innerColor = Color.White, size = 10)
    }

    @Test
    fun makingItemLarger() {
        var height by mutableStateOf(30)
        var actualHeight = 0
        var latch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            val linearLayout = LinearLayout(activity)
            linearLayout.orientation = LinearLayout.VERTICAL
            val child = FrameLayout(activity)
            activity.setContentView(linearLayout)
            linearLayout.addView(
                child, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            linearLayout.addView(
                View(activity), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    10000f
                )
            )
            child.viewTreeObserver.addOnPreDrawListener {
                actualHeight = child.measuredHeight
                latch.countDown()
                true
            }
            child.setContent {
                Layout({}) { _, constraints ->
                    layout(constraints.maxWidth, height.coerceAtMost(constraints.maxHeight)) {}
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        latch = CountDownLatch(1)

        activityTestRule.runOnUiThread {
            assertEquals(height, actualHeight)
            height = 60
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        activityTestRule.runOnUiThread {
            assertEquals(height, actualHeight)
        }
    }

    private fun composeSquares(model: SquareModel) {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Padding(size = model.size, modifier = Modifier.drawBehind {
                    drawRect(model.outerColor)
                }) {
                    AtLeastSize(size = model.size, modifier = Modifier.drawBehind {
                        drawLatch.countDown()
                        drawRect(model.innerColor)
                    })
                }
            }
        }
    }

    private fun composeSquaresWithNestedRepaintBoundaries(model: SquareModel) {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Padding(
                    size = model.size,
                    modifier = fillColor(model, isInner = false, doCountDown = false).drawLayer()
                ) {
                    AtLeastSize(
                        size = model.size,
                        modifier = Modifier.drawLayer().then(fillColor(model, isInner = true))
                    ) {
                    }
                }
            }
        }
    }

    private fun composeMovingSquaresWithRepaintBoundary(model: SquareModel, offset: State<Int>) {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Position(
                    size = model.size * 3,
                    offset = offset,
                    modifier = fillColor(model, isInner = false, doCountDown = false)
                ) {
                    AtLeastSize(
                        size = model.size,
                        modifier = Modifier.drawLayer().then(fillColor(model, isInner = true))
                    ) {
                    }
                }
            }
        }
    }

    private fun composeMovingSquares(model: SquareModel, offset: State<Int>) {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                Position(
                    size = model.size * 3,
                    offset = offset,
                    modifier = fillColor(model, isInner = false, doCountDown = false)
                ) {
                    AtLeastSize(size = model.size, modifier = fillColor(model, isInner = true)) {
                    }
                }
            }
        }
    }

    private fun composeNestedSquares(model: SquareModel) {
        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                val fillColorModifier = Modifier.drawBehind {
                    drawRect(model.innerColor)
                    drawLatch.countDown()
                }
                val innerDrawWithContentModifier = Modifier.drawWithContent {
                    drawRect(model.outerColor)
                    val start = model.size.toFloat()
                    val end = start * 2
                    clipRect(start, start, end, end) {
                        this@drawWithContent.drawContent()
                    }
                }
                AtLeastSize(size = (model.size * 3), modifier = innerDrawWithContentModifier) {
                    AtLeastSize(size = (model.size * 3), modifier = fillColorModifier)
                }
            }
        }
    }

    private fun validateSquareColors(
        outerColor: Color,
        innerColor: Color,
        size: Int,
        offset: Int = 0,
        totalSize: Int = size * 3
    ) {
        activityTestRule.validateSquareColors(
            drawLatch,
            outerColor,
            innerColor,
            size,
            offset,
            totalSize
        )
    }

    @Composable
    private fun fillColor(color: Color, doCountDown: Boolean = true): Modifier =
        Modifier.drawBehind {
            drawRect(color)
            if (doCountDown) {
                drawLatch.countDown()
            }
        }

    @Composable
    private fun fillColor(
        squareModel: SquareModel,
        isInner: Boolean,
        doCountDown: Boolean = true
    ): Modifier = Modifier.drawBehind {
        drawRect(if (isInner) squareModel.innerColor else squareModel.outerColor)
        if (doCountDown) {
            drawLatch.countDown()
        }
    }

    private var positionLatch: CountDownLatch? = null

    @Composable
    fun Position(
        size: Int,
        offset: State<Int>,
        modifier: Modifier = Modifier,
        children: @Composable () -> Unit
    ) {
        Layout(modifier = modifier, children = children) { measurables, constraints ->
            val placeables = measurables.map { m ->
                m.measure(constraints)
            }
            layout(size, size) {
                placeables.forEach { child ->
                    child.place(offset.value, offset.value)
                }
                positionLatch?.countDown()
            }
        }
    }

    fun Modifier.drawLatchModifier() = drawBehind { drawLatch.countDown() }
}

fun Bitmap.assertRect(
    color: Color,
    holeSize: Int = 0,
    size: Int = width,
    centerX: Int = width / 2,
    centerY: Int = height / 2
) {
    assertTrue(centerX + size / 2 <= width)
    assertTrue(centerX - size / 2 >= 0)
    assertTrue(centerY + size / 2 <= height)
    assertTrue(centerY - size / 2 >= 0)
    val halfHoleSize = holeSize / 2
    for (x in centerX - size / 2 until centerX + size / 2) {
        for (y in centerY - size / 2 until centerY + size / 2) {
            if (abs(x - centerX) > halfHoleSize &&
                abs(y - centerY) > halfHoleSize
            ) {
                val currentColor = Color(getPixel(x, y))
                assertColorsEqual(color, currentColor)
            }
        }
    }
}

@Suppress("DEPRECATION")
fun androidx.test.rule.ActivityTestRule<*>.validateSquareColors(
    drawLatch: CountDownLatch,
    outerColor: Color,
    innerColor: Color,
    size: Int,
    offset: Int = 0,
    totalSize: Int = size * 3
) {
    assertTrue("drawLatch timed out", drawLatch.await(1, TimeUnit.SECONDS))
    val bitmap = waitAndScreenShot()
    assertEquals(totalSize, bitmap.width)
    assertEquals(totalSize, bitmap.height)
    val squareStart = (totalSize - size) / 2 + offset
    val squareEnd = totalSize - ((totalSize - size) / 2) + offset
    for (x in 0 until totalSize) {
        for (y in 0 until totalSize) {
            val pixel = Color(bitmap.getPixel(x, y))
            val expected =
                if (!(x < squareStart || x >= squareEnd || y < squareStart || y >= squareEnd)) {
                    innerColor
                } else {
                    outerColor
                }
            assertColorsEqual(expected, pixel) {
                "Pixel within drawn rect[$x, $y] is $expected, but was $pixel"
            }
        }
    }
}

fun assertColorsEqual(
    expected: Color,
    color: Color,
    error: () -> String = { "$expected and $color are not similar!" }
) {
    val errorString = error()
    assertEquals(errorString, expected.red, color.red, 0.01f)
    assertEquals(errorString, expected.green, color.green, 0.01f)
    assertEquals(errorString, expected.blue, color.blue, 0.01f)
    assertEquals(errorString, expected.alpha, color.alpha, 0.01f)
}

@Composable
fun AtLeastSize(
    size: Int,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit = emptyContent()
) {
    Layout(
        measureBlock = { measurables, constraints ->
            val newConstraints = Constraints(
                minWidth = max(size, constraints.minWidth),
                maxWidth = if (constraints.hasBoundedWidth) {
                    max(size, constraints.maxWidth)
                } else {
                    Constraints.Infinity
                },
                minHeight = max(size, constraints.minHeight),
                maxHeight = if (constraints.hasBoundedHeight) {
                    max(size, constraints.maxHeight)
                } else {
                    Constraints.Infinity
                }
            )
            val placeables = measurables.map { m ->
                m.measure(newConstraints)
            }
            var maxWidth = size
            var maxHeight = size
            placeables.forEach { child ->
                maxHeight = max(child.height, maxHeight)
                maxWidth = max(child.width, maxWidth)
            }
            layout(maxWidth, maxHeight) {
                placeables.forEach { child ->
                    child.place(0, 0)
                }
            }
        },
        modifier = modifier,
        children = children
    )
}

@Composable
fun FixedSize(
    size: Int,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit = emptyContent()
) {
    Layout(children = children, modifier = modifier) { measurables, _ ->
        val newConstraints = Constraints.fixed(size, size)
        val placeables = measurables.map { m ->
            m.measure(newConstraints)
        }
        layout(size, size) {
            placeables.forEach { child ->
                child.placeRelative(0, 0)
            }
        }
    }
}

@Composable
fun Align(modifier: Modifier = Modifier.None, children: @Composable () -> Unit) {
    Layout(
        modifier = modifier,
        measureBlock = { measurables, constraints ->
            val newConstraints = Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight
            )
            val placeables = measurables.map { m ->
                m.measure(newConstraints)
            }
            var maxWidth = constraints.minWidth
            var maxHeight = constraints.minHeight
            placeables.forEach { child ->
                maxHeight = max(child.height, maxHeight)
                maxWidth = max(child.width, maxWidth)
            }
            layout(maxWidth, maxHeight) {
                placeables.forEach { child ->
                    child.placeRelative(0, 0)
                }
            }
        }, children = children
    )
}

@Composable
internal fun Padding(
    size: Int,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        measureBlock = { measurables, constraints ->
            val totalDiff = size * 2
            val targetMinWidth = constraints.minWidth - totalDiff
            val targetMaxWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth - totalDiff
            } else {
                Constraints.Infinity
            }
            val targetMinHeight = constraints.minHeight - totalDiff
            val targetMaxHeight = if (constraints.hasBoundedHeight) {
                constraints.maxHeight - totalDiff
            } else {
                Constraints.Infinity
            }
            val newConstraints = Constraints(
                minWidth = targetMinWidth.coerceAtLeast(0),
                maxWidth = targetMaxWidth.coerceAtLeast(0),
                minHeight = targetMinHeight.coerceAtLeast(0),
                maxHeight = targetMaxHeight.coerceAtLeast(0)
            )
            val placeables = measurables.map { m ->
                m.measure(newConstraints)
            }
            var maxWidth = size
            var maxHeight = size
            placeables.forEach { child ->
                maxHeight = max(child.height + totalDiff, maxHeight)
                maxWidth = max(child.width + totalDiff, maxWidth)
            }
            layout(maxWidth, maxHeight) {
                placeables.forEach { child ->
                    child.placeRelative(size, size)
                }
            }
        }, children = children
    )
}

@Composable
fun TwoMeasureLayout(
    size: Int,
    latch: CountDownLatch,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit
) {
    Layout(modifier = modifier, children = children) { measurables, _ ->
        val testConstraints = Constraints()
        measurables.forEach { it.measure(testConstraints) }
        val childConstraints = Constraints.fixed(size, size)
        try {
            val placeables2 = measurables.map { it.measure(childConstraints) }
            fail("Measuring twice on the same Measurable should throw an exception")
            layout(size, size) {
                placeables2.forEach { child ->
                    child.placeRelative(0, 0)
                }
            }
        } catch (_: IllegalStateException) {
            // expected
            latch.countDown()
        }
        layout(0, 0) { }
    }
}

@Composable
fun Wrap(
    modifier: Modifier = Modifier,
    minWidth: Int = 0,
    minHeight: Int = 0,
    children: @Composable () -> Unit = {}
) {
    Layout(modifier = modifier, children = children) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = max(placeables.maxBy { it.width }?.width ?: 0, minWidth)
        val height = max(placeables.maxBy { it.height }?.height ?: 0, minHeight)
        layout(width, height) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
fun Scroller(
    modifier: Modifier = Modifier,
    onScrollPositionChanged: (position: Int, maxPosition: Int) -> Unit,
    offset: State<Int>,
    child: @Composable () -> Unit
) {
    val maxPosition = remember { mutableStateOf(Constraints.Infinity) }
    ScrollerLayout(
        modifier = modifier,
        maxPosition = maxPosition.value,
        onMaxPositionChanged = {
            maxPosition.value = 0
            onScrollPositionChanged(offset.value, 0)
        },
        child = child
    )
}

@Composable
private fun ScrollerLayout(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") maxPosition: Int,
    onMaxPositionChanged: () -> Unit,
    child: @Composable () -> Unit
) {
    Layout(modifier = modifier, children = child) { measurables, constraints ->
        val childConstraints = constraints.copy(
            maxHeight = constraints.maxHeight,
            maxWidth = Constraints.Infinity
        )
        val childMeasurable = measurables.first()
        val placeable = childMeasurable.measure(childConstraints)
        val width = min(placeable.width, constraints.maxWidth)
        layout(width, placeable.height) {
            onMaxPositionChanged()
            placeable.placeRelative(0, 0)
        }
    }
}

@Composable
fun WrapForceRelayout(
    model: State<Int>,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit
) {
    Layout(modifier = modifier, children = children) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxBy { it.width }?.width ?: 0
        val height = placeables.maxBy { it.height }?.height ?: 0
        layout(width, height) {
            model.value
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
fun SimpleRow(modifier: Modifier = Modifier, children: @Composable () -> Unit) {
    Layout(modifier = modifier, children = children) { measurables, constraints ->
        var width = 0
        var height = 0
        val placeables = measurables.map {
            it.measure(constraints.copy(maxWidth = constraints.maxWidth - width)).also {
                width += it.width
                height = max(height, it.height)
            }
        }
        layout(width, height) {
            var currentWidth = 0
            placeables.forEach {
                it.placeRelative(currentWidth, 0)
                currentWidth += it.width
            }
        }
    }
}

@Composable
fun JustConstraints(modifier: Modifier, children: @Composable () -> Unit) {
    Layout(children, modifier) { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }
}

class DrawCounterListener(private val view: View) :
    ViewTreeObserver.OnPreDrawListener {
    val latch = CountDownLatch(5)

    override fun onPreDraw(): Boolean {
        latch.countDown()
        if (latch.count > 0) {
            view.postInvalidate()
        } else {
            view.viewTreeObserver.removeOnPreDrawListener(this)
        }
        return true
    }
}

fun PaddingModifier(padding: Int) = PaddingModifier(padding, padding, padding, padding)

data class PaddingModifier(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(
            constraints.offset(
                horizontal = -left - right,
                vertical = -top - bottom
            )
        )
        return layout(
            constraints.constrainWidth(left + placeable.width + right),
            constraints.constrainHeight(top + placeable.height + bottom)
        ) {
            placeable.placeRelative(left, top)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = measurable.minIntrinsicWidth((height - (top + bottom)).coerceAtLeast(0)) +
            (left + right)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = measurable.maxIntrinsicWidth((height - (top + bottom)).coerceAtLeast(0)) +
            (left + right)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int = measurable.minIntrinsicHeight((width - (left + right)).coerceAtLeast(0)) +
            (top + bottom)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int = measurable.maxIntrinsicHeight((width - (left + right)).coerceAtLeast(0)) +
            (top + bottom)
}

internal val AlignTopLeft = object : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        return layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}

@Stable
class SquareModel(
    size: Int = 10,
    outerColor: Color = Color(0xFF000080),
    innerColor: Color = Color(0xFFFFFFFF)
) {
    var size: Int by mutableStateOf(size)
    var outerColor: Color by mutableStateOf(outerColor)
    var innerColor: Color by mutableStateOf(innerColor)
}

@Suppress("DEPRECATION")
// We only need this because IR compiler doesn't like converting lambdas to Runnables
fun androidx.test.rule.ActivityTestRule<*>.runOnUiThreadIR(block: () -> Unit) {
    val runnable: Runnable = object : Runnable {
        override fun run() {
            block()
        }
    }
    runOnUiThread(runnable)
}

@Suppress("DEPRECATION")
fun androidx.test.rule.ActivityTestRule<*>.findAndroidComposeView(): ViewGroup {
    val contentViewGroup = activity.findViewById<ViewGroup>(android.R.id.content)
    return findAndroidComposeView(contentViewGroup)!!
}

fun findAndroidComposeView(parent: ViewGroup): ViewGroup? {
    for (index in 0 until parent.childCount) {
        val child = parent.getChildAt(index)
        if (child is ViewGroup) {
            if (child is Owner)
                return child
            else {
                val composeView = findAndroidComposeView(child)
                if (composeView != null) {
                    return composeView
                }
            }
        }
    }
    return null
}

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
fun androidx.test.rule.ActivityTestRule<*>.waitAndScreenShot(
    forceInvalidate: Boolean = true
): Bitmap {
    val view = findAndroidComposeView()
    val flushListener = DrawCounterListener(view)
    val offset = intArrayOf(0, 0)
    var handler: Handler? = null
    runOnUiThread {
        view.getLocationInWindow(offset)
        if (forceInvalidate) {
            view.viewTreeObserver.addOnPreDrawListener(flushListener)
            view.invalidate()
        }
        handler = Handler(Looper.getMainLooper())
    }

    if (forceInvalidate) {
        assertTrue("Drawing latch timed out", flushListener.latch.await(1, TimeUnit.SECONDS))
    }
    val width = view.width
    val height = view.height

    val dest =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val srcRect = android.graphics.Rect(0, 0, width, height)
    srcRect.offset(offset[0], offset[1])
    val latch = CountDownLatch(1)
    var copyResult = 0
    val onCopyFinished = object : PixelCopy.OnPixelCopyFinishedListener {
        override fun onPixelCopyFinished(result: Int) {
            copyResult = result
            latch.countDown()
        }
    }
    PixelCopy.request(activity.window, srcRect, dest, onCopyFinished, handler!!)
    assertTrue("Pixel copy latch timed out", latch.await(1, TimeUnit.SECONDS))
    assertEquals(PixelCopy.SUCCESS, copyResult)
    return dest
}

fun Modifier.background(color: Color) = drawBehind {
    drawRect(color)
}

fun Modifier.background(model: SquareModel, isInner: Boolean) = drawBehind {
    drawRect(if (isInner) model.innerColor else model.outerColor)
}

class LayoutAndDrawModifier(val color: Color) : LayoutModifier, DrawModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(Constraints.fixed(10, 10))
        return layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight - placeable.height) / 2
            )
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(color)
    }
}

fun Modifier.scale(scale: Float) = then(LayoutScale(scale))
    .drawLayer(scaleX = scale, scaleY = scale)

class LayoutScale(val scale: Float) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(
            Constraints(
                minWidth = (constraints.minWidth / scale).roundToInt(),
                minHeight = (constraints.minHeight / scale).roundToInt(),
                maxWidth = (constraints.maxWidth / scale).roundToInt(),
                maxHeight = (constraints.maxHeight / scale).roundToInt()
            )
        )
        return layout(
            (placeable.width * scale).roundToInt(),
            (placeable.height * scale).roundToInt()
        ) {
            placeable.placeRelative(0, 0)
        }
    }
}

fun Modifier.latch(countDownLatch: CountDownLatch) = drawBehind {
    countDownLatch.countDown()
}
