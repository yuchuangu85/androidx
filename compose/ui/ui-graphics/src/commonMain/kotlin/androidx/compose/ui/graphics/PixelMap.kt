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

package androidx.compose.ui.graphics

import androidx.compose.ui.util.annotation.IntRange

/**
 * Result of a pixel read operation. This contains the [ImageAsset] pixel information represented
 * as a 1 dimensional array of values that supports queries of pixel values based on the 2
 * dimensional coordinates of the corresponding [ImageAsset] this was obtained from
 *
 * @sample androidx.compose.ui.graphics.samples.ImageAssetReadPixelsSample
 *
 * @param buffer IntArray where pixel information is stored as an ARGB value packed into an Int
 * @param bufferOffset first index in the buffer where pixel information for the [ImageAsset] is
 * stored
 * @param width Width of the subsection of the [ImageAsset] this buffer represents
 * @param height Height of the subsection of the [ImageAsset] this buffer represents
 * @param stride Number of entries to skip between rows
 *
 * @see ImageAsset.readPixels
 * @See ImageAsset.toPixelMap
 */
class PixelMap(
    val buffer: IntArray,
    val width: Int,
    val height: Int,
    val bufferOffset: Int,
    val stride: Int
) {
    /**
     * Obtain the color of the pixel at the given coordinate
     */
    operator fun get(
        @IntRange(from = 0) x: Int,
        @IntRange(from = 0) y: Int
    ): Color = Color(buffer[bufferOffset + y * stride + x])
}