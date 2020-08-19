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
import android.graphics.Color
import android.graphics.Rect
import android.icu.util.Calendar
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.IntDef
import androidx.wear.watchfacestyle.UserStyleManager

/** @hide */
@IntDef(
    value = [
        CanvasType.SOFTWARE,
        CanvasType.HARDWARE
    ]
)
annotation class CanvasType {
    companion object {
        /** A software canvas will be requested. */
        const val SOFTWARE = 0

        /**
         * A hardware canvas will be requested. This is usually faster than software rendering,
         * however it can sometimes increase battery usage by rendering at a higher frame rate.
         */
        const val HARDWARE = 1
    }
}

/**
 * The base class for {@link Canvas} WatchFace rendering. This class's methods should be called on
 * the main thread only.
 */
abstract class CanvasRenderer(
    /** The {@link SurfaceHolder} that {@link onDraw} will draw into. */
    surfaceHolder: SurfaceHolder,

    /** The associated {@link UserStyleManager}. */
    userStyleManager: UserStyleManager,

    /** The associated {@link SystemState}. */
    private val systemState: SystemState,

    /** The type of canvas to use. */
    @CanvasType private val canvasType: Int
) : Renderer(surfaceHolder, userStyleManager) {
    private companion object {
        private const val TAG = "CanvasRenderer"
    }

    override fun onDrawInternal(
        calendar: Calendar
    ) {
        val canvas = if (canvasType == CanvasType.HARDWARE) {
            surfaceHolder.lockHardwareCanvas()
        } else {
            surfaceHolder.lockCanvas()
        }
        if (canvas == null) {
            Log.e(TAG, "Null canvas returned when locking the SurfaceHolder.")
            return
        }
        try {
            if (systemState.isVisible) {
                onDraw(canvas, surfaceHolder.surfaceFrame, calendar)
            } else {
                canvas.drawColor(Color.BLACK)
            }
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    override fun takeScreenshot(
        calendar: Calendar,
        @DrawMode drawMode: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            screenBounds.width(),
            screenBounds.height(),
            Bitmap.Config.ARGB_8888
        )
        val prevDrawMode = drawMode
        this.drawMode = drawMode
        onDraw(Canvas(bitmap), screenBounds, calendar)
        this.drawMode = prevDrawMode
        return bitmap
    }

    /**
     * Called on the main thread. Sub-classes should override this to implement their rendering
     * logic which should respect the current {@link DrawMode}. For correct functioning watch
     * faces must use the supplied {@link Calendar} and avoid using any other ways of getting the
     * time.
     *
     * @param canvas The {@link Canvas} to render into. Don't assume this is always the canvas from
     *     the {@link SurfaceHolder} backing the display
     * @param bounds A {@link Rect} describing the bonds of the canvas to draw into
     * @param calendar The current {@link Calendar}
     */
    protected abstract fun onDraw(
        canvas: Canvas,
        bounds: Rect,
        calendar: Calendar
    )
}
