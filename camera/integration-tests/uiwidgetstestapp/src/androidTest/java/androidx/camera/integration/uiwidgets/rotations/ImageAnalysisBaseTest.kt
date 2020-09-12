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

package androidx.camera.integration.uiwidgets.rotations

import android.content.Intent
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.CoreAppTestUtil
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.testutils.withActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import java.util.concurrent.TimeUnit

/**
 * Base class for rotation image analysis tests.
 *
 * The flow of the tests is:
 * - Launch the activity
 * - Wait fo the camera to be set up
 * - Rotate
 * - Wait a couple of frames
 * - Verify the image analysis image rotation
 */
abstract class ImageAnalysisBaseTest<A : CameraActivity> {

    protected val mDevice: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    protected fun setUp(lensFacing: Int) {
        CoreAppTestUtil.assumeCompatibleDevice()
        Assume.assumeTrue(CameraUtil.hasCameraWithLensFacing(lensFacing))

        // Clear the device's UI and ensure it's in a natural orientation
        CoreAppTestUtil.clearDeviceUI(InstrumentationRegistry.getInstrumentation())
        mDevice.setOrientationNatural()
    }

    protected fun tearDown() {
        mDevice.unfreezeRotation()
    }

    protected inline fun <reified A : CameraActivity> verifyRotation(
        lensFacing: Int,
        rotate: ActivityScenario<A>.() -> Unit
    ) {
        val activityScenario: ActivityScenario<A> = launchActivity(lensFacing)
        activityScenario.use { scenario ->

            // Wait until the camera is set up and analysis starts receiving frames
            scenario.waitOnCameraFrames()

            // Rotate
            rotate(scenario)

            // Reset received camera frames count
            scenario.resetFramesCount()

            // Wait a couple of frames after rotation
            scenario.waitOnCameraFrames()

            // Image rotation is correct if equal to sensor rotation relative to target rotation
            val (sensorToTargetRotation, imageRotationDegrees) = scenario.withActivity {
                Pair(getSensorRotationRelativeToAnalysisTargetRotation(), mAnalysisImageRotation)
            }
            assertThat(sensorToTargetRotation).isEqualTo(imageRotationDegrees)
        }
    }

    protected inline fun <reified A : CameraActivity> launchActivity(lensFacing: Int):
            ActivityScenario<A> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            A::class.java
        ).apply {
            putExtra(CameraActivity.KEY_LENS_FACING, lensFacing)
        }
        return ActivityScenario.launch<A>(intent)
    }

    protected inline fun <reified A : CameraActivity> ActivityScenario<A>.waitOnCameraFrames() {
        val analysisRunning = withActivity { mAnalysisRunning }
        assertThat(analysisRunning.tryAcquire(IMAGES_COUNT, TIMEOUT, TimeUnit.SECONDS)).isTrue()
    }

    protected inline fun <reified A : CameraActivity> ActivityScenario<A>.resetFramesCount() {
        withActivity { mAnalysisRunning.drainPermits() }
    }

    companion object {
        protected const val IMAGES_COUNT = 30
        protected const val TIMEOUT = 5L
    }
}