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

package androidx.ui.test

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.AndroidOwner
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.ui.test.android.AndroidOwnerRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@LargeTest
class AndroidOwnerRegistryTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val onRegistrationChangedListener =
        object : AndroidOwnerRegistry.OnRegistrationChangedListener {
            val recordedChanges = mutableListOf<Pair<AndroidOwner, Boolean>>()
            override fun onRegistrationChanged(owner: AndroidOwner, registered: Boolean) {
                recordedChanges.add(Pair(owner, registered))
            }
        }

    @Before
    fun setUp() {
        assertThat(AndroidOwnerRegistry.isSetUp).isFalse()
        assertThat(AndroidOwnerRegistry.getUnfilteredOwners()).isEmpty()
        AndroidOwnerRegistry.setupRegistry()
        AndroidOwnerRegistry.addOnRegistrationChangedListener(onRegistrationChangedListener)
    }

    @After
    fun tearDown() {
        AndroidOwnerRegistry.removeOnRegistrationChangedListener(onRegistrationChangedListener)
        AndroidOwnerRegistry.tearDownRegistry()
        assertThat(AndroidOwnerRegistry.isSetUp).isFalse()
        assertThat(AndroidOwnerRegistry.getUnfilteredOwners()).isEmpty()
    }

    @Test
    fun registryIsSetUpAndEmpty() {
        assertThat(AndroidOwnerRegistry.isSetUp).isTrue()
        assertThat(AndroidOwnerRegistry.getUnfilteredOwners()).isEmpty()
    }

    @Test
    fun registerOwner() {
        activityRule.scenario.onActivity { activity ->
            // Given an owner
            val owner = AndroidOwner(activity)

            // When we add it to the view hierarchy
            activity.setContentView(owner.view)

            // Then it is registered
            assertThat(AndroidOwnerRegistry.getUnfilteredOwners()).isEqualTo(setOf(owner))
            assertThat(AndroidOwnerRegistry.getOwners()).isEqualTo(setOf(owner))
            // And our listener was notified
            assertThat(onRegistrationChangedListener.recordedChanges).isEqualTo(
                listOf(Pair(owner, true))
            )
        }
    }

    @Test
    fun unregisterOwner() {
        activityRule.scenario.onActivity { activity ->
            // Given an owner
            val owner = AndroidOwner(activity)

            // When we add it to the view hierarchy
            activity.setContentView(owner.view)
            // And remove it again
            activity.setContentView(View(activity))

            // Then it is not registered now
            assertThat(AndroidOwnerRegistry.getUnfilteredOwners()).isEmpty()
            assertThat(AndroidOwnerRegistry.getOwners()).isEmpty()
            // But our listener was notified of addition and removal
            assertThat(onRegistrationChangedListener.recordedChanges).isEqualTo(
                listOf(
                    Pair(owner, true),
                    Pair(owner, false)
                )
            )
        }
    }

    @Test
    fun tearDownRegistry() {
        activityRule.scenario.onActivity { activity ->
            // Given an owner that is registered in the registry
            val owner = AndroidOwner(activity)
            activity.setContentView(owner.view)

            // When we tear down the registry
            AndroidOwnerRegistry.tearDownRegistry()

            // Then the registry is empty
            assertThat(AndroidOwnerRegistry.getUnfilteredOwners()).isEmpty()
            assertThat(AndroidOwnerRegistry.getOwners()).isEmpty()
            // And our listener was notified of addition and removal
            assertThat(onRegistrationChangedListener.recordedChanges).isEqualTo(
                listOf(
                    Pair(owner, true),
                    Pair(owner, false)
                )
            )
        }
    }
}