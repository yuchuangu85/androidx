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

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Looper;

import androidx.annotation.FloatRange;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.camera.camera2.internal.annotation.CameraExecutor;
import androidx.camera.core.CameraControl.OperationCanceledException;
import androidx.camera.core.ZoomState;
import androidx.camera.core.impl.annotation.ExecutedBy;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.camera.core.internal.ImmutableZoomState;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/**
 * Implementation of zoom control used within CameraControl and CameraInfo.
 *
 * <p>It consists of setters and getters. Setters like {@link #setZoomRatio(float)} and
 * {@link #setLinearZoom(float)} return a {@link ListenableFuture} which apps can
 * use to await the async result. The {@link #getZoomState()} getter returns a {@link LiveData}
 * which apps can get immediate value from by
 * {@link LiveData#getValue()} or observe the changes by
 * {@link LiveData#observe(LifecycleOwner, Observer)}.
 *
 * <p>{@link #setZoomRatio(float)} accepts zoom ratio from {@link ZoomState#getMinZoomRatio()} to
 * {@link ZoomState#getMaxZoomRatio()}. Alternatively, app can call {@link #setLinearZoom(float)} to
 * specify the zoom by a [0..1] percentage. The linearZoom value is a float ranging from 0 to 1
 * representing the minimum zoom to maximum zoom respectively. The benefits of using linear zoom
 * is it ensures the FOV width/height is changed linearly.
 *
 * <p>The operation (the setters) will throw {@link IllegalStateException} if {@link ZoomControl} is
 * not active. All states are reset to default values once it is inactive. We should set active
 * on {@link ZoomControl} when apps are ready to accept zoom operations and set inactive if camera
 * is closing or closed.
 */
final class ZoomControl {
    private static final String TAG = "ZoomControl";
    public static final float DEFAULT_ZOOM_RATIO = 1.0f;
    public static final float MIN_ZOOM = DEFAULT_ZOOM_RATIO;

    private final Camera2CameraControlImpl mCamera2CameraControlImpl;
    @CameraExecutor
    private final Executor mExecutor;
    @GuardedBy("mCurrentZoomState")
    private final ZoomStateImpl mCurrentZoomState;
    private final MutableLiveData<ZoomState> mZoomStateLiveData;

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    CallbackToFutureAdapter.Completer<Void> mPendingZoomRatioCompleter;
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    Rect mPendingZoomCropRegion = null;

    /**
     * true if it is ready to accept zoom operation. Any zoom operation during inactive state will
     * throw{@link IllegalStateException}.
     */
    private boolean mIsActive = false;

    ZoomControl(@NonNull Camera2CameraControlImpl camera2CameraControlImpl,
            @NonNull CameraCharacteristics cameraCharacteristics,
            @CameraExecutor @NonNull Executor executor) {
        mCamera2CameraControlImpl = camera2CameraControlImpl;
        mExecutor = executor;

        mCurrentZoomState = new ZoomStateImpl(getMaxDigitalZoom(cameraCharacteristics), MIN_ZOOM);

        mCurrentZoomState.setZoomRatio(DEFAULT_ZOOM_RATIO);
        mZoomStateLiveData = new MutableLiveData<>(ImmutableZoomState.create(mCurrentZoomState));

        camera2CameraControlImpl.addCaptureResultListener(mCaptureResultListener);
    }

