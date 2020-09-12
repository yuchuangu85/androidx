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

package androidx.camera.view

import android.graphics.Rect
import android.util.LayoutDirection
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.ImageOutputConfig.RotationValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrument tests for [PreviewTransformation].
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PreviewTransformationDeviceTest {

    companion object {
        // Size of the PreviewView. Aspect ratio 2:1.
        private val PREVIEW_VIEW_SIZE = Size(400, 200)

        // Size of the Surface. Aspect ratio 3:2.
        private val SURFACE_SIZE = Size(60, 40)

        // 2:1 crop rect.
        private val CROP_RECT = Rect(20, 0, 40, 40)

        // 1:1 crop rect.
        private val FIT_SURFACE_SIZE = Size(60, 60)
        private val MISMATCHED_CROP_RECT = Rect(0, 0, 60, 60)
        private const val FLOAT_ERROR = 1e-3f
    }

    private lateinit var mPreviewTransform: PreviewTransformation
    private lateinit var mView: View

    @Before
    fun setUp() {
        mPreviewTransform = PreviewTransformation()
        mView = View(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun cropRectWidthOffByOnePixel_match() {
        assertThat(
            isCropRectAspectRatioMatchPreviewView(
                Rect(
                    0,
                    0,
                    PREVIEW_VIEW_SIZE.height,
                    PREVIEW_VIEW_SIZE.width - 1
                )
            )
        ).isTrue()
    }

    @Test
    fun cropRectWidthOffByTwoPixels_mismatch() {
        assertThat(
            isCropRectAspectRatioMatchPreviewView(
                Rect(
                    0,
                    0,
                    PREVIEW_VIEW_SIZE.height,
                    PREVIEW_VIEW_SIZE.width - 2
                )
            )
        ).isFalse()
    }

    private fun isCropRectAspectRatioMatchPreviewView(cropRect: Rect): Boolean {
        mPreviewTransform.setTransformationInfo(
            // Height and width is swapped because rotation is 90°.
            SurfaceRequest.TransformationInfo.of(cropRect, 90, Surface.ROTATION_0),
            SURFACE_SIZE
        )
        return mPreviewTransform.isCropRectAspectRatioMatchPreviewView(PREVIEW_VIEW_SIZE)
    }

    @Test
    fun correctTextureViewWith0Rotation() {
        assertThat(getTextureViewCorrection(Surface.ROTATION_0)).isEqualTo(
            floatArrayOf(
                0f,
                0f,
                SURFACE_SIZE.width.toFloat(),
                0f,
                SURFACE_SIZE.width.toFloat(),
                SURFACE_SIZE.height.toFloat(),
                0f,
                SURFACE_SIZE.height.toFloat()
            )
        )
    }

    @Test
    fun correctTextureViewWith90Rotation() {
        assertThat(getTextureViewCorrection(Surface.ROTATION_90)).isEqualTo(
            floatArrayOf(
                0f,
                SURFACE_SIZE.height.toFloat(),
                0f,
                0f,
                SURFACE_SIZE.width.toFloat(),
                0f,
                SURFACE_SIZE.width.toFloat(),
                SURFACE_SIZE.height.toFloat()
            )
        )
    }

    @Test
    fun correctTextureViewWith180Rotation() {
        assertThat(getTextureViewCorrection(Surface.ROTATION_180)).isEqualTo(
            floatArrayOf(
                SURFACE_SIZE.width.toFloat(),
                SURFACE_SIZE.height.toFloat(),
                0f,
                SURFACE_SIZE.height.toFloat(),
                0f,
                0f,
                SURFACE_SIZE.width.toFloat(),
                0f
            )
        )
    }

    @Test
    fun correctTextureViewWith270Rotation() {
        assertThat(getTextureViewCorrection(Surface.ROTATION_270)).isEqualTo(
            floatArrayOf(
                SURFACE_SIZE.width.toFloat(),
                0f,
                SURFACE_SIZE.width.toFloat(),
                SURFACE_SIZE.height.toFloat(),
                0f,
                SURFACE_SIZE.height.toFloat(),
                0f,
                0f
            )
        )
    }

    /**
     * Corrects TextureView based on target rotation and return the corrected vertexes.
     */
    private fun getTextureViewCorrection(@RotationValue rotation: Int): FloatArray {
        // Arrange.
        mPreviewTransform.setTransformationInfo(
            SurfaceRequest.TransformationInfo.of(CROP_RECT, 90, rotation),
            SURFACE_SIZE
        )

        // Act.
        val surfaceVertexes = PreviewTransformation.sizeToVertexes(SURFACE_SIZE)
        mPreviewTransform.textureViewCorrectionMatrix.mapPoints(surfaceVertexes)
        return surfaceVertexes
    }

    @Test
    fun ratioMatch_surfaceIsScaledToFillPreviewView() {
        // Arrange.
        mPreviewTransform.setTransformationInfo(
            SurfaceRequest.TransformationInfo.of(
                CROP_RECT,
                90, Surface.ROTATION_90
            ), SURFACE_SIZE
        )

        // Act.
        mPreviewTransform.transformView(PREVIEW_VIEW_SIZE, LayoutDirection.LTR, mView)

        // Assert.
        val correctCropRectWidth =
            CROP_RECT.height().toFloat() / SURFACE_SIZE.height * SURFACE_SIZE.width
        assertThat(mView.scaleX).isWithin(FLOAT_ERROR)
            .of(PREVIEW_VIEW_SIZE.width / correctCropRectWidth)
        val correctCropRectHeight: Float =
            CROP_RECT.width().toFloat() / SURFACE_SIZE.width * SURFACE_SIZE.height
        assertThat(mView.scaleY).isWithin(FLOAT_ERROR).of(
            PREVIEW_VIEW_SIZE.height / correctCropRectHeight
        )
        assertThat(mView.translationX).isWithin(FLOAT_ERROR).of(0f)
        assertThat(mView.translationY).isWithin(FLOAT_ERROR).of(-200f)
    }

    @Test
    fun mismatchedCropRect_fitStart() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FIT_START,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            0f,
            0f
        )
    }

    @Test
    fun mismatchedCropRect_fitCenter() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FIT_CENTER,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            100f,
            0f
        )
    }

    @Test
    fun mismatchedCropRect_fitEnd() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FIT_END,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            200f,
            0f
        )
    }

    @Test
    fun mismatchedCropRect_fillStart() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FILL_START,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.width.toFloat() / MISMATCHED_CROP_RECT.width(),
            0f,
            0f
        )
    }

    @Test
    fun mismatchedCropRect_fillCenter() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FILL_CENTER,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.width.toFloat() / MISMATCHED_CROP_RECT.width(),
            0f,
            -100f
        )
    }

    @Test
    fun mismatchedCropRect_fillEnd() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FILL_END,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.width.toFloat() / MISMATCHED_CROP_RECT.width(),
            0f,
            -200f
        )
    }

    @Test
    fun mismatchedCropRect_fitStartWithRtl_actsLikeFitEnd() {
        assertForMismatchedCropRect(
            PreviewView.ScaleType.FIT_START,
            LayoutDirection.RTL,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            200f,
            0f
        )
    }

    private fun assertForMismatchedCropRect(
        scaleType: PreviewView.ScaleType,
        layoutDirection: Int,
        scale: Float,
        translationX: Float,
        translationY: Float
    ) {
        // Arrange.
        mPreviewTransform.setTransformationInfo(
            SurfaceRequest.TransformationInfo.of(MISMATCHED_CROP_RECT, 90, Surface.ROTATION_90),
            FIT_SURFACE_SIZE
        )
        mPreviewTransform.scaleType = scaleType

        // Act.
        mPreviewTransform.transformView(PREVIEW_VIEW_SIZE, layoutDirection, mView)

        // Assert.
        assertThat(mView.scaleX).isWithin(FLOAT_ERROR).of(scale)
        assertThat(mView.scaleY).isWithin(FLOAT_ERROR).of(scale)
        assertThat(mView.translationX).isWithin(FLOAT_ERROR).of(translationX)
        assertThat(mView.translationY).isWithin(FLOAT_ERROR).of(translationY)
    }
}