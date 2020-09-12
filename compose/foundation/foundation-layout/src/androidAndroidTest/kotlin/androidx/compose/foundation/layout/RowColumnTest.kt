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

import androidx.compose.foundation.Box
import androidx.compose.foundation.text.FirstBaseline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.emptyContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.HorizontalAlignmentLine
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.VerticalAlignmentLine
import androidx.compose.ui.WithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.Ref
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SmallTest
@RunWith(JUnit4::class)
class RowColumnTest : LayoutTest() {
    // region Size and position tests for Row and Column
    @Test
    fun testRow() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOf(IntSize(-1, -1), IntSize(-1, -1))
        val childPosition = arrayOf(Offset(-1f, -1f), Offset(-1f, -1f))
        show {
            Container(alignment = Alignment.TopStart) {
                Row {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }

                    Container(
                        width = (sizeDp * 2),
                        height = (sizeDp * 2),
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(
            IntSize((sizeDp.toPx() * 2).roundToInt(), (sizeDp.toPx() * 2).roundToInt()),
            childSize[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(size.toFloat(), 0f), childPosition[1])
    }

    @Test
    fun testRow_withChildrenWithWeight() = with(density) {
        val width = 50.toDp()
        val height = 80.toDp()
        val childrenHeight = height.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOfNulls<IntSize>(2)
        val childPosition = arrayOfNulls<Offset>(2)
        show {
            Container(alignment = Alignment.TopStart) {
                Row {
                    Container(
                        Modifier.weight(1f)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[0] = coordinates.size
                                childPosition[0] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }

                    Container(
                        Modifier.weight(2f)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[1] = coordinates.size
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)
        val rootWidth = root.width

        assertEquals(
            IntSize((rootWidth / 3f).roundToInt(), childrenHeight),
            childSize[0]
        )
        assertEquals(
            IntSize((rootWidth * 2f / 3f).roundToInt(), childrenHeight),
            childSize[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset((rootWidth / 3f).roundToInt().toFloat(), 0f), childPosition[1])
    }

    @Test
    fun testRow_withChildrenWithWeightNonFilling() = with(density) {
        val width = 50.toDp()
        val childrenWidth = width.toIntPx()
        val height = 80.toDp()
        val childrenHeight = height.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOfNulls<IntSize>(2)
        val childPosition = arrayOfNulls<Offset>(2)
        show {
            Container(alignment = Alignment.TopStart) {
                Row {
                    Container(
                        Modifier.weight(1f, fill = false)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[0] = coordinates.size
                                childPosition[0] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }

                    Container(
                        Modifier.weight(2f, fill = false)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[1] = coordinates.size
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height * 2
                    ) {
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(IntSize(childrenWidth, childrenHeight), childSize[0])
        assertEquals(IntSize(childrenWidth, childrenHeight * 2), childSize[1])
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(childrenWidth.toFloat(), 0f), childPosition[1])
    }

    @Test
    fun testColumn() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOf(IntSize(-1, -1), IntSize(-1, -1))
        val childPosition = arrayOf(Offset(-1f, -1f), Offset(-1f, -1f))
        show {
            Container(alignment = Alignment.TopStart) {
                Column {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }
                    Container(
                        width = (sizeDp * 2),
                        height = (sizeDp * 2),
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(
            IntSize((sizeDp.toPx() * 2).roundToInt(), (sizeDp.toPx() * 2).roundToInt()),
            childSize[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(0f, size.toFloat()), childPosition[1])
    }

    @Test
    fun testColumn_withChildrenWithWeight() = with(density) {
        val width = 80.toDp()
        val childrenWidth = width.toIntPx()
        val height = 50.toDp()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOfNulls<IntSize>(2)
        val childPosition = arrayOfNulls<Offset>(2)
        show {
            Container(alignment = Alignment.TopStart) {
                Column {
                    Container(
                        Modifier.weight(1f)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[0] = coordinates.size
                                childPosition[0] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }

                    Container(
                        Modifier.weight(2f)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[1] = coordinates.size
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)
        val rootHeight = root.height

        assertEquals(
            IntSize(childrenWidth, (rootHeight / 3f).roundToInt()), childSize[0]
        )
        assertEquals(
            IntSize(childrenWidth, (rootHeight * 2f / 3f).roundToInt()), childSize[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(0f, (rootHeight / 3f).roundToInt().toFloat()), childPosition[1])
    }

    @Test
    fun testColumn_withChildrenWithWeightNonFilling() = with(density) {
        val width = 80.toDp()
        val childrenWidth = width.toIntPx()
        val height = 50.toDp()
        val childrenHeight = height.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOfNulls<IntSize>(2)
        val childPosition = arrayOfNulls<Offset>(2)
        show {
            Container(alignment = Alignment.TopStart) {
                Column {
                    Container(
                        Modifier.weight(1f, fill = false)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[0] = coordinates.size
                                childPosition[0] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }
                    Container(
                        Modifier.weight(2f, fill = false)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childSize[1] = coordinates.size
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            },
                        width = width,
                        height = height
                    ) {
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(IntSize(childrenWidth, childrenHeight), childSize[0])
        assertEquals(
            IntSize(childrenWidth, childrenHeight), childSize[1]
        )
        assertEquals(Offset(0.0f, 0.0f), childPosition[0])
        assertEquals(Offset(0.0f, childrenHeight.toFloat()), childPosition[1])
    }

    @Test
    fun testRow_doesNotPlaceChildrenOutOfBounds_becauseOfRoundings() = with(density) {
        val expectedRowWidth = 11f
        val leftPadding = 1f
        var rowWidth = 0f
        val width = Array(2) { 0f }
        val x = Array(2) { 0f }
        val latch = CountDownLatch(2)
        show {
            Row(
                Modifier.wrapContentSize(Alignment.TopStart)
                    .padding(start = leftPadding.toDp())
                    .preferredWidthIn(max = expectedRowWidth.toDp())
                    .onPositioned { coordinates: LayoutCoordinates ->
                        rowWidth = coordinates.size.width.toFloat()
                    }
            ) {
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            width[0] = coordinates.size.width.toFloat()
                            x[0] = coordinates.positionInRoot.x
                            latch.countDown()
                        }
                ) {
                }
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            width[1] = coordinates.size.width.toFloat()
                            x[1] = coordinates.positionInRoot.x
                            latch.countDown()
                        }
                ) {
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertEquals(expectedRowWidth, rowWidth)
        assertEquals(leftPadding, x[0])
        assertEquals(leftPadding + width[0], x[1])
        assertEquals(rowWidth, width[0] + width[1])
    }

    @Test
    fun testRow_isNotLargerThanItsChildren_becauseOfRoundings() = with(density) {
        val expectedRowWidth = 8f
        val leftPadding = 1f
        var rowWidth = 0f
        val width = Array(3) { 0f }
        val x = Array(3) { 0f }
        val latch = CountDownLatch(3)
        show {
            Row(
                Modifier.wrapContentSize(Alignment.TopStart)
                    .padding(start = leftPadding.toDp())
                    .preferredWidthIn(max = expectedRowWidth.toDp())
                    .onPositioned { coordinates: LayoutCoordinates ->
                        rowWidth = coordinates.size.width.toFloat()
                    }
            ) {
                Container(
                    Modifier.weight(2f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            width[0] = coordinates.size.width.toFloat()
                            x[0] = coordinates.positionInRoot.x
                            latch.countDown()
                        }
                ) {
                }
                Container(
                    Modifier.weight(2f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            width[1] = coordinates.size.width.toFloat()
                            x[1] = coordinates.positionInRoot.x
                            latch.countDown()
                        }
                ) {
                }
                Container(
                    Modifier.weight(3f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            width[2] = coordinates.size.width.toFloat()
                            x[2] = coordinates.positionInRoot.x
                            latch.countDown()
                        }
                ) {
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertEquals(expectedRowWidth, rowWidth)
        assertEquals(leftPadding, x[0])
        assertEquals(leftPadding + width[0], x[1])
        assertEquals(leftPadding + width[0] + width[1], x[2])
        assertEquals(rowWidth, width[0] + width[1] + width[2])
    }

    @Test
    fun testColumn_isNotLargetThanItsChildren_becauseOfRoundings() = with(density) {
        val expectedColumnHeight = 8f
        val topPadding = 1f
        var columnHeight = 0f
        val height = Array(3) { 0f }
        val y = Array(3) { 0f }
        val latch = CountDownLatch(3)
        show {
            Column(
                Modifier.wrapContentSize(Alignment.TopStart)
                    .padding(top = topPadding.toDp())
                    .preferredHeightIn(max = expectedColumnHeight.toDp())
                    .onPositioned { coordinates: LayoutCoordinates ->
                        columnHeight = coordinates.size.height.toFloat()
                    }
            ) {
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            height[0] = coordinates.size.height.toFloat()
                            y[0] = coordinates.positionInRoot.y
                            latch.countDown()
                        }
                ) {
                }
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            height[1] = coordinates.size.height.toFloat()
                            y[1] = coordinates.positionInRoot.y
                            latch.countDown()
                        }
                ) {
                }
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            height[2] = coordinates.size.height.toFloat()
                            y[2] = coordinates.positionInRoot.y
                            latch.countDown()
                        }
                ) {
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertEquals(expectedColumnHeight, columnHeight)
        assertEquals(topPadding, y[0])
        assertEquals(topPadding + height[0], y[1])
        assertEquals(topPadding + height[0] + height[1], y[2])
        assertEquals(columnHeight, height[0] + height[1] + height[2])
    }

    @Test
    fun testColumn_doesNotPlaceChildrenOutOfBounds_becauseOfRoundings() = with(density) {
        val expectedColumnHeight = 11f
        val topPadding = 1f
        var columnHeight = 0f
        val height = Array(2) { 0f }
        val y = Array(2) { 0f }
        val latch = CountDownLatch(2)
        show {
            Column(
                Modifier.wrapContentSize(Alignment.TopStart)
                    .padding(top = topPadding.toDp())
                    .preferredHeightIn(max = expectedColumnHeight.toDp())
                    .onPositioned { coordinates: LayoutCoordinates ->
                        columnHeight = coordinates.size.height.toFloat()
                    }
            ) {
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            height[0] = coordinates.size.height.toFloat()
                            y[0] = coordinates.positionInRoot.y
                            latch.countDown()
                        }
                ) {
                }
                Container(
                    Modifier.weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            height[1] = coordinates.size.height.toFloat()
                            y[1] = coordinates.positionInRoot.y
                            latch.countDown()
                        }
                ) {
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertEquals(expectedColumnHeight, columnHeight)
        assertEquals(topPadding, y[0])
        assertEquals(topPadding + height[0], y[1])
        assertEquals(columnHeight, height[0] + height[1])
    }

    // endregion

    // region Cross axis alignment tests in Row
    @Test
    fun testRow_withStretchCrossAxisAlignment() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOf(IntSize(-1, -1), IntSize(-1, -1))
        val childPosition = arrayOf(Offset(-1f, -1f), Offset(-1f, -1f))
        show {
            Row {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.fillMaxHeight()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }

                Container(
                    width = (sizeDp * 2),
                    height = (sizeDp * 2),
                    modifier = Modifier.fillMaxHeight()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(IntSize(size, root.height), childSize[0])
        assertEquals(
            IntSize((sizeDp.toPx() * 2).roundToInt(), root.height),
            childSize[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(size.toFloat(), 0f), childPosition[1])
    }

    @Test
    fun testRow_withGravityModifier_andGravityParameter() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(3)
        val childSize = arrayOfNulls<IntSize>(3)
        val childPosition = arrayOfNulls<Offset>(3)
        show {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.Top)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        childSize[1] = coordinates.size
                        childPosition[1] = coordinates.positionInRoot
                        drawLatch.countDown()
                    }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.Bottom)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[2] = coordinates.size
                            childPosition[2] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)
        val rootHeight = root.height

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(IntSize(size, size), childSize[1])
        assertEquals(
            Offset(
                size.toFloat(),
                ((rootHeight - size.toFloat()) / 2f).roundToInt().toFloat()
            ),
            childPosition[1]
        )

        assertEquals(IntSize(size, size), childSize[2])
        assertEquals(
            Offset(
                (size.toFloat() * 2),
                (rootHeight - size.toFloat())
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_withGravityModifier() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(3)
        val childSize = arrayOfNulls<IntSize>(3)
        val childPosition = arrayOfNulls<Offset>(3)
        show {
            Row(Modifier.fillMaxHeight()) {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.Top)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.Bottom)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[2] = coordinates.size
                            childPosition[2] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)
        val rootHeight = root.height

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(IntSize(size, size), childSize[1])
        assertEquals(
            Offset(
                size.toFloat(),
                ((rootHeight - size.toFloat()) / 2f).roundToInt().toFloat()
            ),
            childPosition[1]
        )

        assertEquals(IntSize(size, size), childSize[2])
        assertEquals(
            Offset((size.toFloat() * 2), (rootHeight - size.toFloat())),
            childPosition[2]
        )
    }

    @Test
    fun testRow_withRelativeToSiblingsModifier() = with(density) {
        val baseline1Dp = 30.toDp()
        val baseline1 = baseline1Dp.toIntPx()
        val baseline2Dp = 25.toDp()
        val baseline2 = baseline2Dp.toIntPx()
        val sizeDp = 40.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childSize = arrayOfNulls<IntSize>(4)
        val childPosition = arrayOfNulls<Offset>(4)
        show {
            Row(Modifier.fillMaxHeight()) {
                BaselineTestLayout(
                    baseline = baseline1Dp,
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings(TestHorizontalLine)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings { it.height / 2 }
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                BaselineTestLayout(
                    baseline = baseline2Dp,
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings(TestHorizontalLine)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[2] = coordinates.size
                            childPosition[2] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings { it.height * 3 / 4 }
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[3] = coordinates.size
                            childPosition[3] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(IntSize(size, size), childSize[1])
        assertEquals(
            Offset(
                size.toFloat(),
                (baseline1.toFloat() - (size.toFloat() / 2).roundToInt())
            ),
            childPosition[1]
        )

        assertEquals(IntSize(size, size), childSize[2])
        assertEquals(
            Offset((size.toFloat() * 2), (baseline1 - baseline2).toFloat()),
            childPosition[2]
        )

        assertEquals(IntSize(size, size), childSize[3])
        assertEquals(
            Offset((size.toFloat() * 3), 0f),
            childPosition[3]
        )
    }

    @Test
    fun testRow_withRelativeToSiblingsModifier_andWeight() = with(density) {
        val baselineDp = 30.toDp()
        val baseline = baselineDp.toIntPx()
        val sizeDp = 40.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOfNulls<IntSize>(2)
        val childPosition = arrayOfNulls<Offset>(2)
        show {
            Row(Modifier.fillMaxHeight()) {
                BaselineTestLayout(
                    baseline = baselineDp,
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings(TestHorizontalLine)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings { it.height / 2 }
                        .weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(size, childSize[1]!!.height)
        assertEquals(
            Offset(size.toFloat(), (baseline - size / 2).toFloat()),
            childPosition[1]
        )
    }
    // endregion

    // region Cross axis alignment tests in Column
    @Test
    fun testColumn_withStretchCrossAxisAlignment() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOf(IntSize(-1, -1), IntSize(-1, -1))
        val childPosition = arrayOf(Offset(-1f, -1f), Offset(-1f, -1f))
        show {
            Column {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.fillMaxWidth()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }

                Container(
                    width = (sizeDp * 2),
                    height = (sizeDp * 2),
                    modifier = Modifier.fillMaxWidth()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(IntSize(root.width, size), childSize[0])
        assertEquals(
            IntSize(root.width, (sizeDp * 2).toIntPx()),
            childSize[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(0f, size.toFloat()), childPosition[1])
    }

    @Test
    fun testColumn_withGravityModifier() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(3)
        val childSize = arrayOfNulls<IntSize>(3)
        val childPosition = arrayOfNulls<Offset>(3)
        show {
            Column(Modifier.fillMaxWidth()) {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.Start)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.End)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[2] = coordinates.size
                            childPosition[2] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)
        val rootWidth = root.width

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(IntSize(size, size), childSize[1])
        assertEquals(
            Offset(
                ((rootWidth - size.toFloat()) / 2).roundToInt().toFloat(),
                size.toFloat()
            ),
            childPosition[1]
        )

        assertEquals(IntSize(size, size), childSize[2])
        assertEquals(
            Offset((rootWidth - size.toFloat()), size.toFloat() * 2),
            childPosition[2]
        )
    }

    @Test
    fun testColumn_withGravityModifier_andGravityParameter() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(3)
        val childSize = arrayOfNulls<IntSize>(3)
        val childPosition = arrayOfNulls<Offset>(3)
        show {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.Start)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        childSize[1] = coordinates.size
                        childPosition[1] = coordinates.positionInRoot
                        drawLatch.countDown()
                    }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.align(Alignment.End)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[2] = coordinates.size
                            childPosition[2] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)
        val rootWidth = root.width

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(IntSize(size, size), childSize[1])
        assertEquals(
            Offset(
                ((rootWidth - size.toFloat()) / 2).roundToInt().toFloat(),
                size.toFloat()
            ),
            childPosition[1]
        )

        assertEquals(IntSize(size, size), childSize[2])
        assertEquals(
            Offset((rootWidth - size.toFloat()), size.toFloat() * 2),
            childPosition[2]
        )
    }

    @Test
    fun testColumn_withRelativeToSiblingsModifier() = with(density) {
        val sizeDp = 40.toDp()
        val size = sizeDp.toIntPx()
        val firstBaseline1Dp = 20.toDp()
        val firstBaseline2Dp = 30.toDp()

        val drawLatch = CountDownLatch(4)
        val childSize = arrayOfNulls<IntSize>(4)
        val childPosition = arrayOfNulls<Offset>(4)
        show {
            Column(Modifier.fillMaxWidth()) {
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings { it.width }
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings { 0 }
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                BaselineTestLayout(
                    width = sizeDp,
                    height = sizeDp,
                    baseline = firstBaseline1Dp,
                    modifier = Modifier.alignWithSiblings(TestVerticalLine)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[2] = coordinates.size
                            childPosition[2] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                BaselineTestLayout(
                    width = sizeDp,
                    height = sizeDp,
                    baseline = firstBaseline2Dp,
                    modifier = Modifier.alignWithSiblings(TestVerticalLine)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[3] = coordinates.size
                            childPosition[3] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(IntSize(size, size), childSize[1])
        assertEquals(Offset(size.toFloat(), size.toFloat()), childPosition[1])

        assertEquals(IntSize(size, size), childSize[2])
        assertEquals(
            Offset(
                (size - firstBaseline1Dp.toIntPx()).toFloat(),
                size.toFloat() * 2
            ),
            childPosition[2]
        )

        assertEquals(IntSize(size, size), childSize[3])
        assertEquals(
            Offset(
                (size - firstBaseline2Dp.toIntPx()).toFloat(),
                size.toFloat() * 3
            ),
            childPosition[3]
        )
    }

    @Test
    fun testColumn_withRelativeToSiblingsModifier_andWeight() = with(density) {
        val baselineDp = 30.toDp()
        val baseline = baselineDp.toIntPx()
        val sizeDp = 40.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childSize = arrayOfNulls<IntSize>(2)
        val childPosition = arrayOfNulls<Offset>(2)
        show {
            Column(Modifier.fillMaxWidth()) {
                BaselineTestLayout(
                    baseline = baselineDp,
                    width = sizeDp,
                    height = sizeDp,
                    modifier = Modifier.alignWithSiblings(TestVerticalLine)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[0] = coordinates.size
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
                Container(
                    width = sizeDp,
                    modifier = Modifier.alignWithSiblings { it.width / 2 }
                        .weight(1f)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            childSize[1] = coordinates.size
                            childPosition[1] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                ) {
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(size, size), childSize[0])
        assertEquals(Offset(0f, 0f), childPosition[0])

        assertEquals(size, childSize[1]!!.width)
        assertEquals(
            Offset((baseline - (size / 2)).toFloat(), size.toFloat()),
            childPosition[1]
        )
    }
    // endregion

    // region Size tests in Row
    @Test
    fun testRow_expandedWidth_withExpandedModifier() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                Row(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                    rowSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            root.width,
            rowSize.width
        )
    }

    @Test
    fun testRow_wrappedWidth_withNoWeightChildren() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                    rowSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            (sizeDp * 3).toIntPx(),
            rowSize.width
        )
    }

    @Test
    fun testRow_expandedWidth_withWeightChildren() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                    rowSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Container(
                        Modifier.weight(1f),
                        width = sizeDp,
                        height = sizeDp,
                        children = emptyContent()
                    )
                    Container(
                        width = (sizeDp * 2),
                        height = (sizeDp * 2),
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            root.width,
            rowSize.width
        )
    }

    @Test
    fun testRow_withMaxCrossAxisSize() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                Row(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    rowSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            root.height,
            rowSize.height
        )
    }

    @Test
    fun testRow_withMinCrossAxisSize() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                    rowSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            (sizeDp * 2).toIntPx(),
            rowSize.height
        )
    }

    @Test
    fun testRow_withExpandedModifier_respectsMaxWidthConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val rowWidthDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(maxWidth = rowWidthDp)) {
                    Row(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                        rowSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            min(root.width, rowWidthDp.toIntPx()),
            rowSize.width
        )
    }

    @Test
    fun testRow_withChildrenWithWeight_respectsMaxWidthConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val rowWidthDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(maxWidth = rowWidthDp)) {
                    Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        rowSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Container(
                            Modifier.weight(1f),
                            width = sizeDp,
                            height = sizeDp,
                            children = emptyContent()
                        )
                        Container(
                            width = sizeDp * 2,
                            height = sizeDp * 2,
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            min(root.width, rowWidthDp.toIntPx()),
            rowSize.width
        )
    }

    @Test
    fun testRow_withNoWeightChildren_respectsMinWidthConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val rowWidthDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(minWidth = rowWidthDp)) {
                    Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        rowSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            rowWidthDp.toIntPx(),
            rowSize.width
        )
    }

    @Test
    fun testRow_withMaxCrossAxisSize_respectsMaxHeightConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val rowHeightDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(maxHeight = rowHeightDp)) {
                    Row(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                        rowSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            min(root.height, rowHeightDp.toIntPx()),
            rowSize.height
        )
    }

    @Test
    fun testRow_withMinCrossAxisSize_respectsMinHeightConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val rowHeightDp = 150.toDp()

        val drawLatch = CountDownLatch(1)
        var rowSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(minHeight = rowHeightDp)) {
                    Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        rowSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            rowHeightDp.toIntPx(),
            rowSize.height
        )
    }

    @Test
    @Ignore(
        "Wrap is not supported when there are children with weight. " +
                "Should use maxWidth(.Infinity) modifier when it is available"
    )
    fun testRow_withMinMainAxisSize() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()
        val rowWidthDp = 250.toDp()
        val rowWidth = rowWidthDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        var rowSize: IntSize = IntSize.Zero
        var expandedChildSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(minWidth = rowWidthDp)) {
                    // TODO: add maxWidth(Constraints.Infinity) modifier
                    Row(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        rowSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Container(
                            modifier = Modifier.weight(1f)
                                .onPositioned { coordinates: LayoutCoordinates ->
                                    expandedChildSize = coordinates.size
                                    drawLatch.countDown()
                                },
                            width = sizeDp,
                            height = sizeDp
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            IntSize(rowWidth, size),
            rowSize
        )
        assertEquals(
            IntSize(rowWidth, size),
            expandedChildSize
        )
    }

    @Test
    fun testRow_measuresChildrenCorrectly_whenMeasuredWithInfiniteWidth() = with(density) {
        val rowMinWidth = 100.toDp()
        val noWeightChildWidth = 30.toDp()
        val latch = CountDownLatch(1)
        show {
            WithInfiniteConstraints {
                ConstrainedBox(DpConstraints(minWidth = rowMinWidth)) {
                    Row {
                        WithConstraints {
                            assertEquals(Constraints(), constraints)
                            FixedSizeLayout(noWeightChildWidth.toIntPx(), 0, mapOf())
                        }
                        WithConstraints {
                            assertEquals(Constraints(), constraints)
                            FixedSizeLayout(noWeightChildWidth.toIntPx(), 0, mapOf())
                        }
                        Layout({}, Modifier.weight(1f)) { _, constraints ->
                            assertEquals(
                                rowMinWidth.toIntPx() - noWeightChildWidth.toIntPx() * 2,
                                constraints.minWidth
                            )
                            assertEquals(
                                rowMinWidth.toIntPx() - noWeightChildWidth.toIntPx() * 2,
                                constraints.maxWidth
                            )
                            latch.countDown()
                            layout(0, 0) { }
                        }
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testRow_measuresNoWeightChildrenCorrectly() = with(density) {
        val availableWidth = 100.toDp()
        val childWidth = 50.toDp()
        val availableHeight = 200.toDp()
        val childHeight = 100.toDp()
        val latch = CountDownLatch(1)
        show {
            Stack {
                ConstrainedBox(
                    DpConstraints(
                        minWidth = availableWidth,
                        maxWidth = availableWidth,
                        minHeight = availableHeight,
                        maxHeight = availableHeight
                    )
                ) {
                    Row {
                        WithConstraints {
                            assertEquals(
                                Constraints(
                                    maxWidth = availableWidth.toIntPx(),
                                    maxHeight = availableHeight.toIntPx()
                                ),
                                constraints
                            )
                            FixedSizeLayout(childWidth.toIntPx(), childHeight.toIntPx(), mapOf())
                        }
                        WithConstraints {
                            assertEquals(
                                Constraints(
                                    maxWidth = availableWidth.toIntPx() - childWidth.toIntPx(),
                                    maxHeight = availableHeight.toIntPx()
                                ),
                                constraints
                            )
                            FixedSizeLayout(childWidth.toIntPx(), childHeight.toIntPx(), mapOf())
                            latch.countDown()
                        }
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }
    // endregion

    // region Size tests in Column
    @Test
    fun testColumn_expandedHeight_withExpandedModifier() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    columnSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            root.height,
            columnSize.height
        )
    }

    @Test
    fun testColumn_wrappedHeight_withNoChildrenWithWeight() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                    columnSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            (sizeDp * 3).toIntPx(),
            columnSize.height
        )
    }

    @Test
    fun testColumn_expandedHeight_withWeightChildren() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                    columnSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Container(
                        Modifier.weight(1f),
                        width = sizeDp,
                        height = sizeDp,
                        children = emptyContent()
                    )
                    Container(
                        width = (sizeDp * 2),
                        height = (sizeDp * 2),
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            root.height,
            columnSize.height
        )
    }

    @Test
    fun testColumn_withMaxCrossAxisSize() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                Column(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                    columnSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            root.width,
            columnSize.width
        )
    }

    @Test
    fun testColumn_withMinCrossAxisSize() = with(density) {
        val sizeDp = 50.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                    columnSize = coordinates.size
                    drawLatch.countDown()
                }) {
                    Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                    Spacer(Modifier.preferredSize(width = (sizeDp * 2), height = (sizeDp * 2)))
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            (sizeDp * 2).toIntPx(),
            columnSize.width
        )
    }

    @Test
    fun testColumn_withExpandedModifier_respectsMaxHeightConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val columnHeightDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(maxHeight = columnHeightDp)) {
                    Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                        columnSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            min(root.height, columnHeightDp.toIntPx()),
            columnSize.height
        )
    }

    @Test
    fun testColumn_withWeightChildren_respectsMaxHeightConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val columnHeightDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(maxHeight = columnHeightDp)) {
                    Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        columnSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Container(
                            Modifier.weight(1f),
                            width = sizeDp,
                            height = sizeDp,
                            children = emptyContent()
                        )
                        Container(
                            width = sizeDp * 2,
                            height = sizeDp * 2,
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            min(root.height, columnHeightDp.toIntPx()),
            columnSize.height
        )
    }

    @Test
    fun testColumn_withChildren_respectsMinHeightConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val columnHeightDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(minHeight = columnHeightDp)) {
                    Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        columnSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            columnHeightDp.toIntPx(),
            columnSize.height
        )
    }

    @Test
    fun testColumn_withMaxCrossAxisSize_respectsMaxWidthConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val columnWidthDp = 250.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(maxWidth = columnWidthDp)) {
                    Column(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                        columnSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            min(root.width, columnWidthDp.toIntPx()),
            columnSize.width
        )
    }

    @Test
    fun testColumn_withMinCrossAxisSize_respectsMinWidthConstraint() = with(density) {
        val sizeDp = 50.toDp()
        val columnWidthDp = 150.toDp()

        val drawLatch = CountDownLatch(1)
        var columnSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(minWidth = columnWidthDp)) {
                    Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                        columnSize = coordinates.size
                        drawLatch.countDown()
                    }) {
                        Spacer(Modifier.preferredSize(width = sizeDp, height = sizeDp))
                        Spacer(Modifier.preferredSize(width = sizeDp * 2, height = sizeDp * 2))
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            columnWidthDp.toIntPx(),
            columnSize.width
        )
    }

    @Test
    @Ignore(
        "Wrap is not supported when there are weight children. " +
                "Should use maxHeight(Constraints.Infinity) modifier when it is available"
    )
    fun testColumn_withMinMainAxisSize() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()
        val columnHeightDp = 250.toDp()
        val columnHeight = columnHeightDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        var columnSize: IntSize = IntSize.Zero
        var expandedChildSize: IntSize = IntSize.Zero
        show {
            Center {
                ConstrainedBox(constraints = DpConstraints(minHeight = columnHeightDp)) {
                    // TODO: add maxHeight(Constraints.Infinity) modifier
                    Column(Modifier.preferredHeightIn(max = Dp.Infinity)
                        .onPositioned { coordinates: LayoutCoordinates ->
                            columnSize = coordinates.size
                            drawLatch.countDown()
                        }
                    ) {
                        Container(
                            Modifier.weight(1f)
                                .onPositioned { coordinates: LayoutCoordinates ->
                                    expandedChildSize = coordinates.size
                                    drawLatch.countDown()
                                },
                            width = sizeDp,
                            height = sizeDp
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            IntSize(size, columnHeight),
            columnSize
        )
        assertEquals(
            IntSize(size, columnHeight),
            expandedChildSize
        )
    }

    @Test
    fun testColumn_measuresChildrenCorrectly_whenMeasuredWithInfiniteHeight() =
        with(density) {
            val columnMinHeight = 100.toDp()
            val noWeightChildHeight = 30.toDp()
            val latch = CountDownLatch(1)
            show {
                WithInfiniteConstraints {
                    ConstrainedBox(DpConstraints(minHeight = columnMinHeight)) {
                        Column {
                            WithConstraints {
                                assertEquals(Constraints(), constraints)
                                FixedSizeLayout(0, noWeightChildHeight.toIntPx(), mapOf())
                            }
                            WithConstraints {
                                assertEquals(Constraints(), constraints)
                                FixedSizeLayout(0, noWeightChildHeight.toIntPx(), mapOf())
                            }
                            Layout(emptyContent(), Modifier.weight(1f)) { _, constraints ->
                                assertEquals(
                                    columnMinHeight.toIntPx() - noWeightChildHeight.toIntPx() * 2,
                                    constraints.minHeight
                                )
                                assertEquals(
                                    columnMinHeight.toIntPx() - noWeightChildHeight.toIntPx() * 2,
                                    constraints.maxHeight
                                )
                                latch.countDown()
                                layout(0, 0) { }
                            }
                        }
                    }
                }
            }
        }

    @Test
    fun testColumn_measuresNoWeightChildrenCorrectly() = with(density) {
        val availableWidth = 100.toDp()
        val childWidth = 50.toDp()
        val availableHeight = 200.toDp()
        val childHeight = 100.toDp()
        val latch = CountDownLatch(1)
        show {
            Stack {
                ConstrainedBox(
                    DpConstraints(
                        minWidth = availableWidth,
                        maxWidth = availableWidth,
                        minHeight = availableHeight,
                        maxHeight = availableHeight
                    )
                ) {
                    Column {
                        WithConstraints {
                            assertEquals(
                                Constraints(
                                    maxWidth = availableWidth.toIntPx(),
                                    maxHeight = availableHeight.toIntPx()
                                ),
                                constraints
                            )
                            FixedSizeLayout(childWidth.toIntPx(), childHeight.toIntPx(), mapOf())
                        }
                        WithConstraints {
                            assertEquals(
                                Constraints(
                                    maxWidth = availableWidth.toIntPx(),
                                    maxHeight = availableHeight.toIntPx() - childHeight.toIntPx()
                                ),
                                constraints
                            )
                            FixedSizeLayout(childWidth.toIntPx(), childHeight.toIntPx(), mapOf())
                            latch.countDown()
                        }
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }
    // endregion

    // region Main axis alignment tests in Row
    @Test
    fun testRow_withStartArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Row(Modifier.fillMaxWidth()
                    .onPositioned { coordinates: LayoutCoordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    }
                ) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(size.toFloat(), 0f), childPosition[1])
        assertEquals(Offset(size.toFloat() * 2, 0f), childPosition[2])
    }

    @Test
    fun testRow_withEndArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Row(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, horizontalArrangement = Arrangement.End) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(Offset((root.width - size.toFloat() * 3), 0f), childPosition[0])
        assertEquals(Offset((root.width - size.toFloat() * 2), 0f), childPosition[1])
        assertEquals(Offset((root.width - size.toFloat()), 0f), childPosition[2])
    }

    @Test
    fun testRow_withCenterArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Row(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, horizontalArrangement = Arrangement.Center) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val extraSpace = root.width - size * 3
        assertEquals(Offset((extraSpace / 2f).roundToInt().toFloat(), 0f), childPosition[0])
        assertEquals(
            Offset(((extraSpace / 2f) + size.toFloat()).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset(
                ((extraSpace / 2f) + size.toFloat() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_withSpaceEvenlyArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Row(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width - size.toFloat() * 3f) / 4f
        assertEquals(
            Offset(gap.roundToInt().toFloat(), 0f), childPosition[0]
        )
        assertEquals(
            Offset((size.toFloat() + gap * 2f).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset((size.toFloat() * 2f + gap * 3f).roundToInt().toFloat(), 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_withSpaceBetweenArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Row(Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width - size.toFloat() * 3) / 2
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(
            Offset((gap + size.toFloat()).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset((gap * 2 + size.toFloat() * 2).roundToInt().toFloat(), 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_withSpaceAroundArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Row(
                    Modifier.fillMaxWidth().onPositioned { coordinates: LayoutCoordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width.toFloat() - size * 3) / 3
        assertEquals(Offset((gap / 2f).roundToInt().toFloat(), 0f), childPosition[0])
        assertEquals(
            Offset(((gap * 3 / 2) + size.toFloat()).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset(((gap * 5 / 2) + size.toFloat() * 2).roundToInt().toFloat(), 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_withSpacedByArrangement() = with(density) {
        val spacePx = 10f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space),
                    modifier = Modifier.onPositioned {
                        assertEquals((sizePx * 2 + spacePx).roundToInt(), it.size.width)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(0f, it.positionInParent.x)
                        latch.countDown()
                    })
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(sizePx + spacePx, it.positionInParent.x)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testRow_withSpacedByAlignedArrangement() = with(density) {
        val spacePx = 10f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val rowSizePx = 50
        val rowSize = rowSizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space, Alignment.End),
                    modifier = Modifier.size(rowSize).onPositioned {
                        assertEquals(rowSizePx, it.size.width)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(rowSizePx - spacePx - sizePx * 2, it.positionInParent.x)
                        latch.countDown()
                    })
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(rowSizePx - sizePx, it.positionInParent.x)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testRow_withSpacedByArrangement_insufficientSpace() = with(density) {
        val spacePx = 15f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val rowSizePx = 50f
        val rowSize = rowSizePx.toDp()
        val latch = CountDownLatch(4)
        show {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space),
                    modifier = Modifier.size(rowSize).onPositioned {
                        assertEquals(rowSizePx.roundToInt(), it.size.width)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(0f, it.positionInParent.x)
                        assertEquals(sizePx.roundToInt(), it.size.width)
                        latch.countDown()
                    })
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(sizePx + spacePx, it.positionInParent.x)
                        assertEquals((rowSizePx - spacePx - sizePx).roundToInt(), it.size.width)
                        latch.countDown()
                    })
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(rowSizePx, it.positionInParent.x)
                        assertEquals(0, it.size.width)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testRow_withAlignedArrangement() = with(density) {
        val sizePx = 20f
        val size = sizePx.toDp()
        val rowSizePx = 50f
        val rowSize = rowSizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Column {
                Row(
                    horizontalArrangement = Arrangement.aligned(Alignment.End),
                    modifier = Modifier.size(rowSize).onPositioned {
                        assertEquals(rowSizePx.roundToInt(), it.size.width)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(rowSizePx - sizePx * 2, it.positionInParent.x)
                        assertEquals(sizePx.roundToInt(), it.size.width)
                        latch.countDown()
                    })
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(rowSizePx - sizePx, it.positionInParent.x)
                        assertEquals(sizePx.roundToInt(), it.size.width)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }
    // endregion

    // region Main axis alignment tests in Column
    @Test
    fun testColumn_withTopArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(0f, size.toFloat()), childPosition[1])
        assertEquals(Offset(0f, size.toFloat() * 2), childPosition[2])
    }

    @Test
    fun testColumn_withBottomArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, verticalArrangement = Arrangement.Bottom) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(Offset(0f, (root.height - size.toFloat() * 3)), childPosition[0])
        assertEquals(Offset(0f, (root.height - size.toFloat() * 2)), childPosition[1])
        assertEquals(Offset(0f, (root.height - size.toFloat())), childPosition[2])
    }

    @Test
    fun testColumn_withCenterArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, verticalArrangement = Arrangement.Center) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val extraSpace = root.height - size * 3f
        assertEquals(
            Offset(0f, (extraSpace / 2).roundToInt().toFloat()),
            childPosition[0]
        )
        assertEquals(
            Offset(0f, ((extraSpace / 2) + size.toFloat()).roundToInt().toFloat()),
            childPosition[1]
        )
        assertEquals(
            Offset(
                0f,
                ((extraSpace / 2) + size.toFloat() * 2f).roundToInt().toFloat()
            ),
            childPosition[2]
        )
    }

    @Test
    fun testColumn_withSpaceEvenlyArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, verticalArrangement = Arrangement.SpaceEvenly) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.height - size.toFloat() * 3) / 4
        assertEquals(Offset(0f, gap.roundToInt().toFloat()), childPosition[0])
        assertEquals(
            Offset(0f, (size.toFloat() + gap * 2).roundToInt().toFloat()),
            childPosition[1]
        )
        assertEquals(
            Offset(0f, (size.toFloat() * 2 + gap * 3f).roundToInt().toFloat()),
            childPosition[2]
        )
    }

    private fun calculateChildPositions(
        childPosition: Array<Offset>,
        parentLayoutCoordinates: LayoutCoordinates?,
        childLayoutCoordinates: Array<LayoutCoordinates?>
    ) {
        for (i in childPosition.indices) {
            childPosition[i] = parentLayoutCoordinates!!
                .childToLocal(childLayoutCoordinates[i]!!, Offset(0f, 0f))
        }
    }

    @Test
    fun testColumn_withSpaceBetweenArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, verticalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.height - size.toFloat() * 3f) / 2f
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(
            Offset(0f, (gap + size.toFloat()).roundToInt().toFloat()),
            childPosition[1]
        )
        assertEquals(
            Offset(0f, (gap * 2 + size.toFloat() * 2).roundToInt().toFloat()),
            childPosition[2]
        )
    }

    @Test
    fun testColumn_withSpaceAroundArrangement() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(4)
        val childPosition = arrayOf(
            Offset(-1f, -1f), Offset(-1f, -1f), Offset(-1f, -1f)
        )
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Center {
                Column(Modifier.fillMaxHeight().onPositioned { coordinates: LayoutCoordinates ->
                    parentLayoutCoordinates = coordinates
                    drawLatch.countDown()
                }, verticalArrangement = Arrangement.SpaceAround) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            }
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.height - size.toFloat() * 3f) / 3f
        assertEquals(Offset(0f, (gap / 2f).roundToInt().toFloat()), childPosition[0])
        assertEquals(
            Offset(0f, ((gap * 3f / 2f) + size.toFloat()).roundToInt().toFloat()),
            childPosition[1]
        )
        assertEquals(
            Offset(0f, ((gap * 5f / 2f) + size.toFloat() * 2f).roundToInt().toFloat()),
            childPosition[2]
        )
    }

    @Test
    fun testColumn_withSpacedByArrangement() = with(density) {
        val spacePx = 10f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Row {
                Column(
                    verticalArrangement = Arrangement.spacedBy(space),
                    modifier = Modifier.onPositioned {
                        assertEquals((sizePx * 2 + spacePx).roundToInt(), it.size.height)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(0f, it.positionInParent.x)
                        latch.countDown()
                    })
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(sizePx + spacePx, it.positionInParent.y)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testColumn_withSpacedByAlignedArrangement() = with(density) {
        val spacePx = 10f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val columnSizePx = 50
        val columnSize = columnSizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Row {
                Column(
                    verticalArrangement = Arrangement.spacedBy(space, Alignment.Bottom),
                    modifier = Modifier.size(columnSize).onPositioned {
                        assertEquals(columnSizePx, it.size.height)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(columnSizePx - spacePx - sizePx * 2, it.positionInParent.y)
                        latch.countDown()
                    })
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(columnSizePx - sizePx, it.positionInParent.y)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testColumn_withSpacedByArrangement_insufficientSpace() = with(density) {
        val spacePx = 15f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val columnSizePx = 50f
        val columnSize = columnSizePx.toDp()
        val latch = CountDownLatch(4)
        show {
            Row {
                Column(
                    verticalArrangement = Arrangement.spacedBy(space),
                    modifier = Modifier.size(columnSize).onPositioned {
                        assertEquals(columnSizePx.roundToInt(), it.size.height)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(0f, it.positionInParent.y)
                        assertEquals(sizePx.roundToInt(), it.size.height)
                        latch.countDown()
                    })
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(sizePx + spacePx, it.positionInParent.y)
                        assertEquals((columnSizePx - spacePx - sizePx).roundToInt(), it.size.height)
                        latch.countDown()
                    })
                    Box(Modifier.preferredSize(size).onPositioned {
                        assertEquals(columnSizePx, it.positionInParent.y)
                        assertEquals(0, it.size.height)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testColumn_withAlignedArrangement() = with(density) {
        val sizePx = 20f
        val size = sizePx.toDp()
        val columnSizePx = 50
        val columnSize = columnSizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Row {
                Column(
                    verticalArrangement = Arrangement.aligned(Alignment.Bottom),
                    modifier = Modifier.size(columnSize).onPositioned {
                        assertEquals(columnSizePx, it.size.height)
                        latch.countDown()
                    }
                ) {
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(columnSizePx - sizePx * 2, it.positionInParent.y)
                        latch.countDown()
                    })
                    Box(Modifier.size(size).onPositioned {
                        assertEquals(columnSizePx - sizePx, it.positionInParent.y)
                        latch.countDown()
                    })
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testRow_doesNotUseMinConstraintsOnChildren() = with(density) {
        val sizeDp = 50.toDp()
        val childSizeDp = 30.toDp()
        val childSize = childSizeDp.toIntPx()

        val layoutLatch = CountDownLatch(1)
        val containerSize = Ref<IntSize>()
        show {
            Center {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(sizeDp, sizeDp)
                ) {
                    Row {
                        Spacer(
                            Modifier.preferredSize(width = childSizeDp, height = childSizeDp)
                                .onPositioned { coordinates: LayoutCoordinates ->
                                    containerSize.value = coordinates.size
                                    layoutLatch.countDown()
                                })
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(childSize, childSize), containerSize.value)
    }

    @Test
    fun testColumn_doesNotUseMinConstraintsOnChildren() = with(density) {
        val sizeDp = 50.toDp()
        val childSizeDp = 30.toDp()
        val childSize = childSizeDp.toIntPx()

        val layoutLatch = CountDownLatch(1)
        val containerSize = Ref<IntSize>()
        show {
            Center {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(sizeDp, sizeDp)
                ) {
                    Column {
                        Spacer(
                            Modifier.preferredSize(width = childSizeDp, height = childSizeDp).then(
                                Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                    containerSize.value = coordinates.size
                                    layoutLatch.countDown()
                                })
                        )
                    }
                }
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(childSize, childSize), containerSize.value)
    }
    // endregion

    // region Intrinsic measurement tests
    @Test
    fun testRow_withNoWeightChildren_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Row {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth()) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row {
                Container(
                    Modifier.aspectRatio(2f)
                        .align(Alignment.Top),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.align(Alignment.CenterVertically),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row {
                Container(
                    Modifier.aspectRatio(2f).alignWithSiblings(FirstBaseline),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.alignWithSiblings { it.width },
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Container(
                    Modifier.align(Alignment.CenterVertically).aspectRatio(2f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.align(Alignment.CenterVertically),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Container(
                    Modifier.align(Alignment.Bottom).aspectRatio(2f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.align(Alignment.Bottom),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Container(Modifier.fillMaxHeight().aspectRatio(2f), children = emptyContent())
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.fillMaxHeight(),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(50.toDp().toIntPx(), minIntrinsicWidth(0.toDp().toIntPx()))
            assertEquals(
                25.toDp().toIntPx() * 2 + 50.toDp().toIntPx(),
                minIntrinsicWidth(25.toDp().toIntPx())
            )
            assertEquals(50.toDp().toIntPx(), minIntrinsicWidth(Constraints.Infinity))
            // Min height.
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(0.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(70.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(Constraints.Infinity))
            // Max width.
            assertEquals(50.toDp().toIntPx(), maxIntrinsicWidth(0.toDp().toIntPx()))
            assertEquals(
                25.toDp().toIntPx() * 2 + 50.toDp().toIntPx(),
                maxIntrinsicWidth(25.toDp().toIntPx())
            )
            assertEquals(50.toDp().toIntPx(), maxIntrinsicWidth(Constraints.Infinity))
            // Max height.
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(0.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(70.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(Constraints.Infinity))
        }
    }

    @Test
    fun testRow_withWeightChildren_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Row {
                ConstrainedBox(
                    DpConstraints.fixed(20.toDp(), 30.toDp()),
                    Modifier.weight(3f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 40.toDp()),
                    Modifier.weight(2f),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(2f).weight(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(20.toDp(), 30.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row {
                ConstrainedBox(
                    DpConstraints.fixed(20.toDp(), 30.toDp()),
                    Modifier.weight(3f).align(Alignment.Top),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 40.toDp()),
                    Modifier.weight(2f).align(Alignment.CenterVertically),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(2f).weight(2f), children = emptyContent())
                ConstrainedBox(
                    DpConstraints.fixed(20.toDp(), 30.toDp()),
                    Modifier.align(Alignment.Bottom),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(horizontalArrangement = Arrangement.Start) {
                ConstrainedBox(
                    DpConstraints.fixed(20.toDp(), 30.toDp()),
                    Modifier.weight(3f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 40.toDp()),
                    Modifier.weight(2f),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(2f).weight(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(20.toDp(), 30.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row(horizontalArrangement = Arrangement.Center) {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(20.toDp(), 30.toDp()),
                    modifier = Modifier.weight(3f).align(Alignment.CenterVertically),
                    children = emptyContent()
                )
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 40.toDp()),
                    modifier = Modifier.weight(2f).align(Alignment.CenterVertically),
                    children = emptyContent()
                )
                Container(
                    Modifier.aspectRatio(2f).weight(2f).align(Alignment.CenterVertically),
                    children = emptyContent()
                )
                ConstrainedBox(
                    constraints = DpConstraints.fixed(20.toDp(), 30.toDp()),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(horizontalArrangement = Arrangement.End) {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(20.toDp(), 30.toDp()),
                    modifier = Modifier.weight(3f).align(Alignment.Bottom),
                    children = emptyContent()
                )
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 40.toDp()),
                    modifier = Modifier.weight(2f).align(Alignment.Bottom),
                    children = emptyContent()
                )
                Container(
                    Modifier.aspectRatio(2f).weight(2f).align(Alignment.Bottom),
                    children = emptyContent()
                )
                ConstrainedBox(
                    constraints = DpConstraints.fixed(20.toDp(), 30.toDp()),
                    modifier = Modifier.align(Alignment.Bottom),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(horizontalArrangement = Arrangement.SpaceAround) {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(20.toDp(), 30.toDp()),
                    modifier = Modifier.weight(3f).fillMaxHeight(),
                    children = emptyContent()
                )
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 40.toDp()),
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    children = emptyContent()
                )
                Container(
                    Modifier.aspectRatio(2f).weight(2f).fillMaxHeight(),
                    children = emptyContent()
                )
                ConstrainedBox(
                    constraints = DpConstraints.fixed(20.toDp(), 30.toDp()),
                    modifier = Modifier.fillMaxHeight(),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                ConstrainedBox(
                    DpConstraints.fixed(20.toDp(), 30.toDp()),
                    Modifier.weight(3f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 40.toDp()),
                    Modifier.weight(2f),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(2f).weight(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(20.toDp(), 30.toDp()), children = emptyContent())
            }
        }, @Composable {
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                ConstrainedBox(
                    DpConstraints.fixed(20.toDp(), 30.toDp()),
                    Modifier.weight(3f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 40.toDp()),
                    Modifier.weight(2f),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(2f).weight(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(20.toDp(), 30.toDp()), children = emptyContent())
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicWidth(0)
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicWidth(10.toDp().toIntPx())
            )
            assertEquals(
                25.toDp().toIntPx() * 2 / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicWidth(25.toDp().toIntPx())
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicWidth(Constraints.Infinity)
            )
            // Min height.
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(0.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(125.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), minIntrinsicHeight(370.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(Constraints.Infinity))
            // Max width.
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicWidth(0)
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicWidth(10.toDp().toIntPx())
            )
            assertEquals(
                25.toDp().toIntPx() * 2 / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicWidth(25.toDp().toIntPx())
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicWidth(Constraints.Infinity)
            )
            // Max height.
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(0.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(125.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), maxIntrinsicHeight(370.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(Constraints.Infinity))
        }
    }

    @Test
    fun testColumn_withNoWeightChildren_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Column {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Column {
                Container(
                    Modifier.aspectRatio(2f).align(Alignment.Start),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.align(Alignment.End),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Column {
                Container(
                    Modifier.aspectRatio(2f).alignWithSiblings { 0 },
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.alignWithSiblings(TestVerticalLine),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight()) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Top) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Container(
                    Modifier.align(Alignment.CenterHorizontally).aspectRatio(2f),
                    children = emptyContent()
                )
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Container(
                    Modifier.align(Alignment.End).aspectRatio(2f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.align(Alignment.End),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
                Container(Modifier.fillMaxWidth().aspectRatio(2f), children = emptyContent())
                ConstrainedBox(
                    DpConstraints.fixed(50.toDp(), 40.toDp()),
                    Modifier.fillMaxWidth(),
                    children = emptyContent()
                )
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }, @Composable {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Container(Modifier.aspectRatio(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(50.toDp(), 40.toDp()), children = emptyContent())
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(50.toDp().toIntPx(), minIntrinsicWidth(0.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), minIntrinsicWidth(25.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), minIntrinsicWidth(Constraints.Infinity))
            // Min height.
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(0.toDp().toIntPx()))
            assertEquals(
                50.toDp().toIntPx() / 2 + 40.toDp().toIntPx(),
                minIntrinsicHeight(50.toDp().toIntPx())
            )
            assertEquals(40.toDp().toIntPx(), minIntrinsicHeight(Constraints.Infinity))
            // Max width.
            assertEquals(50.toDp().toIntPx(), maxIntrinsicWidth(0.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), maxIntrinsicWidth(25.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), maxIntrinsicWidth(Constraints.Infinity))
            // Max height.
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(0.toDp().toIntPx()))
            assertEquals(
                50.toDp().toIntPx() / 2 + 40.toDp().toIntPx(),
                maxIntrinsicHeight(50.toDp().toIntPx())
            )
            assertEquals(40.toDp().toIntPx(), maxIntrinsicHeight(Constraints.Infinity))
        }
    }

    @Test
    fun testColumn_withWeightChildren_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Column {
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 20.toDp()),
                    Modifier.weight(3f),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(40.toDp(), 30.toDp()),
                    Modifier.weight(2f),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(0.5f).weight(2f), children = emptyContent())
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp()), children = emptyContent())
            }
        }, @Composable {
            Column {
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 20.toDp()),
                    Modifier.weight(3f).align(Alignment.Start),
                    children = emptyContent()
                )
                ConstrainedBox(
                    DpConstraints.fixed(40.toDp(), 30.toDp()),
                    Modifier.weight(2f).align(Alignment.CenterHorizontally),
                    children = emptyContent()
                )
                Container(Modifier.aspectRatio(0.5f).weight(2f)) { }
                ConstrainedBox(
                    DpConstraints.fixed(30.toDp(), 20.toDp()),
                    Modifier.align(Alignment.End)
                ) { }
            }
        }, @Composable {
            Column(verticalArrangement = Arrangement.Top) {
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp()), Modifier.weight(3f)) { }
                ConstrainedBox(DpConstraints.fixed(40.toDp(), 30.toDp()), Modifier.weight(2f)) { }
                Container(Modifier.aspectRatio(0.5f).weight(2f)) { }
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp())) { }
            }
        }, @Composable {
            Column(verticalArrangement = Arrangement.Center) {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 20.toDp()),
                    modifier = Modifier.weight(3f).align(Alignment.CenterHorizontally)
                ) { }
                ConstrainedBox(
                    constraints = DpConstraints.fixed(40.toDp(), 30.toDp()),
                    modifier = Modifier.weight(2f).align(Alignment.CenterHorizontally)
                ) { }
                Container(
                    Modifier.aspectRatio(0.5f).weight(2f).align(Alignment.CenterHorizontally)
                ) { }
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 20.toDp()),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { }
            }
        }, @Composable {
            Column(verticalArrangement = Arrangement.Bottom) {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 20.toDp()),
                    modifier = Modifier.weight(3f).align(Alignment.End)
                ) { }
                ConstrainedBox(
                    constraints = DpConstraints.fixed(40.toDp(), 30.toDp()),
                    modifier = Modifier.weight(2f).align(Alignment.End)
                ) { }
                Container(
                    Modifier.aspectRatio(0.5f).weight(2f).align(Alignment.End)
                ) { }
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 20.toDp()),
                    modifier = Modifier.align(Alignment.End)
                ) { }
            }
        }, @Composable {
            Column(verticalArrangement = Arrangement.SpaceAround) {
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 20.toDp()),
                    modifier = Modifier.weight(3f).fillMaxWidth()
                ) { }
                ConstrainedBox(
                    constraints = DpConstraints.fixed(40.toDp(), 30.toDp()),
                    modifier = Modifier.weight(2f).fillMaxWidth()
                ) { }
                Container(
                    Modifier.aspectRatio(0.5f).weight(2f).fillMaxWidth()
                ) { }
                ConstrainedBox(
                    constraints = DpConstraints.fixed(30.toDp(), 20.toDp()),
                    modifier = Modifier.fillMaxWidth()
                ) { }
            }
        }, @Composable {
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp()), Modifier.weight(3f)) { }
                ConstrainedBox(DpConstraints.fixed(40.toDp(), 30.toDp()), Modifier.weight(2f)) { }
                Container(Modifier.aspectRatio(0.5f).then(Modifier.weight(2f))) { }
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp())) { }
            }
        }, @Composable {
            Column(verticalArrangement = Arrangement.SpaceEvenly) {
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp()), Modifier.weight(3f)) { }
                ConstrainedBox(DpConstraints.fixed(40.toDp(), 30.toDp()), Modifier.weight(2f)) { }
                Container(Modifier.aspectRatio(0.5f).then(Modifier.weight(2f))) { }
                ConstrainedBox(DpConstraints.fixed(30.toDp(), 20.toDp())) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(40.toDp().toIntPx(), minIntrinsicWidth(0.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), minIntrinsicWidth(125.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), minIntrinsicWidth(370.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), minIntrinsicWidth(Constraints.Infinity))
            // Min height.
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicHeight(0)
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicHeight(10.toDp().toIntPx())
            )
            assertEquals(
                25.toDp().toIntPx() * 2 / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicHeight(25.toDp().toIntPx())
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                minIntrinsicHeight(Constraints.Infinity)
            )
            // Max width.
            assertEquals(40.toDp().toIntPx(), maxIntrinsicWidth(0.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), maxIntrinsicWidth(125.toDp().toIntPx()))
            assertEquals(50.toDp().toIntPx(), maxIntrinsicWidth(370.toDp().toIntPx()))
            assertEquals(40.toDp().toIntPx(), maxIntrinsicWidth(Constraints.Infinity))
            // Max height.
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicHeight(0)
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicHeight(10.toDp().toIntPx())
            )
            assertEquals(
                25.toDp().toIntPx() * 2 / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicHeight(25.toDp().toIntPx())
            )
            assertEquals(
                30.toDp().toIntPx() / 2 * 7 + 20.toDp().toIntPx(),
                maxIntrinsicHeight(Constraints.Infinity)
            )
        }
    }

    @Test
    fun testRow_withWIHOChild_hasCorrectIntrinsicMeasurements() = with(density) {
        val dividerWidth = 10.dp
        val rowWidth = 40.dp

        val positionedLatch = CountDownLatch(1)
        show {
            @OptIn(ExperimentalLayout::class)
            Row(Modifier.width(rowWidth).preferredHeight(IntrinsicSize.Min)) {
                Container(Modifier.width(dividerWidth).fillMaxHeight().onPositioned {
                    assertEquals(it.size.height, (rowWidth.toIntPx() - dividerWidth.toIntPx()) / 2)
                    positionedLatch.countDown()
                }) {}
                Layout(
                    children = {},
                    minIntrinsicWidthMeasureBlock = { _, _ -> rowWidth.toIntPx() / 10 },
                    maxIntrinsicWidthMeasureBlock = { _, _ -> rowWidth.toIntPx() * 2 },
                    minIntrinsicHeightMeasureBlock = { _, w -> w / 2 },
                    maxIntrinsicHeightMeasureBlock = { _, w -> w / 2 }
                ) { _, constraints -> layout(constraints.maxWidth, constraints.maxWidth / 2) {} }
            }
        }

        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testColumn_withHIWOChild_hasCorrectIntrinsicMeasurements() = with(density) {
        val dividerHeight = 10.dp
        val columnHeight = 40.dp

        val positionedLatch = CountDownLatch(1)
        show {
            @OptIn(ExperimentalLayout::class)
            Column(Modifier.height(columnHeight).preferredWidth(IntrinsicSize.Min)) {
                Container(Modifier.height(dividerHeight).fillMaxWidth().onPositioned {
                    assertEquals(
                        it.size.width,
                        (columnHeight.toIntPx() - dividerHeight.toIntPx()) / 2
                    )
                    positionedLatch.countDown()
                }) {}
                Layout(
                    children = {},
                    minIntrinsicWidthMeasureBlock = { _, h -> h / 2 },
                    maxIntrinsicWidthMeasureBlock = { _, h -> h / 2 },
                    minIntrinsicHeightMeasureBlock = { _, _ -> columnHeight.toIntPx() / 10 },
                    maxIntrinsicHeightMeasureBlock = { _, _ -> columnHeight.toIntPx() * 2 }
                ) { _, constraints -> layout(constraints.maxHeight / 2, constraints.maxHeight) {} }
            }
        }

        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))
    }

    // endregion

    // region Modifiers specific tests
    @Test
    fun testRowColumnModifiersChain_leftMostWins() = with(density) {
        val positionedLatch = CountDownLatch(1)
        val containerHeight = Ref<Int>()
        val columnHeight = 24

        show {
            Stack {
                Column(Modifier.preferredHeight(columnHeight.toDp())) {
                    Container(
                        Modifier.weight(2f)
                            .weight(1f)
                            .onPositioned { coordinates ->
                                containerHeight.value = coordinates.size.height
                                positionedLatch.countDown()
                            },
                        children = emptyContent()
                    )
                    Container(Modifier.weight(1f), children = emptyContent())
                }
            }
        }

        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertNotNull(containerHeight.value)
        assertEquals(columnHeight * 2 / 3, containerHeight.value)
    }

    @Test
    fun testRelativeToSiblingsModifiersChain_leftMostWins() = with(density) {
        val positionedLatch = CountDownLatch(1)
        val containerSize = Ref<IntSize>()
        val containerPosition = Ref<Offset>()
        val size = 40.dp

        show {
            Row {
                Container(
                    modifier = Modifier.alignWithSiblings { it.height },
                    width = size,
                    height = size,
                    children = emptyContent()
                )
                Container(
                    modifier = Modifier.alignWithSiblings { 0 }
                        .alignWithSiblings { it.height / 2 }
                        .onPositioned { coordinates: LayoutCoordinates ->
                            containerSize.value = coordinates.size
                            containerPosition.value = coordinates.positionInRoot
                            positionedLatch.countDown()
                        },
                    width = size,
                    height = size,
                    children = emptyContent()
                )
            }
        }

        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertNotNull(containerSize)
        assertEquals(Offset(size.toPx(), size.toPx()), containerPosition.value)
    }
    // endregion

    // region Rtl tests
    @Test
    fun testRow_Rtl_arrangementStart() = with(density) {
        val sizeDp = 35.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childPosition = arrayOf(Offset.Zero, Offset.Zero)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(Modifier.fillMaxWidth()) {
                    Container(
                        Modifier.preferredSize(sizeDp).onPositioned { coordinates ->
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }

                    Container(
                        Modifier.preferredSize(sizeDp * 2)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        val root = findOwnerView()
        waitForDraw(root)
        val rootWidth = root.width

        assertEquals(Offset((rootWidth - size.toFloat()), 0f), childPosition[0])
        assertEquals(
            Offset((rootWidth - (sizeDp.toPx() * 3f).roundToInt()).toFloat(), 0f),
            childPosition[1]
        )
    }

    @Test
    fun testRow_Rtl_arrangementCenter() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val extraSpace = root.width - size * 3
        assertEquals(
            Offset(
                ((extraSpace / 2f) + size.toFloat() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[0]
        )
        assertEquals(
            Offset(((extraSpace / 2f) + size.toFloat()).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(Offset((extraSpace / 2f).roundToInt().toFloat(), 0f), childPosition[2])
    }

    @Test
    fun testRow_Rtl_arrangementSpaceEvenly() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in childPosition.indices) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width - size.toFloat() * 3f) / 4f
        assertEquals(
            Offset((size.toFloat() * 2f + gap * 3f).roundToInt().toFloat(), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset((size.toFloat() + gap * 2f).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset(gap.roundToInt().toFloat(), 0f), childPosition[2]
        )
    }

    @Test
    fun testRow_Rtl_arrangementSpaceBetween() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in childPosition.indices) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width - size.toFloat() * 3) / 2
        assertEquals(
            Offset((gap * 2 + size.toFloat() * 2).roundToInt().toFloat(), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset((gap + size.toFloat()).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(Offset(0f, 0f), childPosition[2])
    }

    @Test
    fun testRow_Rtl_arrangementSpaceAround() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates = arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] = coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(childPosition, parentLayoutCoordinates, childLayoutCoordinates)

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width.toFloat() - size * 3) / 3
        assertEquals(
            Offset(((gap * 5 / 2) + size.toFloat() * 2).roundToInt().toFloat(), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset(((gap * 3 / 2) + size.toFloat()).roundToInt().toFloat(), 0f),
            childPosition[1]
        )
        assertEquals(Offset((gap / 2f).roundToInt().toFloat(), 0f), childPosition[2])
    }

    @Test
    fun testRow_Rtl_arrangementEnd() = with(density) {
        val sizeDp = 35.toDp()

        val drawLatch = CountDownLatch(2)
        val childPosition = arrayOf(Offset.Zero, Offset.Zero)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Container(
                        Modifier.preferredSize(sizeDp).onPositioned { coordinates ->
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }

                    Container(
                        Modifier.preferredSize(sizeDp * 2)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        assertEquals(
            Offset(
                (sizeDp.toPx() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[0]
        )
        assertEquals(Offset(0f, 0f), childPosition[1])
    }

    @Test
    fun testRow_Rtl_withSpacedByAlignedArrangement() = with(density) {
        val spacePx = 10f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val rowSizePx = 50
        val rowSize = rowSizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space, Alignment.End),
                        modifier = Modifier.size(rowSize).onPositioned {
                            assertEquals(rowSizePx, it.size.width)
                            latch.countDown()
                        }
                    ) {
                        Box(Modifier.size(size).onPositioned {
                            assertEquals(sizePx + spacePx, it.positionInParent.x)
                            latch.countDown()
                        })
                        Box(Modifier.size(size).onPositioned {
                            assertEquals(0f, it.positionInParent.x)
                            latch.countDown()
                        })
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testColumn_Rtl_gravityStart() = with(density) {
        val sizeDp = 35.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childPosition = arrayOf(Offset.Zero, Offset.Zero)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxWidth()) {
                    Container(
                        Modifier.preferredSize(sizeDp).onPositioned { coordinates ->
                            childPosition[0] = coordinates.positionInRoot
                            drawLatch.countDown()
                        }
                    ) {
                    }

                    Container(
                        Modifier.preferredSize(sizeDp * 2)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        val root = findOwnerView()
        waitForDraw(root)
        val rootWidth = root.width

        assertEquals(Offset((rootWidth - size.toFloat()), 0f), childPosition[0])
        assertEquals(
            Offset(
                (rootWidth - (sizeDp * 2f).toPx()).roundToInt().toFloat(),
                size.toFloat()
            ),
            childPosition[1]
        )
    }

    @Test
    fun testColumn_Rtl_gravityEnd() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childPosition = arrayOf(Offset.Zero, Offset.Zero)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxWidth()) {
                    Container(
                        Modifier.preferredSize(sizeDp)
                            .align(Alignment.End)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[0] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }

                    Container(
                        Modifier.preferredSize(sizeDp * 2)
                            .align(Alignment.End)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(0f, size.toFloat()), childPosition[1])
    }

    @Test
    fun testColumn_Rtl_gravityRelativeToSiblings() = with(density) {
        val sizeDp = 50.toDp()
        val size = sizeDp.toIntPx()

        val drawLatch = CountDownLatch(2)
        val childPosition = arrayOf(Offset.Zero, Offset.Zero)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxWidth()) {
                    Container(
                        Modifier.preferredSize(sizeDp)
                            .alignWithSiblings { it.width }
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[0] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }

                    Container(
                        Modifier.preferredSize(sizeDp)
                            .alignWithSiblings { it.width / 2 }
                            .onPositioned { coordinates: LayoutCoordinates ->
                                childPosition[1] = coordinates.positionInRoot
                                drawLatch.countDown()
                            }
                    ) {
                    }
                }
            }
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        val root = findOwnerView()
        waitForDraw(root)
        val rootWidth = root.width

        assertEquals(
            Offset((rootWidth - size.toFloat()), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset(
                (rootWidth - size.toFloat() * 1.5f).roundToInt().toFloat(),
                size.toFloat()
            ),
            childPosition[1]
        )
    }
    //endregion

    // region AbsoluteArrangement tests
    @Test
    fun testRow_absoluteArrangementLeft() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onPositioned { coordinates: LayoutCoordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                horizontalArrangement = AbsoluteArrangement.Left
            ) {
                for (i in childPosition.indices) {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childLayoutCoordinates[i] = coordinates
                            drawLatch.countDown()
                        },
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(size.toFloat(), 0f), childPosition[1])
        assertEquals(
            Offset(size.toFloat() * 2, 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_Rtl_absoluteArrangementLeft() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = AbsoluteArrangement.Left
                ) {
                    for (i in childPosition.indices) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] =
                                    coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(Offset(size.toFloat(), 0f), childPosition[1])
        assertEquals(
            Offset(size.toFloat() * 2, 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_absoluteArrangementRight() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPositioned { coordinates: LayoutCoordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                horizontalArrangement = AbsoluteArrangement.Right
            ) {
                for (i in childPosition.indices) {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childLayoutCoordinates[i] = coordinates
                            drawLatch.countDown()
                        },
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            Offset((root.width - size.toFloat() * 3), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset((root.width - size.toFloat() * 2), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset((root.width - size.toFloat()), 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_Rtl_absoluteArrangementRight() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates: LayoutCoordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = AbsoluteArrangement.Right
                ) {
                    for (i in childPosition.indices) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] =
                                    coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        assertEquals(
            Offset((root.width - size.toFloat() * 3), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset((root.width - size.toFloat() * 2), 0f),
            childPosition[1]
        )
        assertEquals(
            Offset((root.width - size.toFloat()), 0f),
            childPosition[2]
        )
    }

    @Test
    fun testRow_absoluteArrangementCenter() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPositioned { coordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                horizontalArrangement = AbsoluteArrangement.Center
            ) {
                for (i in 0 until childPosition.size) {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childLayoutCoordinates[i] = coordinates
                            drawLatch.countDown()
                        },
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        val extraSpace = root.width - size * 3
        assertEquals(
            Offset(
                (extraSpace / 2f).roundToInt().toFloat(),
                0f
            ), childPosition[0]
        )
        assertEquals(
            Offset(
                ((extraSpace / 2f) + size.toFloat()).roundToInt().toFloat(),
                0f
            ),
            childPosition[1]
        )
        assertEquals(
            Offset(
                ((extraSpace / 2f) + size.toFloat() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_Rtl_absoluteArrangementCenter() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPositioned { coordinates ->
                            parentLayoutCoordinates = coordinates
                            drawLatch.countDown()
                        },
                    horizontalArrangement = AbsoluteArrangement.Center
                ) {
                    for (i in 0 until childPosition.size) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                childLayoutCoordinates[i] =
                                    coordinates
                                drawLatch.countDown()
                            },
                            children = emptyContent()
                        )
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        val extraSpace = root.width - size * 3
        assertEquals(
            Offset(
                (extraSpace / 2f).roundToInt().toFloat(),
                0f
            ), childPosition[0]
        )
        assertEquals(
            Offset(
                ((extraSpace / 2f) + size.toFloat()).roundToInt().toFloat(),
                0f
            ),
            childPosition[1]
        )
        assertEquals(
            Offset(
                ((extraSpace / 2f) + size.toFloat() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_absoluteArrangementSpaceEvenly() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPositioned { coordinates: LayoutCoordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                horizontalArrangement = AbsoluteArrangement.SpaceEvenly
            ) {
                for (i in childPosition.indices) {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childLayoutCoordinates[i] = coordinates
                            drawLatch.countDown()
                        },
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width - size.toFloat() * 3f) / 4f
        assertEquals(
            Offset(gap.roundToInt().toFloat(), 0f), childPosition[0]
        )
        assertEquals(
            Offset(
                (size.toFloat() + gap * 2f).roundToInt().toFloat(),
                0f
            ),
            childPosition[1]
        )
        assertEquals(
            Offset(
                (size.toFloat() * 2f + gap * 3f).roundToInt().toFloat(),
                0f
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_Row_absoluteArrangementSpaceEvenly() =
        with(density) {
            val size = 100
            val sizeDp = size.toDp()

            val drawLatch = CountDownLatch(4)
            val childPosition = Array(3) { Offset.Zero }
            val childLayoutCoordinates =
                arrayOfNulls<LayoutCoordinates?>(childPosition.size)
            var parentLayoutCoordinates: LayoutCoordinates? = null
            show {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPositioned { coordinates: LayoutCoordinates ->
                                parentLayoutCoordinates =
                                    coordinates
                                drawLatch.countDown()
                            },
                        horizontalArrangement = AbsoluteArrangement.SpaceEvenly
                    ) {
                        for (i in childPosition.indices) {
                            Container(
                                width = sizeDp,
                                height = sizeDp,
                                modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                    childLayoutCoordinates[i] =
                                        coordinates
                                    drawLatch.countDown()
                                },
                                children = emptyContent()
                            )
                        }
                    }
                }
            }
            assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

            calculateChildPositions(
                childPosition,
                parentLayoutCoordinates,
                childLayoutCoordinates
            )

            val root = findOwnerView()
            waitForDraw(root)

            val gap = (root.width - size.toFloat() * 3f) / 4f
            assertEquals(
                Offset(gap.roundToInt().toFloat(), 0f),
                childPosition[0]
            )
            assertEquals(
                Offset(
                    (size.toFloat() + gap * 2f).roundToInt().toFloat(),
                    0f
                ),
                childPosition[1]
            )
            assertEquals(
                Offset(
                    (size.toFloat() * 2f + gap * 3f).roundToInt().toFloat(),
                    0f
                ),
                childPosition[2]
            )
        }

    @Test
    fun testRow_absoluteArrangementSpaceBetween() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPositioned { coordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                horizontalArrangement = AbsoluteArrangement.SpaceBetween
            ) {
                for (i in childPosition.indices) {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childLayoutCoordinates[i] = coordinates
                            drawLatch.countDown()
                        },
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width - size.toFloat() * 3) / 2
        assertEquals(Offset(0f, 0f), childPosition[0])
        assertEquals(
            Offset(
                (gap + size.toFloat()).roundToInt().toFloat(),
                0f
            ),
            childPosition[1]
        )
        assertEquals(
            Offset(
                (gap * 2 + size.toFloat() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_Row_absoluteArrangementSpaceBetween() =
        with(density) {
            val size = 100
            val sizeDp = size.toDp()

            val drawLatch = CountDownLatch(4)
            val childPosition = Array(3) { Offset.Zero }
            val childLayoutCoordinates =
                arrayOfNulls<LayoutCoordinates?>(childPosition.size)
            var parentLayoutCoordinates: LayoutCoordinates? = null
            show {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPositioned { coordinates ->
                                parentLayoutCoordinates =
                                    coordinates
                                drawLatch.countDown()
                            },
                        horizontalArrangement = AbsoluteArrangement.SpaceBetween
                    ) {
                        for (i in childPosition.indices) {
                            Container(
                                width = sizeDp,
                                height = sizeDp,
                                modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                    childLayoutCoordinates[i] =
                                        coordinates
                                    drawLatch.countDown()
                                },
                                children = emptyContent()
                            )
                        }
                    }
                }
            }
            assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

            calculateChildPositions(
                childPosition,
                parentLayoutCoordinates,
                childLayoutCoordinates
            )

            val root = findOwnerView()
            waitForDraw(root)

            val gap = (root.width - size.toFloat() * 3) / 2
            assertEquals(Offset(0f, 0f), childPosition[0])
            assertEquals(
                Offset(
                    (gap + size.toFloat()).roundToInt().toFloat(),
                    0f
                ),
                childPosition[1]
            )
            assertEquals(
                Offset(
                    (gap * 2 + size.toFloat() * 2).roundToInt().toFloat(),
                    0f
                ),
                childPosition[2]
            )
        }

    @Test
    fun testRow_absoluteArrangementSpaceAround() = with(density) {
        val size = 100
        val sizeDp = size.toDp()

        val drawLatch = CountDownLatch(4)
        val childPosition = Array(3) { Offset.Zero }
        val childLayoutCoordinates =
            arrayOfNulls<LayoutCoordinates?>(childPosition.size)
        var parentLayoutCoordinates: LayoutCoordinates? = null
        show {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPositioned { coordinates ->
                        parentLayoutCoordinates = coordinates
                        drawLatch.countDown()
                    },
                horizontalArrangement = AbsoluteArrangement.SpaceAround
            ) {
                for (i in 0 until childPosition.size) {
                    Container(
                        width = sizeDp,
                        height = sizeDp,
                        modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                            childLayoutCoordinates[i] = coordinates
                            drawLatch.countDown()
                        },
                        children = emptyContent()
                    )
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        calculateChildPositions(
            childPosition,
            parentLayoutCoordinates,
            childLayoutCoordinates
        )

        val root = findOwnerView()
        waitForDraw(root)

        val gap = (root.width.toFloat() - size * 3) / 3
        assertEquals(
            Offset((gap / 2f).roundToInt().toFloat(), 0f),
            childPosition[0]
        )
        assertEquals(
            Offset(
                ((gap * 3 / 2) + size.toFloat()).roundToInt().toFloat(),
                0f
            ),
            childPosition[1]
        )
        assertEquals(
            Offset(
                ((gap * 5 / 2) + size.toFloat() * 2).roundToInt().toFloat(),
                0f
            ),
            childPosition[2]
        )
    }

    @Test
    fun testRow_Rtl_absoluteArrangementSpaceAround() =
        with(density) {
            val size = 100
            val sizeDp = size.toDp()

            val drawLatch = CountDownLatch(4)
            val childPosition = Array(3) { Offset.Zero }
            val childLayoutCoordinates =
                arrayOfNulls<LayoutCoordinates?>(childPosition.size)
            var parentLayoutCoordinates: LayoutCoordinates? = null
            show {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPositioned { coordinates ->
                                parentLayoutCoordinates =
                                    coordinates
                                drawLatch.countDown()
                            },
                        horizontalArrangement = AbsoluteArrangement.SpaceAround
                    ) {
                        for (i in 0 until childPosition.size) {
                            Container(
                                width = sizeDp,
                                height = sizeDp,
                                modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
                                    childLayoutCoordinates[i] =
                                        coordinates
                                    drawLatch.countDown()
                                },
                                children = emptyContent()
                            )
                        }
                    }
                }
            }
            assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

            calculateChildPositions(
                childPosition,
                parentLayoutCoordinates,
                childLayoutCoordinates
            )

            val root = findOwnerView()
            waitForDraw(root)

            val gap = (root.width.toFloat() - size * 3) / 3
            assertEquals(
                Offset(
                    (gap / 2f).roundToInt().toFloat(),
                    0f
                ), childPosition[0]
            )
            assertEquals(
                Offset(
                    ((gap * 3 / 2) + size.toFloat()).roundToInt().toFloat(),
                    0f
                ),
                childPosition[1]
            )
            assertEquals(
                Offset(
                    ((gap * 5 / 2) + size.toFloat() * 2).roundToInt().toFloat(),
                    0f
                ),
                childPosition[2]
            )
        }

    @Test
    fun testRow_Rtl_withSpacedByAlignedAbsoluteArrangement() = with(density) {
        val spacePx = 10f
        val space = spacePx.toDp()
        val sizePx = 20f
        val size = sizePx.toDp()
        val rowSizePx = 50
        val rowSize = rowSizePx.toDp()
        val latch = CountDownLatch(3)
        show {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Column {
                    Row(
                        horizontalArrangement = AbsoluteArrangement.spacedBy(space, Alignment.End),
                        modifier = Modifier.size(rowSize).onPositioned {
                            assertEquals(rowSizePx, it.size.width)
                            latch.countDown()
                        }
                    ) {
                        Box(Modifier.size(size).onPositioned {
                            assertEquals(0f, it.positionInParent.x)
                            latch.countDown()
                        })
                        Box(Modifier.size(size).onPositioned {
                            assertEquals(sizePx + spacePx, it.positionInParent.x)
                            latch.countDown()
                        })
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }
    // endregion
}

private val TestHorizontalLine = HorizontalAlignmentLine(::min)
private val TestVerticalLine = VerticalAlignmentLine(::min)

@Composable
private fun BaselineTestLayout(
    width: Dp,
    height: Dp,
    baseline: Dp,
    modifier: Modifier,
    children: @Composable () -> Unit
) {
    Layout(
        children = children,
        modifier = modifier,
        measureBlock = { _, constraints ->
            val widthPx = max(width.toIntPx(), constraints.minWidth)
            val heightPx =
                max(height.toIntPx(), constraints.minHeight)
            layout(
                widthPx, heightPx,
                mapOf(
                    TestHorizontalLine to baseline.toIntPx(),
                    TestVerticalLine to baseline.toIntPx()
                )
            ) {}
        })
}

// Center composable function is deprected whereas FlexTest tests heavily depend on it.
@Composable
private fun Center(children: @Composable () -> Unit) {
    Layout(children) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        // The child cannot be larger than our max constraints, but we ignore min constraints.
        val placeable = measurable?.measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0
            )
        )

        // The layout is as large as possible for bounded constraints,
        // or wrap content otherwise.
        val layoutWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeable?.width ?: constraints.minWidth
        }
        val layoutHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            placeable?.height ?: constraints.minHeight
        }

        layout(layoutWidth, layoutHeight) {
            if (placeable != null) {
                val position = Alignment.Center.align(
                    IntSize(
                        layoutWidth - placeable.width,
                        layoutHeight - placeable.height
                    )
                )
                placeable.placeRelative(position.x, position.y)
            }
        }
    }
}