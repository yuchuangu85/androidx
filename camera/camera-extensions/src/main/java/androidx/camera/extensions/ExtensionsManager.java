/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.camera.extensions.impl.InitializerImpl;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Provides interfaces for third party app developers to get capabilities info of extension
 * functions.
 */
public final class ExtensionsManager {
    private static final String TAG = "ExtensionsManager";

    /** The effect mode options applied on the bound use cases */
    public enum EffectMode {
        /** Normal mode without any specific effect applied. */
        NORMAL,
        /** Bokeh mode that is often applied as portrait mode for people pictures. */
        BOKEH,
        /**
         * HDR mode that may get source pictures with different AE settings to generate a best
         * result.
         */
        HDR,
        /**
         * Night mode is used for taking better still capture images under low-light situations,
         * typically at night time.
         */
        NIGHT,
        /**
         * Beauty mode is used for taking still capture images that incorporate facial changes
         * like skin tone, geometry, or retouching.
         */
        BEAUTY,
        /**
         * Auto mode is used for taking still capture images that automatically adjust to the
         * surrounding scenery.
         */
        AUTO
    }

    public enum ExtensionsAvailability {
        /**
         * The device extensions library exists and has been correctly loaded.
         */
        LIBRARY_AVAILABLE,
        /**
         * The device extensions library exists. However, there was some error loading the library.
         */
        LIBRARY_UNAVAILABLE_ERROR_LOADING,
        /**
         * The device extensions library exists. However, the library is missing implementations.
         */
        LIBRARY_UNAVAILABLE_MISSING_IMPLEMENTATION,
        /**
         * There are no extensions available on this device.
         */
        NONE
    }

    private static final Object ERROR_LOCK = new Object();

    @GuardedBy("ERROR_LOCK")
    private static final Handler DEFAULT_HANDLER = new Handler(Looper.getMainLooper());
    @GuardedBy("ERROR_LOCK")
    private static volatile ExtensionsErrorListener sExtensionsErrorListener = null;

    // Singleton instance of the Extensions object
    private static final Object EXTENSIONS_LOCK = new Object();

    @GuardedBy("EXTENSIONS_LOCK")
    private static ListenableFuture<ExtensionsAvailability> sAvailabilityFuture;

    /**
     * Initialize the extensions asynchronously.
     *
     * <p>This should be the first call to the extensions module. An application must wait until the
     * {@link ListenableFuture} completes before making any other calls to the extensions module.
     */
    @NonNull
    public static ListenableFuture<ExtensionsAvailability> init(@NonNull Context context) {
        synchronized (EXTENSIONS_LOCK) {
            if (ExtensionVersion.getRuntimeVersion() == null) {
                return Futures.immediateFuture(ExtensionsAvailability.NONE);
            }

            if (ExtensionVersion.getRuntimeVersion().compareTo(Version.VERSION_1_1) < 0) {
                return Futures.immediateFuture(
                        ExtensionsAvailability.LIBRARY_AVAILABLE);
            }

            if (sAvailabilityFuture == null) {
                sAvailabilityFuture = CallbackToFutureAdapter.getFuture(completer -> {
                    try {
                        InitializerImpl.init(VersionName.getCurrentVersion().toVersionString(),
                                context,
                                new InitializerImpl.OnExtensionsInitializedCallback() {
                                @Override
                                public void onSuccess() {
                                    Log.d(TAG, "Successfully initialized extensions");
                                    completer.set(
                                        ExtensionsAvailability.LIBRARY_AVAILABLE);
                                    }

                                @Override
                                public void onFailure(int error) {
                                    Log.d(TAG, "Failed to initialize extensions");
                                    completer.set(
                                        ExtensionsAvailability.LIBRARY_UNAVAILABLE_ERROR_LOADING);
                                }
                                },
                                CameraXExecutors.mainThreadExecutor());
                    } catch (NoSuchMethodError | NoClassDefFoundError e) {
                        completer.set(
                                ExtensionsAvailability.LIBRARY_UNAVAILABLE_MISSING_IMPLEMENTATION);
                    }

                    return "Initialize extensions";
                });
            }

            return sAvailabilityFuture;
        }
    }

