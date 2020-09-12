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

package androidx.compose.animation

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.LayoutModifier
import androidx.compose.ui.Measurable
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.filters.MediumTest
import androidx.ui.test.createComposeRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@MediumTest
class AnimationModifierTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun animateContentSizeTest() {
        val startWidth = 100
        val endWidth = 150
        val startHeight = 400
        val endHeight = 200
        var width by mutableStateOf(startWidth)
        var height by mutableStateOf(startHeight)

        var density = 0f
        val testModifier by mutableStateOf(TestModifier())
        var animationStartSize: IntSize? = null
        var animationEndSize: IntSize? = null

        rule.clockTestRule.pauseClock()
        rule.setContent {
            Box(
                testModifier
                    .animateContentSize(
                        tween(
                            200,
                            easing = LinearOutSlowInEasing
                        )
                    ) { startSize, endSize ->
                        animationStartSize = startSize
                        animationEndSize = endSize
                    }
                    .size(width.dp, height.dp)
            )
            density = DensityAmbient.current.density
        }

        rule.runOnIdle {
            width = endWidth
            height = endHeight
        }
        rule.waitForIdle()

        for (i in 0..200 step 20) {
            val fraction = LinearOutSlowInEasing.invoke(i / 200f)
            assertEquals(
                density * (startWidth * (1 - fraction) + endWidth * fraction),
                testModifier.width.toFloat(), 1f
            )

            assertEquals(
                density * (startHeight * (1 - fraction) + endHeight * fraction),
                testModifier.height.toFloat(), 1f
            )

            if (i == 200) {
                assertNotNull(animationStartSize)
                assertEquals(
                    animationStartSize!!.width.toFloat(),
                    startWidth * density, 1f
                )
                assertEquals(
                    animationStartSize!!.height.toFloat(),
                    startHeight * density, 1f
                )
            } else {
                assertNull(animationEndSize)
            }

            rule.clockTestRule.advanceClock(20)
            rule.waitForIdle()
        }
    }
}

internal class TestModifier : LayoutModifier {
    var width: Int = 0
    var height: Int = 0
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(constraints)
        width = placeable.width
        height = placeable.height
        return layout(width, height) {
            placeable.place(0, 0)
        }
    }
}
