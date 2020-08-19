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
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.Preview;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.ConfigProvider;
import androidx.camera.core.impl.PreviewConfig;
import androidx.camera.core.impl.SessionConfig;

/**
 * Provides defaults for {@link PreviewConfig} in the Camera2 implementation.
 */
public final class PreviewConfigProvider implements ConfigProvider<PreviewConfig> {
    private static final String TAG = "PreviewConfigProvider";

    private final WindowManager mWindowManager;

    public PreviewConfigProvider(@NonNull Context context) {
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    @NonNull
    @SuppressWarnings("deprecation") /* defaultDisplay */
    public PreviewConfig getConfig(@Nullable CameraInfo cameraInfo) {
        Preview.Builder builder = Preview.Builder.fromConfig(
                Preview.DEFAULT_CONFIG.getConfig(cameraInfo));

        // SessionConfig containing all intrinsic properties needed for Preview
        SessionConfig.Builder sessionBuilder = new SessionConfig.Builder();
        sessionBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);

        // Add options to UseCaseConfig
        builder.setDefaultSessionConfig(sessionBuilder.build());
        builder.setSessionOptionUnpacker(Camera2SessionOptionUnpacker.INSTANCE);

        CaptureConfig.Builder captureBuilder = new CaptureConfig.Builder();
        captureBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
        builder.setDefaultCaptureConfig(captureBuilder.build());
        builder.setCaptureOptionUnpacker(Camera2CaptureOptionUnpacker.INSTANCE);

        int targetRotation = mWindowManager.getDefaultDisplay().getRotation();
        builder.setTargetRotation(targetRotation);
        builder.setTargetAspectRatio(AspectRatio.RATIO_4_3);

        return builder.getUseCaseConfig();
    }
}
