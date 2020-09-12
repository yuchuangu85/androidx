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

package androidx.compose.androidview.demos

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.integration.demos.common.ComposableDemo
import androidx.compose.integration.demos.common.DemoCategory

// TODO(b/158099918): Add this demo to AndroidViewDemos.kt once b/158099918 has been resolved.
@Suppress("unused")
val ComplexTouchInterop = DemoCategory("Complex Touch Interop", listOf(
    ComposableDemo("Compose in Android in Compose in Android") {
        ComposeInAndroidInComposeEtcTargetingDemo() }
))

@Composable
fun ComposeInAndroidInComposeEtcTargetingDemo() {
    val context = ContextAmbient.current
    Column {
        Text(
            "In this demo, from the inside out, we have a Compose Button, wrapped in 2 Android " +
                    "FrameLayouts, wrapped in a Compose Box, wrapped in a Column (which also has " +
                    "this Text) which is then in the root AndroidComposeView."
        )
        Text(
            "Each node in our tree affects the position of the button and the pointer input " +
                    "events translate from Android to compose a couple of times and everything " +
                    "still works."
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(color = Color(0xFF777777))
                .padding(48.dp)
        ) {

            AndroidView(
                modifier = Modifier.weight(1f),
                viewBlock = {
                    FrameLayout(context).apply {
                        setPadding(100, 100, 100, 100)
                        setBackgroundColor(0xFF888888.toInt())
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        addView(
                            FrameLayout(context).apply {
                                setPadding(100, 100, 100, 100)
                                setBackgroundColor(0xFF999999.toInt())
                                layoutParams =
                                    RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                                        addRule(RelativeLayout.CENTER_IN_PARENT)
                                    }
                                setContent(Recomposer.current()) {
                                    Box(
                                        Modifier
                                            .background(color = Color(0xFFAAAAAA))
                                            .fillMaxSize()
                                            .wrapContentSize(Alignment.Center)
                                    ) {
                                        colorButton()
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun colorButton() {
    val state = remember { mutableStateOf(false) }
    val color =
        if (state.value) {
            Color.Red
        } else {
            Color.Blue
        }
    Button(onClick = { state.value = !state.value }, backgroundColor = color) {
        Text("Click me")
    }
}