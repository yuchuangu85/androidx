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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Radius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.util.annotation.FloatRange

/**
 * Defines a simple shape, used for bounding graphical regions.
 *
 * Can be used for defining a shape of the component background, a shape of
 * shadows cast by the component, or to clip the contents.
 */
sealed class Outline {
    /**
     * Rectangular area.
     */
    @Immutable
    data class Rectangle(val rect: Rect) : Outline()
    /**
     * Rectangular area with rounded corners.
     */
    @Immutable
    data class Rounded(val roundRect: RoundRect) : Outline() {

        /**
         * Optional Path to be created for the RoundRect if the corner radii are not identical
         * This is because Canvas has a built in API for drawing round rectangles with the
         * same corner radii in all 4 corners. However, if each corner has a different
         * corner radii, a path must be drawn instead
         */
        internal val roundRectPath: Path?

        init {
            roundRectPath = if (!roundRect.hasSameCornerRadius()) {
                Path().apply { addRoundRect(roundRect) }
            } else {
                null
            }
        }
    }
    /**
     * An area defined as a path.
     *
     * Note that only convex paths can be used for drawing the shadow. See [Path.isConvex].
     */
    data class Generic(val path: Path) : Outline()
}

/**
 * Adds the [outline] to the [Path].
 */
fun Path.addOutline(outline: Outline) = when (outline) {
    is Outline.Rectangle -> addRect(outline.rect)
    is Outline.Rounded -> addRoundRect(outline.roundRect)
    is Outline.Generic -> addPath(outline.path)
}

/**
 * Draws the [Outline] on a [DrawScope].
 *
 * @param outline the outline to draw.
 * @param color Color applied to the outline when it is drawn
 * @param alpha Opacity to be applied to outline from 0.0f to 1.0f representing
 * fully transparent to fully opaque respectively
 * @param style Specifies whether the outline is stroked or filled in
 * @param colorFilter: ColorFilter to apply to the [color] when drawn into the destination
 * @param blendMode: Blending algorithm to be applied to the outline
 */
fun DrawScope.drawOutline(
    outline: Outline,
    color: Color,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    style: DrawStyle = Fill,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = DrawScope.DefaultBlendMode
) = drawOutlineHelper(
        outline,
        { rect ->
            drawRect(color, rect.topLeft(), rect.size(), alpha, style, colorFilter, blendMode)
        },
        { rrect ->
            val radius = rrect.bottomLeftRadiusX
            drawRoundRect(
                color = color,
                topLeft = rrect.topLeft(),
                size = rrect.size(),
                radius = Radius(radius),
                alpha = alpha,
                style = style,
                colorFilter = colorFilter,
                blendMode = blendMode
            )
        },
        { path -> drawPath(path, color, alpha, style, colorFilter, blendMode) }
    )

/**
 * Draws the [Outline] on a [DrawScope].
 *
 * @param outline the outline to draw.
 * @param brush Brush applied to the outline when it is drawn
 * @param alpha Opacity to be applied to outline from 0.0f to 1.0f representing
 * fully transparent to fully opaque respectively
 * @param style Specifies whether the outline is stroked or filled in
 * @param colorFilter: ColorFilter to apply to the [Brush] when drawn into the destination
 * @param blendMode: Blending algorithm to be applied to the outline
 */
fun DrawScope.drawOutline(
    outline: Outline,
    brush: Brush,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    style: DrawStyle = Fill,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = DrawScope.DefaultBlendMode
) = drawOutlineHelper(
        outline,
        { rect ->
            drawRect(brush, rect.topLeft(), rect.size(), alpha, style, colorFilter, blendMode)
        },
        { rrect ->
            val radius = rrect.bottomLeftRadiusX
            drawRoundRect(
                brush = brush,
                topLeft = rrect.topLeft(),
                size = rrect.size(),
                radius = Radius(radius),
                alpha = alpha,
                style = style,
                colorFilter = colorFilter,
                blendMode = blendMode
            )
        },
        { path -> drawPath(path, brush, alpha, style, colorFilter, blendMode) }
    )

/**
 * Convenience method to obtain an Offset from the Rect's top and left parameters
 */
private fun Rect.topLeft(): Offset = Offset(left, top)

/**
 * Convenience method to obtain a Size from the Rect's width and height
 */
private fun Rect.size(): Size = Size(width, height)

/**
 * Convenience method to obtain an Offset from the RoundRect's top and left parameters
 */
private fun RoundRect.topLeft(): Offset = Offset(left, top)

/**
 * Convenience method to obtain a Size from the RoundRect's width and height parameters
 */
private fun RoundRect.size(): Size = Size(width, height)

/**
 * Helper method that allows for delegation of appropriate drawing call based on type of
 * underlying outline shape
 */
private inline fun DrawScope.drawOutlineHelper(
    outline: Outline,
    drawRectBlock: DrawScope.(rect: Rect) -> Unit,
    drawRoundedRectBlock: DrawScope.(rrect: RoundRect) -> Unit,
    drawPathBlock: DrawScope.(path: Path) -> Unit
) = when (outline) {
        is Outline.Rectangle -> drawRectBlock(outline.rect)
        is Outline.Rounded -> {
            val path = outline.roundRectPath
            // If the rounded rect has a path, then the corner radii are not the same across
            // each of the corners, so we draw the given path.
            // If there is no path available, then the corner radii are identical so call the
            // Canvas primitive for drawing a rounded rectangle
            if (path != null) {
                drawPathBlock(path)
            } else {
                drawRoundedRectBlock(outline.roundRect)
            }
        }
        is Outline.Generic -> drawPathBlock(outline.path)
    }

/**
 * Draws the [Outline] on a [Canvas].
 *
 * @param outline the outline to draw.
 * @param paint the paint used for the drawing.
 */
fun Canvas.drawOutline(outline: Outline, paint: Paint) = when (outline) {
    is Outline.Rectangle -> drawRect(outline.rect, paint)
    is Outline.Rounded -> {
        val path = outline.roundRectPath
        // If the rounded rect has a path, then the corner radii are not the same across
        // each of the corners, so we draw the given path.
        // If there is no path available, then the corner radii are identical so call the
        // Canvas primitive for drawing a rounded rectangle
        if (path != null) {
            drawPath(path, paint)
        } else {
            drawRoundRect(
                left = outline.roundRect.left,
                top = outline.roundRect.top,
                right = outline.roundRect.right,
                bottom = outline.roundRect.bottom,
                radiusX = outline.roundRect.bottomLeftRadiusX,
                radiusY = outline.roundRect.bottomLeftRadiusY,
                paint = paint)
        }
    }
    is Outline.Generic -> drawPath(outline.path, paint)
}

/**
 * Convenience method to determine if the corner radii of the RoundRect are identical
 * in each of the corners. That is the x radius and the y radius are the same for each corner,
 * however, the x and y can be different
 */
private fun RoundRect.hasSameCornerRadius(): Boolean {
    val sameRadiusX = bottomLeftRadiusX == bottomRightRadiusX &&
            bottomRightRadiusX == topRightRadiusX &&
            topRightRadiusX == topLeftRadiusX
    val sameRadiusY = bottomLeftRadiusY == bottomRightRadiusY &&
            bottomRightRadiusY == topRightRadiusY &&
            topRightRadiusY == topLeftRadiusY
    return sameRadiusX && sameRadiusY
}
