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

package androidx.compose.runtime.savedinstancestate

import androidx.compose.runtime.MutableState
import androidx.test.filters.MediumTest
import androidx.ui.test.StateRestorationTester
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
class SavedInstanceStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val restorationTester = StateRestorationTester(composeTestRule)

    @Test
    fun simpleRestore() {
        var state: MutableState<Int>? = null
        restorationTester.setContent {
            state = savedInstanceState { 0 }
        }

        runOnUiThread {
            assertThat(state!!.value).isEqualTo(0)

            state!!.value = 1
            // we null it to ensure recomposition happened
            state = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        runOnUiThread {
            assertThat(state!!.value).isEqualTo(1)
        }
    }

    @Test
    fun restoreWithSaver() {
        var state: MutableState<Holder>? = null
        restorationTester.setContent {
            state = savedInstanceState(saver = HolderSaver) {
                Holder(0)
            }
        }

        runOnIdle {
            assertThat(state!!.value).isEqualTo(Holder(0))

            state!!.value.value = 1
            // we null it to ensure recomposition happened
            state = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        runOnIdle {
            assertThat(state!!.value).isEqualTo(Holder(1))
        }
    }

    @Test
    fun nullableStateRestoresNonNullValue() {
        var state: MutableState<String?>? = null
        restorationTester.setContent {
            state = savedInstanceState<String?> { null }
        }

        runOnUiThread {
            assertThat(state!!.value).isNull()

            state!!.value = "value"
            // we null it to ensure recomposition happened
            state = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        runOnUiThread {
            assertThat(state!!.value).isEqualTo("value")
        }
    }

    @Test
    fun nullableStateRestoresNullValue() {
        var state: MutableState<String?>? = null
        restorationTester.setContent {
            state = savedInstanceState<String?> { "initial" }
        }

        runOnUiThread {
            assertThat(state!!.value).isEqualTo("initial")

            state!!.value = null
            // we null it to ensure recomposition happened
            state = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        runOnUiThread {
            assertThat(state!!.value).isNull()
        }
    }
}
