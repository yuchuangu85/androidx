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

package androidx.compose.ui.input.pointer

import android.content.Context
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Box
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.test.TestActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.filters.MediumTest
import androidx.ui.test.android.createAndroidComposeRule
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// Tests that pointer offsets are correct when a pointer is dispatched from Android through
// Compose and back into Android and each layer offsets the pointer during dispatch.
@MediumTest
@RunWith(JUnit4::class)
class PointerInteropFilterAndroidViewOffsetsTest {

    private lateinit var five: View
    private val theHitListener: () -> Unit = mock()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Before
    fun setup() {
        composeTestRule.activityRule.scenario.onActivity { activity ->

            // one: Android View that is the touch target, inside
            // two: Android View with 1x2 padding, inside
            // three: Compose Box with 2x12 padding, inside
            // four: Android View with 3x13 padding, inside
            // five: Android View with 4x14 padding
            //
            // With all of the padding, "one" is at 10 x 50 relative to "five" and the tests
            // dispatch MotionEvents to "five".

            val one = CustomView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(1, 1)
                hitListener = theHitListener
            }

            val two = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(1, 11, 0, 0)
                addView(one)
            }

            val four = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(3, 13, 0, 0)
                setContent(Recomposer.current()) {
                    with(DensityAmbient.current) {
                        // Box is "three"
                        Box(
                            paddingStart = (2f / density).dp,
                            paddingTop = (12f / density).dp
                        ) {
                            AndroidView({ two })
                        }
                    }
                }
            }

            five = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(4, 14, 0, 0)
                addView(four)
            }

            activity.setContentView(five)
        }
    }

    @Test
    fun uiClick_inside_hits() {
        uiClick(10, 50, true)
    }

    @Test
    fun uiClick_justOutside_misses() {
        uiClick(9, 50, false)
        uiClick(10, 49, false)
        uiClick(11, 50, false)
        uiClick(10, 51, false)
    }

    // Gets reused to should always clean up state.
    private fun uiClick(x: Int, y: Int, hits: Boolean) {
        clearInvocations(theHitListener)

        composeTestRule.activityRule.scenario.onActivity {
            val down =
                MotionEvent.obtain(
                    0L,
                    0L,
                    ACTION_DOWN,
                    x.toFloat(),
                    y.toFloat(),
                    0
                )
            val up =
                MotionEvent.obtain(
                    0L,
                    1L,
                    ACTION_UP,
                    x.toFloat(),
                    y.toFloat(),
                    0
                )
            five.dispatchTouchEvent(down)
            five.dispatchTouchEvent(up)
        }

        if (hits) {
            verify(theHitListener, times(2)).invoke()
        } else {
            verify(theHitListener, never()).invoke()
        }
    }
}

private class CustomView(context: Context) : View(context) {
    lateinit var hitListener: () -> Unit

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        hitListener()
        return true
    }
}