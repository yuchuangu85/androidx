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

package androidx.camera.camera2.interop;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;

import androidx.annotation.experimental.UseExperimental;
import androidx.camera.camera2.internal.Camera2CameraInfoImpl;
import androidx.camera.core.Camera;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
@UseExperimental(markerClass = ExperimentalCamera2Interop.class)
public final class Camera2CameraInfoTest {

    @Test
    public void canGetId_fromCamera2CameraInfo() {
        String cameraId = "42";
        Camera2CameraInfoImpl impl = mock(Camera2CameraInfoImpl.class);
        when(impl.getCameraId()).thenAnswer(ignored -> cameraId);
        Camera2CameraInfo camera2CameraInfo = new Camera2CameraInfo(impl);
        String extractedId = camera2CameraInfo.getCameraId();

        assertThat(extractedId).isEqualTo(cameraId);
    }

    @Test
    public void canExtractCharacteristics_fromCamera2CameraInfo() {
        CameraCharacteristics characteristics = mock(CameraCharacteristics.class);
        when(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).thenReturn(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        Camera2CameraInfoImpl impl = mock(Camera2CameraInfoImpl.class);
        when(impl.getCameraCharacteristics()).thenAnswer(ignored -> characteristics);
        Camera2CameraInfo camera2CameraInfo = new Camera2CameraInfo(impl);
        Integer hardwareLevel = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

        assertThat(hardwareLevel).isEqualTo(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
    }

    @Test
    public void canGetCamera2CameraInfo() {
        Camera2CameraInfo camera2CameraInfo = mock(Camera2CameraInfo.class);
        Camera2CameraInfoImpl cameraInfoImpl = mock(Camera2CameraInfoImpl.class);
        when(cameraInfoImpl.getCamera2CameraInfo()).thenAnswer(
                ignored -> camera2CameraInfo);
        Camera camera = new FakeCamera(null, cameraInfoImpl);
        Camera2CameraInfo resultCamera2CameraInfo = Camera2CameraInfo.fromCameraInfo(
                camera.getCameraInfo());

        assertThat(resultCamera2CameraInfo).isEqualTo(camera2CameraInfo);
    }

    @Test(expected = IllegalStateException.class)
    public void getCameraInfoThrows_whenNotCamera2Impl() {
        CameraInfoInternal wrongCameraInfo = mock(CameraInfoInternal.class);
        Camera camera = new FakeCamera(null, wrongCameraInfo);

        Camera2CameraInfo.fromCameraInfo(camera.getCameraInfo());
    }
}
