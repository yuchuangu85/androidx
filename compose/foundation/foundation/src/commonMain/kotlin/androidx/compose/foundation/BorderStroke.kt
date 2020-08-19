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

package androidx.compose.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp

/**
 * Class to specify the stroke to draw border with.
 *
 * @param width width of the border in [Dp]. Use [Dp.Hairline] for one-pixel border.
 * @param brush brush to paint the border with
 */
@Immutable
data class BorderStroke(val width: Dp, val brush: Brush)

/**
 * Create [BorderStroke] class with width and [Color]
 *
 * @param width width of the border in [Dp]. Use [Dp.Hairline] for one-pixel border.
 * @param color color to paint the border with
 */
@Stable
fun BorderStroke(width: Dp, color: Color) = BorderStroke(width, SolidColor(color))

/**
 * Class to specify border appearance.
 *
 * @param size size of the border in [Dp]. Use [Dp.Hairline] for one-pixel border.
 * @param brush brush to paint the border with
 */
@Immutable
@Deprecated(
    "Use BorderStroke instead", replaceWith = ReplaceWith(
        "BorderStroke(size, brush)",
        "androidx.ui.foundation.BorderStroke"
    )
)
data class Border(val size: Dp, val brush: Brush)

/**
 * Create [Border] class with size and [Color]
 *
 * @param size size of the border in [Dp]. Use [Dp.Hairline] for one-pixel border.
 * @param color color to paint the border with
 */
@Stable
@Deprecated(
    "Use BorderStroke instead", replaceWith = ReplaceWith(
        "BorderStroke(size, color)",
        "androidx.ui.foundation.BorderStroke"
    )
)
@Suppress("DEPRECATION")
fun Border(size: Dp, color: Color) = Border(size, SolidColor(color))