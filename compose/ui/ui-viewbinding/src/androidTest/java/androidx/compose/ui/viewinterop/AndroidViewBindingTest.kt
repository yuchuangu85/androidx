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

package androidx.compose.ui.viewinterop

import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.databinding.TestLayoutBinding
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.ui.test.assertPixels
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.runOnIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class AndroidViewBindingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun drawing() {
        rule.setContent {
            AndroidViewBinding(TestLayoutBinding::inflate, Modifier.testTag("layout"))
        }

        val size = 50.dp
        val sizePx = with(rule.density) { size.toIntPx() }
        onNodeWithTag("layout").captureToBitmap().assertPixels(IntSize(sizePx, sizePx * 2)) {
            if (it.y < sizePx) Color.Blue else Color.Black
        }
    }

    @Test
    fun update() {
        val color = mutableStateOf(Color.Gray)
        rule.setContent {
            AndroidViewBinding(TestLayoutBinding::inflate, Modifier.testTag("layout")) {
                second.setBackgroundColor(color.value.toArgb())
            }
        }

        val size = 50.dp
        val sizePx = with(rule.density) { size.toIntPx() }
        onNodeWithTag("layout").captureToBitmap().assertPixels(IntSize(sizePx, sizePx * 2)) {
            if (it.y < sizePx) Color.Blue else color.value
        }

        runOnIdle { color.value = Color.DarkGray }
        onNodeWithTag("layout").captureToBitmap().assertPixels(IntSize(sizePx, sizePx * 2)) {
            if (it.y < sizePx) Color.Blue else color.value
        }
    }
}
