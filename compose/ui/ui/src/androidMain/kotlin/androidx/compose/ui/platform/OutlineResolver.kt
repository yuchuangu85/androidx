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

package androidx.compose.ui.platform

import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import android.graphics.Outline as AndroidOutline

/**
 * Resolves the [AndroidOutline] from the [Shape] of an [OwnedLayer].
 */
internal class OutlineResolver(private val density: Density) {
    /**
     * The Android Outline that is used in the layer.
     */
    private val cachedOutline = AndroidOutline().apply { alpha = 1f }

    /**
     * The size of the layer. This is used in generating the [Outline] from the [Shape].
     */
    private var size: Size = Size.Zero

    /**
     * The [Shape] of the Outline of the Layer.
     */
    private var shape: Shape = RectangleShape

    /**
     * Asymmetric rounded rectangles need to use a Path. This caches that Path so that
     * a new one doesn't have to be generated each time.
     */
    // TODO(andreykulikov): Make Outline API reuse the Path when generating.
    private var cachedRrectPath: Path? = null // for temporary allocation in rounded rects

    /**
     * The outline Path when a non-conforming (rect or symmetric rounded rect) Outline
     * is used. This Path is necessary when [usePathForClip] is true to indicate the
     * Path to clip in [clipPath].
     */
    private var outlinePath: Path? = null

    /**
     * True when there's been an update that caused a change in the path and the Outline
     * has to be reevaluated.
     */
    private var cacheIsDirty = false

    /**
     * True when Outline cannot clip the content and the path should be used instead.
     * This is when an asymmetric rounded rect or general Path is used in the outline.
     * This is false when a Rect or a symmetric RoundRect is used in the outline.
     */
    private var usePathForClip = false

    /**
     * Returns the Android Outline to be used in the layer.
     */
    val outline: AndroidOutline?
        get() {
            updateCache()
            return if (!outlineNeeded || cachedOutline.isEmpty) null else cachedOutline
        }

    /**
     * When a the layer doesn't support clipping of the outline, this returns the Path
     * that should be used to manually clip. When the layer does support manual clipping
     * or there is no outline, this returns null.
     */
    val clipPath: Path?
        get() {
            updateCache()
            return if (usePathForClip) outlinePath else null
        }

    /**
     * True when we are going to clip or have a non-zero elevation for shadows.
     */
    private var outlineNeeded = false

    /**
     * Updates the values of the outline. Returns `true` when the shape has changed.
     */
    fun update(shape: Shape, alpha: Float, clipToOutline: Boolean, elevation: Float): Boolean {
        cachedOutline.alpha = alpha
        val shapeChanged = this.shape != shape
        if (shapeChanged) {
            this.shape = shape
            cacheIsDirty = true
        }
        val outlineNeeded = clipToOutline || elevation > 0f
        if (this.outlineNeeded != outlineNeeded) {
            this.outlineNeeded = outlineNeeded
            cacheIsDirty = true
        }
        return shapeChanged
    }

    /**
     * Updates the size.
     */
    fun update(size: Size) {
        if (this.size != size) {
            this.size = size
            cacheIsDirty = true
        }
    }

    private fun updateCache() {
        if (cacheIsDirty) {
            cacheIsDirty = false
            usePathForClip = false
            if (outlineNeeded && size.width > 0.0f && size.height > 0.0f) {
                when (val outline = shape.createOutline(size, density)) {
                    is Outline.Rectangle -> updateCacheWithRect(outline.rect)
                    is Outline.Rounded -> updateCacheWithRoundRect(outline.roundRect)
                    is Outline.Generic -> updateCacheWithPath(outline.path)
                }
            } else {
                cachedOutline.setEmpty()
            }
        }
    }

    private fun updateCacheWithRect(rect: Rect) {
        cachedOutline.setRect(
            rect.left.roundToInt(),
            rect.top.roundToInt(),
            rect.right.roundToInt(),
            rect.bottom.roundToInt()
        )
    }

    private fun updateCacheWithRoundRect(roundRect: RoundRect) {
        val radius = roundRect.topLeftRadiusX
        if (roundRect.isSimple) {
            cachedOutline.setRoundRect(
                roundRect.left.roundToInt(),
                roundRect.top.roundToInt(),
                roundRect.right.roundToInt(),
                roundRect.bottom.roundToInt(),
                radius
            )
        } else {
            val path = cachedRrectPath ?: Path().also { cachedRrectPath = it }
            path.reset()
            path.addRoundRect(roundRect)
            updateCacheWithPath(path)
        }
    }

    @Suppress("deprecation")
    private fun updateCacheWithPath(composePath: Path) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || composePath.isConvex) {
            // TODO(mount): Use setPath() for R+ when available.
            cachedOutline.setConvexPath(composePath.asAndroidPath())
            usePathForClip = !cachedOutline.canClip()
        } else {
            cachedOutline.setEmpty()
            usePathForClip = true
        }
        outlinePath = composePath
    }
}