    /**
     * Set current active state. Set active if it is ready to accept zoom operations.
     *
     * <p>Any zoom operation during inactive state will do nothing and report a error in
     * ListenableFuture. All zoom states are reset to default once it is changed to inactive state.
     */
    @ExecutedBy("mExecutor")
    void setActive(boolean isActive) {
        // Only do variable assignment within the synchronized block to prevent form dead lock.
        if (mIsActive == isActive) {
            return;
        }

        mIsActive = isActive;

        if (!mIsActive) {
            // Reset all values if zoomControl is inactive.
            ZoomState zoomState;
            synchronized (mCurrentZoomState) {
                mCurrentZoomState.setZoomRatio(DEFAULT_ZOOM_RATIO);
                zoomState = ImmutableZoomState.create(mCurrentZoomState);
            }
            updateLiveData(zoomState);

            mPendingZoomCropRegion = null;
            mCamera2CameraControlImpl.setCropRegionInternal(null);

            // Fails the pending ListenableFuture.
            if (mPendingZoomRatioCompleter != null) {
                mPendingZoomRatioCompleter
                        .setException(new OperationCanceledException("Camera is not active."));
                mPendingZoomRatioCompleter = null;
            }
        }
    }

    private Camera2CameraControlImpl.CaptureResultListener mCaptureResultListener =
            new Camera2CameraControlImpl.CaptureResultListener() {
                @ExecutedBy("mExecutor")
                @Override
                public boolean onCaptureResult(@NonNull TotalCaptureResult captureResult) {
                    // Compare the requested crop region, not the result's crop region because HAL
                    // could modify the requested crop region.
                    if (mPendingZoomRatioCompleter != null) {
                        CaptureRequest request = captureResult.getRequest();
                        Rect cropRect = (request == null) ? null :
                                request.get(CaptureRequest.SCALER_CROP_REGION);

                        if (mPendingZoomCropRegion != null
                                && mPendingZoomCropRegion.equals(cropRect)) {
                            mPendingZoomRatioCompleter.set(null);
                            mPendingZoomRatioCompleter = null;
                            mPendingZoomCropRegion = null;
                        }
                    }
                    return false; // continue checking
                }
            };

    /**
     * Sets current zoom by ratio.
     *
     * <p>It modifies both current zoomRatio and linearZoom so if apps are observing
     * zoomRatio or linearZoom, they will get the update as well. If the ratio is
     * smaller than {@link ZoomState#getMinZoomRatio()} or larger than
     * {@link ZoomState#getMaxZoomRatio()} ()}, the returned {@link ListenableFuture} will fail with
     * {@link IllegalArgumentException} and it won't modify current zoom ratio. It is the
     * applications' duty to clamp the ratio.
     *
     * @return a {@link ListenableFuture} which is finished when current repeating request
     * result contains the requested zoom ratio. It fails with
     * {@link OperationCanceledException} if there is newer value being set or camera is closed. If
     * the ratio is out of range, it fails with {@link IllegalArgumentException}.
     */
    @NonNull
    ListenableFuture<Void> setZoomRatio(float ratio) {
        // If the requested ratio is out of range, it will not modify zoom value but report
        // IllegalArgumentException in returned ListenableFuture.
        ZoomState zoomState;
        synchronized (mCurrentZoomState) {
            try {
                mCurrentZoomState.setZoomRatio(ratio);
                zoomState = ImmutableZoomState.create(mCurrentZoomState);
            } catch (IllegalArgumentException e) {
                return Futures.immediateFailedFuture(e);
            }
        }

        updateLiveData(zoomState);

        return CallbackToFutureAdapter.getFuture(completer -> {
            mExecutor.execute(() -> submitCameraZoomRatio(completer, zoomState));
            return "setZoomRatio";
        });
    }

    @NonNull
    @VisibleForTesting
    static Rect getCropRectByRatio(@NonNull Rect sensorRect, float ratio) {
        float cropWidth = (sensorRect.width() / ratio);
        float cropHeight = (sensorRect.height() / ratio);
        float left = ((sensorRect.width() - cropWidth) / 2.0f);
        float top = ((sensorRect.height() - cropHeight) / 2.0f);
        return new Rect((int) left, (int) top, (int) (left + cropWidth),
                (int) (top + cropHeight));
    }

