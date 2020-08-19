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

package androidx.compose.ui.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.drawLayer
import androidx.compose.ui.platform.InspectableParameter
import com.google.common.truth.Truth
import org.junit.Test

class DrawLayerModifierTest {

    @Test
    fun testInspectable() {
        val modifier = Modifier.drawLayer(rotationX = 2.0f) as InspectableParameter
        Truth.assertThat(modifier.nameFallback).isEqualTo("drawLayer")
        Truth.assertThat(modifier.valueOverride).isNull()
        Truth.assertThat(modifier.inspectableElements.map { it.name }.toList())
            .containsExactlyElementsIn(modifier.javaClass.declaredFields
                .filter { !it.isSynthetic && it.name != "nameFallback" }
                .map { it.name })
    }
}