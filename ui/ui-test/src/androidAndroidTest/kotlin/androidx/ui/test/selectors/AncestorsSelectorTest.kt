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

package androidx.ui.test.selectors

import androidx.test.filters.MediumTest
import androidx.ui.test.assert
import androidx.ui.test.assertCountEquals
import androidx.ui.test.createComposeRule
import androidx.ui.test.hasTestTag
import androidx.ui.test.onAncestors
import androidx.ui.test.onFirst
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.onParent
import androidx.ui.test.util.BoundaryNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class AncestorsSelectorTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun threeAncestors() {
        rule.setContent {
            BoundaryNode(testTag = "NodeA") {
                BoundaryNode(testTag = "NodeB") {
                    BoundaryNode(testTag = "NodeC") {
                        BoundaryNode(testTag = "NodeD")
                    }
                }
            }
        }

        rule.onNodeWithTag("NodeD")
            .onAncestors()
            .assertCountEquals(4)
            .apply {
                get(0).assert(hasTestTag("NodeC"))
                get(1).assert(hasTestTag("NodeB"))
                get(2).assert(hasTestTag("NodeA"))
            }
    }

    @Test
    fun threeAncestors_navigateUp() {
        rule.setContent {
            BoundaryNode(testTag = "NodeA") {
                BoundaryNode(testTag = "NodeB") {
                    BoundaryNode(testTag = "NodeC") {
                        BoundaryNode(testTag = "NodeD")
                    }
                }
            }
        }

        rule.onNodeWithTag("NodeD")
            .onAncestors()
            .onFirst()
            .onAncestors()
            .assertCountEquals(3)
            .apply {
                get(0).assert(hasTestTag("NodeB"))
                get(1).assert(hasTestTag("NodeA"))
            }
    }

    @Test
    fun noAncestors() {
        rule.setContent {
            BoundaryNode(testTag = "Node")
        }

        rule.onNodeWithTag("Node")
            .onParent()
            .onAncestors()
            .assertCountEquals(0)
    }
}