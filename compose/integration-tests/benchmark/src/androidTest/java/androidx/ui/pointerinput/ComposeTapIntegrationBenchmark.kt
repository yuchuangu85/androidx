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

package androidx.ui.pointerinput

import android.view.View
import android.view.ViewGroup
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.foundation.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.dp
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Benchmark for simply tapping on an item in Compose.
 *
 * The intent is to measure the speed of all parts necessary for a normal tap starting from
 * [MotionEvent]s getting dispatched to a particular view.  The test therefore includes hit
 * testing and dispatch.
 *
 * This is intended to be an equivalent counterpart to [AndroidTapIntegrationBenchmark].
 *
 * The hierarchy is set up to look like:
 * rootView
 *   -> Column
 *     -> Text (with click listener)
 *     -> Text (with click listener)
 *     -> Text (with click listener)
 *     -> ...
 *
 * MotionEvents are dispatched to rootView as ACTION_DOWN followed by ACTION_UP.  The validity of
 * the test is verified inside the click listener with com.google.common.truth.Truth.assertThat
 * and by counting the clicks in the click listener and later verifying that they count is
 * sufficiently high.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ComposeTapIntegrationBenchmark {

    private lateinit var rootView: View
    private lateinit var expectedLabel: String

    private var itemHeightDp = 0.dp // Is set to correct value during composition.
    private var actualClickCount = 0
    private var expectedClickCount = 0

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Suppress("DEPRECATION")
    @get:Rule
    val activityTestRule = androidx.test.rule.ActivityTestRule(TestActivity::class.java)

    @Before
    fun setup() {
        val activity = activityTestRule.activity
        Assert.assertTrue(
            "timed out waiting for activity focus",
            activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
        )

        rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        activityTestRule.runOnUiThreadIR {
            activity.setContent {
                with(DensityAmbient.current) {
                    itemHeightDp = ItemHeightPx.toDp()
                }
                App()
            }
        }
    }

    // This test requires more hit test processing so changes to hit testing will be tracked more
    // by this test.
    @UiThreadTest
    @Test
    fun clickOnLateItem() {
        // As items that are laid out last are hit tested first (so z order is respected), item
        // at 0 will be hit tested late.
        clickOnItem(0, "0")
    }

    // This test requires less hit testing so changes to dispatch will be tracked more by this test.
    @UiThreadTest
    @Test
    fun clickOnEarlyItemFyi() {
        // As items that are laid out last are hit tested first (so z order is respected), item
        // at NumItems - 1 will be hit tested early.
        val lastItem = NumItems - 1
        clickOnItem(lastItem, "$lastItem")
    }

    private fun clickOnItem(item: Int, expectedLabel: String) {

        this.expectedLabel = expectedLabel

        // half height of an item + top of the chosen item = middle of the chosen item
        val y = (ItemHeightPx / 2) + (item * ItemHeightPx)

        val down = MotionEvent(
            0,
            android.view.MotionEvent.ACTION_DOWN,
            1,
            0,
            arrayOf(PointerProperties(0)),
            arrayOf(PointerCoords(0f, y)),
            rootView
        )

        val up = MotionEvent(
            10,
            android.view.MotionEvent.ACTION_UP,
            1,
            0,
            arrayOf(PointerProperties(0)),
            arrayOf(PointerCoords(0f, y)),
            rootView
        )

        benchmarkRule.measureRepeated {
            rootView.dispatchTouchEvent(down)
            rootView.dispatchTouchEvent(up)
            expectedClickCount++
        }

        assertThat(actualClickCount).isEqualTo(expectedClickCount)
    }

    @Composable
    fun App() {
        EmailList(NumItems)
    }

    @Composable
    fun EmailList(count: Int) {
        Column {
            repeat(count) { i ->
                Email("$i")
            }
        }
    }

    @Composable
    fun Email(label: String) {
        Text(
            text = label,
            modifier = Modifier
                .clickable {
                    assertThat(label).isEqualTo(expectedLabel)
                    actualClickCount++
                }
                .fillMaxWidth()
                .height(itemHeightDp)
        )
    }
}
