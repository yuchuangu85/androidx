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
import android.graphics.Rect
import android.icu.util.Calendar
import android.view.SurfaceHolder
import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.wear.watchface.style.UserStyleRepository

/** The base class for {@link CanvasRenderer} and {@link Gles2Renderer}. */
abstract class Renderer(
    /** The {@link SurfaceHolder} that {@link onDraw} will draw into. */
    _surfaceHolder: SurfaceHolder,

    /** The associated {@link UserStyleRepository}. */
    internal val userStyleRepository: UserStyleRepository,

    /** The associated {@link WatchState}. */
    internal val watchState: WatchState
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
    @UiThread
    open fun onDestroy() {}

    @UiThread
    open fun onSurfaceDestroyed(holder: SurfaceHolder) {}

    /**
     * Renders the watch face into the {@link #surfaceHolder} using the current {@link #drawMode}
     * with the user style specified by the {@link #userStyleRepository}.
     *
     * @param calendar The Calendar to use when rendering the watch face
     * @return A {@link Bitmap} containing a screenshot of the watch face
     */
    @UiThread
    internal abstract fun renderInternal(calendar: Calendar)

    /**
     * Renders the watch face into a Bitmap with the user style specified by the
     * {@link #userStyleRepository}.
     *
     * @param calendar The Calendar to use when rendering the watch face
     * @param drawMode The {@link DrawMode} to use when rendering the watch face
     * @return A {@link Bitmap} containing a screenshot of the watch face
     */
    @UiThread
    internal abstract fun takeScreenshot(
        calendar: Calendar,
        @DrawMode drawMode: Int
    ): Bitmap

    /**
     * Called when the {@link DrawMode} has been updated. Will always be called before the first
     * call to onDraw().
     */
    @UiThread
    protected open fun onDrawModeChanged(@DrawMode drawMode: Int) {}

    /**
     * This method is used for accessibility support to describe the portion of the screen
     * containing  the main clock element. By default we assume this is contained in the central
     * half of the watch face. Watch faces should override this to return the correct bounds for
     * the main clock element.
     *
     * @return A {@link Rect} describing the bounds of the watch faces' main clock element
     */
    @UiThread
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
    @UiThread
    open fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenBounds = holder.surfaceFrame
        centerX = screenBounds.exactCenterX()
        centerY = screenBounds.exactCenterY()
    }

    /**
     * The system periodically (at least once per minute) calls onTimeTick() to trigger a display
     * update. If the watch face needs to animate with an interactive frame rate, calls to
     * invalidate must be scheduled. This method controls whether or not we should do that and if
     * shouldAnimate returns true we inhibit entering {@link DrawMode#AMBIENT}.
     *
     * By default we remain at an interactive frame rate when the watch face is visible and we're
     * not in ambient mode. Watchfaces with animated transitions for entering ambient mode may
     * need to override this to ensure they play smoothly.
     *
     * @return Whether we should schedule an onDraw call to maintain an interactive frame rate
     */
    @UiThread
    open fun shouldAnimate() = watchState.isVisible && !watchState.isAmbient
}
