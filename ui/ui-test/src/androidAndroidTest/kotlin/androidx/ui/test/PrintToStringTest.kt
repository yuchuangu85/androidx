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

import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.test.filters.MediumTest
import androidx.ui.test.util.BoundaryNode
import androidx.ui.test.util.expectErrorMessageStartsWith
import androidx.ui.test.util.obfuscateNodesInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class PrintToStringTest {

    @get:Rule
    val rule = createComposeRule(disableTransitions = true)

    @Test
    fun printToString_nothingFound() {
        rule.setContent {
            ComposeSimpleCase()
        }

        expectErrorMessageStartsWith(
            "Failed: assertExists.\n" +
            "Reason: Expected exactly '1' node but could not find any node that satisfies:"
        ) {
            rule.onNodeWithText("Oops").printToString()
        }
    }

    @Test
    fun printToString_one() {
        rule.setContent {
            ComposeSimpleCase()
        }

        val result = rule.onNodeWithText("Hello")
            .printToString(maxDepth = 0)

        assertThat(obfuscateNodesInfo(result)).isEqualTo("" +
                "Node #X at (X, X, X, X)px\n" +
                "Text = 'Hello'\n" +
                "GetTextLayoutResult = 'AccessibilityAction(label=null, action=Function1<java" +
                ".util.List<androidx.compose.ui.text.TextLayoutResult>, java.lang.Boolean>)'\n" +
                "Has 1 sibling")
    }

    @Test
    fun printToString_many() {
        rule.setContent {
            ComposeSimpleCase()
        }

        val result = rule.onRoot()
            .onChildren()
            .printToString()

        assertThat(obfuscateNodesInfo(result)).isEqualTo("" +
                "1) Node #X at (X, X, X, X)px\n" +
                "Text = 'Hello'\n" +
                "GetTextLayoutResult = 'AccessibilityAction(label=null, action=Function1<java" +
                ".util.List<androidx.compose.ui.text.TextLayoutResult>, java.lang.Boolean>)'\n" +
                "Has 1 sibling\n" +
                "2) Node #X at (X, X, X, X)px\n" +
                "Text = 'World'\n" +
                "GetTextLayoutResult = 'AccessibilityAction(label=null, action=Function1<java" +
                ".util.List<androidx.compose.ui.text.TextLayoutResult>, java.lang.Boolean>)'\n" +
                "Has 1 sibling")
    }

    @Test
    fun printHierarchy() {
        rule.setContent {
            Column(Modifier.semantics { this.disabled(); this.testTag = "column" }) {
                Box(Modifier.semantics { this.disabled(); this.testTag = "box" }) {
                    Button(onClick = {}) {
                        Text("Button")
                    }
                }
                Text("Hello")
            }
        }

        val result = rule.onRoot()
            .printToString()

        assertThat(obfuscateNodesInfo(result)).isEqualTo("" +
                "Node #X at (X, X, X, X)px\n" +
                " |-Node #X at (X, X, X, X)px, Tag: 'column'\n" +
                "   Disabled = 'kotlin.Unit'\n" +
                "    |-Node #X at (X, X, X, X)px, Tag: 'box'\n" +
                "    | Disabled = 'kotlin.Unit'\n" +
                "    |  |-Node #X at (X, X, X, X)px\n" +
                "    |    OnClick = 'AccessibilityAction(label=null, action=Function0" +
                "<java.lang.Boolean>)'\n" +
                "    |    Text = 'Button'\n" +
                "    |    GetTextLayoutResult = 'AccessibilityAction(label=null, " +
                "action=Function1<java.util.List<androidx.compose.ui.text.TextLayoutResult>," +
                " java.lang" +
                ".Boolean>)'\n" +
                "    |    MergeDescendants = 'true'\n" +
                "    |-Node #X at (X, X, X, X)px\n" +
                "      Text = 'Hello'\n" +
                "      GetTextLayoutResult = 'AccessibilityAction(label=null, " +
                "action=Function1<java.util.List<androidx.compose.ui.text.TextLayoutResult>," +
                " java.lang" +
                ".Boolean>)'")
    }

    @Test
    fun printMultiple_withDepth() {
        rule.setContent {
            BoundaryNode("tag1") {
                BoundaryNode("tag11") {
                    BoundaryNode("tag111")
                }
            }
            BoundaryNode("tag2") {
                BoundaryNode("tag22") {
                    BoundaryNode("tag222")
                }
            }
        }

        val result = rule.onRoot()
            .onChildren()
            .printToString(maxDepth = 1)

        assertThat(obfuscateNodesInfo(result)).isEqualTo("" +
                "1) Node #X at (X, X, X, X)px, Tag: 'tag1'\n" +
                " |-Node #X at (X, X, X, X)px, Tag: 'tag11'\n" +
                "   Has 1 child\n" +
                "2) Node #X at (X, X, X, X)px, Tag: 'tag2'\n" +
                " |-Node #X at (X, X, X, X)px, Tag: 'tag22'\n" +
                "   Has 1 child")
    }

    @Composable
    fun ComposeSimpleCase() {
        MaterialTheme {
            Column {
                Text("Hello")
                Text("World")
            }
        }
    }
}