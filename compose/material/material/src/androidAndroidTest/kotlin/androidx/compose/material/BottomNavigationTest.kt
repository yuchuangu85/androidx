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
package androidx.compose.material

import androidx.compose.foundation.Box
import androidx.compose.foundation.Icon
import androidx.compose.foundation.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.samples.BottomNavigationSample
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.globalPosition
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.filters.LargeTest
import androidx.ui.test.assertCountEquals
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsEqualTo
import androidx.ui.test.assertIsNotDisplayed
import androidx.ui.test.assertIsNotSelected
import androidx.ui.test.assertIsSelected
import androidx.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.ui.test.assertTopPositionInRootIsEqualTo
import androidx.ui.test.createComposeRule
import androidx.ui.test.getUnclippedBoundsInRoot
import androidx.ui.test.isInMutuallyExclusiveGroup
import androidx.ui.test.onAllNodes
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.onNodeWithText
import androidx.ui.test.performClick
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
/**
 * Test for [BottomNavigation] and [BottomNavigationItem].
 */
class BottomNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun bottomNavigation_size() {
        val height = 56.dp
        composeTestRule.setMaterialContentForSizeAssertions {
            BottomNavigationSample()
        }
            .assertWidthFillsRoot()
            .assertHeightIsEqualTo(height)
    }

    @Test
    fun bottomNavigationItem_sizeAndPositions() {
        lateinit var parentCoords: LayoutCoordinates
        val itemCoords = mutableMapOf<Int, LayoutCoordinates>()
        composeTestRule.setMaterialContent(Modifier.onPositioned { coords: LayoutCoordinates ->
            parentCoords = coords
        }) {
            Box {
                BottomNavigation {
                    repeat(4) { index ->
                        BottomNavigationItem(
                            icon = { Icon(Icons.Filled.Favorite) },
                            label = { Text("Item $index") },
                            selected = index == 0,
                            onSelect = {},
                            modifier = Modifier.onPositioned { coords: LayoutCoordinates ->
                                itemCoords[index] = coords
                            }
                        )
                    }
                }
            }
        }

        composeTestRule.runOnIdleWithDensity {
            val totalWidth = parentCoords.size.width

            val expectedItemWidth = totalWidth / 4
            val expectedItemHeight = 56.dp.toIntPx()

            Truth.assertThat(itemCoords.size).isEqualTo(4)

            itemCoords.forEach { (index, coord) ->
                Truth.assertThat(coord.size.width).isEqualTo(expectedItemWidth)
                Truth.assertThat(coord.size.height).isEqualTo(expectedItemHeight)
                Truth.assertThat(coord.globalPosition.x)
                    .isEqualTo((expectedItemWidth * index).toFloat())
            }
        }
    }

    @Test
    fun bottomNavigationItemContent_withLabel_sizeAndPosition() {
        composeTestRule.setMaterialContent {
            Box {
                BottomNavigation {
                    BottomNavigationItem(
                        modifier = Modifier.testTag("item"),
                        icon = {
                            Icon(Icons.Filled.Favorite,
                                Modifier.testTag("icon")
                            )
                        },
                        label = {
                            Text("ItemText")
                        },
                        selected = true,
                        onSelect = {}
                    )
                }
            }
        }

        val itemBounds = onNodeWithTag("item").getUnclippedBoundsInRoot()
        val iconBounds = onNodeWithTag("icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val textBounds = onNodeWithText("ItemText").getUnclippedBoundsInRoot()

        // Distance from the bottom to the text baseline and from the text baseline to the
        // bottom of the icon
        val textBaseline = 12.dp

        // Relative position of the baseline to the top of text
        val relativeTextBaseline = onNodeWithText("ItemText").getLastBaselinePosition()
        // Absolute y position of the text baseline
        val absoluteTextBaseline = textBounds.top + relativeTextBaseline

        val itemBottom = itemBounds.height + itemBounds.top
        // Text baseline should be 12.dp from the bottom of the item
        absoluteTextBaseline.assertIsEqualTo(itemBottom - textBaseline)

        onNodeWithTag("icon", useUnmergedTree = true)
            // The icon should be centered in the item
            .assertLeftPositionInRootIsEqualTo((itemBounds.width - iconBounds.width) / 2)
            // The bottom of the icon is 12.dp above the text baseline
            .assertTopPositionInRootIsEqualTo(absoluteTextBaseline - 12.dp - iconBounds.height)
    }

    @Test
    fun bottomNavigationItemContent_withLabel_unselected_sizeAndPosition() {
        composeTestRule.setMaterialContent {
            Box {
                BottomNavigation {
                    BottomNavigationItem(
                        modifier = Modifier.testTag("item"),
                        icon = {
                            Icon(Icons.Filled.Favorite,
                                Modifier.testTag("icon")
                            )
                        },
                        label = {
                            Text("ItemText")
                        },
                        selected = false,
                        onSelect = {},
                        alwaysShowLabels = false
                    )
                }
            }
        }

        // The text should not be placed, since the item is not selected and alwaysShowLabels
        // is false
        onNodeWithText("ItemText", useUnmergedTree = true).assertIsNotDisplayed()

        val itemBounds = onNodeWithTag("item").getUnclippedBoundsInRoot()
        val iconBounds = onNodeWithTag("icon", useUnmergedTree = true).getUnclippedBoundsInRoot()

        onNodeWithTag("icon", useUnmergedTree = true)
            .assertLeftPositionInRootIsEqualTo((itemBounds.width - iconBounds.width) / 2)
            .assertTopPositionInRootIsEqualTo((itemBounds.height - iconBounds.height) / 2)
    }

    @Test
    fun bottomNavigationItemContent_withoutLabel_sizeAndPosition() {
        composeTestRule.setMaterialContent {
            Box {
                BottomNavigation {
                    BottomNavigationItem(
                        modifier = Modifier.testTag("item"),
                        icon = {
                            Icon(Icons.Filled.Favorite,
                                Modifier.testTag("icon")
                            )
                        },
                        label = {},
                        selected = false,
                        onSelect = {}
                    )
                }
            }
        }

        val itemBounds = onNodeWithTag("item").getUnclippedBoundsInRoot()
        val iconBounds = onNodeWithTag("icon", useUnmergedTree = true).getUnclippedBoundsInRoot()

        // The icon should be centered in the item, as there is no text placeable provided
        onNodeWithTag("icon", useUnmergedTree = true)
            .assertLeftPositionInRootIsEqualTo((itemBounds.width - iconBounds.width) / 2)
            .assertTopPositionInRootIsEqualTo((itemBounds.height - iconBounds.height) / 2)
    }

    @Test
    fun bottomNavigation_selectNewItem() {
        composeTestRule.setMaterialContent {
            BottomNavigationSample()
        }

        // Find all items and ensure there are 3
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(3)
            // Ensure semantics match for selected state of the items
            .apply {
                get(0).assertIsSelected()
                get(1).assertIsNotSelected()
                get(2).assertIsNotSelected()
            }
            // Click the last item
            .apply {
                get(2).performClick()
            }
            .apply {
                get(0).assertIsNotSelected()
                get(1).assertIsNotSelected()
                get(2).assertIsSelected()
            }
    }
}
