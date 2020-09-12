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
import androidx.compose.runtime.EmbeddingContext
import androidx.compose.runtime.EmbeddingContextFactory
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.DesktopOwner
import androidx.compose.ui.platform.DesktopOwners
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skija.Surface
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.Component
import java.util.LinkedList

actual fun createComposeRule(
    disableTransitions: Boolean,
    disableBlinkingCursor: Boolean
): ComposeTestRuleJUnit {
    return DesktopComposeTestRule(
        disableTransitions,
        disableBlinkingCursor
    )
}

class DesktopComposeTestRule(
    private val disableTransitions: Boolean = false,
    private val disableBlinkingCursor: Boolean = true
) : ComposeTestRuleJUnit, EmbeddingContext {

    companion object {
        init {
            initCompose()
        }

        var current: DesktopComposeTestRule? = null
    }

    var owners: DesktopOwners? = null

    override val clockTestRule: AnimationClockTestRule = DesktopAnimationClockTestRule()

    override val density: Density
        get() = TODO()

    override val displaySize: IntSize get() = IntSize(1024, 768)

    val executionQueue = LinkedList<() -> Unit>()

    override fun apply(base: Statement, description: Description?): Statement {
        current = this
        return object : Statement() {
            override fun evaluate() {
                EmbeddingContextFactory = fun() = this@DesktopComposeTestRule
                base.evaluate()
                runExecutionQueue()
            }
        }
    }

    private fun runExecutionQueue() {
        while (executionQueue.isNotEmpty()) {
            executionQueue.removeFirst()()
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    private fun isIdle() =
                !Snapshot.current.hasPendingChanges() &&
                !Recomposer.current().hasPendingChanges()

    override fun waitForIdle() {
        while (!isIdle()) {
            runExecutionQueue()
            Thread.sleep(10)
        }
    }

    override fun <T> runOnUiThread(action: () -> T): T {
        return action()
    }

    override fun <T> runOnIdle(action: () -> T): T {
        // Method below make sure that compose is idle.
        waitForIdle()
        return action()
    }

    override fun setContent(composable: @Composable () -> Unit) {
        val surface = Surface.makeRasterN32Premul(displaySize.width, displaySize.height)
        val canvas = surface.canvas
        val component = object : Component() {}
        val owners = DesktopOwners(component = component, invalidate = {}).also {
            owners = it
        }
        val owner = DesktopOwner(owners)
        owner.setContent(composable)
        owner.setSize(displaySize.width, displaySize.height)
        owner.draw(canvas)
    }

    override fun isMainThread() = true
    override fun mainThreadCompositionContext() = Dispatchers.Default
}