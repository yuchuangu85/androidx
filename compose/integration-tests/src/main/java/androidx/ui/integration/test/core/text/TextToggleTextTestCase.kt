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

package androidx.ui.integration.test.core.text

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.ui.integration.test.RandomTextGenerator
import androidx.ui.test.ToggleableTestCase
import androidx.ui.test.ComposeTestCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

class TextToggleTextTestCase(
    private val textGenerator: RandomTextGenerator,
    private val textLength: Int,
    private val textNumber: Int,
    private val width: Dp,
    private val fontSize: TextUnit
) : ComposeTestCase, ToggleableTestCase {

    private val texts = mutableStateOf(
        List(textNumber) {
            textGenerator.nextParagraph(length = textLength)
        }
    )

    @Composable
    override fun emitContent() {
        Column(
            modifier = Modifier.wrapContentSize(Alignment.Center).preferredWidth(width)
        ) {
            for (text in texts.value) {
                Text(text = text, color = Color.Black, fontSize = fontSize)
            }
        }
    }

    override fun toggleState() {
        texts.value = List(textNumber) {
            textGenerator.nextParagraph(length = textLength)
        }
    }
}