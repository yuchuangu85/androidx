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

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.ui.test.android.AndroidOwnerRegistry
import androidx.ui.test.android.FirstDrawRegistry
import androidx.ui.test.android.registerComposeWithEspresso
import androidx.ui.test.android.unregisterComposeFromEspresso
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.animation.transitionsEnabled
import androidx.compose.ui.platform.setContent
import androidx.compose.foundation.InternalFoundationApi
import androidx.compose.foundation.blinkingCursorEnabled
import androidx.compose.ui.text.input.textInputServiceFactory
import androidx.compose.ui.unit.Density
import org.junit.runner.Description
import org.junit.runners.model.Statement
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ui.test.android.SynchronizedTreeCollector
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

actual fun createComposeRule(
    disableTransitions: Boolean,
    disableBlinkingCursor: Boolean
): ComposeTestRuleJUnit =
    createAndroidComposeRule<ComponentActivity>(
        disableTransitions,
        disableBlinkingCursor
    )

/**
 * Factory method to provide android specific implementation of [createComposeRule], for a given
 * activity class type [T].
 *
 * This method is useful for tests that require a custom Activity. This is usually the case for
 * app tests. Make sure that you add the provided activity into your app's manifest file (usually
 * in main/AndroidManifest.xml).
 *
 * If you don't care about specific activity and just want to test composables in general, see
 * [createComposeRule].
 */
inline fun <reified T : ComponentActivity> createAndroidComposeRule(
    disableTransitions: Boolean = false,
    disableBlinkingCursor: Boolean = true
): AndroidComposeTestRule<T> {
    // TODO(b/138993381): By launching custom activities we are losing control over what content is
    //  already there. This is issue in case the user already set some compose content and decides
    //  to set it again via our API. In such case we won't be able to dispose the old composition.
    //  Other option would be to provide a smaller interface that does not expose these methods.
    return AndroidComposeTestRule(
        activityRule = ActivityScenarioRule(T::class.java),
        disableTransitions = disableTransitions,
        disableBlinkingCursor = disableBlinkingCursor
    )
}

/**
 * Factory method to provide android specific implementation of [createComposeRule], for a given
 * [activityClass].
 *
 * This method is useful for tests that require a custom Activity. This is usually the case for
 * app tests. Make sure that you add the provided activity into your app's manifest file (usually
 * in main/AndroidManifest.xml).
 *
 * If you don't care about specific activity and just want to test composables in general, see
 * [createComposeRule].
 */
fun <T : ComponentActivity> createAndroidComposeRule(
    activityClass: Class<T>,
    disableTransitions: Boolean = false,
    disableBlinkingCursor: Boolean = true
): AndroidComposeTestRule<T> = AndroidComposeTestRule(
    ActivityScenarioRule(activityClass),
    disableTransitions,
    disableBlinkingCursor
)

/**
 * Android specific implementation of [ComposeTestRule].
 */
class AndroidComposeTestRule<T : ComponentActivity>(
    // TODO(b/153623653): Remove activityRule from arguments when AndroidComposeTestRule can
    //  work with any kind of Activity launcher.
    val activityRule: ActivityScenarioRule<T>,
    private val disableTransitions: Boolean = false,
    private val disableBlinkingCursor: Boolean = true
) : ComposeTestRuleJUnit {

    private fun getActivity(): T {
        var activity: T? = null
        if (activity == null) {
            activityRule.scenario.onActivity { activity = it }
            if (activity == null) {
                throw IllegalStateException("Activity was not set in the ActivityScenarioRule!")
            }
        }
        return activity!!
    }

    override val clockTestRule: AnimationClockTestRule = AndroidAnimationClockTestRule()

    internal var disposeContentHook: (() -> Unit)? = null

    override val density: Density get() =
        Density(getActivity().resources.displayMetrics.density)

    override val displaySize by lazy {
        getActivity().resources.displayMetrics.let {
            IntSize(it.widthPixels, it.heightPixels)
        }
    }

    override fun apply(base: Statement, description: Description?): Statement {
        val activityTestRuleStatement = activityRule.apply(base, description)
        val composeTestRuleStatement = AndroidComposeStatement(activityTestRuleStatement)
        return clockTestRule.apply(composeTestRuleStatement, description)
    }

    /**
     * @throws IllegalStateException if called more than once per test.
     */
    @SuppressWarnings("SyntheticAccessor")
    override fun setContent(composable: @Composable () -> Unit) {
        check(disposeContentHook == null) {
            "Cannot call setContent twice per test!"
        }

        lateinit var activity: T
        activityRule.scenario.onActivity { activity = it }

        runOnUiThread {
            val composition = activity.setContent(
                Recomposer.current(),
                composable
            )
            disposeContentHook = {
                composition.dispose()
            }
        }

        if (!isOnUiThread()) {
            // Only wait for idleness if not on the UI thread. If we are on the UI thread, the
            // caller clearly wants to keep tight control over execution order, so don't go
            // executing future tasks on the main thread.
            waitForIdle()
        }
    }

    override fun waitForIdle() {
        SynchronizedTreeCollector.waitForIdle()
    }

    @SuppressLint("DocumentExceptions")
    override fun <T> runOnUiThread(action: () -> T): T {
        if (isOnUiThread()) {
            return action()
        }

        // Note: This implementation is directly taken from ActivityTestRule
        val task: FutureTask<T> = FutureTask(action)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        try {
            return task.get()
        } catch (e: ExecutionException) { // Expose the original exception
            throw e.cause!!
        }
    }

    override fun <T> runOnIdle(action: () -> T): T {
        // Method below make sure that compose is idle.
        waitForIdle()
        // Execute the action on ui thread in a blocking way.
        return runOnUiThread(action)
    }

    inner class AndroidComposeStatement(
        private val base: Statement
    ) : Statement() {
        override fun evaluate() {
            val oldTextInputFactory = @Suppress("DEPRECATION_ERROR")(textInputServiceFactory)
            beforeEvaluate()
            try {
                base.evaluate()
            } finally {
                afterEvaluate()
                @Suppress("DEPRECATION_ERROR")
                textInputServiceFactory = oldTextInputFactory
            }
        }

        @OptIn(InternalFoundationApi::class)
        private fun beforeEvaluate() {
            transitionsEnabled = !disableTransitions
            blinkingCursorEnabled = !disableBlinkingCursor
            AndroidOwnerRegistry.setupRegistry()
            FirstDrawRegistry.setupRegistry()
            registerComposeWithEspresso()
            @Suppress("DEPRECATION_ERROR")
            textInputServiceFactory = {
                TextInputServiceForTests(it)
            }
        }

        @OptIn(InternalFoundationApi::class)
        private fun afterEvaluate() {
            transitionsEnabled = true
            blinkingCursorEnabled = true
            AndroidOwnerRegistry.tearDownRegistry()
            FirstDrawRegistry.tearDownRegistry()
            unregisterComposeFromEspresso()
            // Dispose the content
            if (disposeContentHook != null) {
                runOnUiThread {
                    // NOTE: currently, calling dispose after an exception that happened during
                    // composition is not a safe call. Compose runtime should fix this, and then
                    // this call will be okay. At the moment, however, calling this could
                    // itself produce an exception which will then obscure the original
                    // exception. To fix this, we will just wrap this call in a try/catch of
                    // its own
                    try {
                        disposeContentHook!!()
                    } catch (e: Exception) {
                        // ignore
                    }
                    disposeContentHook = null
                }
            }
        }
    }
}
