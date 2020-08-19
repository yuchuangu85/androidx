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

package androidx.ui.test

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.filters.MediumTest
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.foundation.Box
import androidx.compose.foundation.ScrollableColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.ui.test.android.createAndroidComposeRule
import androidx.ui.test.util.BoundaryNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class IsDisplayedTest(val config: BitmapCapturingTest.TestConfig) {
    data class TestConfig(
        val activityClass: Class<out ComponentActivity>
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<BitmapCapturingTest.TestConfig> = listOf(
            BitmapCapturingTest.TestConfig(ComponentActivity::class.java),
            BitmapCapturingTest.TestConfig(ActivityWithActionBar::class.java)
        )
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule(config.activityClass)

    private val colors = listOf(Color.Red, Color.Green, Color.Blue)

    @Composable
    private fun Item(i: Int, width: Dp? = null, height: Dp? = null) {
        BoundaryNode("item$i") {
            Box(
                modifier =
                with(Modifier) { width?.let { width(it) } ?: fillMaxWidth() }
                    .then(with(Modifier) { height?.let { height(it) } ?: fillMaxHeight() }),
                backgroundColor = colors[i % colors.size]
            )
        }
    }

    @Composable
    fun PlaceConditionally(place: Boolean, child: @Composable () -> Unit) {
        Layout(children = child) { measurables, constraints ->
            if (place) {
                val placeable = measurables[0].measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            } else {
                layout(0, 0) {}
            }
        }
    }

    @Test
    fun componentInScrollable_isDisplayed() {
        composeTestRule.setContent {
            ScrollableColumn(modifier = Modifier.size(100.dp)) {
                repeat(10) { Item(it, height = 30.dp) }
            }
        }

        onNodeWithTag("item0")
            .assertIsDisplayed()
    }

    @Test
    fun componentInScrollable_isNotDisplayed() {
        composeTestRule.setContent {
            ScrollableColumn(modifier = Modifier.size(100.dp)) {
                repeat(10) { Item(it, height = 30.dp) }
            }
        }

        onNodeWithTag("item4")
            .assertIsNotDisplayed()
    }

    @Test
    fun togglePlacement() {
        var place by mutableStateOf(true)

        composeTestRule.setContent {
            PlaceConditionally(place) {
                // Item instead of BoundaryNode because we need non-zero size
                Item(0)
            }
        }

        onNodeWithTag("item0")
            .assertIsDisplayed()

        runOnIdle {
            place = false
        }

        onNodeWithTag("item0")
            .assertIsNotDisplayed()
    }

    @Test
    fun toggleParentPlacement() {
        var place by mutableStateOf(true)

        composeTestRule.setContent {
            PlaceConditionally(place) {
                Stack {
                    // Item instead of BoundaryNode because we need non-zero size
                    Item(0)
                }
            }
        }

        onNodeWithTag("item0")
            .assertIsDisplayed()

        runOnIdle {
            place = false
        }

        onNodeWithTag("item0")
            .assertIsNotDisplayed()
    }

    @Test
    fun rowTooSmall() {
        composeTestRule.setContent {
            Row(modifier = Modifier.size(100.dp)) {
                repeat(10) { Item(it, width = 30.dp) }
            }
        }

        onNodeWithTag("item9")
            .assertIsNotDisplayed()
    }

    @Test
    fun viewVisibility_androidComposeView() {
        lateinit var androidComposeView: View
        composeTestRule.activityRule.scenario.onActivity { activity ->
            // FrameLayout(id=100, w=100, h=100)
            // '- AndroidComposeView
            androidComposeView = FrameLayout(activity).apply {
                id = 100
                layoutParams = ViewGroup.MarginLayoutParams(100, 100)
                activity.setContentView(this)
                setContent(Recomposer.current()) {
                    Item(0)
                }
            }.getChildAt(0)
        }

        fun onComposeView(): ViewInteraction {
            return onView(allOf(withParent(withId(100))))
        }

        onComposeView().check(matches(isDisplayed()))
        onNodeWithTag("item0").assertIsDisplayed()

        runOnIdle {
            androidComposeView.visibility = View.GONE
        }

        onComposeView().check(matches(not(isDisplayed())))
        onNodeWithTag("item0").assertIsNotDisplayed()
    }

    @Test
    fun viewVisibility_parentView() {
        lateinit var composeContainer: View
        composeTestRule.activityRule.scenario.onActivity { activity ->
            // FrameLayout
            // '- FrameLayout(id=100, w=100, h=100) -> composeContainer
            //    '- AndroidComposeView
            composeContainer = FrameLayout(activity).apply {
                id = 100
                layoutParams = ViewGroup.MarginLayoutParams(100, 100)
                activity.setContentView(FrameLayout(activity).also { it.addView(this) })
                setContent(Recomposer.current()) {
                    Item(0)
                }
            }
        }

        fun onComposeView(): ViewInteraction {
            return onView(allOf(withParent(withId(100))))
        }

        onComposeView().check(matches(isDisplayed()))
        onNodeWithTag("item0").assertIsDisplayed()

        runOnIdle {
            composeContainer.visibility = View.GONE
        }

        onComposeView().check(matches(not(isDisplayed())))
        onNodeWithTag("item0").assertIsNotDisplayed()
    }
}
