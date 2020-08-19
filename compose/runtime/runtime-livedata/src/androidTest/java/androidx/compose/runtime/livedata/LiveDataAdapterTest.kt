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

package androidx.compose.runtime.livedata

import androidx.compose.runtime.Providers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LifecycleOwnerAmbient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.test.filters.MediumTest
import androidx.ui.test.createComposeRule
import androidx.ui.test.runOnIdle
import androidx.ui.test.runOnUiThread
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class LiveDataAdapterTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun whenValueIsNotSetWeGotNull() {
        val liveData = MutableLiveData<String>()
        var realValue: String? = "to-be-updated"
        rule.setContent {
            realValue = liveData.observeAsState().value
        }

        assertThat(realValue).isNull()
    }

    @Test
    fun weGotInitialValue() {
        val liveData = MutableLiveData<String>()
        liveData.postValue("value")
        var realValue: String? = null
        rule.setContent {
            realValue = liveData.observeAsState().value
        }

        assertThat(realValue).isEqualTo("value")
    }

    @Test
    fun weReceiveUpdates() {
        val liveData = MutableLiveData<String>()
        liveData.postValue("value")
        var realValue: String? = null
        rule.setContent {
            realValue = liveData.observeAsState().value
        }

        runOnIdle {
            liveData.postValue("value2")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value2")
        }
    }

    @Test
    fun noUpdatesAfterDestroy() {
        val liveData = MutableLiveData<String>()
        liveData.postValue("value")
        var realValue: String? = null
        val lifecycleOwner = runOnUiThread { RegistryOwner() }
        rule.setContent {
            Providers(LifecycleOwnerAmbient provides lifecycleOwner) {
                realValue = liveData.observeAsState().value
            }
        }

        runOnIdle {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.DESTROYED
        }

        runOnIdle {
            liveData.postValue("value2")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value")
        }
    }

    @Test
    fun observerRemovedWhenDisposed() {
        val liveData = MutableLiveData<String>()
        var emit by mutableStateOf(false)
        val lifecycleOwner = runOnUiThread { RegistryOwner() }
        rule.setContent {
            Providers(LifecycleOwnerAmbient provides lifecycleOwner) {
                if (emit) {
                    liveData.observeAsState()
                }
            }
        }

        val initialCount = runOnIdle { lifecycleOwner.lifecycle.observerCount }

        runOnIdle { emit = true }

        assertThat(runOnIdle { lifecycleOwner.lifecycle.observerCount }).isEqualTo(initialCount + 1)

        runOnIdle { emit = false }

        assertThat(runOnIdle { lifecycleOwner.lifecycle.observerCount }).isEqualTo(initialCount)
    }

    @Test
    fun noUpdatesWhenActivityStopped() {
        val liveData = MutableLiveData<String>()
        var realValue: String? = null
        val lifecycleOwner = runOnUiThread { RegistryOwner() }
        rule.setContent {
            Providers(LifecycleOwnerAmbient provides lifecycleOwner) {
                realValue = liveData.observeAsState().value
            }
        }

        runOnIdle {
            // activity stopped
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.CREATED
        }

        runOnIdle {
            liveData.postValue("value2")
        }

        runOnIdle {
            assertThat(realValue).isNull()
        }

        runOnIdle {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value2")
        }
    }

    @Test
    fun initialValueIsUpdatedWithTheRealOneRightAfterIfLifecycleIsStarted() {
        val liveData = MutableLiveData<String>()
        liveData.postValue("value")
        var realValue: String? = "to-be-updated"
        val lifecycleOwner = runOnUiThread {
            RegistryOwner().apply {
                lifecycle.currentState = Lifecycle.State.STARTED
            }
        }
        rule.setContent {
            Providers(LifecycleOwnerAmbient provides lifecycleOwner) {
                realValue = liveData.observeAsState(null).value
            }
        }

        assertThat(realValue).isEqualTo("value")
    }

    @Test
    fun currentValueIsUsedWhenWeHadRealAndDidntHaveInitialInCreated() {
        val liveData = MutableLiveData<String>()
        liveData.postValue("value")
        var realValue = "to-be-updated"
        val lifecycleOwner = runOnUiThread {
            RegistryOwner().apply {
                lifecycle.currentState = Lifecycle.State.CREATED
            }
        }
        rule.setContent {
            Providers(LifecycleOwnerAmbient provides lifecycleOwner) {
                realValue = liveData.observeAsState().value!!
            }
        }

        assertThat(realValue).isEqualTo("value")
    }
}

private class RegistryOwner : LifecycleOwner {
    var registry = LifecycleRegistry(this).also {
        it.currentState = Lifecycle.State.RESUMED
    }
    override fun getLifecycle() = registry
}
