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

@file:Suppress("PLUGIN_ERROR")
package androidx.compose.runtime

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.platform.setViewContent
import androidx.compose.ui.platform.subcomposeInto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class TestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            id = ROOT_ID
        })
    }
}
@Suppress("DEPRECATION")
fun makeTestActivityRule() = androidx.test.rule.ActivityTestRule(TestActivity::class.java)

private val ROOT_ID = 18284847

internal val Activity.root get() = findViewById(ROOT_ID) as ViewGroup

internal fun Activity.uiThread(block: () -> Unit) {
    val latch = CountDownLatch(1)
    var throwable: Throwable? = null
    runOnUiThread(object : Runnable {
        override fun run() {
            try {
                block()
            } catch (e: Throwable) {
                throwable = e
            } finally {
                latch.countDown()
            }
        }
    })

    val completed = latch.await(5, TimeUnit.SECONDS)
    if (!completed) error("UI thread work did not complete within 5 seconds")
    throwable?.let {
        throw when (it) {
            is AssertionError -> AssertionError(it.localizedMessage, it)
            else ->
                IllegalStateException(
                    "UI thread threw an exception: ${it.localizedMessage}",
                    it
                )
        }
    }
}

internal fun Activity.show(block: @Composable () -> Unit): Composition {
    var composition: Composition? = null
    uiThread {
        @OptIn(ExperimentalComposeApi::class)
        Snapshot.sendApplyNotifications()
        @Suppress("DEPRECATION")
        composition = setViewContent(block)
    }
    return composition!!
}

internal fun Activity.waitForAFrame() {
    if (Looper.getMainLooper() == Looper.myLooper()) {
        throw Exception("Cannot be run from the main thread")
    }
    val latch = CountDownLatch(1)
    uiThread {
        Choreographer.getInstance().postFrameCallback { latch.countDown() }
    }
    assertTrue(latch.await(1, TimeUnit.HOURS), "Time-out waiting for choreographer frame")
}

abstract class BaseComposeTest {

    @Suppress("DEPRECATION")
    abstract val activityRule: androidx.test.rule.ActivityTestRule<TestActivity>

    val activity get() = activityRule.activity

    fun compose(
        composable: @Composable () -> Unit
    ) = ComposeTester(
        activity,
        composable
    )

    @Composable
    fun subCompose(block: @Composable () -> Unit) {
        @OptIn(ExperimentalLayoutNodeApi::class)
        val container = remember { LayoutNode() }
        val reference = compositionReference()
        // TODO(b/150390669): Review use of @ComposableContract(tracked = false)
        @OptIn(ExperimentalComposeApi::class)
        subcomposeInto(
            container,
            Recomposer.current(),
            reference
        ) @ComposableContract(tracked = false) {
            block()
        }
    }
}

class ComposeTester(val activity: Activity, val composable: @Composable () -> Unit) {
    inner class ActiveTest(val activity: Activity, val composition: Composition) {
        fun then(block: ActiveTest.(activity: Activity) -> Unit): ActiveTest {
            activity.waitForAFrame()
            activity.uiThread {
                block(activity)
            }
            return this
        }

        fun done() {
            activity.waitForAFrame()
        }
    }

    private fun initialComposition(composable: @Composable () -> Unit): Composition {
        return activity.show {
            Providers(
                ContextAmbient provides activity
            ) {
                composable()
            }
        }
    }

    fun then(block: ComposeTester.(activity: Activity) -> Unit): ActiveTest {
        val composition = initialComposition(composable)
        activity.waitForAFrame()
        activity.uiThread {
            block(activity)
        }
        return ActiveTest(activity, composition)
    }
}
