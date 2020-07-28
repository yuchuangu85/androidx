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

package androidx.camera.lifecycle

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.annotation.experimental.UseExperimental
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.Preview
import androidx.camera.core.impl.CameraFactory
import androidx.camera.core.impl.CameraThreadConfig
import androidx.camera.core.impl.ExtendableUseCaseConfigFactory
import androidx.camera.testing.fakes.FakeAppConfig
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager
import androidx.camera.testing.fakes.FakeCameraFactory
import androidx.camera.testing.fakes.FakeCameraInfoInternal
import androidx.camera.testing.fakes.FakeLifecycleOwner
import androidx.concurrent.futures.await
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.testutils.assertThrows
import com.google.common.truth.Truth.assertThat
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

@SmallTest
class ProcessCameraProviderTest {

    private val context = ApplicationProvider.getApplicationContext() as Context
    private val lifecycleOwner0 = FakeLifecycleOwner()
    private val lifecycleOwner1 = FakeLifecycleOwner()

    private lateinit var provider: ProcessCameraProvider

    @After
    fun tearDown() {
        runBlocking {
            try {
                val provider = ProcessCameraProvider.getInstance(context).await()
                provider.shutdown().await()
            } catch (e: IllegalStateException) {
                // ProcessCameraProvider may not be configured. Ignore.
            }
        }
    }

    @Test
    fun uninitializedGetInstance_throwsISE() {
        runBlocking {
            assertThrows<IllegalStateException> {
                ProcessCameraProvider.getInstance(context).await()
            }
        }
    }

    @Test
    fun canGetInstance_fromResources() = runBlocking {
        // Wrap the context with a TestAppContextWrapper. This returns customized resources which
        // will provide a CameraXConfig.Provider.
        val contextWrapper = TestAppContextWrapper(context)
        provider = ProcessCameraProvider.getInstance(contextWrapper).await()
        assertThat(provider).isNotNull()
        assertThat(contextWrapper.testResources.defaultProviderRetrieved).isTrue()
    }

    @UseExperimental(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun configuredGetInstance_doesNotUseResources() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking {
            // Wrap the context with a TestAppContextWrapper. This returns customized resources
            // which we can check whether a default config provider was provided.
            val contextWrapper = TestAppContextWrapper(context)
            provider = ProcessCameraProvider.getInstance(contextWrapper).await()
            assertThat(provider).isNotNull()
            assertThat(contextWrapper.testResources.defaultProviderRetrieved).isFalse()
        }
    }

    @UseExperimental(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun configuredGetInstance_doesNotUseApplication() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking {
            // Wrap the context with a TestAppContextWrapper and provide a context with an
            // Application that implements CameraXConfig.Provider. Because the
            // ProcessCameraProvider is already configured, this Application should not be used.
            val testApp = TestApplication()
            val contextWrapper = TestAppContextWrapper(context, testApp)
            provider = ProcessCameraProvider.getInstance(contextWrapper).await()
            assertThat(provider).isNotNull()
            assertThat(testApp.providerUsed).isFalse()
        }
    }

    @Test
    fun unconfiguredGetInstance_usesApplicationProvider() = runBlocking {
        val testApp = TestApplication()
        val contextWrapper = TestAppContextWrapper(context, testApp)
        provider = ProcessCameraProvider.getInstance(contextWrapper).await()
        assertThat(provider).isNotNull()
        assertThat(testApp.providerUsed).isTrue()
    }

