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

package androidx.camera.camera2.internal;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.ConfigProvider;
import androidx.camera.core.impl.ImageCaptureConfig;
import androidx.camera.core.impl.SessionConfig;

/**
 * Provides defaults for {@link ImageCaptureConfig} in the Camera2 implementation.
 */
public final class ImageCaptureConfigProvider implements ConfigProvider<ImageCaptureConfig> {
    private static final String TAG = "ImageCaptureProvider";

    private final WindowManager mWindowManager;

    public ImageCaptureConfigProvider(@NonNull Context context) {
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    @NonNull
    @SuppressWarnings("deprecation") /* defaultDisplay */
    public ImageCaptureConfig getConfig() {
        ImageCapture.Builder builder = ImageCapture.Builder.fromConfig(
                ImageCapture.DEFAULT_CONFIG.getConfig());

        // SessionConfig containing all intrinsic properties needed for ImageCapture
        SessionConfig.Builder sessionBuilder = new SessionConfig.Builder();
        // TODO(b/114762170): Must set to preview here until we allow for multiple template types
        sessionBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);

        // Add options to UseCaseConfig
        builder.setDefaultSessionConfig(sessionBuilder.build());
        builder.setSessionOptionUnpacker(Camera2SessionOptionUnpacker.INSTANCE);

        CaptureConfig.Builder captureConfig = new CaptureConfig.Builder();
        captureConfig.setTemplateType(CameraDevice.TEMPLATE_STILL_CAPTURE);
        builder.setDefaultCaptureConfig(captureConfig.build());
        builder.setCaptureOptionUnpacker(ImageCaptureOptionUnpacker.INSTANCE);
        builder.setTargetAspectRatio(AspectRatio.RATIO_4_3);

        // TODO(b/160477756): Make UseCase default config providers only provider static configs
        int targetRotation = mWindowManager.getDefaultDisplay().getRotation();
        builder.setTargetRotation(targetRotation);

        return builder.getUseCaseConfig();
    }
}
