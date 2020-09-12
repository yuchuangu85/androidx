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

package androidx.compose.runtime

import android.view.Choreographer
import androidx.compose.runtime.dispatch.MonotonicFrameClock
import androidx.compose.runtime.dispatch.withFrameNanos
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@MediumTest
@RunWith(AndroidJUnit4::class)
class SuspendingEffectsTests : BaseComposeTest() {

    @After
    fun teardown() {
        clearRoots()
    }

    @get:Rule
    override val activityRule = makeTestActivityRule()

    @Test
    fun testLaunchInComposition() {
        var counter by mutableStateOf(0)

        // Used as a signal that launchInComposition will await
        val ch = Channel<Unit>(Channel.CONFLATED)
        compose {
            launchInComposition {
                counter++
                ch.receive()
                counter++
                ch.receive()
                counter++
            }
        }.then {
            assertEquals(1, counter)
            ch.offer(Unit)
        }.then {
            assertEquals(2, counter)
            ch.offer(Unit)
        }.then {
            assertEquals(3, counter)
        }
    }

    @Test
    fun testAwaitFrameFromLaunchInComposition() {
        var choreographerTime by mutableStateOf(Long.MIN_VALUE)
        var awaitFrameTime by mutableStateOf(Long.MAX_VALUE)
        compose {
            launchInComposition {
                withFrameNanos {
                    awaitFrameTime = it
                }
            }
            onCommit(true) {
                Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
                    choreographerTime = frameTimeNanos
                }
            }
        }.then {
            assertNotEquals(choreographerTime, Long.MIN_VALUE, "Choreographer callback never ran")
            assertNotEquals(awaitFrameTime, Long.MAX_VALUE, "awaitFrameNanos callback never ran")
            assertEquals(choreographerTime, awaitFrameTime,
                "expected same values from choreographer post and awaitFrameNanos")
        }
    }

    @Test
    fun testAwaitFrameFromRememberCoroutineScope() {
        var choreographerTime by mutableStateOf(Long.MIN_VALUE)
        var awaitFrameTime by mutableStateOf(Long.MAX_VALUE)
        compose {
            val scope = rememberCoroutineScope()
            onCommit(true) {
                scope.launch {
                    withFrameNanos {
                        awaitFrameTime = it
                    }
                }
                Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
                    choreographerTime = frameTimeNanos
                }
            }
        }.then {
            assertNotEquals(choreographerTime, Long.MIN_VALUE, "Choreographer callback never ran")
            assertNotEquals(awaitFrameTime, Long.MAX_VALUE, "awaitFrameNanos callback never ran")
            assertEquals(choreographerTime, awaitFrameTime,
                "expected same values from choreographer post and awaitFrameNanos")
        }
    }

    @Test
    fun testRememberCoroutineScopeActiveWithComposition() {
        lateinit var coroutineScope: CoroutineScope
        val tester = compose {
            coroutineScope = rememberCoroutineScope()
        }.then {
            assertTrue(coroutineScope.isActive, "coroutine scope was active before dispose")
        }
        val composition = tester.composition
        tester.then {
            composition.dispose()
            assertFalse(coroutineScope.isActive, "coroutine scope was inactive after dispose")
        }
    }

    @Test
    fun testRememberCoroutineScopeActiveAfterLeave() {
        var coroutineScope: CoroutineScope? = null
        var toggle by mutableStateOf(true)
        compose {
            if (toggle) {
                coroutineScope = rememberCoroutineScope()
            }
        }.then {
            assertTrue(coroutineScope?.isActive == true,
                "coroutine scope should be active after initial composition")
        }.then {
            toggle = false
        }.then {
            assertTrue(coroutineScope?.isActive == false,
                "coroutine scope should be cancelled after leaving composition")
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    @Test
    fun testCoroutineScopesHaveCorrectFrameClock() {
        var recomposerClock: MonotonicFrameClock? = null
        var launchInCompositionClock: MonotonicFrameClock? = null
        var rememberCoroutineScopeFrameClock: MonotonicFrameClock? = null

        compose {
            recomposerClock = currentComposer.recomposer.frameClock
            launchInComposition {
                launchInCompositionClock = coroutineContext[MonotonicFrameClock]
            }
            val rememberedScope = rememberCoroutineScope()
            onCommit {
                rememberCoroutineScopeFrameClock =
                    rememberedScope.coroutineContext[MonotonicFrameClock]
            }
        }.then {
            assertNotNull(recomposerClock, "Recomposer frameClock")
            assertSame(recomposerClock, launchInCompositionClock, "launchInComposition clock")
            assertSame(recomposerClock, rememberCoroutineScopeFrameClock,
                "rememberCoroutineScope clock")
        }
    }

    @Test
    fun testLaunchInCompositionRunsAfter() {
        var onCommitRan = false
        var launchRanAfter = false
        compose {
            // Confirms that these run "out of order" with respect to one another because
            // the launch runs dispatched.
            launchInComposition {
                launchRanAfter = onCommitRan
            }
            onCommit {
                onCommitRan = true
            }
        }.then {
            assertTrue(launchRanAfter, "expected launchInComposition to run after later onCommit")
        }
    }

    /*
    // Forced to disable test due to a bug in the Kotlin compiler
    // which caused this function to fail to build due to invalid bytecode
    // Build fails with AnalyzerException: Incompatible stack heights
    @Test
    fun testRememberCoroutineScopeDisallowsParentJob() {
        var coroutineScope: CoroutineScope? = null
        compose {
            coroutineScope = rememberCoroutineScope { Job() }
        }.then {
            val scope = coroutineScope
            assertNotNull(scope, "received non-null coroutine scope")
            val job = scope.coroutineContext[Job]
            assertNotNull(job, "scope context contains a job")
            assertTrue(job.isCompleted, "job is complete")
            var cause: Throwable? = null
            job.invokeOnCompletion { cause = it }
            assertTrue(cause is IllegalArgumentException,
                "scope Job should be failed with IllegalArgumentException")
        }
    }
    */
}
