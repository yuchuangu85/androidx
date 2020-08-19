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

package androidx.compose.animation.core

import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KeyframeAnimationTest {

    @Test
    fun equalsStartAndEndValues() {
        val start = 0f
        val end = start // the same
        val fullTime = 400
        val animation = keyframes<Float> {
            durationMillis = fullTime
            start at 100
            0.5f at 200
            0.8f at 300
            end at fullTime
        }.vectorize(Float.VectorConverter)

        assertThat(animation.at(0)).isEqualTo(start)
        assertThat(animation.at(250)).isEqualTo(0.65f)
        assertThat(animation.at(fullTime.toLong())).isEqualTo(end)
    }

    @Test
    fun possibleToOverrideStartAndEndValues() {
        val fullTime = 100
        val animation = keyframes<Float> {
            durationMillis = fullTime
            1f at 0
            0f at fullTime
        }.vectorize(Float.VectorConverter)

        assertThat(animation.at(0)).isEqualTo(1f)
        assertThat(animation.at(fullTime.toLong())).isEqualTo(0f)
    }

    @Test
    fun withEasingOnFullDuration() {
        val easing = FastOutSlowInEasing
        val animation = keyframes<Float> {
            durationMillis = 100
            0f at 0 with easing
            1f at durationMillis
        }.vectorize(Float.VectorConverter)

        assertThat(animation.at(31)).isEqualTo(easing(0.31f))
    }

    @Test
    fun easingOnTheSecondPart() {
        val easing = FastOutSlowInEasing
        val animation = keyframes<Float> {
            durationMillis = 200
            1f at 100 with easing
            2f at durationMillis
        }.vectorize(Float.VectorConverter)

        assertThat(animation.at(140)).isEqualTo(1f + easing(0.4f))
    }

    @Test
    fun firstPartIsLinearWithEasingOnTheSecondPart() {
        val animation = keyframes<Float> {
            durationMillis = 100
            0.5f at 50 with FastOutSlowInEasing
            1f at durationMillis
        }.vectorize(Float.VectorConverter)

        assertThat(animation.at(25)).isEqualTo(0.25f)
    }

    @Test
    fun testMultiDimensKeyframesWithEasing() {
        val easing = FastOutLinearInEasing
        val animation = keyframes<AnimationVector2D> {
            durationMillis = 400
            AnimationVector(200f, 300f) at 200 with easing
        }.vectorize(TwoWayConverter<AnimationVector2D, AnimationVector2D>({ it }, { it }))

        val start = AnimationVector(0f, 0f)
        val end = AnimationVector(200f, 400f)

        for (time in 0..400 step 50) {
            val v1: Float
            val v2: Float
            if (time < 200) {
                v1 = lerp(0f, 200f, time / 200f)
                v2 = lerp(0f, 300f, time / 200f)
            } else {
                v1 = 200f
                v2 = lerp(300f, 400f, easing((time - 200) / 200f))
            }
            assertEquals(
                AnimationVector(v1, v2), animation.getValue(
                    time.toLong(), start, end,
                    AnimationVector(0f, 0f)
                )
            )
        }
    }
}
