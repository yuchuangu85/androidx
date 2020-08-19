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

package androidx.camera.camera2.pipe.impl

import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.Request
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import javax.inject.Qualifier
import javax.inject.Scope

@Scope
annotation class CameraGraphScope

@Qualifier
annotation class ForCameraGraph

@CameraGraphScope
@Subcomponent(
    modules = [
        CameraGraphModules::class,
        CameraGraphConfigModule::class]
)
interface CameraGraphComponent {
    fun cameraGraph(): CameraGraph

    @Subcomponent.Builder
    interface Builder {
        fun cameraGraphConfigModule(config: CameraGraphConfigModule): Builder
        fun build(): CameraGraphComponent
    }
}

@Module
class CameraGraphConfigModule(private val config: CameraGraph.Config) {
    @Provides
    fun provideCameraGraphConfig(): CameraGraph.Config = config
}

@Module(
    includes = [
        SessionFactoryModule::class
    ]
)
abstract class CameraGraphModules {
    @Binds
    abstract fun bindCameraGraph(cameraGraph: CameraGraphImpl): CameraGraph

    @Binds
    abstract fun bindGraphProcessor(graphProcessor: GraphProcessorImpl): GraphProcessor

    companion object {
        @CameraGraphScope
        @Provides
        @ForCameraGraph
        fun provideCameraGraphCoroutineScope(threads: Threads): CoroutineScope {
            return CoroutineScope(threads.defaultDispatcher.plus(CoroutineName("CXCP-Graph")))
        }

        @CameraGraphScope
        @Provides
        @ForCameraGraph
        fun provideRequestListeners(
            graphConfig: CameraGraph.Config
        ): java.util.ArrayList<Request.Listener> {
            // TODO: Dagger doesn't appear to like standard kotlin lists. Replace this with a standard
            //   Kotlin list interfaces when dagger compiles with them.
            // TODO: Add internal listeners before adding external global listeners.
            return java.util.ArrayList(graphConfig.listeners)
        }
    }
}
