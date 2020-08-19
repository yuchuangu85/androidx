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
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.material.TabConstants.defaultTabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.samples.ScrollingTextTabs
import androidx.compose.material.samples.TextTabs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.filters.LargeTest
import androidx.ui.test.assertCountEquals
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsEqualTo
import androidx.ui.test.assertIsNotSelected
import androidx.ui.test.assertIsSelected
import androidx.ui.test.assertPositionInRootIsEqualTo
import androidx.ui.test.createComposeRule
import androidx.ui.test.getUnclippedBoundsInRoot
import androidx.ui.test.isInMutuallyExclusiveGroup
import androidx.ui.test.onAllNodes
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class TabTest {

    private val ExpectedSmallTabHeight = 48.dp
    private val ExpectedLargeTabHeight = 72.dp

    private val icon = Icons.Filled.Favorite

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun textTab_height() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                Tab(text = { Text("Text") }, selected = true, onClick = {})
            }
            .assertHeightIsEqualTo(ExpectedSmallTabHeight)
    }

    @Test
    fun iconTab_height() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                Tab(icon = { Icon(icon) }, selected = true, onClick = {})
            }
            .assertHeightIsEqualTo(ExpectedSmallTabHeight)
    }

    @Test
    fun textAndIconTab_height() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                Surface {
                    Tab(
                        text = { Text("Text and Icon") },
                        icon = { Icon(icon) },
                        selected = true,
                        onClick = {}
                    )
                }
            }
            .assertHeightIsEqualTo(ExpectedLargeTabHeight)
    }

    @Test
    fun fixedTabRow_indicatorPosition() {
        val indicatorHeight = 1.dp

        composeTestRule.setMaterialContent {
            var state by remember { mutableStateOf(0) }
            val titles = listOf("TAB 1", "TAB 2")

            val indicator = @Composable { tabPositions: List<TabPosition> ->
                Box(
                    Modifier
                        .defaultTabIndicatorOffset(tabPositions[state])
                        .fillMaxWidth()
                        .preferredHeight(indicatorHeight)
                        .background(color = Color.Red)
                        .testTag("indicator")
                )
            }

            Box(Modifier.testTag("tabRow")) {
                TabRow(
                    selectedTabIndex = state,
                    indicator = indicator
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = state == index,
                            onClick = { state = index }
                        )
                    }
                }
            }
        }

        val tabRowBounds = onNodeWithTag("tabRow").getUnclippedBoundsInRoot()

        onNodeWithTag("indicator")
            .assertPositionInRootIsEqualTo(
                expectedLeft = 0.dp,
                expectedTop = tabRowBounds.height - indicatorHeight
            )

        // Click the second tab
        onAllNodes(isInMutuallyExclusiveGroup())[1].performClick()

        // Indicator should now be placed in the bottom left of the second tab, so its x coordinate
        // should be in the middle of the TabRow
        onNodeWithTag("indicator")
            .assertPositionInRootIsEqualTo(
                expectedLeft = (tabRowBounds.width / 2),
                expectedTop = tabRowBounds.height - indicatorHeight
            )
    }

    @Test
    fun singleLineTab_textBaseline() {
        composeTestRule.setMaterialContent {
            var state by remember { mutableStateOf(0) }
            val titles = listOf("TAB")

            Box {
                TabRow(
                    modifier = Modifier.testTag("tabRow"),
                    selectedTabIndex = state
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            text = {
                                Text(title, Modifier.testTag("text"))
                            },
                            selected = state == index,
                            onClick = { state = index }
                        )
                    }
                }
            }
        }

        val expectedBaseline = 18.dp
        val indicatorHeight = 2.dp
        val expectedBaselineDistance = expectedBaseline + indicatorHeight

        val tabRowBounds = onNodeWithTag("tabRow").getUnclippedBoundsInRoot()
        val textBounds =
            onNodeWithTag("text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val textBaselinePos =
            onNodeWithTag("text", useUnmergedTree = true).getLastBaselinePosition()

        val baselinePositionY = textBounds.top + textBaselinePos
        val expectedPositionY = tabRowBounds.height - expectedBaselineDistance
        baselinePositionY.assertIsEqualTo(expectedPositionY, "baseline y-position")
    }

    @Test
    fun singleLineTab_withIcon_textBaseline() {
        composeTestRule.setMaterialContent {
            var state by remember { mutableStateOf(0) }
            val titles = listOf("TAB")

            Box {
                TabRow(
                    modifier = Modifier.testTag("tabRow"),
                    selectedTabIndex = state
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            text = {
                                Text(title, Modifier.testTag("text"))
                            },
                            icon = { Icon(Icons.Filled.Favorite) },
                            selected = state == index,
                            onClick = { state = index }
                        )
                    }
                }
            }
        }

        val expectedBaseline = 14.dp
        val indicatorHeight = 2.dp
        val expectedBaselineDistance = expectedBaseline + indicatorHeight

        val tabRowBounds = onNodeWithTag("tabRow").getUnclippedBoundsInRoot()
        val textBounds =
            onNodeWithTag("text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val textBaselinePos =
            onNodeWithTag("text", useUnmergedTree = true).getLastBaselinePosition()

        val baselinePositionY = textBounds.top + textBaselinePos
        val expectedPositionY = tabRowBounds.height - expectedBaselineDistance
        baselinePositionY.assertIsEqualTo(expectedPositionY, "baseline y-position")
    }

    @Test
    fun twoLineTab_textBaseline() {
        composeTestRule.setMaterialContent {
            var state by remember { mutableStateOf(0) }
            val titles = listOf("Two line \n text")

            Box {
                TabRow(
                    modifier = Modifier.testTag("tabRow"),
                    selectedTabIndex = state
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            text = {
                                Text(title, Modifier.testTag("text"), maxLines = 2)
                            },
                            selected = state == index,
                            onClick = { state = index }
                        )
                    }
                }
            }
        }

        val expectedBaseline = 10.dp
        val indicatorHeight = 2.dp

        val tabRowBounds = onNodeWithTag("tabRow").getUnclippedBoundsInRoot()
        val textBounds =
            onNodeWithTag("text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val textBaselinePos =
            onNodeWithTag("text", useUnmergedTree = true).getLastBaselinePosition()

        val expectedBaselineDistance = expectedBaseline + indicatorHeight

        val baselinePositionY = textBounds.top + textBaselinePos
        val expectedPositionY = (tabRowBounds.height - expectedBaselineDistance)
        baselinePositionY.assertIsEqualTo(expectedPositionY, "baseline y-position")
    }

    @Test
    fun scrollableTabRow_indicatorPosition() {
        val indicatorHeight = 1.dp
        val minimumTabWidth = 90.dp

        composeTestRule.setMaterialContent {
            var state by remember { mutableStateOf(0) }
            val titles = listOf("TAB 1", "TAB 2")

            val indicator = @Composable { tabPositions: List<TabPosition> ->
                Box(
                    Modifier
                        .defaultTabIndicatorOffset(tabPositions[state])
                        .fillMaxWidth()
                        .preferredHeight(indicatorHeight)
                        .background(color = Color.Red)
                    .testTag("indicator")
                )
            }

            Box {
                ScrollableTabRow(
                    modifier = Modifier.testTag("tabRow"),
                    selectedTabIndex = state,
                    indicator = indicator
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = state == index,
                            onClick = { state = index }
                        )
                    }
                }
            }
        }

        val tabRowBounds = onNodeWithTag("tabRow").getUnclippedBoundsInRoot()

        // Indicator should be placed in the bottom left of the first tab
        onNodeWithTag("indicator")
            .assertPositionInRootIsEqualTo(
                // Tabs in a scrollable tab row are offset 52.dp from each end
                expectedLeft = TabConstants.DefaultScrollableTabRowPadding,
                expectedTop = tabRowBounds.height - indicatorHeight
            )

        // Click the second tab
        onAllNodes(isInMutuallyExclusiveGroup())[1].performClick()

        // Indicator should now be placed in the bottom left of the second tab, so its x coordinate
        // should be in the middle of the TabRow
        onNodeWithTag("indicator")
            .assertPositionInRootIsEqualTo(
                expectedLeft = TabConstants.DefaultScrollableTabRowPadding + minimumTabWidth,
                expectedTop = tabRowBounds.height - indicatorHeight
            )
    }

    @Test
    fun fixedTabRow_initialTabSelected() {
        composeTestRule
            .setMaterialContent {
                TextTabs()
            }

        // Only the first tab should be selected
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(3)
            .apply {
                get(0).assertIsSelected()
                get(1).assertIsNotSelected()
                get(2).assertIsNotSelected()
            }
    }

    @Test
    fun fixedTabRow_selectNewTab() {
        composeTestRule
            .setMaterialContent {
                TextTabs()
            }

        // Only the first tab should be selected
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(3)
            .apply {
                get(0).assertIsSelected()
                get(1).assertIsNotSelected()
                get(2).assertIsNotSelected()
            }

        // Click the last tab
        onAllNodes(isInMutuallyExclusiveGroup())[2].performClick()

        // Now only the last tab should be selected
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(3)
            .apply {
                get(0).assertIsNotSelected()
                get(1).assertIsNotSelected()
                get(2).assertIsSelected()
            }
    }

    @Test
    fun scrollableTabRow_initialTabSelected() {
        composeTestRule
            .setMaterialContent {
                ScrollingTextTabs()
            }

        // Only the first tab should be selected
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(10)
            .apply {
                get(0).assertIsSelected()
                (1..9).forEach {
                    get(it).assertIsNotSelected()
                }
            }
    }

    @Test
    fun scrollableTabRow_selectNewTab() {
        composeTestRule
            .setMaterialContent {
                ScrollingTextTabs()
            }

        // Only the first tab should be selected
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(10)
            .apply {
                get(0).assertIsSelected()
                (1..9).forEach {
                    get(it).assertIsNotSelected()
                }
            }

        // Click the second tab
        onAllNodes(isInMutuallyExclusiveGroup())[1].performClick()

        // Now only the second tab should be selected
        onAllNodes(isInMutuallyExclusiveGroup())
            .assertCountEquals(10)
            .apply {
                get(0).assertIsNotSelected()
                get(1).assertIsSelected()
                (2..9).forEach {
                    get(it).assertIsNotSelected()
                }
            }
    }
}
