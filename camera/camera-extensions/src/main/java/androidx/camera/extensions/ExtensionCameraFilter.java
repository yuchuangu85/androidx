/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraFilter;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.ExperimentalCameraFilter;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.extensions.impl.ImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.PreviewExtenderImpl;
import androidx.core.util.Preconditions;

import java.util.LinkedHashSet;

/**
 * A filter that filters camera based on extender implementation. If the implementation is
 * unavailable, the camera will be considered available.
 */
@UseExperimental(markerClass = ExperimentalCameraFilter.class)
public final class ExtensionCameraFilter implements CameraFilter {
    private final PreviewExtenderImpl mPreviewExtenderImpl;
    private final ImageCaptureExtenderImpl mImageCaptureExtenderImpl;

    ExtensionCameraFilter(@Nullable PreviewExtenderImpl previewExtenderImpl) {
        mPreviewExtenderImpl = previewExtenderImpl;
        mImageCaptureExtenderImpl = null;
    }

    ExtensionCameraFilter(@Nullable ImageCaptureExtenderImpl imageCaptureExtenderImpl) {
        mPreviewExtenderImpl = null;
        mImageCaptureExtenderImpl = imageCaptureExtenderImpl;
    }

    ExtensionCameraFilter(@Nullable PreviewExtenderImpl previewExtenderImpl,
            @Nullable ImageCaptureExtenderImpl imageCaptureExtenderImpl) {
        mPreviewExtenderImpl = previewExtenderImpl;
        mImageCaptureExtenderImpl = imageCaptureExtenderImpl;
    }

    @UseExperimental(markerClass = ExperimentalCamera2Interop.class)
    @NonNull
    @Override
    public LinkedHashSet<Camera> filter(@NonNull LinkedHashSet<Camera> cameras) {
        LinkedHashSet<Camera> resultCameras = new LinkedHashSet<>();
        for (Camera camera : cameras) {
            Preconditions.checkState(camera instanceof CameraInternal,
                    "The camera doesn't contain internal implementation.");
            String cameraId = ((CameraInternal) camera).getCameraInfoInternal().getCameraId();
            CameraInfo cameraInfo = camera.getCameraInfo();

            boolean available = true;

            // If preview extender impl isn't null, check if the camera id is supported.
            if (mPreviewExtenderImpl != null) {
                available =
                        mPreviewExtenderImpl.isExtensionAvailable(cameraId,
                                Camera2CameraInfo.extractCameraCharacteristics(cameraInfo));
            }
            // If image capture extender impl isn't null, check if the camera id is supported.
            if (mImageCaptureExtenderImpl != null) {
                available = mImageCaptureExtenderImpl.isExtensionAvailable(cameraId,
                        Camera2CameraInfo.extractCameraCharacteristics(cameraInfo));
            }

            if (available) {
                resultCameras.add(camera);
            }
        }

        return resultCameras;
    }
}