    @UseExperimental(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun multipleConfigureInstance_throwsISE() {
        val config = FakeAppConfig.create()
        ProcessCameraProvider.configureInstance(config)
        assertThrows<IllegalStateException> {
            ProcessCameraProvider.configureInstance(config)
        }
    }

    @UseExperimental(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun configuredGetInstance_returnsProvider() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking {
            provider = ProcessCameraProvider.getInstance(context).await()
            assertThat(provider).isNotNull()
        }
    }

    @UseExperimental(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun configuredGetInstance_usesConfiguredExecutor() {
        var executeCalled = false
        val config =
            CameraXConfig.Builder.fromConfig(FakeAppConfig.create()).setCameraExecutor { runnable ->
                run {
                    executeCalled = true
                    Dispatchers.Default.asExecutor().execute(runnable)
                }
            }.build()
        ProcessCameraProvider.configureInstance(config)
        runBlocking {
            ProcessCameraProvider.getInstance(context).await()
            assertThat(executeCalled).isTrue()
        }
    }

    @UseExperimental(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun canRetrieveCamera_withZeroUseCases() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val camera =
                provider.bindToLifecycle(lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA)
            assertThat(camera).isNotNull()
        }
    }

    @Test
    fun bindUseCase_isBound() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(
                lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                useCase
            )

            assertThat(provider.isBound(useCase)).isTrue()
        }
    }

    @Test
    fun bindSecondUseCaseToDifferentLifecycle_firstUseCaseStillBound() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(
                lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                useCase0
            )
            provider.bindToLifecycle(
                lifecycleOwner1, CameraSelector.DEFAULT_BACK_CAMERA,
                useCase1
            )

            // TODO(b/158595693) Add check on whether or not camera for fakeUseCase0 should be
            //  exist or not
            // assertThat(fakeUseCase0.camera).isNotNull() (or isNull()?)
            assertThat(provider.isBound(useCase0)).isTrue()
            assertThat(useCase1.camera).isNotNull()
            assertThat(provider.isBound(useCase1)).isTrue()
        }
    }

    @Test
    fun isNotBound_afterUnbind() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(
                lifecycleOwner0,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCase
            )

            provider.unbind(useCase)

            assertThat(provider.isBound(useCase)).isFalse()
        }
    }

    @Test
    fun unbindFirstUseCase_secondUseCaseStillBound() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(
                lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                useCase0, useCase1
            )

            provider.unbind(useCase0)

            assertThat(useCase0.camera).isNull()
            assertThat(provider.isBound(useCase0)).isFalse()
            assertThat(useCase1.camera).isNotNull()
            assertThat(provider.isBound(useCase1)).isTrue()
        }
    }

    @Test
    fun unbindAll_unbindsAllUseCasesFromCameras() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(
                lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA, useCase
            )

            provider.unbindAll()

            assertThat(useCase.camera).isNull()
            assertThat(provider.isBound(useCase)).isFalse()
        }
    }

    @Test
    fun bindMultipleUseCases() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(
                lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA, useCase0, useCase1
            )

            assertThat(provider.isBound(useCase0)).isTrue()
            assertThat(provider.isBound(useCase1)).isTrue()
        }
    }

    @Test
    fun bind_createsDifferentLifecycleCameras_forDifferentLifecycles() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val camera0 = provider.bindToLifecycle(lifecycleOwner0,
                CameraSelector.DEFAULT_BACK_CAMERA, useCase0)

            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val camera1 = provider.bindToLifecycle(lifecycleOwner1,
                CameraSelector.DEFAULT_BACK_CAMERA, useCase1)

            assertThat(camera0).isNotEqualTo(camera1)
        }
    }

    @Test
    fun exception_withDestroyedLifecycle() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            lifecycleOwner0.destroy()

            assertThrows<IllegalArgumentException> {
                provider.bindToLifecycle(lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA)
            }
        }
    }

    @Test
    fun bind_returnTheSameCameraForSameSelectorAndLifecycleOwner() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            val camera0 = provider.bindToLifecycle(lifecycleOwner0, CameraSelector
            .DEFAULT_BACK_CAMERA, useCase0)
            val camera1 = provider.bindToLifecycle(lifecycleOwner0, CameraSelector
            .DEFAULT_BACK_CAMERA,
                useCase1)

            assertThat(camera0).isSameInstanceAs(camera1)
        }
    }

    @Test
    fun bindUseCases_withDifferentLensFacingButSameLifecycleOwner() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA, useCase0)

            assertThrows<IllegalArgumentException> {
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCase1
                )
            }
        }
    }

    @Test
    fun bindUseCases_withDifferentLensFacingAndLifecycle() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            val camera0 = provider.bindToLifecycle(lifecycleOwner0, CameraSelector
                .DEFAULT_BACK_CAMERA, useCase0)

            val camera1 = provider.bindToLifecycle(lifecycleOwner1, CameraSelector
                .DEFAULT_FRONT_CAMERA, useCase1)

            assertThat(camera0).isNotEqualTo(camera1)
        }
    }

    @Test
    fun bindUseCases_withNotExistedLensFacingCamera() {
        val cameraFactoryProvider =
            CameraFactory.Provider { _: Context?, _: CameraThreadConfig? ->
                val cameraFactory = FakeCameraFactory()
                cameraFactory.insertCamera(
                    CameraSelector.LENS_FACING_BACK,
                    "0"
                ) {
                    FakeCamera(
                        "0", null,
                        FakeCameraInfoInternal(
                            "0", 0,
                            CameraSelector.LENS_FACING_BACK
                        )
                    )
                }
                cameraFactory
            }

        val appConfigBuilder = CameraXConfig.Builder()
            .setCameraFactoryProvider(cameraFactoryProvider)
            .setDeviceSurfaceManagerProvider { FakeCameraDeviceSurfaceManager() }
            .setUseCaseConfigFactoryProvider { ExtendableUseCaseConfigFactory() }

        ProcessCameraProvider.configureInstance(appConfigBuilder.build())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()

            // The front camera is not defined, we should get the IllegalArgumentException when it
            // tries to get the camera.
            assertThrows<IllegalArgumentException> {
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCase
                )
            }
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_withZeroUseCases_bindBeforeLifecycleStarted() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA) as
                        LifecycleCamera
            lifecycleOwner0.startAndResume()
            assertThat(camera.isActive).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_withZeroUseCases_bindAfterLifecycleStarted() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA) as
                        LifecycleCamera
            assertThat(camera.isActive).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsActive_withUseCases_bindBeforeLifecycleStarted() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            lifecycleOwner0.startAndResume()
            assertThat(camera.isActive).isTrue()
        }
    }

    @Test
    fun lifecycleCameraIsActive_withUseCases_bindAfterLifecycleStarted() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            assertThat(camera.isActive).isTrue()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_unbindUseCase() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            assertThat(camera.isActive).isTrue()
            provider.unbind(useCase)
            assertThat(camera.isActive).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_unbindAll() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _ -> }.build()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0, CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            assertThat(camera.isActive).isTrue()
            provider.unbindAll()
            assertThat(camera.isActive).isFalse()
        }
    }
}

private class TestAppContextWrapper(base: Context, val app: Application? = null) : ContextWrapper
    (base) {

    val testResources = TestResources(base.resources)

    override fun getApplicationContext(): Context? {
        return app ?: this
    }

    override fun getResources(): Resources {
        return testResources
    }
}

private class TestApplication : Application(), CameraXConfig.Provider {
    private val used = atomic(false)
    val providerUsed: Boolean
        get() = used.value

    override fun getCameraXConfig(): CameraXConfig {
        used.value = true
        return FakeAppConfig.create()
    }
}

@Suppress("DEPRECATION")
private class TestResources(base: Resources) : Resources(
    base.assets, base.displayMetrics, base
        .configuration
) {

    private val retrieved = atomic(false)
    val defaultProviderRetrieved: Boolean
        get() = retrieved.value

    override fun getString(id: Int): String {
        if (id == androidx.camera.core.R.string.androidx_camera_default_config_provider) {
            retrieved.value = true
            return FakeAppConfig.DefaultProvider::class.java.name
        }
        return super.getString(id)
    }
}
