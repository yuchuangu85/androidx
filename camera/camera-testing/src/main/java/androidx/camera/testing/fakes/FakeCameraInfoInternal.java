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

package androidx.camera.testing.fakes;

import android.util.Range;
import android.util.Rational;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalExposureCompensation;
import androidx.camera.core.ExposureState;
import androidx.camera.core.TorchState;
import androidx.camera.core.ZoomState;
import androidx.camera.core.impl.CameraCaptureCallback;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.ImageOutputConfig.RotationValue;
import androidx.camera.core.impl.utils.CameraOrientationUtil;
import androidx.camera.core.internal.ImmutableZoomState;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.Executor;

/**
 * Information for a fake camera.
 *
 * <p>This camera info can be constructed with fake values.
 */
public final class FakeCameraInfoInternal implements CameraInfoInternal {
    private final String mCameraId;
    private final int mSensorRotation;
    @CameraSelector.LensFacing
    private final int mLensFacing;
    private final boolean mHasFlashUnit = true;
    private MutableLiveData<Integer> mTorchState = new MutableLiveData<>(TorchState.OFF);

    private final MutableLiveData<ZoomState> mZoomLiveData;
    private String mImplementationType = IMPLEMENTATION_TYPE_FAKE;

    public FakeCameraInfoInternal() {
        this(/*sensorRotation=*/ 0, /*lensFacing=*/ CameraSelector.LENS_FACING_BACK);
    }

    public FakeCameraInfoInternal(@NonNull String cameraId) {
        this(cameraId, 0, CameraSelector.LENS_FACING_BACK);
    }

    public FakeCameraInfoInternal(int sensorRotation, @CameraSelector.LensFacing int lensFacing) {
        this("0", sensorRotation, lensFacing);
    }

    public FakeCameraInfoInternal(@NonNull String cameraId, int sensorRotation,
            @CameraSelector.LensFacing int lensFacing) {
        mCameraId = cameraId;
        mSensorRotation = sensorRotation;
        mLensFacing = lensFacing;
        mZoomLiveData = new MutableLiveData<>(ImmutableZoomState.create(1.0f, 4.0f, 1.0f, 0.0f));
    }

    @Nullable
    @Override
    public Integer getLensFacing() {
        return mLensFacing;
    }

    @NonNull
    @Override
    public String getCameraId() {
        return mCameraId;
    }

    @Override
    public int getSensorRotationDegrees(@RotationValue int relativeRotation) {
        int relativeRotationDegrees =
                CameraOrientationUtil.surfaceRotationToDegrees(relativeRotation);
        // Currently this assumes that a back-facing camera is always opposite to the screen.
        // This may not be the case for all devices, so in the future we may need to handle that
        // scenario.
        boolean isOppositeFacingScreen = (CameraSelector.LENS_FACING_BACK == getLensFacing());
        return CameraOrientationUtil.getRelativeImageRotation(
                relativeRotationDegrees,
                mSensorRotation,
                isOppositeFacingScreen);
    }

    @Override
    public int getSensorRotationDegrees() {
        return getSensorRotationDegrees(Surface.ROTATION_0);
    }

    @Override
    public boolean hasFlashUnit() {
        return mHasFlashUnit;
    }

    @NonNull
    @Override
    public LiveData<Integer> getTorchState() {
        return mTorchState;
    }

    @NonNull
    @Override
    public LiveData<ZoomState> getZoomState() {
        return mZoomLiveData;
    }

    @NonNull
    @Override
    @ExperimentalExposureCompensation
    public ExposureState getExposureState() {
        return new ExposureState() {
            @Override
            public int getExposureCompensationIndex() {
                return 0;
            }

            @NonNull
            @Override
            public Range<Integer> getExposureCompensationRange() {
                return Range.create(0, 0);
            }

            @NonNull
            @Override
            public Rational getExposureCompensationStep() {
                return Rational.ZERO;
            }

            @Override
            public boolean isExposureCompensationSupported() {
                return true;
            }
        };
    }

    @NonNull
    @Override
    public String getImplementationType() {
        return mImplementationType;
    }

    @Override
    public void addSessionCaptureCallback(@NonNull Executor executor,
            @NonNull CameraCaptureCallback callback) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public void removeSessionCaptureCallback(@NonNull CameraCaptureCallback callback) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    /**
     * Set the implementation type for testing
     */
    public void setImplementationType(@NonNull @ImplementationType String implementationType) {
        mImplementationType = implementationType;
    }
}
