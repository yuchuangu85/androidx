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

package androidx.compose.runtime.benchmark

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotReadObserver
import androidx.compose.runtime.snapshots.SnapshotWriteObserver
import androidx.compose.runtime.snapshots.takeMutableSnapshot
import androidx.compose.ui.platform.AndroidOwner
import androidx.compose.ui.platform.setContent
import org.junit.Assert.assertTrue
import org.junit.Rule

@OptIn(ExperimentalComposeApi::class, InternalComposeApi::class)
abstract class ComposeBenchmarkBase {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Suppress("DEPRECATION")
    @get:Rule
    val activityRule = androidx.test.rule.ActivityTestRule(ComposeActivity::class.java)

    fun measureCompose(block: @Composable () -> Unit) {
        val activity = activityRule.activity
        var composition: Composition? = null
        benchmarkRule.measureRepeated {
            composition = activity.setContent(Recomposer.current(), block)

            runWithTimingDisabled {
                composition?.dispose()
            }
        }
        composition?.dispose()
    }

    fun measureRecompose(block: RecomposeReceiver.() -> Unit) {
        val receiver = RecomposeReceiver()
        receiver.block()
        var activeComposer: Composer<*>? = null

        val activity = activityRule.activity

        val composition = activity.setContent {
            activeComposer = currentComposer
            receiver.composeCb()
        }

        val composer = activeComposer
        require(composer != null) { "Composer was null" }
        val readObserver: SnapshotReadObserver = { composer.recordReadOf(it) }
        val writeObserver: SnapshotWriteObserver = { composer.recordWriteOf(it) }
        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                receiver.updateModelCb()
                Snapshot.sendApplyNotifications()
            }
            val didSomething = composer.performRecompose(readObserver, writeObserver)
            assertTrue(didSomething)
            runWithTimingDisabled {
                receiver.resetCb()
                Snapshot.sendApplyNotifications()
                composer.performRecompose(readObserver, writeObserver)
            }
        }

        composition.dispose()
    }
}

@OptIn(ExperimentalComposeApi::class, InternalComposeApi::class)
fun Composer<*>.performRecompose(
    readObserver: SnapshotReadObserver,
    writeObserver: SnapshotWriteObserver
): Boolean {
    val snapshot = takeMutableSnapshot(readObserver, writeObserver)
    val result = snapshot.enter {
        recompose().also { applyChanges() }
    }
    snapshot.apply().check()
    return result
}

class RecomposeReceiver {
    var composeCb: @Composable () -> Unit = @Composable { }
    var updateModelCb: () -> Unit = { }
    var resetCb: () -> Unit = {}

    fun compose(block: @Composable () -> Unit) {
        composeCb = block
    }

    fun reset(block: () -> Unit) {
        resetCb = block
    }

    fun update(block: () -> Unit) {
        updateModelCb = block
    }
}

// TODO(chuckj): Consider refacgtoring to use AndroidTestCaseRunner from UI
// This code is copied from AndroidTestCaseRunner.kt
private fun findComposeView(activity: Activity): AndroidOwner? {
    return findComposeView(activity.findViewById(android.R.id.content) as ViewGroup)
}

private fun findComposeView(view: View): AndroidOwner? {
    if (view is AndroidOwner) {
        return view
    }

    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val composeView = findComposeView(view.getChildAt(i))
            if (composeView != null) {
                return composeView
            }
        }
    }
    return null
}