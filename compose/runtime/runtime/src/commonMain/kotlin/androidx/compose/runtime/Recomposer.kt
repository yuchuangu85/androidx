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

@file:OptIn(
    ExperimentalComposeApi::class,
    InternalComposeApi::class
)
package androidx.compose.runtime

import androidx.compose.runtime.dispatch.BroadcastFrameClock
import androidx.compose.runtime.dispatch.DefaultMonotonicFrameClock
import androidx.compose.runtime.dispatch.MonotonicFrameClock
import androidx.compose.runtime.snapshots.MutableSnapshot
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotApplyObserver
import androidx.compose.runtime.snapshots.SnapshotApplyResult
import androidx.compose.runtime.snapshots.SnapshotReadObserver
import androidx.compose.runtime.snapshots.SnapshotWriteObserver
import androidx.compose.runtime.snapshots.takeMutableSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Runs [block] with a new, active [Recomposer] applying changes in the calling [CoroutineContext].
 */
suspend fun withRunningRecomposer(
    block: suspend CoroutineScope.(recomposer: Recomposer) -> Unit
): Unit = coroutineScope {
    val recomposer = Recomposer()
    val recompositionJob = launch { recomposer.runRecomposeAndApplyChanges() }
    block(recomposer)
    recompositionJob.cancel()
}

/**
 * The scheduler for performing recomposition and applying updates to one or more [Composition]s.
 * [frameClock] is used to align changes with display frames.
 */
class Recomposer(var embeddingContext: EmbeddingContext = EmbeddingContext()) {

    /**
     * This collection is its own lock, shared with [invalidComposersAwaiter]
     */
    private val invalidComposers = mutableSetOf<Composer<*>>()

    /**
     * The continuation to resume when there are invalid composers to process.
     */
    private var invalidComposersAwaiter: Continuation<Unit>? = null

    /**
     * Track if any outstanding invalidated composers are awaiting recomposition.
     * This latch is closed any time we resume invalidComposersAwaiter and opened
     * by [recomposeAndApplyChanges] when it suspends when it has no further work to do.
     */
    private val idlingLatch = Latch()

    /**
     * Enforces that only one caller of [runRecomposeAndApplyChanges] is active at a time
     * while carrying its calling scope. Used to [launchEffect] on the apply dispatcher.
     */
    // TODO(adamp) convert to atomicfu once ready
    private val applyingScope = AtomicReference<CoroutineScope?>(null)

    private val broadcastFrameClock = BroadcastFrameClock {
        synchronized(invalidComposers) {
            invalidComposersAwaiter?.let {
                invalidComposersAwaiter = null
                idlingLatch.closeLatch()
                it.resume(Unit)
            }
        }
    }
    val frameClock: MonotonicFrameClock get() = broadcastFrameClock

    /**
     * Await the invalidation of any associated [Composer]s, recompose them, and apply their
     * changes to their associated [Composition]s if recomposition is successful.
     *
     * While [runRecomposeAndApplyChanges] is running, [awaitIdle] will suspend until there are no
     * more invalid composers awaiting recomposition.
     *
     * This method never returns. Cancel the calling [CoroutineScope] to stop.
     */
    suspend fun runRecomposeAndApplyChanges(): Nothing {
        coroutineScope {
            recomposeAndApplyChanges(this, Long.MAX_VALUE)
        }
        error("this function never returns")
    }

    /**
     * Await the invalidation of any associated [Composer]s, recompose them, and apply their
     * changes to their associated [Composition]s if recomposition is successful. Any launched
     * effects of composition will be launched into the receiver [CoroutineScope].
     *
     * While [runRecomposeAndApplyChanges] is running, [awaitIdle] will suspend until there are no
     * more invalid composers awaiting recomposition.
     *
     * This method returns after recomposing [frameCount] times.
     */
    suspend fun recomposeAndApplyChanges(
        applyCoroutineScope: CoroutineScope,
        frameCount: Long
    ) {
        var framesRemaining = frameCount
        val toRecompose = mutableListOf<Composer<*>>()

        if (!applyingScope.compareAndSet(null, applyCoroutineScope)) {
            error("already recomposing and applying changes")
        }

        // Cache this so we don't go looking for it each time through the loop.
        val frameClock = coroutineContext[MonotonicFrameClock] ?: DefaultMonotonicFrameClock

        try {
            idlingLatch.closeLatch()
            while (frameCount == Long.MAX_VALUE || framesRemaining-- > 0L) {
                // Don't hold the monitor lock across suspension.
                val hasInvalidComposers = synchronized(invalidComposers) {
                    invalidComposers.isNotEmpty()
                }
                if (!hasInvalidComposers && !broadcastFrameClock.hasAwaiters) {
                    // Suspend until we have something to do
                    suspendCancellableCoroutine<Unit> { co ->
                        synchronized(invalidComposers) {
                            if (invalidComposers.isEmpty()) {
                                invalidComposersAwaiter = co
                                idlingLatch.openLatch()
                            } else {
                                // We raced and lost, someone invalidated between our check
                                // and suspension. Resume immediately.
                                co.resume(Unit)
                                return@suspendCancellableCoroutine
                            }
                        }
                        co.invokeOnCancellation {
                            synchronized(invalidComposers) {
                                if (invalidComposersAwaiter === co) {
                                    invalidComposersAwaiter = null
                                }
                            }
                        }
                    }
                }

                // Align work with the next frame to coalesce changes.
                // Note: it is possible to resume from the above with no recompositions pending,
                // instead someone might be awaiting our frame clock dispatch below.
                frameClock.withFrameNanos { frameTime ->
                    trace("recomposeFrame") {
                        // Propagate the frame time to anyone who is awaiting from the
                        // recomposer clock.
                        broadcastFrameClock.sendFrame(frameTime)

                        // Ensure any global changes are observed
                        Snapshot.sendApplyNotifications()

                        // ...and make sure we know about any pending invalidations the commit
                        // may have caused before recomposing - Handler messages can't run between
                        // input processing and the frame clock pulse!
                        FrameManager.synchronize()

                        // ...and pick up any stragglers as a result of the above snapshot sync
                        synchronized(invalidComposers) {
                            toRecompose.addAll(invalidComposers)
                            invalidComposers.clear()
                        }

                        if (toRecompose.isNotEmpty()) {
                            for (i in 0 until toRecompose.size) {
                                performRecompose(toRecompose[i])
                            }
                            toRecompose.clear()
                        }
                    }
                }
            }
        } finally {
            applyingScope.set(null)
            // If we're not still running frames, we're effectively idle.
            idlingLatch.openLatch()
        }
    }

