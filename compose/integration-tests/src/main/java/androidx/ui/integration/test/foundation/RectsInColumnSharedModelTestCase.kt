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

package androidx.ui.integration.test.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Box
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.ui.test.ComposeTestCase
import androidx.ui.test.ToggleableTestCase
import androidx.compose.foundation.layout.preferredSize

/**
 * Test case that puts the given amount of rectangles into a column layout and makes changes by
 * modifying the color used in the model.
 *
 * Note: Rectangle are created in for loop that reference a single model. Currently it will happen
 * that the whole loop has to be re-run when model changes.
 */
class RectsInColumnSharedModelTestCase(
    private val amountOfRectangles: Int
) : ComposeTestCase, ToggleableTestCase {

    private val color = mutableStateOf(Color.Black)

    @Composable
    override fun emitContent() {
        MaterialTheme {
            Column {
                repeat(amountOfRectangles) { i ->
                    if (i == 0) {
                        Box(Modifier.preferredSize(100.dp, 50.dp).background(color = color.value))
                    } else {
                        Box(Modifier.preferredSize(100.dp, 50.dp).background(color = Color.Green))
                    }
                }
            }
        }
    }

    override fun toggleState() {
        if (color.value == Color.Magenta) {
            color.value = Color.Blue
        } else {
            color.value = Color.Magenta
        }
    }
}