    /**
     * Submits the request for updating the zoom ratio of the underlying camera instance.
     *
     * <p>When the returned {@link ListenableFuture} completes, either the zoom ratio will be
     * updated or it will have failed, because some other action canceled the updating of the zoom.
     */
    @ExecutedBy("mExecutor")
    private void submitCameraZoomRatio(@NonNull CallbackToFutureAdapter.Completer<Void> completer,
            @NonNull ZoomState zoomState) {
        if (!mIsActive) {
            synchronized (mCurrentZoomState) {
                mCurrentZoomState.setZoomRatio(DEFAULT_ZOOM_RATIO);
                zoomState = ImmutableZoomState.create(mCurrentZoomState);
            }
            updateLiveData(zoomState);
            completer.setException(new OperationCanceledException("Camera is not active."));
            return;
        }

        updateLiveData(zoomState);

        Rect sensorRect = mCamera2CameraControlImpl.getSensorRect();
        mPendingZoomCropRegion = getCropRectByRatio(sensorRect, zoomState.getZoomRatio());
        mCamera2CameraControlImpl.setCropRegionInternal(mPendingZoomCropRegion);

        if (mPendingZoomRatioCompleter != null) {
            mPendingZoomRatioCompleter.setException(
                    new OperationCanceledException("There is a new zoomRatio being set"));
        }
        mPendingZoomRatioCompleter = completer;
    }

    /**
     * Sets current zoom by a linear zoom value ranging from 0f to 1.0f. LinearZoom 0f represents
     * the minimum zoom while linearZoom 1.0f represents the maximum zoom. The advantage of
     * linearZoom is that it ensures the field of view (FOV) varies linearly with the linearZoom
     * value, for use with slider UI elements (while {@link #setZoomRatio(float)} works well
     * for pinch-zoom gestures).
     *
     * <p>It modifies both current zoomRatio and linearZoom so if apps are observing
     * zoomRatio or linearZoom, they will get the update as well. If the linearZoom is not in
     * the range [0..1], the returned {@link ListenableFuture} will fail with
     * {@link IllegalArgumentException} and it won't modify current linearZoom and zoomRatio. It is
     * application's duty to clamp the linearZoom within [0..1].
     *
     * @return a {@link ListenableFuture} which is finished when current repeating request
     * result contains the requested linearZoom. It fails with
     * {@link OperationCanceledException} if there is newer value being set or camera is closed.
     * If linearZoom is not in range [0..1], it fails with {@link IllegalArgumentException}.
     */
    @NonNull
    ListenableFuture<Void> setLinearZoom(@FloatRange(from = 0f, to = 1f) float linearZoom) {
        // If the requested linearZoom is out of range, it will not modify zoom value but
        // report IllegalArgumentException in returned ListenableFuture.
        ZoomState zoomState;
        synchronized (mCurrentZoomState) {
            try {
                mCurrentZoomState.setLinearZoom(linearZoom);
                zoomState = ImmutableZoomState.create(mCurrentZoomState);
            } catch (IllegalArgumentException e) {
                return Futures.immediateFailedFuture(e);
            }
        }

        updateLiveData(zoomState);

        return CallbackToFutureAdapter.getFuture(completer -> {
            mExecutor.execute(() -> submitCameraZoomRatio(completer, zoomState));
            return "setLinearZoom";
        });
    }

    private void updateLiveData(ZoomState zoomState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mZoomStateLiveData.setValue(zoomState);
        } else {
            mZoomStateLiveData.postValue(zoomState);
        }
    }

    /**
     * Returns a {@link LiveData} of the current {@link ZoomState}.
     *
     * <p>Setting the zoom via {@link #setLinearZoom(float)} or {@link #setZoomRatio(float)} will
     * trigger a change in the values of the LiveData.
     */
    LiveData<ZoomState> getZoomState() {
        return mZoomStateLiveData;
    }

    private static float getMaxDigitalZoom(CameraCharacteristics cameraCharacteristics) {
        Float maxZoom = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

        if (maxZoom == null) {
            return MIN_ZOOM;
        }

        return maxZoom;
    }
}
