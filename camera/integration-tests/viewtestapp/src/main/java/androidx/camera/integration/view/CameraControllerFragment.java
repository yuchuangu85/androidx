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

package androidx.camera.integration.view;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.VideoCapture;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link Fragment} for testing {@link LifecycleCameraController}.
 */
@SuppressLint("RestrictedAPI")
public class CameraControllerFragment extends Fragment {

    private static final String TAG = "CameraCtrlFragment";

    private LifecycleCameraController mCameraController;
    private PreviewView mPreviewView;
    private FrameLayout mContainer;
    private Button mFlashMode;
    private ToggleButton mCameraToggle;
    private ExecutorService mExecutorService;
    private ToggleButton mVideoEnabledToggle;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @NonNull
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mExecutorService = Executors.newSingleThreadExecutor();
        mCameraController = new LifecycleCameraController(getContext());
        mCameraController.bindToLifecycle(CameraControllerFragment.this);

        View view = inflater.inflate(R.layout.camera_controller_view, container, false);
        mPreviewView = view.findViewById(R.id.preview_view);
        // Use compatible mode so StreamState is accurate.
        mPreviewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        mPreviewView.setController(mCameraController);

        // Set up the button to add and remove the PreviewView
        mContainer = view.findViewById(R.id.container);
        view.findViewById(R.id.remove_or_add).setOnClickListener(v -> {
            if (mContainer.getChildCount() == 0) {
                mContainer.addView(mPreviewView);
            } else {
                mContainer.removeView(mPreviewView);
            }
        });

        // Set up the button to change the PreviewView's size.
        view.findViewById(R.id.shrink).setOnClickListener(v -> {
            // Shrinks PreviewView by 10% each time it's clicked.
            mPreviewView.setLayoutParams(new FrameLayout.LayoutParams(mPreviewView.getWidth(),
                    (int) (mPreviewView.getHeight() * 0.9)));
        });

        // Set up the front/back camera toggle.
        mCameraToggle = view.findViewById(R.id.camera_toggle);
        mCameraToggle.setOnCheckedChangeListener(
                (compoundButton, value) -> mCameraController.setCameraSelector(value
                        ? CameraSelector.DEFAULT_BACK_CAMERA
                        : CameraSelector.DEFAULT_FRONT_CAMERA));

        // Image Capture enable switch.
        ToggleButton captureEnabled = view.findViewById(R.id.capture_enabled);
        captureEnabled.setOnCheckedChangeListener(
                (compoundButton, value) -> mCameraController.setImageCaptureEnabled(value));
        captureEnabled.setChecked(mCameraController.isImageCaptureEnabled());

        // Flash mode for image capture.
        mFlashMode = view.findViewById(R.id.flash_mode);
        mFlashMode.setOnClickListener(v -> {
            switch (mCameraController.getImageCaptureFlashMode()) {
                case ImageCapture.FLASH_MODE_AUTO:
                    mCameraController.setImageCaptureFlashMode(ImageCapture.FLASH_MODE_ON);
                    break;
                case ImageCapture.FLASH_MODE_ON:
                    mCameraController.setImageCaptureFlashMode(ImageCapture.FLASH_MODE_OFF);
                    break;
                case ImageCapture.FLASH_MODE_OFF:
                    mCameraController.setImageCaptureFlashMode(ImageCapture.FLASH_MODE_AUTO);
                    break;
                default:
                    throw new IllegalStateException("Invalid flash mode: "
                            + mCameraController.getImageCaptureFlashMode());
            }
            updateUiText();
        });

