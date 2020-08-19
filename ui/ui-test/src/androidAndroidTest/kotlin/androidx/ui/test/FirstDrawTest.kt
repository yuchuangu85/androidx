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

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.test.espresso.Espresso.onIdle
import androidx.test.filters.MediumTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.foundation.Box
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.ui.test.android.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@MediumTest
class FirstDrawTest {

    @get:Rule
    val testRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Tests that the compose tree has been drawn at least once when [onIdle] finishes.
     */
    @Test
    fun waitsForFirstDraw() {
        var drawn = false
        testRule.setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawn = true
            }
        }
        onIdle()
        assertThat(drawn).isTrue()
    }

    /**
     * Tests that the compose tree has been drawn at least once when [onIdle] finishes.
     */
    @Test
    fun waitsForFirstDraw_withoutOnIdle() {
        var drawn = false
        testRule.setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawn = true
            }
        }
        // onIdle() shouldn't be necessary
        assertThat(drawn).isTrue()
    }

    /**
     * Tests that [onIdle] doesn't timeout when the compose tree is completely off-screen and
     * will hence not be drawn.
     */
    @Test
    fun waitsForOutOfBoundsComposeView() {
        var drawn = false

        testRule.activityRule.scenario.onActivity { activity ->
            // Set the compose content in a FrameLayout that is completely placed out of the
            // screen, and enforce clipToPadding in case clipping will prevent the clipped
            // content from being drawn.

            val root = object : FrameLayout(activity) {
                override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                    // Place our child out of bounds
                    getChildAt(0).layout(-200, 0, -100, 100)
                }
            }.apply {
                // Enforce clipping:
                setPadding(1, 1, 1, 1)
                clipToPadding = true
            }

            val outOfBoundsView = FrameLayout(activity).apply {
                layoutParams = ViewGroup.MarginLayoutParams(100, 100)
            }

            root.addView(outOfBoundsView)
            activity.setContentView(root)
            outOfBoundsView.setContent(Recomposer.current()) {
                // If you see this box when running the test, the test is setup incorrectly
                Box(Modifier, backgroundColor = Color.Yellow)
                Canvas(Modifier) {
                    drawn = true
                }
            }
        }

        // onIdle shouldn't timeout
        onIdle()
        // The compose view was off-screen, so it hasn't drawn yet
        assertThat(drawn).isFalse()
    }
}
