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

package androidx.compose.ui.layout

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onActive
import androidx.compose.runtime.onDispose
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.draw.assertColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidOwnerExtraAssertionsRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.assertPositionInRootIsEqualTo
import androidx.ui.test.assertWidthIsEqualTo
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.runOnIdle
import androidx.ui.test.waitForIdle
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
@OptIn(ExperimentalSubcomposeLayoutApi::class)
class SubcomposeLayoutTest {

    @get:Rule
    val rule = createComposeRule()
    @get:Rule
    val excessiveAssertions = AndroidOwnerExtraAssertionsRule()

    @Test
    fun useSizeOfTheFirstItemInSecondSubcomposition() {
        val firstTag = "first"
        val secondTag = "second"

        rule.setContent {
            SubcomposeLayout<Int> { constraints ->
                val first = subcompose(0) {
                    Spacer(Modifier.size(50.dp).testTag(firstTag))
                }.first().measure(constraints)

                // it is an input for the second subcomposition
                val halfFirstSize = (first.width / 2).toDp()

                val second = subcompose(1) {
                    Spacer(Modifier.size(halfFirstSize).testTag(secondTag))
                }.first().measure(constraints)

                layout(first.width, first.height) {
                    first.place(0, 0)
                    second.place(first.width - second.width, first.height - second.height)
                }
            }
        }

        onNodeWithTag(firstTag)
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertWidthIsEqualTo(50.dp)
            .assertHeightIsEqualTo(50.dp)

        onNodeWithTag(secondTag)
            .assertPositionInRootIsEqualTo(25.dp, 25.dp)
            .assertWidthIsEqualTo(25.dp)
            .assertHeightIsEqualTo(25.dp)
    }

    @Test
    fun subcomposeMultipleLayoutsInOneSlot() {
        val firstTag = "first"
        val secondTag = "second"
        val layoutTag = "layout"

        rule.setContent {
            SubcomposeLayout<Unit>(Modifier.testTag(layoutTag)) { constraints ->
                val placeables = subcompose(Unit) {
                    Spacer(Modifier.size(50.dp).testTag(firstTag))
                    Spacer(Modifier.size(30.dp).testTag(secondTag))
                }.map {
                    it.measure(constraints)
                }

                val maxWidth = placeables.maxByOrNull { it.width }!!.width
                val height = placeables.sumBy { it.height }

                layout(maxWidth, height) {
                    placeables.fold(0) { top, placeable ->
                        placeable.place(0, top)
                        top + placeable.height
                    }
                }
            }
        }

        onNodeWithTag(firstTag)
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertWidthIsEqualTo(50.dp)
            .assertHeightIsEqualTo(50.dp)

        onNodeWithTag(secondTag)
            .assertPositionInRootIsEqualTo(0.dp, 50.dp)
            .assertWidthIsEqualTo(30.dp)
            .assertHeightIsEqualTo(30.dp)

        onNodeWithTag(layoutTag)
            .assertWidthIsEqualTo(50.dp)
            .assertHeightIsEqualTo(80.dp)
    }

