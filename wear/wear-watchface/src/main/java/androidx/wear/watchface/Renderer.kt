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

package androidx.wear.watchface

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.icu.util.Calendar
import android.view.SurfaceHolder
import androidx.annotation.CallSuper
import androidx.wear.watchfacestyle.UserStyleManager

/** The base class for {@link CanvasRenderer} and {@link Gles2Renderer}. */
abstract class Renderer(
    /** The {@link SurfaceHolder} that {@link onDraw} will draw into. */
    _surfaceHolder: SurfaceHolder,

    /** The associated {@link UserStyleManager}. */
    protected val userStyleManager: UserStyleManager
) {
    protected var surfaceHolder = _surfaceHolder
        private set

    var screenBounds: Rect = surfaceHolder.surfaceFrame
        private set

    var centerX: Float = screenBounds.exactCenterX()
        private set

    var centerY: Float = screenBounds.exactCenterY()
        private set

    @DrawMode
    private var _drawMode: Int? = null

    /** The current DrawMode. Updated before every onDraw call. */
    @DrawMode
    var drawMode: Int
        get() = _drawMode ?: DrawMode.INTERACTIVE
        internal set(value) {
            if (value != _drawMode) {
                _drawMode = value
                onDrawModeChanged(value)
            }
        }

    /** Called when the Renderer is destroyed. */
    open fun onDestroy() {}

    open fun onSurfaceDestroyed(holder: SurfaceHolder) {}

    /**
     * Renders the watch face into the {@link #surfaceHolder} using the current {@link #drawMode}
     * with the user style specified by the {@link #userStyleManager}.
     *
     * @param calendar The Calendar to use when rendering the watch face
     * @return A {@link Bitmap} containing a screenshot of the watch face
     */
    internal abstract fun onDrawInternal(
        calendar: Calendar
    )

    /**
     * Renders the watch face into a Bitmap with the user style specified by the {@link #userStyleManager}.
     *
     * @param calendar The Calendar to use when rendering the watch face
     * @param drawMode The {@link DrawMode} to use when rendering the watch face
     * @return A {@link Bitmap} containing a screenshot of the watch face
     */
    abstract fun takeScreenshot(
        calendar: Calendar,
        @DrawMode drawMode: Int
    ): Bitmap

    /**
     * Called when the {@link DrawMode} has been updated. Will always be called before the first
     * call to onDraw().
     */
    protected open fun onDrawModeChanged(@DrawMode drawMode: Int) {}

    /**
     * This method is used for accessibility support to describe the portion of the screen
     * containing  the main clock element. By default we assume this is contained in the central
     * half of the watch face. Watch faces should override this to return the correct bounds for
     * the main clock element.
     *
     * @return A {@link Rect} describing the bounds of the watch faces' main clock element
     */
    open fun getMainClockElementBounds(): Rect {
        val quarterX = centerX / 2
        val quarterY = centerY / 2
        return Rect(
            (centerX - quarterX).toInt(), (centerY - quarterY).toInt(),
            (centerX + quarterX).toInt(), (centerY + quarterY).toInt()
        )
    }

    /**
     * Convenience for {@link SurfaceHolder.Callback#surfaceChanged}. Called when the
     * {@link SurfaceHolder} containing the display surface changes.
     *
     * @param holder The new {@link SurfaceHolder} containing the display surface
     * @param format The new {@link android.graphics.PixelFormat} of the surface
     * @param width The width of the new display surface
     * @param height The height of the new display surface
     */
    @CallSuper
    open fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenBounds = holder.surfaceFrame
        centerX = screenBounds.exactCenterX()
        centerY = screenBounds.exactCenterY()
    }

    /**
     * Used by {@link ConfigFragment} to draw the watch face in COMPLICATION_SELECT mode.
     *
     * @param canvas The {@param Canvas} to render into
     * @param bounds A {@param Rect} describing the bounds of the canvas
     * @param calendar The {@param Calendar} to use for rendering
     */
    fun drawComplicationSelect(canvas: Canvas, bounds: Rect, calendar: Calendar) {
        val oldDrawMode = drawMode
        _drawMode =
            DrawMode.COMPLICATION_SELECT
        if (oldDrawMode != drawMode) {
            onDrawModeChanged(DrawMode.COMPLICATION_SELECT)
        }

        val bitmap = takeScreenshot(
            calendar,
            DrawMode.COMPLICATION_SELECT
        )
        canvas.drawBitmap(bitmap, screenBounds, bounds, null)
    }
}
