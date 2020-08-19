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

package androidx.compose.ui.input.key

import androidx.compose.foundation.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.test.filters.SmallTest
import androidx.ui.test.createComposeRule
import androidx.ui.test.runOnIdle
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class FindParentKeyInputNodeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun returnsImmediateParentFromModifierChain() {
        // Arrange.
        // keyInputNode1--keyInputNode2--keyInputNode3--keyInputNode4--keyInputNode5
        lateinit var modifier1: KeyInputModifier
        lateinit var modifier2: KeyInputModifier
        lateinit var modifier3: KeyInputModifier
        lateinit var modifier4: KeyInputModifier
        lateinit var modifier5: KeyInputModifier
        composeTestRule.setContent {
            modifier1 = KeyInputModifier(null, null)
            modifier2 = KeyInputModifier(null, null)
            modifier3 = KeyInputModifier(null, null)
            modifier4 = KeyInputModifier(null, null)
            modifier5 = KeyInputModifier(null, null)
            Box(modifier1.then(modifier2).then(modifier3).then(modifier4).then(modifier5)) {}
        }

        // Act.
        val parent = runOnIdle {
            modifier3.keyInputNode.findParentKeyInputNode()
        }

        // Assert.
        runOnIdle {
            assertThat(parent).isEqualTo(modifier2.keyInputNode)
        }
    }

    @Test
    fun returnsImmediateParentFromModifierChain_ignoresNonKeyInputModifiers() {
        // Arrange.
        // keyInputNode1--keyInputNode2--nonKeyInputNode--keyInputNode3
        lateinit var modifier1: KeyInputModifier
        lateinit var modifier2: KeyInputModifier
        lateinit var modifier3: KeyInputModifier
        composeTestRule.setContent {
            modifier1 = KeyInputModifier(null, null)
            modifier2 = KeyInputModifier(null, null)
            modifier3 = KeyInputModifier(null, null)
            Box(
                modifier = modifier1
                    .then(modifier2)
                    .background(color = Color.Red)
                    .then(modifier3)
            )
        }

        // Act.
        val parent = runOnIdle {
            modifier3.keyInputNode.findParentKeyInputNode()
        }

        // Assert.
        runOnIdle {
            assertThat(parent).isEqualTo(modifier2.keyInputNode)
        }
    }

    @Test
    fun returnsLastKeyInputParentFromParentLayoutNode() {
        // Arrange.
        // parentLayoutNode--parentKeyInputNode1--parentKeyInputNode2
        //       |
        // layoutNode--keyInputNode
        lateinit var parentKeyInputModifier1: KeyInputModifier
        lateinit var parentKeyInputModifier2: KeyInputModifier
        lateinit var keyInputModifier: KeyInputModifier
        composeTestRule.setContent {
            parentKeyInputModifier1 = KeyInputModifier(null, null)
            parentKeyInputModifier2 = KeyInputModifier(null, null)
            keyInputModifier = KeyInputModifier(null, null)
            Box(modifier = parentKeyInputModifier1.then(parentKeyInputModifier2)) {
                Box(modifier = keyInputModifier)
            }
        }

        // Act.
        val parent = runOnIdle {
            keyInputModifier.keyInputNode.findParentKeyInputNode()
        }

        // Assert.
        runOnIdle {
            assertThat(parent).isEqualTo(parentKeyInputModifier2.keyInputNode)
        }
    }

    @Test
    fun returnsImmediateParent() {
        // Arrange.
        // grandparentLayoutNode--grandparentKeyInputNode
        //       |
        // parentLayoutNode--parentKeyInputNode
        //       |
        // layoutNode--keyInputNode
        lateinit var grandparentKeyInputModifier: KeyInputModifier
        lateinit var parentKeyInputModifier: KeyInputModifier
        lateinit var keyInputModifier: KeyInputModifier
        composeTestRule.setContent {
            grandparentKeyInputModifier = KeyInputModifier(null, null)
            parentKeyInputModifier = KeyInputModifier(null, null)
            keyInputModifier = KeyInputModifier(null, null)
            Box(modifier = grandparentKeyInputModifier) {
                Box(modifier = parentKeyInputModifier) {
                    Box(modifier = keyInputModifier)
                }
            }
        }

        // Act.
        val parent = runOnIdle {
            keyInputModifier.keyInputNode.findParentKeyInputNode()
        }

        // Assert.
        runOnIdle {
            assertThat(parent).isEqualTo(parentKeyInputModifier.keyInputNode)
        }
    }

    @Test
    fun ignoresIntermediateLayoutNodesThatDontHaveKeyInputNodes() {
        // Arrange.
        // grandparentLayoutNode--grandparentKeyInputNode
        //       |
        // parentLayoutNode
        //       |
        // layoutNode--keyInputNode
        lateinit var grandparentKeyInputModifier: KeyInputModifier
        lateinit var keyInputModifier: KeyInputModifier
        composeTestRule.setContent {
            grandparentKeyInputModifier = KeyInputModifier(null, null)
            keyInputModifier = KeyInputModifier(null, null)
            Box(modifier = grandparentKeyInputModifier) {
                Box {
                    Box(modifier = keyInputModifier)
                }
            }
        }

        // Act.
        val parent = runOnIdle {
            keyInputModifier.keyInputNode.findParentKeyInputNode()
        }

        // Assert.
        runOnIdle {
            assertThat(parent).isEqualTo(grandparentKeyInputModifier.keyInputNode)
        }
    }
}