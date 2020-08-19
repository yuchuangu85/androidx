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

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset

/**
 * Class that represents the corresponding Shader implementation on a platform. This maps
 * to Gradients or ImageShaders
 */
expect class Shader

/**
 * Creates a linear gradient from `from` to `to`.
 *
 * If `colorStops` is provided, each value is a number from 0.0 to 1.0
 * that specifies where the color at the corresponding index in [colors]
 * begins in the gradient. If `colorStops` is not provided, then the colors are dispersed evenly
 *
 * The behavior before [from] and after [to] is described by the `tileMode`
 * argument. For details, see the [TileMode] enum. If no [TileMode] is provided
 * the default value of [TileMode.Clamp] is used
 */
fun LinearGradientShader(
    from: Offset,
    to: Offset,
    colors: List<Color>,
    colorStops: List<Float>? = null,
    tileMode: TileMode = TileMode.Clamp
): Shader = ActualLinearGradientShader(
    from,
    to,
    colors,
    colorStops,
    tileMode
)

internal expect fun ActualLinearGradientShader(
    from: Offset,
    to: Offset,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode
): Shader

/**
 * Creates a radial gradient centered at `center` that ends at `radius`
 * distance from the center.
 *
 * If `colorStops` is provided, each value is a number from 0.0 to 1.0
 * that specifies where the color at the corresponding index in [colors]
 * begins in the gradient. If `colorStops` is not provided, then the colors are dispersed evenly
 *
 * The behavior before and after the radius is described by the `tileMode`
 * argument. For details, see the [TileMode] enum.
 *
 * The behavior outside of the bounds of [center] +/- [radius] is described by the `tileMode`
 * argument. For details, see the [TileMode] enum. If no [TileMode] is provided
 * the default value of [TileMode.Clamp] is used
 */
fun RadialGradientShader(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    colorStops: List<Float>? = null,
    tileMode: TileMode = TileMode.Clamp
): Shader = ActualRadialGradientShader(center, radius, colors, colorStops, tileMode)

internal expect fun ActualRadialGradientShader(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode
): Shader

fun ImageShader(
    image: ImageAsset,
    tileModeX: TileMode = TileMode.Clamp,
    tileModeY: TileMode = TileMode.Clamp
): Shader = ActualImageShader(image, tileModeX, tileModeY)

internal expect fun ActualImageShader(
    image: ImageAsset,
    tileModeX: TileMode,
    tileModeY: TileMode
): Shader