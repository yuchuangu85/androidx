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
import android.icu.util.Calendar
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.CallSuper
import androidx.wear.watchfacestyle.UserStyleManager

import java.nio.ByteBuffer

private val EGL_CONFIG_ATTRIB_LIST = intArrayOf(
    EGL14.EGL_RENDERABLE_TYPE,
    EGL14.EGL_OPENGL_ES2_BIT,
    EGL14.EGL_RED_SIZE,
    8,
    EGL14.EGL_GREEN_SIZE,
    8,
    EGL14.EGL_BLUE_SIZE,
    8,
    EGL14.EGL_ALPHA_SIZE,
    8,
    EGL14.EGL_NONE
)

private val EGL_CONTEXT_ATTRIB_LIST =
    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)

private val EGL_SURFACE_ATTRIB_LIST = intArrayOf(EGL14.EGL_NONE)

/**
 * The base class {@link GLES20} WatchFace rendering. Generally this class's methods should be
 * called on the main thread only.
 */
abstract class Gles2Renderer (
    /** The {@link SurfaceHolder} that {@link onDraw} will draw into. */
    surfaceHolder: SurfaceHolder,

    /** The associated {@link UserStyleManager}. */
    userStyleManager: UserStyleManager
) : Renderer(surfaceHolder, userStyleManager) {
    private companion object {
        private const val TAG = "Gles2WatchFace"
    }

    private var eglDisplay: EGLDisplay? = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).apply {
        if (this == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay returned EGL_NO_DISPLAY")
        }
        // Initialize the display. The major and minor version numbers are passed back.
        val version = IntArray(2)
        if (!EGL14.eglInitialize(this, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }
    }

    private var eglConfig: EGLConfig = chooseEglConfig(eglDisplay!!)

    @SuppressWarnings("SyntheticAccessor")
    private var eglContext: EGLContext? = EGL14.eglCreateContext(
        eglDisplay,
        eglConfig,
        EGL14.EGL_NO_CONTEXT,
        EGL_CONTEXT_ATTRIB_LIST,
        0
    )

    init {
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext failed")
        }
    }

    private var eglSurface: EGLSurface? =
        createWindowSurface(eglDisplay!!, eglConfig, surfaceHolder)

    private var calledOnGlContextCreated = false

    init {
        makeContextCurrent()
    }

    /**
     * Returns the attributes to be passed to {@link EGL14.eglChooseConfig}. By default this selects
     * an RGBAB8888 back buffer.
     */
    @SuppressWarnings("SyntheticAccessor")
    protected open fun getEglConfigAttribList() = EGL_CONFIG_ATTRIB_LIST

    /**
     * Chooses the EGLConfig to use, by default this calls {@link getEglConfigAttribList} to get
     * the attributes list to pass to {@link EGL14.eglChooseConfig}.
     * @throws RuntimeException if {@link EGL14.eglChooseConfig} fails
     */
    protected open fun chooseEglConfig(eglDisplay: EGLDisplay): EGLConfig {
        val numEglConfigs = IntArray(1)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        if (!EGL14.eglChooseConfig(
                eglDisplay,
                getEglConfigAttribList(),
                0,
                eglConfigs,
                0,
                eglConfigs.size,
                numEglConfigs,
                0
            )
        ) {
            throw RuntimeException("eglChooseConfig failed")
        }
        if (numEglConfigs[0] == 0) {
            throw RuntimeException("no matching EGL configs")
        }
        return eglConfigs[0]!!
    }

    /**
     * Returns the attributes to be passed to {@link EGL14.eglCreateWindowSurface}. By default this
     * is empty.
     */
    @SuppressWarnings("SyntheticAccessor")
    protected open fun getEglSurfaceAttribList() = EGL_SURFACE_ATTRIB_LIST

    private fun createWindowSurface(
        eglDisplay: EGLDisplay,
        eglConfig: EGLConfig,
        surfaceHolder: SurfaceHolder
    ): EGLSurface {
        val result = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            surfaceHolder.surface,
            getEglSurfaceAttribList(),
            0
        )
        if (result == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed")
        }
        return result
    }

    @CallSuper
    override fun onDestroy() {
        if (eglSurface != null) {
            if (!EGL14.eglDestroySurface(eglDisplay, eglSurface)) {
                Log.w(TAG, "eglDestroySurface failed")
            }
            eglSurface = null
        }
        if (eglContext != null) {
            if (!EGL14.eglDestroyContext(eglDisplay, eglContext)) {
                Log.w(TAG, "eglDestroyContext failed")
            }
            eglContext = null
        }
        if (eglDisplay != null) {
            if (!EGL14.eglTerminate(eglDisplay)) {
                Log.w(TAG, "eglTerminate failed")
            }
            eglDisplay = null
        }
    }

    /**
     * Sets our GL context to be the current one. This method *must* be called before any
     * OpenGL APIs are used.
     */
    private fun makeContextCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    @CallSuper
    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)

        if (eglSurface != null) {
            if (!EGL14.eglDestroySurface(eglDisplay, eglSurface)) {
                Log.w(TAG, "eglDestroySurface failed")
            }
        }
        eglSurface = createWindowSurface(eglDisplay!!, eglConfig, holder)
        makeContextCurrent()
        GLES20.glViewport(0, 0, width, height)
        if (!calledOnGlContextCreated) {
            calledOnGlContextCreated = true
            onGlContextCreated()
        }
        onGlSurfaceCreated(width, height)
    }

    @CallSuper
    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
        try {
            if (!EGL14.eglDestroySurface(eglDisplay, eglSurface)) {
                Log.w(TAG, "eglDestroySurface failed")
            }
            eglSurface = null
        } finally {
            super.onSurfaceDestroyed(holder)
        }
    }

    /** Called when a new GL context is created. It's safe to use GL APIs in this method.  */
    open fun onGlContextCreated() {}

    /**
     * Called when a new GL surface is created. It's safe to use GL APIs in this method.
     *
     * @param width width of surface in pixels
     * @param height height of surface in pixels
     */
    open fun onGlSurfaceCreated(width: Int, height: Int) {}

    override fun onDrawInternal(
        calendar: Calendar
    ) {
        makeContextCurrent()
        onDraw(calendar)
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            Log.w(TAG, "eglSwapBuffers failed")
        }
    }

    override fun takeScreenshot(
        calendar: Calendar,
        @DrawMode drawMode: Int
    ): Bitmap {
        val width = screenBounds.width()
        val height = screenBounds.height()
        val pixelBuf = ByteBuffer.allocateDirect(width * height * 4)
        makeContextCurrent()
        this.drawMode = drawMode
        onDraw(calendar)
        GLES20.glFinish()
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(pixelBuf)
        return bitmap
    }

    /**
     * Called on the main thread. Sub-classes should override this to implement their rendering
     * logic which should respect the current {@link DrawMode}. For correct functioning watch
     * faces must use the supplied {@link Calendar} and avoid using any other ways of getting the
     * time.
     *
     * @param calendar The current {@link Calendar}
     */
    protected abstract fun onDraw(
        calendar: Calendar
    )
}
