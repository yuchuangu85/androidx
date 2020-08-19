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

package androidx.ui.test

import androidx.compose.runtime.Composable
import androidx.test.filters.MediumTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.accessibilityLabel
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.ui.test.util.expectError
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class FindersTest {

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun findAll_zeroOutOfOne_findsNone() {
        composeTestRule.setContent {
            BoundaryNode { testTag = "not_myTestTag" }
        }

        onAllNodes(hasTestTag("myTestTag")).assertCountEquals(0)
    }

    @Test
    fun findAll_oneOutOfTwo_findsOne() {
        composeTestRule.setContent {
            BoundaryNode { testTag = "myTestTag" }
            BoundaryNode { testTag = "myTestTag2" }
        }

        onAllNodes(hasTestTag("myTestTag"))
            .assertCountEquals(1)
            .onFirst()
            .assert(hasTestTag("myTestTag"))
    }

    @Test
    fun findAll_twoOutOfTwo_findsTwo() {
        composeTestRule.setContent {
            BoundaryNode { testTag = "myTestTag" }
            BoundaryNode { testTag = "myTestTag" }
        }

        onAllNodes(hasTestTag("myTestTag"))
            .assertCountEquals(2)
            .apply {
                get(0).assert(hasTestTag("myTestTag"))
                get(1).assert(hasTestTag("myTestTag"))
            }
    }

    @Test
    fun findByText_matches() {
        composeTestRule.setContent {
            BoundaryNode { accessibilityLabel = "Hello World" }
        }

        onNodeWithText("Hello World")
    }

    @Test(expected = AssertionError::class)
    fun findByText_fails() {
        composeTestRule.setContent {
            BoundaryNode { accessibilityLabel = "Hello World" }
        }

        // Need to assert exists or it won't fail
        onNodeWithText("World").assertExists()
    }

    @Test
    fun findBySubstring_matches() {
        composeTestRule.setContent {
            BoundaryNode { text = AnnotatedString("Hello World") }
        }

        onNodeWithSubstring("World")
    }

    @Test
    fun findBySubstring_ignoreCase_matches() {
        composeTestRule.setContent {
            BoundaryNode { text = AnnotatedString("Hello World") }
        }

        onNodeWithSubstring("world", ignoreCase = true)
    }

    @Test
    fun findBySubstring_wrongCase_fails() {
        composeTestRule.setContent {
            BoundaryNode { text = AnnotatedString("Hello World") }
        }

        expectError<AssertionError> {
            // Need to assert exists or it won't fetch nodes
            onNodeWithSubstring("world").assertExists()
        }
    }

    @Composable
    fun BoundaryNode(props: (SemanticsPropertyReceiver.() -> Unit)) {
        Column(Modifier.semantics(properties = props)) {}
    }
}