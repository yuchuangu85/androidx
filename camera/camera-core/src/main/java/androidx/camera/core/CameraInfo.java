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

package androidx.camera.core;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.StringDef;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An interface for retrieving camera information.
 *
 * <p>Applications can retrieve an instance via {@link Camera#getCameraInfo()}.
 */
public interface CameraInfo {

    /**
     * An unknown camera implementation type.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    String IMPLEMENTATION_TYPE_UNKNOWN = "<unknown>";

    /**
     * A Camera2 API implementation type where the camera support level is
     * {@link android.hardware.camera2.CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
     * LIMITED},
     * {@link android.hardware.camera2.CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_FULL FULL},
     * {@link android.hardware.camera2.CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_3 LEVEL_3} or
     * {@link android.hardware.camera2.CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL
     * EXTRERNAL}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    String IMPLEMENTATION_TYPE_CAMERA2 = "androidx.camera.camera2";

    /**
     * A Camera2 API implementation type where the camera support level is
     * {@link android.hardware.camera2.CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY LEGACY}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    String IMPLEMENTATION_TYPE_CAMERA2_LEGACY = IMPLEMENTATION_TYPE_CAMERA2 + ".legacy";

    /**
     * A fake camera implementation type.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    String IMPLEMENTATION_TYPE_FAKE = "androidx.camera.fake";

    /**
     * Returns the sensor rotation in degrees, relative to the device's "natural" (default)
     * orientation.
     *
     * @return The sensor rotation in degrees, relative to device's "natural" (default) orientation.
     * @see
     * <a href="https://developer.android.com/guide/topics/sensors/sensors_overview#sensors-coords">
     * Sensor Coordinate System</a>
     */
    int getSensorRotationDegrees();

    /**
     * Returns the sensor rotation, in degrees, relative to the given rotation value.
     *
     * <p>Valid values for the relative rotation are {@link Surface#ROTATION_0} (natural), {@link
     * Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
     *
     * @param relativeRotation The rotation relative to which the output will be calculated.
     * @return The sensor rotation in degrees.
     */
    int getSensorRotationDegrees(@ImageOutputConfig.RotationValue int relativeRotation);

    /** Returns if flash unit is available or not. */
    boolean hasFlashUnit();

    /**
     * Returns a {@link LiveData} of current {@link TorchState}.
     *
     * <p>The torch can be turned on and off via {@link CameraControl#enableTorch(boolean)} which
     * will trigger the change event to the returned {@link LiveData}. Apps can either get
     * immediate value via {@link LiveData#getValue()} or observe it via
     * {@link LiveData#observe(LifecycleOwner, Observer)} to update torch UI accordingly.
     *
     * <p>If the camera doesn't have a flash unit (see {@link #hasFlashUnit()}), then the torch
     * state will be {@link TorchState#OFF}.
     *
     * @return a {@link LiveData} containing current torch state.
     */
    @NonNull
    LiveData<Integer> getTorchState();

    /**
     * Returns a {@link LiveData} of {@link ZoomState}.
     *
     * <p>The LiveData will be updated whenever the set zoom state has been changed. This can
     * occur when the application updates the zoom via {@link CameraControl#setZoomRatio(float)}
     * or {@link CameraControl#setLinearZoom(float)}. The zoom state can also change anytime a
     * camera starts up, for example when a {@link UseCase} is bound to it.
     */
    @NonNull
    LiveData<ZoomState> getZoomState();

    /**
     * Returns a {@link ExposureState}.
     *
     * <p>The {@link ExposureState} contains the current exposure related information.
     */
    @NonNull
    @ExperimentalExposureCompensation
    ExposureState getExposureState();

    /**
     * Returns the implementation type of the camera, this depends on the {@link CameraXConfig}
     * used in the initialization of CameraX.
     *
     * @return The implementation type of the camera, which can be one of the following:
     * {@link #IMPLEMENTATION_TYPE_UNKNOWN}, {@link #IMPLEMENTATION_TYPE_CAMERA2_LEGACY},
     * {@link #IMPLEMENTATION_TYPE_CAMERA2}, {@link #IMPLEMENTATION_TYPE_FAKE}.
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    @ImplementationType
    String getImplementationType();

    /** @hide */
    @StringDef(open = true, value = {IMPLEMENTATION_TYPE_UNKNOWN,
            IMPLEMENTATION_TYPE_CAMERA2_LEGACY, IMPLEMENTATION_TYPE_CAMERA2,
            IMPLEMENTATION_TYPE_FAKE})
    @Retention(RetentionPolicy.SOURCE)
    @RestrictTo(Scope.LIBRARY_GROUP)
    @interface ImplementationType {
    }
}