    /**
     * Indicates whether the camera device with the lensFacing can support the specific
     * extension function.
     *
     * @param effectMode The extension function to be checked.
     * @param lensFacing The lensFacing of the camera device to be checked.
     * @return True if the specific extension function is supported for the camera device.
     */
    public static boolean isExtensionAvailable(@NonNull EffectMode effectMode,
            @CameraSelector.LensFacing int lensFacing) {
        boolean isImageCaptureAvailable = checkImageCaptureExtensionCapability(effectMode,
                lensFacing);
        boolean isPreviewAvailable = checkPreviewExtensionCapability(effectMode, lensFacing);

        if (isImageCaptureAvailable != isPreviewAvailable) {
            Log.e(TAG, "ImageCapture and Preview are not available simultaneously for "
                    + effectMode.name());
        }

        return isImageCaptureAvailable && isPreviewAvailable;
    }

    /**
     * Indicates whether the camera device with the lensFacing can support the specific
     * extension function for specific use case.
     *
     * @param klass      The {@link ImageCapture} or {@link Preview} class to be checked.
     * @param effectMode The extension function to be checked.
     * @param lensFacing The lensFacing of the camera device to be checked.
     * @return True if the specific extension function is supported for the camera device.
     */
    public static boolean isExtensionAvailable(@NonNull Class<?> klass,
            @NonNull EffectMode effectMode, @CameraSelector.LensFacing int lensFacing) {
        boolean isAvailable = false;

        if (klass == ImageCapture.class) {
            isAvailable = checkImageCaptureExtensionCapability(effectMode, lensFacing);
        } else if (klass.equals(Preview.class)) {
            isAvailable = checkPreviewExtensionCapability(effectMode, lensFacing);
        }

        return isAvailable;
    }

    private static boolean checkImageCaptureExtensionCapability(EffectMode effectMode,
            @CameraSelector.LensFacing int lensFacing) {
        ImageCapture.Builder builder = new ImageCapture.Builder();
        CameraSelector selector =
                new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        ImageCaptureExtender extender;

        switch (effectMode) {
            case BOKEH:
                extender = BokehImageCaptureExtender.create(builder);
                break;
            case HDR:
                extender = HdrImageCaptureExtender.create(builder);
                break;
            case NIGHT:
                extender = NightImageCaptureExtender.create(builder);
                break;
            case BEAUTY:
                extender = BeautyImageCaptureExtender.create(builder);
                break;
            case AUTO:
                extender = AutoImageCaptureExtender.create(builder);
                break;
            case NORMAL:
                return true;
            default:
                return false;
        }

        return extender.isExtensionAvailable(selector);
    }

    /**
     * Sets an {@link ExtensionsErrorListener} which will get called any time an
     * extensions error is encountered.
     *
     * @param listener The {@link ExtensionsErrorListener} listener that will be run.
     */
    public static void setExtensionsErrorListener(@Nullable ExtensionsErrorListener listener) {
        synchronized (ERROR_LOCK) {
            sExtensionsErrorListener = listener;
        }
    }

    static void postExtensionsError(ExtensionsErrorListener.ExtensionsErrorCode errorCode) {
        synchronized (ERROR_LOCK) {
            final ExtensionsErrorListener listenerReference = sExtensionsErrorListener;
            if (listenerReference != null) {
                DEFAULT_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        listenerReference.onError(errorCode);
                    }
                });
            }
        }
    }

    private static boolean checkPreviewExtensionCapability(EffectMode effectMode,
            @CameraSelector.LensFacing int lensFacing) {
        Preview.Builder builder = new Preview.Builder();
        CameraSelector cameraSelector =
                new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        PreviewExtender extender;

        switch (effectMode) {
            case BOKEH:
                extender = BokehPreviewExtender.create(builder);
                break;
            case HDR:
                extender = HdrPreviewExtender.create(builder);
                break;
            case NIGHT:
                extender = NightPreviewExtender.create(builder);
                break;
            case BEAUTY:
                extender = BeautyPreviewExtender.create(builder);
                break;
            case AUTO:
                extender = AutoPreviewExtender.create(builder);
                break;
            case NORMAL:
                return true;
            default:
                return false;
        }

        return extender.isExtensionAvailable(cameraSelector);
    }

    private ExtensionsManager() {
    }
}