    @Test
    fun recompositionDeepInsideTheSlotDoesntRecomposeUnaffectedLayerOrRemeasure() {
        val model = mutableStateOf(0)
        var measuresCount = 0
        var recompositionsCount1 = 0
        var recompositionsCount2 = 0

        rule.setContent {
            SubcomposeLayout<Unit> { constraints ->
                measuresCount++
                val placeable = subcompose(Unit) {
                    recompositionsCount1++
                    Stack(Modifier.size(20.dp)) {
                        model.value // model read
                        recompositionsCount2++
                    }
                }.first().measure(constraints)

                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
        }

        runOnIdle { model.value++ }

        runOnIdle {
            assertEquals(1, measuresCount)
            assertEquals(1, recompositionsCount1)
            assertEquals(2, recompositionsCount2)
        }
    }

    @Test
    fun recompositionOfTheFirstSlotDoestAffectTheSecond() {
        val model = mutableStateOf(0)
        var recompositionsCount1 = 0
        var recompositionsCount2 = 0

        rule.setContent {
            SubcomposeLayout<Int> {
                subcompose(1) {
                    recompositionsCount1++
                    model.value // model read
                }
                subcompose(2) {
                    recompositionsCount2++
                }

                layout(100, 100) {
                }
            }
        }

        runOnIdle { model.value++ }

        runOnIdle {
            assertEquals(2, recompositionsCount1)
            assertEquals(1, recompositionsCount2)
        }
    }

    @Test
    fun addLayoutOnlyAfterRecomposition() {
        val addChild = mutableStateOf(false)
        val childTag = "child"
        val layoutTag = "layout"

        rule.setContent {
            SubcomposeLayout<Unit>(Modifier.testTag(layoutTag)) { constraints ->
                val placeables = subcompose(Unit) {
                    if (addChild.value) {
                        Spacer(Modifier.size(20.dp).testTag(childTag))
                    }
                }.map { it.measure(constraints) }

                val size = placeables.firstOrNull()?.width ?: 0
                layout(size, size) {
                    placeables.forEach { it.place(0, 0) }
                }
            }
        }

        onNodeWithTag(layoutTag)
            .assertWidthIsEqualTo(0.dp)
            .assertHeightIsEqualTo(0.dp)

        onNodeWithTag(childTag)
            .assertDoesNotExist()

        runOnIdle {
            addChild.value = true
        }

        onNodeWithTag(layoutTag)
            .assertWidthIsEqualTo(20.dp)
            .assertHeightIsEqualTo(20.dp)

        onNodeWithTag(childTag)
            .assertWidthIsEqualTo(20.dp)
            .assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun providingNewLambdaCausingRecomposition() {
        val content = mutableStateOf<@Composable () -> Unit>({
            Spacer(Modifier.size(10.dp))
        })

        rule.setContent {
            MySubcomposeLayout(content.value)
        }

        val updatedTag = "updated"

        runOnIdle {
            content.value = {
                Spacer(Modifier.size(10.dp).testTag(updatedTag))
            }
        }

        onNodeWithTag(updatedTag)
            .assertIsDisplayed()
    }

    @Composable
    private fun MySubcomposeLayout(slotContent: @Composable () -> Unit) {
        SubcomposeLayout<Unit> { constraints ->
            val placeables = subcompose(Unit, slotContent).map { it.measure(constraints) }
            val maxWidth = placeables.maxByOrNull { it.width }!!.width
            val height = placeables.sumBy { it.height }
            layout(maxWidth, height) {
                placeables.forEach { it.place(0, 0) }
            }
        }
    }

    @Test
    fun notSubcomposedSlotIsDisposed() {
        val addSlot = mutableStateOf(true)
        var composed = false
        var disposed = false

        rule.setContent {
            SubcomposeLayout<Unit> {
                if (addSlot.value) {
                    subcompose(Unit) {
                        onActive {
                            composed = true
                        }
                        onDispose {
                            disposed = true
                        }
                    }
                }
                layout(10, 10) {}
            }
        }

        runOnIdle {
            assertThat(composed).isTrue()
            assertThat(disposed).isFalse()

            addSlot.value = false
        }

        runOnIdle {
            assertThat(disposed).isTrue()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun slotsAreDrawnInTheOrderTheyComposed() {
        val layoutTag = "layout"

        rule.setContent {
            SubcomposeLayout<Color>(Modifier.testTag(layoutTag)) { constraints ->
                val first = subcompose(Color.Red) {
                    Spacer(Modifier.size(10.dp).background(Color.Red))
                }.first().measure(constraints)
                val second = subcompose(Color.Green) {
                    Spacer(Modifier.size(10.dp).background(Color.Green))
                }.first().measure(constraints)
                layout(first.width, first.height) {
                    first.place(0, 0)
                    second.place(0, 0)
                }
            }
        }

        waitForIdle()

        onNodeWithTag(layoutTag)
            .captureToBitmap()
            .assertCenterPixelColor(Color.Green)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun slotsCouldBeReordered() {
        val layoutTag = "layout"
        val firstSlotIsRed = mutableStateOf(true)

        rule.setContent {
            SubcomposeLayout<Color>(Modifier.testTag(layoutTag)) { constraints ->
                val firstColor = if (firstSlotIsRed.value) Color.Red else Color.Green
                val secondColor = if (firstSlotIsRed.value) Color.Green else Color.Red
                val first = subcompose(firstColor) {
                    Spacer(Modifier.size(10.dp).background(firstColor))
                }.first().measure(constraints)
                val second = subcompose(secondColor) {
                    Spacer(Modifier.size(10.dp).background(secondColor))
                }.first().measure(constraints)
                layout(first.width, first.height) {
                    first.place(0, 0)
                    second.place(0, 0)
                }
            }
        }

        onNodeWithTag(layoutTag)
            .captureToBitmap()
            .assertCenterPixelColor(Color.Green)

        runOnIdle {
            firstSlotIsRed.value = false
        }

        onNodeWithTag(layoutTag)
            .captureToBitmap()
            .assertCenterPixelColor(Color.Red)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun drawingOrderCouldBeChangedUsingZIndex() {
        val layoutTag = "layout"

        rule.setContent {
            SubcomposeLayout<Color>(Modifier.testTag(layoutTag)) { constraints ->
                val first = subcompose(Color.Red) {
                    Spacer(Modifier.size(10.dp).background(Color.Red).zIndex(1f))
                }.first().measure(constraints)
                val second = subcompose(Color.Green) {
                    Spacer(Modifier.size(10.dp).background(Color.Green))
                }.first().measure(constraints)
                layout(first.width, first.height) {
                    first.place(0, 0)
                    second.place(0, 0)
                }
            }
        }

        onNodeWithTag(layoutTag)
            .captureToBitmap()
            .assertCenterPixelColor(Color.Red)
    }

    @Test
    fun slotsAreDisposedWhenLayoutIsDisposed() {
        val addLayout = mutableStateOf(true)
        var firstDisposed = false
        var secondDisposed = false

        rule.setContent {
            if (addLayout.value) {
                SubcomposeLayout<Int> {
                    subcompose(0) {
                        onDispose {
                            firstDisposed = true
                        }
                    }
                    subcompose(1) {
                        onDispose {
                            secondDisposed = true
                        }
                    }
                    layout(10, 10) {}
                }
            }
        }

        runOnIdle {
            assertThat(firstDisposed).isFalse()
            assertThat(secondDisposed).isFalse()

            addLayout.value = false
        }

        runOnIdle {
            assertThat(firstDisposed).isTrue()
            assertThat(secondDisposed).isTrue()
        }
    }
}

fun Bitmap.assertCenterPixelColor(expectedColor: Color) {
    assertColor(expectedColor, width / 2, height / 2)
}
