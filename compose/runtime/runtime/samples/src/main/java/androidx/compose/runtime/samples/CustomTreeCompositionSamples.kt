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

package androidx.compose.runtime.samples

import androidx.annotation.Sampled
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.compositionFor
import androidx.compose.runtime.emit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Suppress("unused")
@OptIn(ExperimentalComposeApi::class)
@Sampled
fun CustomTreeComposition() {
    // Provided we have a tree with a node base type like the following
    abstract class Node {
        val children = mutableListOf<Node>()
    }

    // We would implement an Applier class like the following, which would teach compose how to
    // manage a tree of Nodes.
    class NodeApplier(root: Node) : AbstractApplier<Node>(root) {
        override fun insert(index: Int, instance: Node) {
            current.children.add(index, instance)
        }

        override fun remove(index: Int, count: Int) {
            current.children.remove(index, count)
        }

        override fun move(from: Int, to: Int, count: Int) {
            current.children.move(from, to, count)
        }

        override fun onClear() {
            root.children.clear()
        }
    }

    // A function like the following could be created to create a composition provided a root Node.
    fun Node.setContent(content: @Composable () -> Unit): Composition {
        return compositionFor(this, NodeApplier(this), Recomposer.current()).also {
            setContent(content)
        }
    }

    // assuming we have Node sub-classes like "TextNode" and "GroupNode"
    class TextNode : Node() {
        var text: String = ""
        var onClick: () -> Unit = {}
    }
    class GroupNode : Node()

    // Composable equivalents could be created
    @Composable fun Text(text: String, onClick: () -> Unit = {}) {
        emit<TextNode, NodeApplier>(::TextNode) {
            set(text) { this.text = it }
            set(onClick) { this.onClick = it }
        }
    }

    @Composable fun Group(content: @Composable () -> Unit) {
        emit<GroupNode, NodeApplier>(::GroupNode, {}, content)
    }

    // and then a sample tree could be composed:
    fun runApp(root: GroupNode) {
        root.setContent {
            var count by remember { mutableStateOf(0) }
            Group {
                Text("Count: $count")
                Text("Increment") { count++ }
            }
        }
    }
}