    private class CompositionCoroutineScopeImpl(
        override val coroutineContext: CoroutineContext,
        frameClock: MonotonicFrameClock
    ) : CompositionCoroutineScope(), MonotonicFrameClock by frameClock

    /**
     * Implementation note: we launch effects undispatched so they can begin immediately during
     * the apply step. This function is only called internally by [launchInComposition]
     * implementations during [CompositionLifecycleObserver] callbacks dispatched on the
     * applying scope, so we consider this safe.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun launchEffect(
        block: suspend CompositionCoroutineScope.() -> Unit
    ): Job = applyingScope.get()?.launch(start = CoroutineStart.UNDISPATCHED) {
        CompositionCoroutineScopeImpl(coroutineContext, frameClock).block()
    } ?: error("apply scope missing; runRecomposeAndApplyChanges must be running")

    // TODO this is temporary until more of this logic moves to Composition
    internal val applyingCoroutineContext: CoroutineContext?
        get() = applyingScope.get()?.coroutineContext

    internal fun composeInitial(
        composable: @Composable () -> Unit,
        composer: Composer<*>
    ) {
        if (composer.disposeHook == null) {
            // This will eventually move to the recomposer once it tracks active compositions.
            // After this is moved the disposeHook should be removed as well.
            composer.disposeHook = Snapshot.registerApplyObserver(applyObserverOf(composer))
        }

        val composerWasComposing = composer.isComposing
        composing(composer) {
            composer.composeInitial(composable)
        }
        // TODO(b/143755743)
        if (!composerWasComposing) {
            Snapshot.notifyObjectsInitialized()
        }
        composer.applyChanges()

        if (!composerWasComposing) {
            // Ensure that any state objects created during applyChanges are seen as changed
            // if modified after this call.
            Snapshot.notifyObjectsInitialized()
        }
    }

    private fun performRecompose(composer: Composer<*>): Boolean {
        if (composer.isComposing) return false
        return composing(composer) {
            composer.recompose().also {
                composer.applyChanges()
            }
        }
    }

    private fun readObserverOf(composer: Composer<*>): SnapshotReadObserver {
        return { value -> composer.recordReadOf(value) }
    }

    private fun writeObserverOf(composer: Composer<*>): SnapshotWriteObserver {
        return { value -> composer.recordWriteOf(value) }
    }

    private fun applyObserverOf(composer: Composer<*>): SnapshotApplyObserver {
        return { values, _ ->
            if (embeddingContext.isMainThread())
                composer.recordModificationsOf(values)
            else {
                FrameManager.schedule {
                    composer.recordModificationsOf(values)
                }
            }
        }
    }

    private inline fun <T> composing(composer: Composer<*>, block: () -> T): T {
        val snapshot = takeMutableSnapshot(
            readObserverOf(composer), writeObserverOf(composer))
        try {
            return snapshot.enter(block)
        } finally {
            applyAndCheck(snapshot)
        }
    }

    private fun applyAndCheck(snapshot: MutableSnapshot) {
        val applyResult = snapshot.apply()
        if (applyResult is SnapshotApplyResult.Failure) {
            error("Unsupported concurrent change during composition. A state object was " +
                    "modified by composition as well as being modified outside composition.")
            // TODO(chuckj): Consider lifting this restriction by forcing a recompose
        }
    }

    fun hasPendingChanges(): Boolean =
        !idlingLatch.isOpen || synchronized(invalidComposers) { invalidComposers.isNotEmpty() }

    internal fun scheduleRecompose(composer: Composer<*>) {
        synchronized(invalidComposers) {
            invalidComposers.add(composer)
            invalidComposersAwaiter?.let {
                invalidComposersAwaiter = null
                idlingLatch.closeLatch()
                it.resume(Unit)
            }
        }
    }

    /**
     * Suspends until the currently pending recomposition frame is complete.
     * Any recomposition for this recomposer triggered by actions before this call begins
     * will be complete and applied (if recomposition was successful) when this call returns.
     *
     * If [runRecomposeAndApplyChanges] is not currently running the [Recomposer] is considered idle
     * and this method will not suspend.
     */
    suspend fun awaitIdle(): Unit = idlingLatch.await()

    companion object {
        private val embeddingContext by lazy { EmbeddingContext() }
        /**
         * Retrieves [Recomposer] for the current thread. Needs to be the main thread.
         */
        @TestOnly
        fun current(): Recomposer {
            return mainRecomposer ?: run {
                val mainScope = CoroutineScope(NonCancellable +
                        embeddingContext.mainThreadCompositionContext())

                Recomposer(embeddingContext).also {
                    mainRecomposer = it
                    @OptIn(ExperimentalCoroutinesApi::class)
                    mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        it.runRecomposeAndApplyChanges()
                    }
                }
            }
        }

        private var mainRecomposer: Recomposer? = null
    }
}