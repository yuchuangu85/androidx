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

import androidx.compose.ui.Alignment
import androidx.compose.ui.ContentDrawScope
import androidx.compose.ui.DrawModifier
import androidx.compose.ui.LayoutModifier
import androidx.compose.ui.Measurable
import androidx.compose.ui.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.platform.InspectableParameter
import androidx.compose.ui.platform.ParameterElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.hasFixedHeight
import androidx.compose.ui.unit.hasFixedWidth
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Paint the content using [painter].
 *
 * @param sizeToIntrinsics `true` to size the element relative to [Painter.intrinsicSize]
 * @param alignment specifies alignment of the [painter] relative to content
 * @param contentScale strategy for scaling [painter] if its size does not match the content size
 * @param alpha opacity of [painter]
 * @param colorFilter optional [ColorFilter] to apply to [painter]
 *
 * @sample androidx.compose.ui.samples.PainterModifierSample
 */
fun Modifier.paint(
    painter: Painter,
    sizeToIntrinsics: Boolean = true,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Inside,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null
) = this.then(
    PainterModifier(
        painter = painter,
        sizeToIntrinsics = sizeToIntrinsics,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter
    )
)

/**
 * [DrawModifier] used to draw the provided [Painter] followed by the contents
 * of the component itself
 */
