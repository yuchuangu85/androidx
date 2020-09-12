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
package androidx.compose.desktop.examples.example1

import androidx.compose.animation.animate
import androidx.compose.animation.core.TweenSpec
import androidx.compose.desktop.AppWindow
import androidx.compose.desktop.Window
import androidx.compose.foundation.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.ExperimentalLazyDsl
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.annotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDecoration.Companion.Underline
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val title = "Desktop Compose Elements"

fun main() {
    AppWindow(title, IntSize(1024, 850)).show {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text("BUTTON") },
                    onClick = {
                        println("Floating button clicked")
                    }
                )
            },
            bodyContent = {
                Row {
                    LeftColumn(Modifier.weight(1f))
                    RightColumn(Modifier.width(200.dp))
                }
            }
        )
    }
}

@Composable
private fun LeftColumn(modifier: Modifier) = Column(modifier) {
    val amount = remember { mutableStateOf(0) }
    val animation = remember { mutableStateOf(true) }
    val text = remember {
        mutableStateOf("Hello \uD83E\uDDD1\uD83C\uDFFF\u200D\uD83E\uDDB0\nПривет")
    }
    Column(Modifier.fillMaxSize(), Arrangement.SpaceEvenly) {
        Text(
            text = "Привет! 你好! Desktop Compose ${amount.value}",
            color = Color.Black,
            modifier = Modifier
                .background(Color.Blue)
                .preferredHeight(56.dp)
                .wrapContentSize(Alignment.Center)
        )

        val inlineIndicatorId = "indicator"

        Text(
            text = annotatedString {
                append("The quick ")
                if (animation.value) {
                    appendInlineContent(inlineIndicatorId)
                }
                pushStyle(SpanStyle(
                    color = Color(0xff964B00),
                    shadow = Shadow(Color.Green, offset = Offset(1f, 1f))
                ))
                append("brown fox")
                pop()
                pushStyle(SpanStyle(background = Color.Yellow))
                append(" 🦊 ate a ")
                pop()
                pushStyle(SpanStyle(fontSize = 30.sp, textDecoration = Underline))
                append("zesty hamburgerfons")
                pop()
                append(" 🍔.\nThe 👩‍👩‍👧‍👧 laughed.")
                addStyle(SpanStyle(color = Color.Green), 25, 35)
            },
            color = Color.Black,
            inlineContent = mapOf(
                inlineIndicatorId to InlineTextContent(
                    Placeholder(
                        width = 1.em,
                        height = 1.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                    )
                ) {
                    CircularProgressIndicator(Modifier.padding(end = 3.dp))
                }
            )
        )

        val loremColors = listOf(
            Color.Black,
            Color.Yellow,
            Color.Green,
            Color.Blue
        )
        var loremColor by remember { mutableStateOf(0) }

        val loremDecorations = listOf(
            TextDecoration.None,
            TextDecoration.Underline,
            TextDecoration.LineThrough
        )
        var loremDecoration by remember { mutableStateOf(0) }
        Text(
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do" +
                    " eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad" +
                    " minim veniam, quis nostrud exercitation ullamco laboris nisi ut" +
                    " aliquipex ea commodo consequat. Duis aute irure dolor in reprehenderit" +
                    " in voluptate velit esse cillum dolore eu fugiat nulla pariatur." +
                    " Excepteur" +
                    " sint occaecat cupidatat non proident, sunt in culpa qui officia" +
                    " deserunt mollit anim id est laborum.",
            color = loremColors[loremColor],
            textDecoration = loremDecorations[loremDecoration],
            modifier = Modifier.clickable {
                if (loremColor < loremColors.size - 1) {
                    loremColor += 1
                } else {
                    loremColor = 0
                }

                if (loremDecoration < loremDecorations.size - 1) {
                    loremDecoration += 1
                } else {
                    loremDecoration = 0
                }
            }
        )

        Text(
            text = "fun <T : Comparable<T>> List<T>.quickSort(): List<T> = when {\n" +
                    "  size < 2 -> this\n" +
                    "  else -> {\n" +
                    "    val pivot = first()\n" +
                    "    val (smaller, greater) = drop(1).partition { it <= pivot }\n" +
                    "    smaller.quickSort() + pivot + greater.quickSort()\n" +
                    "   }\n" +
                    "}",
            modifier = Modifier.padding(10.dp)
        )

        Button(modifier = Modifier.padding(4.dp), onClick = {
            amount.value++
        }) {
            Text("Base")
        }

        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Button(
                    modifier = Modifier.padding(4.dp),
                    onClick = {
                        animation.value = !animation.value
                    }) {
                    Text("Toggle")
                }

                Button(
                    modifier = Modifier.padding(4.dp),
                    onClick = {
                        Window(size = IntSize(400, 200)) {
                            Animations(isCircularEnabled = animation.value)
                        }
                    }) {
                    Text("Window")
                }
            }

            Animations(isCircularEnabled = animation.value)
        }

        Slider(value = amount.value.toFloat() / 100f,
            onValueChange = { amount.value = (it * 100).toInt() })
        TextField(
            value = amount.value.toString(),
            onValueChange = { amount.value = it.toIntOrNull() ?: 42 },
            label = { Text(text = "Input1") }
        )
        TextField(
            value = text.value,
            onValueChange = { text.value = it },
            label = { Text(text = "Input2") }
        )

        Image(imageResource("androidx/compose/desktop/example/circus.jpg"))
    }
}

@Composable
fun Animations(isCircularEnabled: Boolean) = Row {
    if (isCircularEnabled) {
        CircularProgressIndicator(Modifier.padding(10.dp))
    }

    val enabled = remember { mutableStateOf(true) }
    val color = animate(
        if (enabled.value) Color.Green else Color.Red,
        animSpec = TweenSpec(durationMillis = 2000)
    )

    MaterialTheme {
        Box(
            Modifier.size(70.dp).clickable { enabled.value = !enabled.value },
            backgroundColor = color
        )
    }
}

@OptIn(ExperimentalLazyDsl::class)
@Composable
private fun RightColumn(modifier: Modifier) = LazyColumn(modifier) {
    items((1..10000).toList()) { x ->
        Text(x.toString())
    }
}
