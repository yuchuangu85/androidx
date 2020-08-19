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

import androidx.test.filters.MediumTest
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.Modifier
import androidx.compose.ui.onPositioned
import androidx.compose.foundation.Box
import androidx.compose.foundation.ContentGravity
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@RunWith(JUnit4::class)
class LayoutCoordinatesHelperTest {

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun positionInParent_noOffset() {
        val latch = CountDownLatch(2)
        var parentCoordinates: LayoutCoordinates? = null
        var childCoordinates: LayoutCoordinates? = null
        composeTestRule.setContent {
            Column(Modifier.onPositioned { coordinates: LayoutCoordinates ->
                parentCoordinates = coordinates
                latch.countDown()
            }) {
                Box(
                    Modifier.preferredSize(10.dp)
                        .gravity(Alignment.Start)
                        .onPositioned { coordinates ->
                            childCoordinates = coordinates
                            latch.countDown()
                        }
                )
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(
            Offset.Zero,
            parentCoordinates!!.childToLocal(childCoordinates!!, Offset.Zero)
        )
    }

    @Test
    fun positionInParent_centered() {
        val latch = CountDownLatch(2)
        var parentCoordinates: LayoutCoordinates? = null
        var childCoordinates: LayoutCoordinates? = null
        composeTestRule.setContent {
            with(DensityAmbient.current) {
                Box(Modifier.preferredWidth(40.toDp()), gravity = ContentGravity.Center) {
                    Column(
                        Modifier.preferredWidth(20.toDp())
                            .onPositioned { coordinates: LayoutCoordinates ->
                                parentCoordinates = coordinates
                                latch.countDown()
                            }
                    ) {
                        Box(
                            Modifier.preferredSize(10.toDp())
                                .gravity(Alignment.CenterHorizontally)
                                .onPositioned { coordinates ->
                                    childCoordinates = coordinates
                                    latch.countDown()
                                }
                        )
                    }
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(
            Offset(5f, 0f),
            parentCoordinates!!.childToLocal(childCoordinates!!, Offset.Zero)
        )
    }
}