private data class PainterModifier(
    val painter: Painter,
    val sizeToIntrinsics: Boolean,
    val alignment: Alignment = Alignment.Center,
    val contentScale: ContentScale = ContentScale.Inside,
    val alpha: Float = DefaultAlpha,
    val colorFilter: ColorFilter? = null
) : LayoutModifier, DrawModifier, InspectableParameter {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureScope.MeasureResult {
        val placeable = measurable.measure(modifyConstraints(constraints))
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return if (sizeToIntrinsics) {
            val constraints = Constraints(maxHeight = height)
            val layoutWidth =
                measurable.minIntrinsicWidth(modifyConstraints(constraints).maxHeight)
            val scaledSize = calculateScaledSize(Size(layoutWidth.toFloat(), height.toFloat()))
            max(scaledSize.width.roundToInt(), layoutWidth)
        } else {
            measurable.minIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return if (sizeToIntrinsics) {
            val constraints = Constraints(maxHeight = height)
            val layoutWidth =
                measurable.maxIntrinsicWidth(modifyConstraints(constraints).maxHeight)
            val scaledSize = calculateScaledSize(Size(layoutWidth.toFloat(), height.toFloat()))
            max(scaledSize.width.roundToInt(), layoutWidth)
        } else {
            measurable.maxIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return if (sizeToIntrinsics) {
            val constraints = Constraints(maxWidth = width)
            val layoutHeight =
                measurable.minIntrinsicHeight(modifyConstraints(constraints).maxWidth)
            val scaledSize = calculateScaledSize(Size(width.toFloat(), layoutHeight.toFloat()))
            max(scaledSize.height.roundToInt(), layoutHeight)
        } else {
            measurable.minIntrinsicHeight(width)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return if (sizeToIntrinsics) {
            val constraints = Constraints(maxWidth = width)
            val layoutHeight =
                measurable.maxIntrinsicHeight(modifyConstraints(constraints).maxWidth)
            val scaledSize = calculateScaledSize(Size(width.toFloat(), layoutHeight.toFloat()))
            max(scaledSize.height.roundToInt(), layoutHeight)
        } else {
            measurable.maxIntrinsicHeight(width)
        }
    }

    private fun calculateScaledSize(dstSize: Size): Size {
        return if (!sizeToIntrinsics) {
            dstSize
        } else {
            val intrinsicWidth = painter.intrinsicSize.width
            val intrinsicHeight = painter.intrinsicSize.height
            val srcWidth = if (intrinsicWidth == Float.POSITIVE_INFINITY) {
                dstSize.width
            } else {
                intrinsicWidth
            }

            val srcHeight = if (intrinsicHeight == Float.POSITIVE_INFINITY) {
                dstSize.height
            } else {
                intrinsicHeight
            }

            val srcSize = Size(srcWidth, srcHeight)
            srcSize * contentScale.scale(srcSize, dstSize)
        }
    }

    private fun modifyConstraints(constraints: Constraints): Constraints {
        if (!sizeToIntrinsics || (constraints.hasFixedWidth && constraints.hasFixedHeight)) {
            // If we have fixed constraints or we are not attempting to size the
            // composable based on the size of the Painter, do not attempt to
            // modify them. Otherwise rely on Alignment and ContentScale
            // to determine how to position the drawing contents of the Painter within
            // the provided bounds
            return constraints
        }

        val intrinsicSize = painter.intrinsicSize
        val intrinsicWidth =
            if (intrinsicSize.width != Float.POSITIVE_INFINITY) {
                intrinsicSize.width.roundToInt()
            } else {
                constraints.minWidth
            }

        val intrinsicHeight =
            if (intrinsicSize.height != Float.POSITIVE_INFINITY) {
                intrinsicSize.height.roundToInt()
            } else {
                constraints.minHeight
            }

        // Scale the width and height appropriately based on the given constraints
        // and ContentScale
        val constrainedWidth = constraints.constrainWidth(intrinsicWidth)
        val constrainedHeight = constraints.constrainHeight(intrinsicHeight)
        val scaledSize = calculateScaledSize(
            Size(constrainedWidth.toFloat(), constrainedHeight.toFloat())
        )

        // For both width and height constraints, consume the minimum of the scaled width
        // and the maximum constraint as some scale types can scale larger than the maximum
        // available size (ex ContentScale.Crop)
        // In this case the larger of the 2 dimensions is used and the aspect ratio is
        // maintained. Even if the size of the composable is smaller, the painter will
        // draw its content clipped
        val minWidth = constraints.constrainWidth(scaledSize.width.roundToInt())
        val minHeight = constraints.constrainHeight(scaledSize.height.roundToInt())
        return constraints.copy(minWidth = minWidth, minHeight = minHeight)
    }

    override fun ContentDrawScope.draw() {
        val intrinsicSize = painter.intrinsicSize
        val srcWidth = if (intrinsicSize.width != Float.POSITIVE_INFINITY) {
            intrinsicSize.width
        } else {
            size.width
        }

        val srcHeight = if (intrinsicSize.height != Float.POSITIVE_INFINITY) {
            intrinsicSize.height
        } else {
            size.height
        }

        val srcSize = Size(srcWidth, srcHeight)
        val scale = contentScale.scale(srcSize, size)

        // Compute the offset to translate the content based on the given alignment
        // and size to draw based on the ContentScale parameter
        val scaledSize = srcSize * scale
        val alignedPosition = alignment.align(
            IntSize(
                ceil(size.width - (scaledSize.width)).toInt(),
                ceil(size.height - (scaledSize.height)).toInt()
            )
        )

        val dx = alignedPosition.x.toFloat()
        val dy = alignedPosition.y.toFloat()

        // Only translate the current drawing position while delegating the Painter to draw
        // with scaled size.
        // Individual Painter implementations should be responsible for scaling their drawing
        // content accordingly to fit within the drawing area.
        translate(dx, dy) {
            with(painter) {
                draw(size = scaledSize, alpha = alpha, colorFilter = colorFilter)
            }
        }
    }

    override val nameFallback = "paint"

    override val inspectableElements: Sequence<ParameterElement>
        get() = sequenceOf(
            ParameterElement("painter", painter),
            ParameterElement("sizeToIntrinsics", sizeToIntrinsics),
            ParameterElement("alignment", alignment),
            ParameterElement("contentScale", contentScale),
            ParameterElement("alpha", alpha),
            ParameterElement("colorFilter", colorFilter)
        )
}