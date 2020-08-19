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

package androidx.compose.material

import android.os.Build
import androidx.compose.foundation.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Providers
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.ui.test.captureToBitmap
import androidx.ui.test.center
import androidx.ui.test.createComposeRule
import androidx.ui.test.down
import androidx.ui.test.isToggleable
import androidx.ui.test.move
import androidx.ui.test.onNode
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.performGesture
import androidx.ui.test.up
import androidx.ui.test.waitForIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class SwitchScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL)

    // TODO: this test tag as well as Boxes inside testa are temporarty, remove then b/157687898
    //  is fixed
    private val wrapperTestTag = "switchWrapper"

    private val wrapperModifier = Modifier
        .wrapContentSize(Alignment.TopStart)
        .testTag(wrapperTestTag)

    @Test
    fun switchTest_checked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = true, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_checked")
    }

    @Test
    fun switchTest_checked_rtl() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    Switch(checked = true, onCheckedChange = { })
                }
            }
        }
        assertToggeableAgainstGolden("switch_checked_rtl")
    }

    @Test
    fun switchTest_checked_customColor() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = true, onCheckedChange = { }, color = Color.Red)
            }
        }
        assertToggeableAgainstGolden("switch_checked_customColor")
    }

    @Test
    fun switchTest_unchecked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_unchecked")
    }

    @Test
    fun switchTest_unchecked_rtl() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                    Switch(checked = false, onCheckedChange = { })
                }
            }
        }
        assertToggeableAgainstGolden("switch_unchecked_rtl")
    }

    @Test
    fun switchTest_pressed() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = false, enabled = true, onCheckedChange = { })
            }
        }

        onNodeWithTag(wrapperTestTag).performGesture {
            down(center)
        }
        assertToggeableAgainstGolden("switch_pressed")
    }

    @Test
    fun switchTest_disabled_checked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = true, enabled = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_disabled_checked")
    }

    @Test
    fun switchTest_disabled_unchecked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = false, enabled = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_disabled_unchecked")
    }

    @Test
    fun switchTest_unchecked_animateToChecked() {
        composeTestRule.setMaterialContent {
            val isChecked = remember { mutableStateOf(false) }
            Box(wrapperModifier) {
                Switch(
                    checked = isChecked.value,
                    onCheckedChange = { isChecked.value = it }
                )
            }
        }

        composeTestRule.clockTestRule.pauseClock()

        onNode(isToggleable())
            // split click into (down) and (move, up) to enforce a composition in between
            .performGesture { down(center) }
            .performGesture { move(); up() }

        waitForIdle()

        composeTestRule.clockTestRule.advanceClock(60)

        assertToggeableAgainstGolden("switch_animateToChecked")
    }

    @Test
    fun switchTest_checked_animateToUnchecked() {
        composeTestRule.setMaterialContent {
            val isChecked = remember { mutableStateOf(true) }
            Box(wrapperModifier) {
                Switch(
                    checked = isChecked.value,
                    onCheckedChange = { isChecked.value = it }
                )
            }
        }

        composeTestRule.clockTestRule.pauseClock()

        onNode(isToggleable())
            // split click into (down) and (move, up) to enforce a composition in between
            .performGesture { down(center) }
            .performGesture { move(); up() }

        waitForIdle()

        composeTestRule.clockTestRule.advanceClock(60)

        assertToggeableAgainstGolden("switch_animateToUnchecked")
    }

    private fun assertToggeableAgainstGolden(goldenName: String) {
        // TODO: replace with find(isToggeable()) after b/157687898 is fixed
        onNodeWithTag(wrapperTestTag)
            .captureToBitmap()
            .assertAgainstGolden(screenshotRule, goldenName)
    }
}