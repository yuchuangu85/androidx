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

package androidx.ui.test.predicates

import androidx.test.filters.MediumTest
import androidx.ui.test.assert
import androidx.ui.test.assertCountEquals
import androidx.ui.test.createComposeRule
import androidx.ui.test.hasAnyChild
import androidx.ui.test.hasTestTag
import androidx.ui.test.util.BoundaryNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class HasAnyChildTest {

    @get:Rule
    val rule = createComposeRule(disableTransitions = true)

    @Test
    fun findByChild_oneSubtree_oneChild_matches() {
        rule.setContent {
            BoundaryNode(testTag = "Node")
            BoundaryNode(testTag = "Parent") {
                BoundaryNode(testTag = "Child")
            }
        }

        rule.onNode(hasAnyChild(hasTestTag("Child")))
            .assert(hasTestTag("Parent"))
    }

    @Test
    fun findByChild_threeSubtrees_twoChildren_matches() {
        rule.setContent {
            BoundaryNode(testTag = "Node")
            BoundaryNode(testTag = "Parent1") {
                BoundaryNode(testTag = "Child1")
                BoundaryNode(testTag = "Child2")
            }
            BoundaryNode(testTag = "Parent2") {
                BoundaryNode(testTag = "Child2")
                BoundaryNode(testTag = "Child1")
            }
            BoundaryNode(testTag = "Parent3") {
                BoundaryNode(testTag = "Child2")
                BoundaryNode(testTag = "Child3")
            }
        }

        rule.onAllNodes(hasAnyChild(hasTestTag("Child1")))
            .assertCountEquals(2)
        rule.onAllNodes(hasAnyChild(hasTestTag("Child2")))
            .assertCountEquals(3)
        rule.onAllNodes(hasAnyChild(hasTestTag("Child3")))
            .assertCountEquals(1)
    }

    @Test
    fun findByChild_justSelf_oneFound() {
        rule.setContent {
            BoundaryNode(testTag = "Child")
        }

        rule.onNode(hasAnyChild(hasTestTag("Child")))
            .assertExists() // The root node
    }

    @Test
    fun findByChild_nothingFound() {
        rule.setContent {
            BoundaryNode(testTag = "Parent") {
                BoundaryNode(testTag = "ExtraNode") {
                    BoundaryNode(testTag = "Child")
                }
            }
        }

        rule.onNode(hasAnyChild(hasTestTag("Child"))
                and hasTestTag("Parent"))
            .assertDoesNotExist()
    }

    @Test
    fun findByGrandChild_oneFound() {
        rule.setContent {
            BoundaryNode(testTag = "Parent") {
                BoundaryNode(testTag = "ExtraNode") {
                    BoundaryNode(testTag = "Child")
                }
            }
        }

        rule.onNode(hasAnyChild(hasAnyChild(hasTestTag("Child"))))
            .assert(hasTestTag("Parent"))
    }
}