        // Take picture button.
        view.findViewById(R.id.capture).setOnClickListener(
                v -> {
                    try {
                        takePicture(new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(
                                    @NonNull ImageCapture.OutputFileResults outputFileResults) {
                                toast("Image saved to: " + outputFileResults.getSavedUri());
                            }

                            @Override
                            public void onError(@NonNull ImageCaptureException exception) {
                                toast("Failed to save picture: " + exception.getMessage());
                            }
                        });
                    } catch (RuntimeException exception) {
                        toast("Failed to take picture: " + exception.getMessage());
                    }
                });

        // Set up video UI.
        mVideoEnabledToggle = view.findViewById(R.id.video_enabled);
        mVideoEnabledToggle.setOnCheckedChangeListener(
                (compoundButton, checked) -> {
                    mCameraController.setVideoCaptureEnabled(checked);
                    updateUiText();
                });

        view.findViewById(R.id.video_record).setOnClickListener(v -> {
            try {
                String videoFileName = "video_" + System.currentTimeMillis();
                ContentResolver resolver = getContext().getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                contentValues.put(MediaStore.Video.Media.TITLE, videoFileName);
                contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName);
                VideoCapture.OutputFileOptions outputFileOptions =
                        new VideoCapture.OutputFileOptions.Builder(resolver,
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                contentValues).build();
                mCameraController.startRecording(outputFileOptions, mExecutorService,
                        new VideoCapture.OnVideoSavedCallback() {
                            @Override
                            public void onVideoSaved(
                                    @NonNull VideoCapture.OutputFileResults outputFileResults) {
                                toast("Video saved to: "
                                        + outputFileResults.getSavedUri());
                            }

                            @Override
                            public void onError(int videoCaptureError,
                                    @NonNull String message,
                                    @Nullable Throwable cause) {
                                toast("Failed to save video: " + message);
                            }
                        });
            } catch (RuntimeException exception) {
                toast("Failed to record video: " + exception.getMessage());
            }
            updateUiText();
        });
        view.findViewById(R.id.video_stop_recording).setOnClickListener(
                v -> {
                    mCameraController.stopRecording();
                    updateUiText();
                });

        updateUiText();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mExecutorService != null) {
            mExecutorService.shutdown();
        }
    }

    // Synthetic access
    @SuppressWarnings("WeakerAccess")
    void toast(String message) {
        getActivity().runOnUiThread(
                () -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Updates UI text based on the state of {@link #mCameraController}.
     */
    private void updateUiText() {
        mFlashMode.setText(getFlashModeTextResId());
        mCameraToggle.setChecked(mCameraController.getCameraSelector().getLensFacing()
                == CameraSelector.LENS_FACING_BACK);
        mVideoEnabledToggle.setChecked(mCameraController.isVideoCaptureEnabled());
    }

    private void createDefaultPictureFolderIfNotExist() {
        File pictureFolder = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        if (!pictureFolder.exists()) {
            if (!pictureFolder.mkdir()) {
                Log.e(TAG, "Failed to create directory: " + pictureFolder);
            }
        }
    }

    private int getFlashModeTextResId() {
        switch (mCameraController.getImageCaptureFlashMode()) {
            case ImageCapture.FLASH_MODE_AUTO:
                return R.string.flash_mode_auto;
            case ImageCapture.FLASH_MODE_ON:
                return R.string.flash_mode_on;
            case ImageCapture.FLASH_MODE_OFF:
                return R.string.flash_mode_off;
            default:
                throw new IllegalStateException("Invalid flash mode: "
                        + mCameraController.getImageCaptureFlashMode());
        }
    }

    // -----------------
    // For testing
    // -----------------

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.TESTS)
    LifecycleCameraController getCameraController() {
        return mCameraController;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.TESTS)
    void observePreviewStreamState(Observer<PreviewView.StreamState> observer) {
        mPreviewView.getPreviewStreamState().observe(CameraControllerFragment.this,
                observer);
    }

    @VisibleForTesting
    void takePicture(ImageCapture.OnImageSavedCallback callback) {
        createDefaultPictureFolderIfNotExist();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        getContext().getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues).build();
        mCameraController.takePicture(outputFileOptions, mExecutorService, callback);
    }

}
