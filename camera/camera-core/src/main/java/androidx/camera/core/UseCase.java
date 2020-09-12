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

package androidx.camera.core;

import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_ROTATION;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.media.ImageReader;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.impl.CameraControlInternal;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.Config.Option;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.camera.core.impl.MutableConfig;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.internal.utils.UseCaseConfigUtil;
import androidx.core.util.Preconditions;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The use case which all other use cases are built on top of.
 *
 * <p>A UseCase provides functionality to map the set of arguments in a use case to arguments
 * that are usable by a camera. UseCase also will communicate of the active/inactive state to
 * the Camera.
 */
public abstract class UseCase {

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase lifetime constant] - Stays constant for the lifetime of the UseCase. Which means
    // they could be created in the constructor.
    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The set of {@link StateChangeCallback} that are currently listening state transitions of this
     * use case.
     */
    private final Set<StateChangeCallback> mStateChangeCallbacks = new HashSet<>();

    /**
     * Store the initial {@link UseCaseConfig} used to create the use case.
     */
    private final UseCaseConfig<?> mInitialUseCaseConfig;
    private final Object mCameraLock = new Object();

    /**
     * The default target rotation value is determined when the use case is created. Detaching
     * and attaching the use case won't change the use case's default target rotation value.
     */
    @ImageOutputConfig.RotationValue
    private final int mDefaultTargetRotation;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase lifetime dynamic] - Dynamic variables which could change during anytime during
    // the UseCase lifetime.
    ////////////////////////////////////////////////////////////////////////////////////////////

    private State mState = State.INACTIVE;

    /** The target rotation setting set via {@link #setTargetRotationInternal(int)}. */
    @ImageOutputConfig.RotationValue
    private int mTargetRotation;

    private UseCaseConfig<?> mUseCaseConfig;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase attached constant] - Is only valid when the UseCase is attached to a camera.
    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The resolution assigned to the {@link UseCase} based on the attached camera.
     */
    private Size mAttachedResolution;

    /**
     * The crop rect calculated at the time of binding based on {@link ViewPort}.
     */
    @Nullable
    private Rect mViewPortCropRect;

    @GuardedBy("mCameraLock")
    private CameraInternal mCamera;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase attached dynamic] - Can change but is only available when the UseCase is attached.
    ////////////////////////////////////////////////////////////////////////////////////////////

    // The currently attached session config
    private SessionConfig mAttachedSessionConfig = SessionConfig.defaultEmptySessionConfig();

    /**
     * Creates a named instance of the use case.
     *
     * @param useCaseConfig the configuration object used for this use case
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected UseCase(@NonNull UseCaseConfig<?> useCaseConfig) {
        mInitialUseCaseConfig = useCaseConfig;
        mUseCaseConfig = useCaseConfig;

        // Determine the use case's target rotation value. If it has been provided in the
        // useCaseConfig, extract it. Otherwise, obtain it from the default config provider.
        if (useCaseConfig.containsOption(OPTION_TARGET_ROTATION)) {
            mDefaultTargetRotation = useCaseConfig.retrieveOption(OPTION_TARGET_ROTATION);
        } else {
            UseCaseConfig.Builder<?, ?, ?> defaultBuilder = getDefaultBuilder();
            mDefaultTargetRotation =
                    defaultBuilder != null ? defaultBuilder.getUseCaseConfig().retrieveOption(
                            OPTION_TARGET_ROTATION, Surface.ROTATION_0) : Surface.ROTATION_0;
        }

        mTargetRotation = mDefaultTargetRotation;
    }

    /**
     * Returns a use case configuration pre-populated with default configuration
     * options.
     *
     * <p>This is used to generate a final configuration by combining the user-supplied
     * configuration with the default configuration. Subclasses can override this method to provide
     * the pre-populated builder. If <code>null</code> is returned, then the user-supplied
     * configuration will be used directly.
     *
     * @return A builder pre-populated with use case default options.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder() {
        return null;
    }

    /**
     * Updates the stored use case configuration.
     *
     * <p>This configuration will be combined with the default configuration that is contained in
     * the pre-populated builder supplied by {@link #getDefaultBuilder}, if it exists and the
     * behavior of {@link #applyDefaults(UseCaseConfig, UseCaseConfig.Builder)} is not overridden.
     * Once this method returns, the combined use case configuration can be retrieved with
     * {@link #getUseCaseConfig()}.
     *
     * <p>This method alone will not make any changes to the {@link SessionConfig}, it is up to
     * the use case to decide when to modify the session configuration.
     *
     * @param useCaseConfig Configuration which will be applied on top of use case defaults, if a
     *                      default builder is provided by {@link #getDefaultBuilder}.
     * @throws IllegalStateException if this function is called when the UseCase is not attached
     * to a camera.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected final void updateUseCaseConfig(@NonNull UseCaseConfig<?> useCaseConfig) {
        // updateUseCaseConfig() can only be called after the use case is attached to a camera
        // and then the settings will only be applied to the use case config. This is to make the
        // use case config static.
        CameraInternal camera = getCamera();

        if (camera == null) {
            throw new IllegalStateException("Disallow to call updateUseCaseConfig() before the "
                    + "use case is attached to a camera.");
        }

        // Attempt to retrieve builder containing defaults for this use case's config
        UseCaseConfig.Builder<?, ?, ?> defaultBuilder = getDefaultBuilder();

        // Combine with default configuration.
        mUseCaseConfig = applyDefaults(useCaseConfig, defaultBuilder);
    }

    /**
     * Combines user-supplied configuration with use case default configuration.
     *
     * <p>Subclasses can override this method to
     * modify the behavior of combining user-supplied values and default values.
     *
     * @param userConfig           The user-supplied configuration.
     * @param defaultConfigBuilder A builder containing use-case default values, or {@code null}
     *                             if no default values exist.
     * @return The configuration that will be used by this use case.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public UseCaseConfig<?> applyDefaults(
            @NonNull UseCaseConfig<?> userConfig,
            @Nullable UseCaseConfig.Builder<?, ?, ?> defaultConfigBuilder) {
        if (defaultConfigBuilder == null) {
            // No default builder was retrieved, return config directly
            return userConfig;
        }

        MutableConfig defaultMutableConfig = defaultConfigBuilder.getMutableConfig();

        // If OPTION_TARGET_RESOLUTION has been set by the user, remove
        // OPTION_TARGET_ASPECT_RATIO from defaultConfigBuilder because these two settings can be
        // set at the same time.
        if (userConfig.containsOption(ImageOutputConfig.OPTION_TARGET_RESOLUTION)
                && defaultMutableConfig.containsOption(
                ImageOutputConfig.OPTION_TARGET_ASPECT_RATIO)) {
            defaultMutableConfig.removeOption(ImageOutputConfig.OPTION_TARGET_ASPECT_RATIO);
        }

        // Overwrite the default config builder's target rotation value by the determined default
        // target rotation value of the use case.
        defaultMutableConfig.insertOption(OPTION_TARGET_ROTATION, mDefaultTargetRotation);

        // If any options need special handling, this is the place to do it. For now we'll just copy
        // over all options.
        for (Option<?> opt : userConfig.listOptions()) {
            @SuppressWarnings("unchecked") // Options/values are being copied directly
                    Option<Object> objectOpt = (Option<Object>) opt;

            defaultMutableConfig.insertOption(objectOpt,
                    userConfig.getOptionPriority(opt), userConfig.retrieveOption(objectOpt));
        }

        return defaultConfigBuilder.getUseCaseConfig();
    }

    /**
     * Updates the target rotation of the use case config.
     *
     * @param targetRotation Target rotation of the output image, expressed as one of
     *                       {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
     *                       {@link Surface#ROTATION_180}, or {@link Surface#ROTATION_270}.
     * @return true if the target rotation was changed.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected boolean setTargetRotationInternal(
            @ImageOutputConfig.RotationValue int targetRotation) {
        ImageOutputConfig oldConfig = (ImageOutputConfig) getUseCaseConfig();
        int oldRotation = oldConfig.getTargetRotation(ImageOutputConfig.INVALID_ROTATION);
        if (oldRotation == ImageOutputConfig.INVALID_ROTATION || oldRotation != targetRotation) {
            // Camera is not null if the use case has been attached to a camera. Only calling
            // updateUseCaseConfig() when the use case has been attached to a camera. So that
            // some default config will be applied to the use case config.
            if (getCamera() != null) {
                UseCaseConfig.Builder<?, ?, ?> builder = getUseCaseConfigBuilder();
                UseCaseConfigUtil.updateTargetRotationAndRelatedConfigs(builder, targetRotation);
                updateUseCaseConfig(builder.getUseCaseConfig());
            }

            mTargetRotation = targetRotation;
            return true;
        }
        return false;
    }

    /**
     * Returns the rotation that the intended target resolution is expressed in.
     *
     * @return The rotation of the intended target.
     *
     * @hide
     */
    @SuppressLint("WrongConstant")
    @RestrictTo(Scope.LIBRARY_GROUP)
    @ImageOutputConfig.RotationValue
    protected int getTargetRotationInternal() {
        return mTargetRotation;
    }

    /**
     * Gets the relative rotation degrees based on the target rotation.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @IntRange(from = 0, to = 359)
    protected int getRelativeRotation(@NonNull CameraInternal cameraInternal) {
        return cameraInternal.getCameraInfoInternal().getSensorRotationDegrees(mTargetRotation);
    }

    /**
     * Sets the {@link SessionConfig} that will be used by the attached {@link Camera}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected void updateSessionConfig(@NonNull SessionConfig sessionConfig) {
        mAttachedSessionConfig = sessionConfig;
    }

    /**
     * Add a {@link StateChangeCallback}, which listens to this UseCase's active and inactive
     * transition events.
     */
    private void addStateChangeCallback(@NonNull StateChangeCallback callback) {
        mStateChangeCallbacks.add(callback);
    }

    /**
     * Remove a {@link StateChangeCallback} from listening to this UseCase's active and inactive
     * transition events.
     *
     * <p>If the listener isn't currently listening to the UseCase then this call does nothing.
     */
    private void removeStateChangeCallback(@NonNull StateChangeCallback callback) {
        mStateChangeCallbacks.remove(callback);
    }

    /**
     * Get the current {@link SessionConfig}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public SessionConfig getSessionConfig() {
        return mAttachedSessionConfig;
    }

    /**
     * Notify all {@link StateChangeCallback} that are listening to this UseCase that it has
     * transitioned to an active state.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected final void notifyActive() {
        mState = State.ACTIVE;
        notifyState();
    }

    /**
     * Notify all {@link StateChangeCallback} that are listening to this UseCase that it has
     * transitioned to an inactive state.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected final void notifyInactive() {
        mState = State.INACTIVE;
        notifyState();
    }

    /**
     * Notify all {@link StateChangeCallback} that are listening to this UseCase that the
     * settings have been updated.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected final void notifyUpdated() {
        for (StateChangeCallback stateChangeCallback : mStateChangeCallbacks) {
            stateChangeCallback.onUseCaseUpdated(this);
        }
    }

    /**
     * Notify all {@link StateChangeCallback} that are listening to this UseCase that the use
     * case needs to be completely reset.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected final void notifyReset() {
        for (StateChangeCallback stateChangeCallback : mStateChangeCallbacks) {
            stateChangeCallback.onUseCaseReset(this);
        }
    }

    /**
     * Notify all {@link StateChangeCallback} that are listening to this UseCase of its current
     * state.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public final void notifyState() {
        switch (mState) {
            case INACTIVE:
                for (StateChangeCallback stateChangeCallback : mStateChangeCallbacks) {
                    stateChangeCallback.onUseCaseInactive(this);
                }
                break;
            case ACTIVE:
                for (StateChangeCallback stateChangeCallback : mStateChangeCallbacks) {
                    stateChangeCallback.onUseCaseActive(this);
                }
                break;
        }
    }

    /**
     * Returns the camera ID for the currently attached camera, or throws an exception if no
     * camera is attached.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected String getCameraId() {
        return Preconditions.checkNotNull(getCamera(),
                "No camera attached to use case: " + this).getCameraInfoInternal().getCameraId();
    }

    /**
     * Checks whether the provided camera ID is the currently attached camera ID.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected boolean isCurrentCamera(@NonNull String cameraId) {
        if (getCamera() == null) {
            return false;
        }
        return Objects.equals(cameraId, getCameraId());
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public String getName() {
        return mUseCaseConfig.getTargetName("<UnknownUseCase-" + this.hashCode() + ">");
    }

    /**
     * Retrieves the configuration used by this use case.
     *
     * @return the configuration used by this use case.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public UseCaseConfig<?> getUseCaseConfig() {
        return mUseCaseConfig;
    }

    /**
     * Returns a builder based on the current use case config.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    public abstract UseCaseConfig.Builder<?, ?, ?> getUseCaseConfigBuilder();

    /**
     * Returns the currently attached {@link Camera} or {@code null} if none is attached.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public CameraInternal getCamera() {
        synchronized (mCameraLock) {
            return mCamera;
        }
    }

    /**
     * Retrieves the currently attached surface resolution.
     *
     * @return the currently attached surface resolution for the given camera id.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public Size getAttachedSurfaceResolution() {
        return mAttachedResolution;
    }

    /**
     * Offers suggested resolution for the UseCase.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void updateSuggestedResolution(@NonNull Size suggestedResolution) {
        mAttachedResolution = onSuggestedResolutionUpdated(suggestedResolution);
    }

    /**
     * Called when binding new use cases via {@code CameraX#bindToLifecycle(LifecycleOwner,
     * CameraSelector, UseCase...)}.
     *
     * <p>Override to create necessary objects like {@link ImageReader} depending
     * on the resolution.
     *
     * @param suggestedResolution The suggested resolution that depends on camera device
     *                            capability and what and how many use cases will be bound.
     * @return The resolution that finally used to create the SessionConfig to
     * attach to the camera device.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected abstract Size onSuggestedResolutionUpdated(@NonNull Size suggestedResolution);

    /**
     * Called when CameraControlInternal is attached into the UseCase. UseCase may need to
     * override this method to configure the CameraControlInternal here. Ex. Setting correct flash
     * mode by CameraControlInternal.setFlashMode to enable correct AE mode and flash state.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected void onCameraControlReady() {
    }

    /**
     * Called when use case is attaching to a camera.
     *
     * @hide
     */
    @SuppressLint("WrongConstant")
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void onAttach(@NonNull CameraInternal camera) {
        synchronized (mCameraLock) {
            mCamera = camera;
            addStateChangeCallback(camera);
        }

        updateUseCaseConfig(mUseCaseConfig);

        // Updates the user persistent target rotation setting to the use case config.
        setTargetRotationInternal(mTargetRotation);

        EventCallback eventCallback = mUseCaseConfig.getUseCaseEventCallback(null);
        if (eventCallback != null) {
            eventCallback.onBind(camera.getCameraInfoInternal().getCameraId());
        }
        onAttached();
    }

    /**
     * Called in the end of onAttach().
     *
     * <p>Called after the use case is attached to a camera. After the use case is attached, the
     * default config settings are also applied to the use case config. The sub classes should
     * create the necessary objects to make the use case work correctly.
     *
     * <p>When onAttached is called, then UseCase should run setup to make sure that the UseCase
     * sets up the pipeline to receive data from the camera.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void onAttached() {
    }

    /**
     * Called when use case is detaching from a camera.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    public void onDetach(@NonNull CameraInternal camera) {
        // Do any cleanup required by the UseCase implementation
        onDetached();

        // Cleanup required for any type of UseCase
        EventCallback eventCallback = mUseCaseConfig.getUseCaseEventCallback(null);
        if (eventCallback != null) {
            eventCallback.onUnbind();
        }

        synchronized (mCameraLock) {
            Preconditions.checkArgument(camera == mCamera);
            removeStateChangeCallback(mCamera);
            mCamera = null;
        }

        mAttachedResolution = null;
        mViewPortCropRect = null;

        // Resets the mUseCaseConfig to the initial status when the use case was created to make
        // the use case reusable.
        mUseCaseConfig = mInitialUseCaseConfig;
    }

    /**
     * Clears internal state of this use case.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void onDetached() {
    }

    /**
     * Called when use case is attached to the camera. This method is called on main thread.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @CallSuper
    public void onStateAttached() {
        onCameraControlReady();
    }

    /**
     * Called when use case is detached from the camera. This method is called on main thread.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void onStateDetached() {
    }

    /**
     * Retrieves a previously attached {@link CameraControlInternal}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected CameraControlInternal getCameraControl() {
        synchronized (mCameraLock) {
            if (mCamera == null) {
                return CameraControlInternal.DEFAULT_EMPTY_INSTANCE;
            }
            return mCamera.getCameraControlInternal();
        }
    }

    /**
     * Sets the view port crop rect calculated at the time of binding.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    public void setViewPortCropRect(@Nullable Rect viewPortCropRect) {
        mViewPortCropRect = viewPortCropRect;
    }

    /**
     * Gets the view port crop rect.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    @Nullable
    public Rect getViewPortCropRect() {
        return mViewPortCropRect;
    }

    /**
     * Get image format for the use case.
     *
     * @return image format for the use case
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public int getImageFormat() {
        return mUseCaseConfig.getInputFormat();
    }

    enum State {
        /** Currently waiting for image data. */
        ACTIVE,
        /** Currently not waiting for image data. */
        INACTIVE
    }

    /**
     * Callback for when a {@link UseCase} transitions between active/inactive states.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public interface StateChangeCallback {
        /**
         * Called when a {@link UseCase} becomes active.
         *
         * <p>When a UseCase is active it expects that all data producers attached to itself
         * should start producing data for it to consume. In addition the UseCase will start
         * producing data that other classes can be consumed.
         */
        void onUseCaseActive(@NonNull UseCase useCase);

        /**
         * Called when a {@link UseCase} becomes inactive.
         *
         * <p>When a UseCase is inactive it no longer expects data to be produced for it. In
         * addition the UseCase will stop producing data for other classes to consume.
         */
        void onUseCaseInactive(@NonNull UseCase useCase);

        /**
         * Called when a {@link UseCase} has updated settings.
         *
         * <p>When a {@link UseCase} has updated settings, it is expected that the listener will
         * use these updated settings to reconfigure the listener's own state. A settings update is
         * orthogonal to the active/inactive state change.
         */
        void onUseCaseUpdated(@NonNull UseCase useCase);

        /**
         * Called when a {@link UseCase} has updated settings that require complete reset of the
         * camera.
         *
         * <p>Updating certain parameters of the use case require a full reset of the camera. This
         * includes updating the {@link Surface} used by the use case.
         */
        void onUseCaseReset(@NonNull UseCase useCase);
    }

    /**
     * Callback for when a {@link UseCase} transitions between bind/unbind states.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public interface EventCallback {

        /**
         * Called when use case was bound to the life cycle.
         *
         * @param cameraId that current used.
         */
        void onBind(@NonNull String cameraId);

        /**
         * Called when use case was unbind from the life cycle and clear the resource of the use
         * case.
         */
        void onUnbind();
    }
}
