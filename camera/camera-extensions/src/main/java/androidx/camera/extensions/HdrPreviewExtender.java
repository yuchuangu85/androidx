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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Logger;
import androidx.camera.core.Preview;
import androidx.camera.extensions.ExtensionsManager.EffectMode;
import androidx.camera.extensions.impl.HdrPreviewExtenderImpl;

/**
 * Load the OEM extension Preview implementation for HDR effect type.
 */
public class HdrPreviewExtender extends PreviewExtender {
    private static final String TAG = "HdrPreviewExtender";

    /**
     * Create a new instance of the HDR extender.
     *
     * @param builder Builder that will be used to create the configurations for the
     *                {@link androidx.camera.core.Preview}.
     */
    @NonNull
    public static HdrPreviewExtender create(@NonNull Preview.Builder builder) {
        if (ExtensionVersion.isExtensionVersionSupported()) {
            try {
                return new VendorHdrPreviewExtender(builder);
            } catch (NoClassDefFoundError e) {
                Logger.d(TAG, "No HDR preview extender found. Falling back to default.");
            }
        }

        return new DefaultHdrPreviewExtender();
    }

    /** Empty implementation of HDR extender which does nothing. */
    static class DefaultHdrPreviewExtender extends HdrPreviewExtender {
        DefaultHdrPreviewExtender() {
        }

        @Override
        public boolean isExtensionAvailable(@NonNull CameraSelector selector) {
            return false;
        }

        @Override
        public void enableExtension(@NonNull CameraSelector selector) {
        }
    }

    /** HDR extender that calls into the vendor provided implementation. */
    static class VendorHdrPreviewExtender extends HdrPreviewExtender {
        private final HdrPreviewExtenderImpl mImpl;

        VendorHdrPreviewExtender(Preview.Builder builder) {
            mImpl = new HdrPreviewExtenderImpl();
            init(builder, mImpl, EffectMode.HDR);
        }
    }

    private HdrPreviewExtender() {
    }
}
