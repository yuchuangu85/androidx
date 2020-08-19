/*
 * Copyright 2018 The Android Open Source Project
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

import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/**
 * Graphics object that represents a 2 dimensional array of pixel information represented
 * as ARGB values
 */
interface ImageAsset {

    /** The number of image pixels along the ImageAssets's horizontal axis. */
    val width: Int

    /** The number of image pixels along the ImageAssets's vertical axis. */
    val height: Int

    /** ColorSpace the Image renders in **/
    val colorSpace: ColorSpace

    /** Determines whether or not the ImageAsset contains an alpha channel **/
    val hasAlpha: Boolean

    /**
     * Returns the current configuration of this Image, either:
     * @see ImageAssetConfig.Argb8888
     * @see ImageAssetConfig.Rgb565
     * @see ImageAssetConfig.Alpha8
     * @see ImageAssetConfig.Gpu
     */
    val config: ImageAssetConfig

    /**
     * Copies the pixel data within the ImageAsset into the given array. Each value is
     * represented as ARGB values packed into an Int.
     * The stride parameter allows the caller to allow for gaps in the returned pixels array
     * between rows. For normal packed, results, the stride value is equivalent to the width of
     * the [ImageAsset]. The returned colors are non-premultiplied ARGB values in the
     * [ColorSpaces.Srgb] color space.
     *
     * Note this method can block so it is recommended to not invoke this method in performance
     * critical code paths
     *
     * @sample androidx.compose.ui.graphics.samples.ImageAssetReadPixelsSample
     *
     * @param buffer The array to store the [ImageAsset]'s colors. By default this allocates an
     * [IntArray] large enough to store all the pixel information. Consumers of this API are
     * advised to use the smallest [IntArray] necessary to extract relevant pixel information, that
     * is the 2 dimensional area of the section of the [ImageAsset] to be queried.
     *
     * @param startX The x-coordinate of the first pixel to read from the [ImageAsset]
     * @param startY The y-coordinate of the first pixel to read from the [ImageAsset]
     * @param width The number of pixels to read from each row
     * @param height The number of rows to read
     * @param bufferOffset The first index to write into the buffer array, this defaults to 0
     * @param stride The number of entries in [buffer] to skip between rows (must be >= [width]
     */
    fun readPixels(
        buffer: IntArray,
        startX: Int = 0,
        startY: Int = 0,
        width: Int = this.width,
        height: Int = this.height,
        bufferOffset: Int = 0,
        stride: Int = width
    )

    /**
     * Builds caches associated with the ImageAsset that are used for drawing it. This method can
     * be used as a signal to upload textures to the GPU to eventually be rendered
     */
    fun prepareToDraw()
}

/**
 * Convenience method to extract pixel information from the given ImageAsset into a [PixelMap]
 * that supports for querying pixel information based on
 *
 * Note this method can block so it is recommended to not invoke this method in performance
 * critical code paths
 *
 * @sample androidx.compose.ui.graphics.samples.ImageAssetToPixelMapSample
 *
 * @param startX The x-coordinate of the first pixel to read from the [ImageAsset]
 * @param startY The y-coordinate of the first pixel to read from the [ImageAsset]
 * @param width The number of pixels to read from each row
 * @param height The number of rows to read
 * @param buffer The array to store the [ImageAsset]'s colors. By default this allocates an
 * [IntArray] large enough to store all the pixel information. Consumers of this API are
 * advised to use the smallest [IntArray] necessary to extract relevant pixel information
 * @param bufferOffset The first index to write into the buffer array, this defaults to 0
 * @param stride The number of entries in [buffer] to skip between rows (must be >= [width]
 *
 * @see ImageAsset.readPixels
 */
fun ImageAsset.toPixelMap(
    startX: Int = 0,
    startY: Int = 0,
    width: Int = this.width,
    height: Int = this.height,
    buffer: IntArray = IntArray(width * height),
    bufferOffset: Int = 0,
    stride: Int = width
): PixelMap {
    readPixels(
        buffer,
        startX,
        startY,
        width,
        height,
        bufferOffset,
        stride
    )
    return PixelMap(buffer, width, height, bufferOffset, stride)
}

/**
 * Possible ImageAsset configurations. An ImageAsset configuration describes
 * how pixels are stored. This affects the quality (color depth) as
 * well as the ability to display transparent/translucent colors.
 */
enum class ImageAssetConfig {
    /**
     * Each pixel is stored on 4 bytes. Each channel (RGB and alpha
     * for translucency) is stored with 8 bits of precision (256
     * possible values.)
     *
     * This configuration is very flexible and offers the best
     * quality. It should be used whenever possible.
     *
     *      Use this formula to pack into 32 bits:
     *
     * ```
     * val color =
     *    ((A and 0xff) shl 24) or
     *    ((B and 0xff) shl 16) or
     *    ((G and 0xff) shl 8) or
     *    (R and 0xff)
     * ```
     */
    Argb8888,

    /**
     * Each pixel is stored as a single translucency (alpha) channel.
     * This is very useful to efficiently store masks for instance.
     * No color information is stored.
     * With this configuration, each pixel requires 1 byte of memory.
     */
    Alpha8,

    /**
     * Each pixel is stored on 2 bytes and only the RGB channels are
     * encoded: red is stored with 5 bits of precision (32 possible
     * values), green is stored with 6 bits of precision (64 possible
     * values) and blue is stored with 5 bits of precision.
     *
     * This configuration can produce slight visual artifacts depending
     * on the configuration of the source. For instance, without
     * dithering, the result might show a greenish tint. To get better
     * results dithering should be applied.
     *
     * This configuration may be useful when using opaque bitmaps
     * that do not require high color fidelity.
     *
     *      Use this formula to pack into 16 bits:
     * ```
     *  val color =
     *      ((R and 0x1f) shl 11) or
     *      ((G and 0x3f) shl 5) or
     *      (B and 0x1f)
     * ```
     */
    Rgb565,

    /**
     * Each pixel is stored on 8 bytes. Each channel (RGB and alpha
     * for translucency) is stored as a
     * half-precision floating point value.
     *
     * This configuration is particularly suited for wide-gamut and
     * HDR content.
     *
     *      Use this formula to pack into 64 bits:
     * ```
     *    val color =
     *      ((A and 0xffff) shl 48) or
     *      ((B and 0xffff) shl 32) or
     *      ((G and 0xffff) shl 16) or
     *      (R and 0xffff)
     * ```
     */
    F16,

    /**
     * Special configuration, when an ImageAsset is stored only in graphic memory.
     * ImageAssets in this configuration are always immutable.
     *
     * It is optimal for cases, when the only operation with the ImageAsset is to draw it on a
     * screen.
     */
    Gpu
}

internal expect fun ActualImageAsset(
    width: Int,
    height: Int,
    config: ImageAssetConfig,
    hasAlpha: Boolean,
    colorSpace: ColorSpace
): ImageAsset

fun ImageAsset(
    width: Int,
    height: Int,
    config: ImageAssetConfig = ImageAssetConfig.Argb8888,
    hasAlpha: Boolean = true,
    colorSpace: ColorSpace = ColorSpaces.Srgb
): ImageAsset = ActualImageAsset(
    width,
    height,
    config,
    hasAlpha,
    colorSpace
)