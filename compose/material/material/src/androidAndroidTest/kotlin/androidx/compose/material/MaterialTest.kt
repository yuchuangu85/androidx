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

import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.preferredSizeIn
import androidx.compose.foundation.text.FirstBaseline
import androidx.compose.foundation.text.LastBaseline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.platform.AndroidOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.ui.test.ComposeTestRule
import androidx.ui.test.ComposeTestRuleJUnit
import androidx.ui.test.SemanticsNodeInteraction
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsEqualTo
import androidx.ui.test.assertWidthIsEqualTo
import androidx.ui.test.getAlignmentLinePosition
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.onRoot

fun ComposeTestRuleJUnit.setMaterialContent(
    modifier: Modifier = Modifier,
    composable: @Composable () -> Unit
) {
    setContent {
        MaterialTheme {
            Surface(modifier = modifier, content = composable)
        }
    }
}

fun <T> ComposeTestRule.runOnIdleWithDensity(action: Density.() -> T): T {
    return runOnIdle {
        density.action()
    }
}

fun SemanticsNodeInteraction.getFirstBaselinePosition() = getAlignmentLinePosition(FirstBaseline)

fun SemanticsNodeInteraction.getLastBaselinePosition() = getAlignmentLinePosition(LastBaseline)

fun SemanticsNodeInteraction.assertIsSquareWithSize(expectedSize: Dp) =
    assertWidthIsEqualTo(expectedSize).assertHeightIsEqualTo(expectedSize)

fun SemanticsNodeInteraction.assertWidthFillsRoot(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode("Failed to assertWidthFillsScreen")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = node.componentNode.owner as AndroidOwner
    val rootViewWidth = owner.view.width

    with(owner.density) {
        node.boundsInRoot.width.toDp().assertIsEqualTo(rootViewWidth.toDp())
    }
    return this
}

fun ComposeTestRule.rootWidth(): Dp {
    val nodeInteraction = onRoot()
    val node = nodeInteraction.fetchSemanticsNode("Failed to get screen width")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = node.componentNode.owner as AndroidOwner

    return with(owner.density) {
        owner.view.width.toDp()
    }
}

fun ComposeTestRule.rootHeight(): Dp {
    val nodeInteraction = onRoot()
    val node = nodeInteraction.fetchSemanticsNode("Failed to get screen height")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = node.componentNode.owner as AndroidOwner

    return with(owner.density) {
        owner.view.height.toDp()
    }
}

/**
 * Constant to emulate very big but finite constraints
 */
val BigTestMaxWidth = 5000.dp
val BigTestMaxHeight = 5000.dp

fun ComposeTestRuleJUnit.setMaterialContentForSizeAssertions(
    parentMaxWidth: Dp = BigTestMaxWidth,
    parentMaxHeight: Dp = BigTestMaxHeight,
    // TODO : figure out better way to make it flexible
    children: @Composable () -> Unit
): SemanticsNodeInteraction {
    setContent {
        MaterialTheme {
            Surface {
                Stack {
                    Stack(
                        Modifier.preferredSizeIn(
                            maxWidth = parentMaxWidth,
                            maxHeight = parentMaxHeight
                        ).testTag("containerForSizeAssertion")
                    ) {
                        children()
                    }
                }
            }
        }
    }

    return onNodeWithTag("containerForSizeAssertion")
}
