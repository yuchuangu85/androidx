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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry
import androidx.compose.runtime.savedinstancestate.UiSavedStateRegistryAmbient
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.runtime.savedinstancestate.savedInstanceState

/**
 * Helps to test the state restoration for your Composable component.
 *
 * Instead of calling [ComposeTestRuleJUnit.setContent] you need to use [setContent] on this object,
 * then change your state so there is some change to be restored, then execute
 * [emulateSavedInstanceStateRestore] and assert your state is restored properly.
 *
 * Note that this tests only the restoration of the local state of the composable you passed to
 * [setContent] and useful for testing [savedInstanceState] or [rememberSavedInstanceState]
 * integration. It is not testing the integration with any other life cycles or Activity callbacks.
 */
class StateRestorationTester(private val composeTestRule: ComposeTestRuleJUnit) {

    private var registry: RestorationRegistry? = null

    /**
     * This functions is a direct replacement for [ComposeTestRuleJUnit.setContent] if you are going
     * to use [emulateSavedInstanceStateRestore] in the test.
     *
     * @see ComposeTestRuleJUnit.setContent
     */
    fun setContent(composable: @Composable () -> Unit) {
        composeTestRule.setContent {
            InjectRestorationRegistry { registry ->
                this.registry = registry
                composable()
            }
        }
    }

    /**
     * Saves all the state stored via [savedInstanceState] or [rememberSavedInstanceState],
     * disposes current composition, and composes again the content passed to [setContent].
     * Allows to test how your component behaves when the state restoration is happening.
     * Note that the state stored via regular state() or remember() will be lost.
     */
    fun emulateSavedInstanceStateRestore() {
        val registry = checkNotNull(registry) {
            "setContent should be called first!"
        }
        composeTestRule.runOnIdle {
            registry.saveStateAndDisposeChildren()
        }
        composeTestRule.runOnIdle {
            registry.emitChildrenWithRestoredState()
        }
        composeTestRule.runOnIdle {
            // we just wait for the children to be emitted
        }
    }

    @Composable
    private fun InjectRestorationRegistry(children: @Composable (RestorationRegistry) -> Unit) {
        val original = requireNotNull(UiSavedStateRegistryAmbient.current) {
            "StateRestorationTester requires composeTestRule.setContent() to provide " +
                    "an UiSavedStateRegistry implementation via UiSavedStateRegistryAmbient"
        }
        val restorationRegistry = remember { RestorationRegistry(original) }
        Providers(UiSavedStateRegistryAmbient provides restorationRegistry) {
            if (restorationRegistry.shouldEmitChildren) {
                children(restorationRegistry)
            }
        }
    }

    private class RestorationRegistry(private val original: UiSavedStateRegistry) :
        UiSavedStateRegistry {

        var shouldEmitChildren by mutableStateOf(true)
            private set
        private var currentRegistry: UiSavedStateRegistry = original
        private var savedMap: Map<String, List<Any?>> = emptyMap()

        fun saveStateAndDisposeChildren() {
            savedMap = currentRegistry.performSave()
            shouldEmitChildren = false
        }

        fun emitChildrenWithRestoredState() {
            currentRegistry = UiSavedStateRegistry(
                restoredValues = savedMap,
                canBeSaved = { original.canBeSaved(it) }
            )
            shouldEmitChildren = true
        }

        override fun consumeRestored(key: String) = currentRegistry.consumeRestored(key)

        override fun registerProvider(key: String, valueProvider: () -> Any?) =
            currentRegistry.registerProvider(key, valueProvider)

        override fun unregisterProvider(key: String, valueProvider: () -> Any?) =
            currentRegistry.unregisterProvider(key, valueProvider)

        override fun canBeSaved(value: Any) = currentRegistry.canBeSaved(value)

        override fun performSave() = currentRegistry.performSave()
    }
}