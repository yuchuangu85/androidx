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

package androidx.compose.foundation.layout

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Alignment
import androidx.compose.ui.AlignmentLine
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.Placeable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.node.Ref
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.AndroidOwner
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.enforce
import androidx.compose.ui.unit.hasFixedHeight
import androidx.compose.ui.unit.hasFixedWidth
import androidx.compose.ui.unit.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

open class LayoutTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val activityTestRule = androidx.test.rule.ActivityTestRule<TestActivity>(
        TestActivity::class.java
    )
    lateinit var activity: TestActivity
    lateinit var handler: Handler
    internal lateinit var density: Density

    @Before
    fun setup() {
        activity = activityTestRule.activity
        density = Density(activity)
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)

        // Kotlin IR compiler doesn't seem too happy with auto-conversion from
        // lambda to Runnable, so separate it here
        val runnable: Runnable = object : Runnable {
            override fun run() {
                handler = Handler(Looper.getMainLooper())
            }
        }
        activityTestRule.runOnUiThread(runnable)
    }

    internal fun show(composable: @Composable () -> Unit) {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                activity.setContent(Recomposer.current(), composable)
            }
        }
        activityTestRule.runOnUiThread(runnable)
    }

    internal fun findOwnerView(): View {
        return findOwner(activity).view
    }

    internal fun findOwner(activity: Activity): AndroidOwner {
        val contentViewGroup = activity.findViewById<ViewGroup>(android.R.id.content)
        return findOwner(contentViewGroup)!!
    }

    internal fun findOwner(parent: ViewGroup): AndroidOwner? {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child is AndroidOwner) {
                return child
            } else if (child is ViewGroup) {
                val owner = findOwner(child)
                if (owner != null) {
                    return owner
                }
            }
        }
        return null
    }

    internal fun waitForDraw(view: View) {
        val viewDrawLatch = CountDownLatch(1)
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                viewDrawLatch.countDown()
            }
        }
        view.post(object : Runnable {
            override fun run() {
                view.viewTreeObserver.addOnDrawListener(listener)
                view.invalidate()
            }
        })
        assertTrue(viewDrawLatch.await(1, TimeUnit.SECONDS))
    }

    internal fun Modifier.saveLayoutInfo(
        size: Ref<IntSize>,
        position: Ref<Offset>,
        positionedLatch: CountDownLatch
    ): Modifier = this.onPositioned { coordinates ->
        size.value = IntSize(coordinates.size.width, coordinates.size.height)
        position.value = coordinates.localToRoot(Offset(0f, 0f))
        positionedLatch.countDown()
    }

    internal fun testIntrinsics(
        vararg layouts: @Composable () -> Unit,
        test: ((Int) -> Int, (Int) -> Int, (Int) -> Int, (Int) -> Int) -> Unit
    ) {
        layouts.forEach { layout ->
            val layoutLatch = CountDownLatch(1)
            show {
                Layout(
                    layout,
                    minIntrinsicWidthMeasureBlock = { _, _ -> 0 },
                    minIntrinsicHeightMeasureBlock = { _, _ -> 0 },
                    maxIntrinsicWidthMeasureBlock = { _, _ -> 0 },
                    maxIntrinsicHeightMeasureBlock = { _, _ -> 0 }
                ) { measurables, _ ->
                    val measurable = measurables.first()
                    test(
                        { h -> measurable.minIntrinsicWidth(h) },
                        { w -> measurable.minIntrinsicHeight(w) },
                        { h -> measurable.maxIntrinsicWidth(h) },
                        { w -> measurable.maxIntrinsicHeight(w) }
                    )
                    layoutLatch.countDown()
                    layout(0, 0) {}
                }
            }
            assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        }
    }

    @Composable
    internal fun FixedSizeLayout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int>
    ) {
        Layout({}) { _, constraints ->
            layout(
                constraints.constrainWidth(width),
                constraints.constrainHeight(height),
                alignmentLines
            ) {}
        }
    }

    @Composable
    internal fun WithInfiniteConstraints(children: @Composable () -> Unit) {
        Layout(children) { measurables, _ ->
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(0, 0) {
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }
    }

    @Composable
    internal fun ConstrainedBox(
        constraints: DpConstraints,
        modifier: Modifier = Modifier,
        children: @Composable () -> Unit
    ) {
        with(DensityAmbient.current) {
            val pxConstraints = Constraints(constraints)
            Layout(
                children,
                modifier = modifier,
                minIntrinsicWidthMeasureBlock = { measurables, h ->
                    val width = measurables.firstOrNull()?.minIntrinsicWidth(h) ?: 0
                    pxConstraints.constrainWidth(width)
                },
                minIntrinsicHeightMeasureBlock = { measurables, w ->
                    val height = measurables.firstOrNull()?.minIntrinsicHeight(w) ?: 0
                    pxConstraints.constrainHeight(height)
                },
                maxIntrinsicWidthMeasureBlock = { measurables, h ->
                    val width = measurables.firstOrNull()?.maxIntrinsicWidth(h) ?: 0
                    pxConstraints.constrainWidth(width)
                },
                maxIntrinsicHeightMeasureBlock = { measurables, w ->
                    val height = measurables.firstOrNull()?.maxIntrinsicHeight(w) ?: 0
                    pxConstraints.constrainHeight(height)
                }
            ) { measurables, incomingConstraints ->
                val measurable = measurables.firstOrNull()
                val childConstraints = Constraints(constraints).enforce(incomingConstraints)
                val placeable = measurable?.measure(childConstraints)

                val layoutWidth = placeable?.width ?: childConstraints.minWidth
                val layoutHeight = placeable?.height ?: childConstraints.minHeight
                layout(layoutWidth, layoutHeight) {
                    placeable?.placeRelative(0, 0)
                }
            }
        }
    }

    internal fun assertEquals(expected: Size?, actual: Size?) {
        assertNotNull("Null expected size", expected)
        expected as Size
        assertNotNull("Null actual size", actual)
        actual as Size

        assertEquals(
            "Expected width ${expected.width} but obtained ${actual.width}",
            expected.width,
            actual.width,
            0f
        )
        assertEquals(
            "Expected height ${expected.height} but obtained ${actual.height}",
            expected.height,
            actual.height,
            0f
        )
        if (actual.width != actual.width.toInt().toFloat()) {
            fail("Expected integer width")
        }
        if (actual.height != actual.height.toInt().toFloat()) {
            fail("Expected integer height")
        }
    }

    internal fun assertEquals(expected: Offset?, actual: Offset?) {
        assertNotNull("Null expected position", expected)
        expected as Offset
        assertNotNull("Null actual position", actual)
        actual as Offset

        assertEquals(
            "Expected x ${expected.x} but obtained ${actual.x}",
            expected.x,
            actual.x,
            0f
        )
        assertEquals(
            "Expected y ${expected.y} but obtained ${actual.y}",
            expected.y,
            actual.y,
            0f
        )
        if (actual.x != actual.x.toInt().toFloat()) {
            fail("Expected integer x coordinate")
        }
        if (actual.y != actual.y.toInt().toFloat()) {
            fail("Expected integer y coordinate")
        }
    }

    internal fun assertEquals(expected: Int, actual: Int) {
        assertEquals(
            "Expected $expected but obtained $actual",
            expected.toFloat(),
            actual.toFloat(),
            0f
        )
    }

    @Composable
    internal fun Container(
        modifier: Modifier = Modifier,
        padding: InnerPadding = InnerPadding(0.dp),
        alignment: Alignment = Alignment.Center,
        expanded: Boolean = false,
        constraints: DpConstraints = DpConstraints(),
        width: Dp? = null,
        height: Dp? = null,
        children: @Composable () -> Unit
    ) {
        Layout(children, modifier) { measurables, incomingConstraints ->
            val containerConstraints = Constraints(constraints)
                .copy(
                    width?.toIntPx() ?: constraints.minWidth.toIntPx(),
                    width?.toIntPx() ?: constraints.maxWidth.toIntPx(),
                    height?.toIntPx() ?: constraints.minHeight.toIntPx(),
                    height?.toIntPx() ?: constraints.maxHeight.toIntPx()
                ).enforce(incomingConstraints)
            val totalHorizontal = padding.start.toIntPx() + padding.end.toIntPx()
            val totalVertical = padding.top.toIntPx() + padding.bottom.toIntPx()
            val childConstraints = containerConstraints
                .copy(minWidth = 0, minHeight = 0)
                .offset(-totalHorizontal, -totalVertical)
            var placeable: Placeable? = null
            val containerWidth = if ((containerConstraints.hasFixedWidth || expanded) &&
                containerConstraints.hasBoundedWidth
            ) {
                containerConstraints.maxWidth
            } else {
                placeable = measurables.firstOrNull()?.measure(childConstraints)
                max((placeable?.width ?: 0) + totalHorizontal, containerConstraints.minWidth)
            }
            val containerHeight = if ((containerConstraints.hasFixedHeight || expanded) &&
                containerConstraints.hasBoundedHeight
            ) {
                containerConstraints.maxHeight
            } else {
                if (placeable == null) {
                    placeable = measurables.firstOrNull()?.measure(childConstraints)
                }
                max((placeable?.height ?: 0) + totalVertical, containerConstraints.minHeight)
            }
            layout(containerWidth, containerHeight) {
                val p = placeable ?: measurables.firstOrNull()?.measure(childConstraints)
                p?.let {
                    val position = alignment.align(
                        IntSize(
                            containerWidth - it.width - totalHorizontal,
                            containerHeight - it.height - totalVertical
                        )
                    )
                    it.placeRelative(
                        padding.start.toIntPx() + position.x,
                        padding.top.toIntPx() + position.y
                    )
                }
            }
        }
    }
}