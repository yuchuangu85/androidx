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

package androidx.ui.core

import androidx.ui.geometry.Size
import androidx.ui.unit.Px
import androidx.ui.unit.PxSize
import kotlin.math.max
import kotlin.math.min

private const val OriginalScale = 1.0f

/**
 * Convenience method to compute the scale factor from [Size] parameters
 */
fun ContentScale.scale(srcSize: Size, dstSize: Size): Float =
    scale(
        PxSize(
            Px(srcSize.width),
            Px(srcSize.height)
        ),
        PxSize(
            Px(dstSize.width),
            Px(dstSize.height)
        )
    )

/**
 * Represents a rule to apply to scale a source rectangle to be inscribed into a destination
 */
interface ContentScale {

    /**
     * Computes the scale factor to apply to both dimensions in order to fit the source
     * appropriately with the given destination size
     */
    fun scale(srcSize: PxSize, dstSize: PxSize): Float

    /**
     * Companion object containing commonly used [ContentScale] implementations
     */
    companion object {

        /**
         * Scale the source uniformly (maintaining the source's aspect ratio) so that both
         * dimensions (width and height) of the source will be equal to or larger than the
         * corresponding dimension of the destination.
         *
         * This [ContentScale] implementation in combination with usage of [Alignment.Center]
         * provides similar behavior to [android.widget.ImageView.ScaleType.CENTER_CROP]
         */
        val Crop = object : ContentScale {
            override fun scale(srcSize: PxSize, dstSize: PxSize): Float =
                computeFillMaxDimension(srcSize, dstSize)
        }

        /**
         * Scale the source uniformly (maintaining the source's aspect ratio) so that both
         * dimensions (width and height) of the source will be equal to or less than the
         * corresponding dimension of the destination
         *
         * This [ContentScale] implementation in combination with usage of [Alignment.Center]
         * provides similar behavior to [android.widget.ImageView.ScaleType.FIT_CENTER]
         */
        val Fit = object : ContentScale {
            override fun scale(srcSize: PxSize, dstSize: PxSize): Float =
                computeFillMinDimension(srcSize, dstSize)
        }

        /**
         * Scale the source maintaining the aspect ratio so that the bounds match the destination
         * height. This can cover a larger area than the destination if the height is larger than
         * the width.
         */
        val FillHeight = object : ContentScale {
            override fun scale(srcSize: PxSize, dstSize: PxSize): Float =
                computeFillHeight(srcSize, dstSize)
        }

        /**
         * Scale the source maintaining the aspect ratio so that the bounds match the
         * destination width. This can cover a larger area than the destination if the width is
         * larger than the height.
         */
        val FillWidth = object : ContentScale {
            override fun scale(srcSize: PxSize, dstSize: PxSize): Float =
                computeFillWidth(srcSize, dstSize)
        }

        /**
         * Scale the source to maintain the aspect ratio to be inside the destination bounds
         * if the source is larger than the destination. If the source is smaller than or equal
         * to the destination in both dimensions, this behaves similarly to [None]. This will
         * always be contained within the bounds of the destination.
         *
         * This [ContentScale] implementation in combination with usage of [Alignment.Center]
         * provides similar behavior to [android.widget.ImageView.ScaleType.CENTER_INSIDE]
         */
        val Inside = object : ContentScale {
            override fun scale(srcSize: PxSize, dstSize: PxSize): Float =
                if (srcSize.width <= dstSize.width && srcSize.height <= dstSize.height) {
                    OriginalScale
                } else {
                    computeFillMinDimension(srcSize, dstSize)
                }
        }

        /**
         * Do not apply any scaling to the source
         */
        val None = FixedScale(OriginalScale)
    }
}

/**
 * [ContentScale] implementation that always scales the dimension by the provided
 * fixed floating point value
 */
data class FixedScale(val value: Float) : ContentScale {
    override fun scale(srcSize: PxSize, dstSize: PxSize): Float = value
}

private fun computeFillMaxDimension(srcSize: PxSize, dstSize: PxSize): Float {
    val widthScale = computeFillWidth(srcSize, dstSize)
    val heightScale = computeFillHeight(srcSize, dstSize)
    return max(widthScale, heightScale)
}

private fun computeFillMinDimension(srcSize: PxSize, dstSize: PxSize): Float {
    val widthScale = computeFillWidth(srcSize, dstSize)
    val heightScale = computeFillHeight(srcSize, dstSize)
    return min(widthScale, heightScale)
}

private fun computeFillWidth(srcSize: PxSize, dstSize: PxSize): Float =
    dstSize.width.value / srcSize.width.value

private fun computeFillHeight(srcSize: PxSize, dstSize: PxSize): Float =
    dstSize.height.value / srcSize.height.value
