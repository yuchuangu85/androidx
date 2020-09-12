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

package androidx.compose.material.textfield

import android.os.Build
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.material.GOLDEN_MATERIAL
import androidx.compose.material.TextField
import androidx.compose.material.setMaterialContent
import androidx.compose.runtime.Providers
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.ui.test.captureToBitmap
import androidx.ui.test.center
import androidx.ui.test.createComposeRule
import androidx.ui.test.down
import androidx.ui.test.move
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.performGesture
import androidx.ui.test.up
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class TextFieldScreenshotTest {
    private val TextFieldTag = "TextField"

    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL)

    @Test
    fun textField_withInput() {
        rule.setMaterialContent {
            Box(Modifier.semantics(mergeAllDescendants = true) {}.testTag(TextFieldTag)) {
                TextField(value = "Text",
                    onValueChange = {},
                    label = { Text("Label") }
                )
            }
        }

        assertAgainstGolden("filled_textField_withInput")
    }

    @Test
    fun textField_notFocused() {
        rule.setMaterialContent {
            Box(Modifier.semantics(mergeAllDescendants = true) {}.testTag(TextFieldTag)) {
                TextField(value = "",
                    onValueChange = {},
                    label = { Text("Label") }
                )
            }
        }

        assertAgainstGolden("filled_textField_not_focused")
    }

    @Test
    fun textField_focused() {
        rule.setMaterialContent {
            Box(Modifier.semantics(mergeAllDescendants = true) {}.testTag(TextFieldTag)) {
                TextField(value = "",
                    onValueChange = {},
                    label = { Text("Label") }
                )
            }
        }

        rule.onNodeWithTag(TextFieldTag)
            // split click into (down) and (move, up) to enforce a composition in between
            .performGesture { down(center) }
            .performGesture { move(); up() }

        assertAgainstGolden("filled_textField_focused")
    }

    @Test
    fun textField_focused_rtl() {
        rule.setMaterialContent {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Box(Modifier.semantics(mergeAllDescendants = true) {}.testTag(TextFieldTag)) {
                    TextField(value = "",
                        onValueChange = {},
                        label = { Text("Label") }
                    )
                }
            }
        }

        rule.onNodeWithTag(TextFieldTag)
            // split click into (down) and (move, up) to enforce a composition in between
            .performGesture { down(center) }
            .performGesture { move(); up() }

        assertAgainstGolden("filled_textField_focused_rtl")
    }

    @Test
    fun textField_error_focused() {
        rule.setMaterialContent {
            TextField(
                value = "Input",
                onValueChange = {},
                label = { Text("Label") },
                isErrorValue = true,
                modifier = Modifier.testTag(TextFieldTag)
            )
        }

        rule.onNodeWithTag(TextFieldTag)
            // split click into (down) and (move, up) to enforce a composition in between
            .performGesture { down(center) }
            .performGesture { move(); up() }

        assertAgainstGolden("filled_textField_focused_errorState")
    }

    @Test
    fun textField_error_notFocused() {
        rule.setMaterialContent {
            TextField(
                value = "",
                onValueChange = {},
                label = { Text("Label") },
                isErrorValue = true,
                modifier = Modifier.testTag(TextFieldTag)
            )
        }

        assertAgainstGolden("filled_textField_notFocused_errorState")
    }

    private fun assertAgainstGolden(goldenIdentifier: String) {
        rule.onNodeWithTag(TextFieldTag)
            .captureToBitmap()
            .assertAgainstGolden(screenshotRule, goldenIdentifier)
    }
}