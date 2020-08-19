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
import androidx.compose.foundation.currentTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.filters.SmallTest
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.ui.test.assertTopPositionInRootIsEqualTo
import androidx.ui.test.createComposeRule
import androidx.ui.test.getUnclippedBoundsInRoot
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class AppBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appBarHeight = 56.dp

    @Test
    fun topAppBar_expandsToScreen() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                TopAppBar(title = { Text("Title") })
            }
            .assertHeightIsEqualTo(appBarHeight)
            .assertWidthFillsRoot()
    }

    @Test
    fun topAppBar_withTitle() {
        val title = "Title"
        composeTestRule.setMaterialContent {
            TopAppBar(title = { Text(title) })
        }
        onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun topAppBar_default_positioning() {
        composeTestRule.setMaterialContent {
            Box(Modifier.testTag("bar")) {
                TopAppBar(
                    navigationIcon = {
                        FakeIcon(Modifier.testTag("navigationIcon"))
                    },
                    title = {
                        Text("title", Modifier.testTag("title"))
                    },
                    actions = {
                        FakeIcon(Modifier.testTag("action"))
                    }
                )
            }
        }

        val appBarBounds = onNodeWithTag("bar").getUnclippedBoundsInRoot()
        val titleBounds = onNodeWithTag("title").getUnclippedBoundsInRoot()
        val appBarBottomEdgeY = appBarBounds.top + appBarBounds.height

        onNodeWithTag("navigationIcon")
            // Navigation icon should be 4.dp from the start
            .assertLeftPositionInRootIsEqualTo(AppBarStartAndEndPadding)
            // Navigation icon should be 4.dp from the bottom
            .assertTopPositionInRootIsEqualTo(
                appBarBottomEdgeY - AppBarStartAndEndPadding - FakeIconSize)

        onNodeWithTag("title")
            // Title should be 72.dp from the start
            // 4.dp padding for the whole app bar + 68.dp inset
            .assertLeftPositionInRootIsEqualTo(4.dp + 68.dp)
            // Title should be vertically centered
            .assertTopPositionInRootIsEqualTo((appBarBounds.height - titleBounds.height) / 2)

        onNodeWithTag("action")
            // Action should be placed at the end
            .assertLeftPositionInRootIsEqualTo(expectedActionPosition(appBarBounds.width))
            // Action should be 4.dp from the bottom
            .assertTopPositionInRootIsEqualTo(
                appBarBottomEdgeY - AppBarStartAndEndPadding - FakeIconSize)
    }

    @Test
    fun topAppBar_noNavigationIcon_positioning() {
        composeTestRule.setMaterialContent {
            Box(Modifier.testTag("bar")) {
                TopAppBar(
                    title = {
                        Text("title", Modifier.testTag("title"))
                    },
                    actions = {
                        FakeIcon(Modifier.testTag("action"))
                    }
                )
            }
        }

        val appBarBounds = onNodeWithTag("bar").getUnclippedBoundsInRoot()

        onNodeWithTag("title")
            // Title should now be placed 16.dp from the start, as there is no navigation icon
            // 4.dp padding for the whole app bar + 12.dp inset
            .assertLeftPositionInRootIsEqualTo(4.dp + 12.dp)

        onNodeWithTag("action")
            // Action should still be placed at the end
            .assertLeftPositionInRootIsEqualTo(expectedActionPosition(appBarBounds.width))
    }

    @Test
    fun topAppBar_titleDefaultStyle() {
        var textStyle: TextStyle? = null
        var h6Style: TextStyle? = null
        composeTestRule.setMaterialContent {
            Box {
                TopAppBar(
                    title = {
                        Text("App Bar Title")
                        textStyle = currentTextStyle()
                        h6Style = MaterialTheme.typography.h6
                    }
                )
            }
        }
        assertThat(textStyle!!.fontSize).isEqualTo(h6Style!!.fontSize)
        assertThat(textStyle!!.fontFamily).isEqualTo(h6Style!!.fontFamily)
    }

    @Test
    fun bottomAppBar_expandsToScreen() {
        composeTestRule
            .setMaterialContentForSizeAssertions {
                BottomAppBar {}
            }
            .assertHeightIsEqualTo(appBarHeight)
            .assertWidthFillsRoot()
    }

    @Test
    fun bottomAppBar_default_positioning() {
        composeTestRule.setMaterialContent {
            BottomAppBar(Modifier.testTag("bar")) {
                FakeIcon(Modifier.testTag("icon"))
            }
        }

        val appBarBounds = onNodeWithTag("bar").getUnclippedBoundsInRoot()
        val appBarBottomEdgeY = appBarBounds.top + appBarBounds.height

        onNodeWithTag("icon")
            // Child icon should be 4.dp from the start
            .assertLeftPositionInRootIsEqualTo(AppBarStartAndEndPadding)
            // Child icon should be 4.dp from the bottom
            .assertTopPositionInRootIsEqualTo(
                appBarBottomEdgeY - AppBarStartAndEndPadding - FakeIconSize)
    }

    /**
     * [IconButton] that just draws a red box, to simulate a real icon for testing positions.
     */
    private val FakeIcon = @Composable { modifier: Modifier ->
        IconButton(onClick = {}, modifier = modifier) {
            Icon(ColorPainter(Color.Red))
        }
    }

    private fun expectedActionPosition(appBarWidth: Dp): Dp =
        appBarWidth - AppBarStartAndEndPadding - FakeIconSize

    private val AppBarStartAndEndPadding = 4.dp

    private val FakeIconSize = 48.dp
}
