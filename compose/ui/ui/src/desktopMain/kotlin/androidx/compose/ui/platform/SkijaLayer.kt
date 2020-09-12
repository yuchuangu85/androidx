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

import androidx.compose.ui.DrawLayerModifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.DesktopCanvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skija.Matrix33
import org.jetbrains.skija.Picture
import org.jetbrains.skija.PictureRecorder
import org.jetbrains.skija.Rect

class SkijaLayer(
    density: Density,
    modifier: DrawLayerModifier,
    private val invalidateParentLayer: () -> Unit,
    private val drawBlock: SkijaLayer.(Canvas) -> Unit
) : OwnedLayer {
    private var size = IntSize.Zero
    private var position = IntOffset.Zero
    private var outlineCache = OutlineCache(density, size, modifier.shape)
    private val pictureRecorder = PictureRecorder()
    private var picture: Picture? = null

    override var modifier: DrawLayerModifier = modifier
        set(value) {
            field = value
            outlineCache.shape = value.shape
            invalidate()
        }

    override val layerId = 0L

    override fun destroy() {
        picture?.close()
        pictureRecorder.close()
    }

    override fun resize(size: IntSize) {
        if (size != this.size) {
            this.size = size
            outlineCache.size = size
            invalidate()
        }
    }

    override fun move(position: IntOffset) {
        if (position != this.position) {
            this.position = position
            invalidateParentLayer()
        }
    }

    // TODO(demin): calculate matrix
    override fun getMatrix(matrix: Matrix) {
        matrix.reset()
    }

    override fun invalidate() {
        picture = null
        invalidateParentLayer()
    }

    override fun drawLayer(canvas: Canvas) {
        if (picture == null) {
            val pictureCanvas = pictureRecorder.beginRecording(
                Rect.makeWH(
                    size.width.toFloat(),
                    size.height.toFloat()
                )
            )
            performDrawLayer(DesktopCanvas(pictureCanvas))
            picture = pictureRecorder.finishRecordingAsPicture()
        }
        canvas.nativeCanvas.drawPicture(
            picture,
            Matrix33.makeTranslate(position.x.toFloat(), position.y.toFloat()),
            null
        )
    }

    // TODO(demin): implement alpha, rotationX, rotationY, shadowElevation
    private fun performDrawLayer(canvas: DesktopCanvas) {
        canvas.save()

        val pivotX = modifier.transformOrigin.pivotFractionX * size.width
        val pivotY = modifier.transformOrigin.pivotFractionY * size.height

        canvas.translate(modifier.translationX, modifier.translationY)
        canvas.translate(pivotX, pivotY)

        if (modifier.rotationZ != 0f) {
            canvas.rotate(modifier.rotationZ)
        }

        if (modifier.scaleX != 1f || modifier.scaleY != 1f) {
            canvas.scale(modifier.scaleX, modifier.scaleY)
        }

        canvas.translate(-pivotX, -pivotY)

        if (modifier.clip && size != IntSize.Zero) {
            when (val outline = outlineCache.outline) {
                is Outline.Rectangle -> canvas.clipRect(outline.rect)
                is Outline.Rounded -> canvas.clipRoundRect(outline.roundRect)
                is Outline.Generic -> canvas.clipPath(outline.path)
            }
        }

        if (modifier.alpha != 0f) {
            drawBlock(canvas)
        }

        canvas.restore()
    }

    override fun updateDisplayList() = Unit

    override fun updateLayerProperties() = Unit
}
