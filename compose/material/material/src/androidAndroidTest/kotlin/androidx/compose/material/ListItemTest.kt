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

package androidx.compose.material

import androidx.compose.foundation.Box
import androidx.compose.foundation.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.Text
import androidx.compose.foundation.text.FirstBaseline
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.Ref
import androidx.compose.ui.onPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.filters.SmallTest
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.roundToInt

@SmallTest
@RunWith(JUnit4::class)
class ListItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    val icon24x24 by lazy { ImageAsset(width = 24.dp.toIntPx(), height = 24.dp.toIntPx()) }
    val icon40x40 by lazy { ImageAsset(width = 40.dp.toIntPx(), height = 40.dp.toIntPx()) }
    val icon56x56 by lazy { ImageAsset(width = 56.dp.toIntPx(), height = 56.dp.toIntPx()) }

    @Test
    fun listItem_oneLine_size() {
        val expectedHeightNoIcon = 48.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(text = { Text("Primary text") })
            }
            .assertHeightIsEqualTo(expectedHeightNoIcon)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_oneLine_withIcon24_size() {
        val expectedHeightSmallIcon = 56.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    text = { Text("Primary text") },
                    icon = { Icon(icon24x24) }
                )
            }
            .assertHeightIsEqualTo(expectedHeightSmallIcon)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_oneLine_withIcon56_size() {
        val expectedHeightLargeIcon = 72.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    text = { Text("Primary text") },
                    icon = { Icon(icon56x56) }
                )
            }
            .assertHeightIsEqualTo(expectedHeightLargeIcon)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_twoLine_size() {
        val expectedHeightNoIcon = 64.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    text = { Text("Primary text") },
                    secondaryText = { Text("Secondary text") }
                )
            }
            .assertHeightIsEqualTo(expectedHeightNoIcon)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_twoLine_withIcon_size() {
        val expectedHeightWithIcon = 72.dp

        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    text = { Text("Primary text") },
                    secondaryText = { Text("Secondary text") },
                    icon = { Icon(icon24x24) }
                )
            }
            .assertHeightIsEqualTo(expectedHeightWithIcon)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_threeLine_size() {
        val expectedHeight = 88.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    overlineText = { Text("OVERLINE") },
                    text = { Text("Primary text") },
                    secondaryText = { Text("Secondary text") }
                )
            }
            .assertHeightIsEqualTo(expectedHeight)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_threeLine_noSingleLine_size() {
        val expectedHeight = 88.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    text = { Text("Primary text") },
                    secondaryText = { Text("Secondary text with long text") },
                    singleLineSecondaryText = false
                )
            }
            .assertHeightIsEqualTo(expectedHeight)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_threeLine_metaText_size() {
        val expectedHeight = 88.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    overlineText = { Text("OVERLINE") },
                    text = { Text("Primary text") },
                    secondaryText = { Text("Secondary text") },
                    trailing = { Text("meta") }
                )
            }
            .assertHeightIsEqualTo(expectedHeight)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_threeLine_noSingleLine_metaText_size() {
        val expectedHeight = 88.dp
        composeTestRule
            .setMaterialContentForSizeAssertions {
                ListItem(
                    text = { Text("Primary text") },
                    secondaryText = { Text("Secondary text with long text") },
                    singleLineSecondaryText = false,
                    trailing = { Text("meta") }
                )
            }
            .assertHeightIsEqualTo(expectedHeight)
            .assertWidthFillsRoot()
    }

    @Test
    fun listItem_oneLine_positioning_noIcon() {
        val listItemHeight = 48.dp
        val expectedLeftPadding = 16.dp
        val expectedRightPadding = 16.dp

        val textPosition = Ref<Offset>()
        val textSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingSize = Ref<IntSize>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    text = { Text("Primary text", saveLayout(textPosition, textSize)) },
                    trailing = {
                        Image(icon24x24, saveLayout(trailingPosition, trailingSize))
                    }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx()
                    .toFloat()
            )
            assertThat(textPosition.value!!.y).isEqualTo(
                ((listItemHeight.toIntPx() - textSize.value!!.height) / 2f).roundToInt().toFloat()
            )
            val dm = composeTestRule.displayMetrics
            assertThat(trailingPosition.value!!.x).isEqualTo(
                dm.widthPixels - trailingSize.value!!.width -
                        expectedRightPadding.toIntPx().toFloat()
            )
            assertThat(trailingPosition.value!!.y).isEqualTo(
                ((listItemHeight.toIntPx() - trailingSize.value!!.height) / 2f).roundToInt()
                    .toFloat()
            )
        }
    }

    @Test
    fun listItem_oneLine_positioning_withIcon() {
        val listItemHeight = 56.dp
        val expectedLeftPadding = 16.dp
        val expectedTextLeftPadding = 32.dp

        val textPosition = Ref<Offset>()
        val textSize = Ref<IntSize>()
        val iconPosition = Ref<Offset>()
        val iconSize = Ref<IntSize>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    text = { Text("Primary text", saveLayout(textPosition, textSize)) },
                    icon = { Image(icon24x24, saveLayout(iconPosition, iconSize)) }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(iconPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.y).isEqualTo(
                ((listItemHeight.toIntPx() - iconSize.value!!.height) / 2f).roundToInt().toFloat()
            )
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() +
                        iconSize.value!!.width +
                        expectedTextLeftPadding.toIntPx().toFloat()
            )
            assertThat(textPosition.value!!.y).isEqualTo(
                ((listItemHeight.toIntPx() - textSize.value!!.height) / 2f).roundToInt().toFloat()
            )
        }
    }

    @Test
    fun listItem_twoLine_positioning_noIcon() {
        val expectedLeftPadding = 16.dp
        val expectedRightPadding = 16.dp
        val expectedTextBaseline = 28.dp
        val expectedSecondaryTextBaselineOffset = 20.dp

        val textPosition = Ref<Offset>()
        val textBaseline = Ref<Float>()
        val textSize = Ref<IntSize>()
        val secondaryTextPosition = Ref<Offset>()
        val secondaryTextBaseline = Ref<Float>()
        val secondaryTextSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingBaseline = Ref<Float>()
        val trailingSize = Ref<IntSize>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    text = {
                        Text("Primary text", saveLayout(textPosition, textSize, textBaseline))
                    },
                    secondaryText = {
                        Text(
                            "Secondary text",
                            saveLayout(
                                secondaryTextPosition,
                                secondaryTextSize,
                                secondaryTextBaseline
                            )
                        )
                    },
                    trailing = {
                        Text("meta", saveLayout(trailingPosition, trailingSize, trailingBaseline))
                    }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat()
            )
            assertThat(textBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat()
            )
            assertThat(secondaryTextPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat()
            )
            assertThat(secondaryTextBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat() +
                        expectedSecondaryTextBaselineOffset.toIntPx().toFloat()
            )
            val dm = composeTestRule.displayMetrics
            assertThat(trailingPosition.value!!.x).isEqualTo(
                dm.widthPixels - trailingSize.value!!.width -
                        expectedRightPadding.toIntPx().toFloat()
            )
            assertThat(trailingBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat()
            )
        }
    }

    @Test
    fun listItem_twoLine_positioning_withSmallIcon() {
        val expectedLeftPadding = 16.dp
        val expectedIconTopPadding = 16.dp
        val expectedContentLeftPadding = 32.dp
        val expectedTextBaseline = 32.dp
        val expectedSecondaryTextBaselineOffset = 20.dp

        val textPosition = Ref<Offset>()
        val textBaseline = Ref<Float>()
        val textSize = Ref<IntSize>()
        val secondaryTextPosition = Ref<Offset>()
        val secondaryTextBaseline = Ref<Float>()
        val secondaryTextSize = Ref<IntSize>()
        val iconPosition = Ref<Offset>()
        val iconSize = Ref<IntSize>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    text = {
                        Text("Primary text", saveLayout(textPosition, textSize, textBaseline))
                    },
                    secondaryText = {
                        Text(
                            "Secondary text",
                            saveLayout(
                                secondaryTextPosition,
                                secondaryTextSize,
                                secondaryTextBaseline
                            )
                        )
                    },
                    icon = {
                        Image(icon24x24, saveLayout(iconPosition, iconSize))
                    }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() + iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(textBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat()
            )
            assertThat(secondaryTextPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() +
                        iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(secondaryTextBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat() +
                        expectedSecondaryTextBaselineOffset.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.y).isEqualTo(
                expectedIconTopPadding.toIntPx().toFloat()
            )
        }
    }

    @Test
    fun listItem_twoLine_positioning_withLargeIcon() {
        val listItemHeight = 72.dp
        val expectedLeftPadding = 16.dp
        val expectedIconTopPadding = 16.dp
        val expectedContentLeftPadding = 16.dp
        val expectedTextBaseline = 32.dp
        val expectedSecondaryTextBaselineOffset = 20.dp
        val expectedRightPadding = 16.dp

        val textPosition = Ref<Offset>()
        val textBaseline = Ref<Float>()
        val textSize = Ref<IntSize>()
        val secondaryTextPosition = Ref<Offset>()
        val secondaryTextBaseline = Ref<Float>()
        val secondaryTextSize = Ref<IntSize>()
        val iconPosition = Ref<Offset>()
        val iconSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingSize = Ref<IntSize>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    text = {
                        Text("Primary text", saveLayout(textPosition, textSize, textBaseline))
                    },
                    secondaryText = {
                        Text(
                            "Secondary text",
                            saveLayout(
                                secondaryTextPosition,
                                secondaryTextSize,
                                secondaryTextBaseline
                            )
                        )
                    },
                    icon = {
                        Image(icon40x40, saveLayout(iconPosition, iconSize))
                    },
                    trailing = {
                        Image(icon24x24, saveLayout(trailingPosition, trailingSize))
                    }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() + iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(textBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat()
            )
            assertThat(secondaryTextPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() +
                        iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(secondaryTextBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat() +
                        expectedSecondaryTextBaselineOffset.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.y).isEqualTo(
                expectedIconTopPadding.toIntPx().toFloat()
            )
            val dm = composeTestRule.displayMetrics
            assertThat(trailingPosition.value!!.x).isEqualTo(
                dm.widthPixels - trailingSize.value!!.width -
                        expectedRightPadding.toIntPx().toFloat()
            )
            assertThat(trailingPosition.value!!.y).isEqualTo(
                ((listItemHeight.toIntPx() - trailingSize.value!!.height) / 2).toFloat()
            )
        }
    }

    @Test
    fun listItem_threeLine_positioning_noOverline_metaText() {
        val expectedLeftPadding = 16.dp
        val expectedIconTopPadding = 16.dp
        val expectedContentLeftPadding = 32.dp
        val expectedTextBaseline = 28.dp
        val expectedSecondaryTextBaselineOffset = 20.dp
        val expectedRightPadding = 16.dp

        val textPosition = Ref<Offset>()
        val textBaseline = Ref<Float>()
        val textSize = Ref<IntSize>()
        val secondaryTextPosition = Ref<Offset>()
        val secondaryTextBaseline = Ref<Float>()
        val secondaryTextSize = Ref<IntSize>()
        val iconPosition = Ref<Offset>()
        val iconSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingSize = Ref<IntSize>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    text = {
                        Text("Primary text", saveLayout(textPosition, textSize, textBaseline))
                    },
                    secondaryText = {
                        Text(
                            "Secondary text",
                            saveLayout(
                                secondaryTextPosition,
                                secondaryTextSize,
                                secondaryTextBaseline
                            )
                        )
                    },
                    singleLineSecondaryText = false,
                    icon = {
                        Image(icon24x24, saveLayout(iconPosition, iconSize))
                    },
                    trailing = {
                        Image(icon24x24, saveLayout(trailingPosition, trailingSize))
                    }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() + iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(textBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat()
            )
            assertThat(secondaryTextPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() + iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(secondaryTextBaseline.value!!).isEqualTo(
                expectedTextBaseline.toIntPx().toFloat() +
                        expectedSecondaryTextBaselineOffset.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.x).isEqualTo(expectedLeftPadding.toIntPx().toFloat())
            assertThat(iconPosition.value!!.y).isEqualTo(
                expectedIconTopPadding.toIntPx().toFloat()
            )
            val dm = composeTestRule.displayMetrics
            assertThat(trailingPosition.value!!.x).isEqualTo(
                dm.widthPixels - trailingSize.value!!.width.toFloat() -
                        expectedRightPadding.toIntPx().toFloat()
            )
            assertThat(trailingPosition.value!!.y).isEqualTo(
                expectedIconTopPadding.toIntPx().toFloat()
            )
        }
    }

    @Test
    fun listItem_threeLine_positioning_overline_trailingIcon() {
        val expectedLeftPadding = 16.dp
        val expectedIconTopPadding = 16.dp
        val expectedContentLeftPadding = 16.dp
        val expectedOverlineBaseline = 28.dp
        val expectedTextBaselineOffset = 20.dp
        val expectedSecondaryTextBaselineOffset = 20.dp
        val expectedRightPadding = 16.dp

        val textPosition = Ref<Offset>()
        val textBaseline = Ref<Float>()
        val textSize = Ref<IntSize>()
        val overlineTextPosition = Ref<Offset>()
        val overlineTextBaseline = Ref<Float>()
        val overlineTextSize = Ref<IntSize>()
        val secondaryTextPosition = Ref<Offset>()
        val secondaryTextBaseline = Ref<Float>()
        val secondaryTextSize = Ref<IntSize>()
        val iconPosition = Ref<Offset>()
        val iconSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingSize = Ref<IntSize>()
        val trailingBaseline = Ref<Float>()
        composeTestRule.setMaterialContent {
            Box {
                ListItem(
                    overlineText = {
                        Text(
                            "OVERLINE",
                            saveLayout(
                                overlineTextPosition,
                                overlineTextSize,
                                overlineTextBaseline
                            )
                        )
                    },
                    text = {
                        Text("Primary text", saveLayout(textPosition, textSize, textBaseline))
                    },
                    secondaryText = {
                        Text(
                            "Secondary text",
                            saveLayout(
                                secondaryTextPosition,
                                secondaryTextSize,
                                secondaryTextBaseline
                            )
                        )
                    },
                    icon = {
                        Image(
                            icon40x40,
                            saveLayout(iconPosition, iconSize)
                        )
                    },
                    trailing = {
                        Text(
                            "meta",
                            saveLayout(
                                trailingPosition,
                                trailingSize,
                                trailingBaseline
                            )
                        )
                    }
                )
            }
        }
        composeTestRule.runOnIdleWithDensity {
            assertThat(textPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() +
                        iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(textBaseline.value!!).isEqualTo(
                expectedOverlineBaseline.toIntPx().toFloat() +
                        expectedTextBaselineOffset.toIntPx().toFloat()
            )
            assertThat(overlineTextPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() +
                        iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(overlineTextBaseline.value!!).isEqualTo(
                expectedOverlineBaseline.toIntPx().toFloat()
            )
            assertThat(secondaryTextPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat() +
                        iconSize.value!!.width +
                        expectedContentLeftPadding.toIntPx().toFloat()
            )
            assertThat(secondaryTextBaseline.value!!).isEqualTo(
                expectedOverlineBaseline.toIntPx().toFloat() +
                        expectedTextBaselineOffset.toIntPx().toFloat() +
                        expectedSecondaryTextBaselineOffset.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.x).isEqualTo(
                expectedLeftPadding.toIntPx().toFloat()
            )
            assertThat(iconPosition.value!!.y).isEqualTo(
                expectedIconTopPadding.toIntPx().toFloat()
            )
            val dm = composeTestRule.displayMetrics
            assertThat(trailingPosition.value!!.x).isEqualTo(
                dm.widthPixels - trailingSize.value!!.width -
                        expectedRightPadding.toIntPx().toFloat()
            )
            assertThat(trailingBaseline.value!!).isEqualTo(
                expectedOverlineBaseline.toIntPx().toFloat()
            )
        }
    }

    private fun Dp.toIntPx() = (this.value * composeTestRule.density.density).roundToInt()

    private fun saveLayout(
        coords: Ref<Offset>,
        size: Ref<IntSize>,
        baseline: Ref<Float> = Ref()
    ): Modifier = Modifier.onPositioned { coordinates: LayoutCoordinates ->
        coords.value = coordinates.localToRoot(Offset.Zero)
        baseline.value = coordinates[FirstBaseline].toFloat() + coords.value!!.y
        size.value = coordinates.size
    }
}
