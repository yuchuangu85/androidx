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

package androidx.compose.foundation.lazy

import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.ui.test.assertTopPositionInRootIsEqualTo
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LazyForIndexedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun columnWithIndexesComposedWithCorrectIndexAndItem() {
        val items = (0..1).map { it.toString() }

        composeTestRule.setContent {
            LazyColumnForIndexed(items, Modifier.preferredHeight(200.dp)) { index, item ->
                Text("${index}x$item", Modifier.fillParentMaxWidth().height(100.dp))
            }
        }

        onNodeWithText("0x0")
            .assertTopPositionInRootIsEqualTo(0.dp)

        onNodeWithText("1x1")
            .assertTopPositionInRootIsEqualTo(100.dp)
    }

    @Test
    fun rowWithIndexesComposedWithCorrectIndexAndItem() {
        val items = (0..1).map { it.toString() }

        composeTestRule.setContent {
            LazyRowForIndexed(items, Modifier.preferredWidth(200.dp)) { index, item ->
                Text("${index}x$item", Modifier.fillParentMaxHeight().width(100.dp))
            }
        }

        onNodeWithText("0x0")
            .assertLeftPositionInRootIsEqualTo(0.dp)

        onNodeWithText("1x1")
            .assertLeftPositionInRootIsEqualTo(100.dp)
    }
}
