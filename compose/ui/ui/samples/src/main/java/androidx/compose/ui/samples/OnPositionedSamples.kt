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

package androidx.compose.ui.samples

import androidx.annotation.Sampled
import androidx.compose.foundation.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.globalPosition
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.onPositioned
import androidx.compose.ui.unit.dp

@Sampled
@Composable
fun OnPositionedSample() {
    Column(Modifier.onPositioned { coordinates ->
        // This will be the size of the Column.
        coordinates.size
        // The position of the Column relative to the application window.
        coordinates.globalPosition
        // The position of the Column relative to the Compose root.
        coordinates.positionInRoot
        // These will be the alignment lines provided to the layout (empty here for Column).
        coordinates.providedAlignmentLines
        // This will a LayoutCoordinates instance corresponding to the parent of Column.
        coordinates.parentCoordinates
    }) {
        Box(Modifier.preferredSize(20.dp), backgroundColor = Color.Green)
        Box(Modifier.preferredSize(20.dp), backgroundColor = Color.Blue)
    }
}
