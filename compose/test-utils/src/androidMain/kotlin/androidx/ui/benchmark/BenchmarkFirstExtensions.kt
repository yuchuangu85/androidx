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

package androidx.ui.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ui.test.ComposeExecutionControl
import androidx.ui.test.ComposeTestCase
import androidx.ui.test.LayeredComposeTestCase
import androidx.ui.test.assertNoPendingChanges
import androidx.ui.test.doFramesUntilNoChangesPending
import org.junit.Assert.assertTrue

/**
 * Measures the time of the first composition right after the given test case is added to an
 * already existing hierarchy.
 */
fun ComposeBenchmarkRule.benchmarkFirstComposeFast(caseFactory: () -> LayeredComposeTestCase) {
    runBenchmarkFor(LayeredCaseAdapter.of(caseFactory)) {
        measureRepeated {
            runWithTimingDisabled {
                doFramesUntilNoChangesPending()
                // Add the content to benchmark
                getTestCase().addMeasuredContent()
            }

            recomposeUntilNoChangesPending()

            runWithTimingDisabled {
                disposeContent()
            }
        }
    }
}

/**
 * Measures the time of the first measure right after the given test case is added to an already
 * existing hierarchy.
 */
fun ComposeBenchmarkRule.benchmarkFirstMeasureFast(caseFactory: () -> LayeredComposeTestCase) {
    runBenchmarkFor(LayeredCaseAdapter.of(caseFactory)) {
        measureRepeated {
            runWithTimingDisabled {
                doFramesUntilNoChangesPending()
                // Add the content to benchmark
                getTestCase().addMeasuredContent()
                recomposeUntilNoChangesPending()
                requestLayout()
            }

            measure()

            runWithTimingDisabled {
                assertNoPendingChanges()
                disposeContent()
            }
        }
    }
}

/**
 * Measures the time of the first layout right after the given test case is added to an already
 * existing hierarchy.
 */
fun ComposeBenchmarkRule.benchmarkFirstLayoutFast(caseFactory: () -> LayeredComposeTestCase) {
    runBenchmarkFor(LayeredCaseAdapter.of(caseFactory)) {
        measureRepeated {
            runWithTimingDisabled {
                doFramesUntilNoChangesPending()
                // Add the content to benchmark
                getTestCase().addMeasuredContent()
                recomposeUntilNoChangesPending()
                requestLayout()
                measure()
            }

            layout()

            runWithTimingDisabled {
                assertNoPendingChanges()
                disposeContent()
            }
        }
    }
}

/**
 * Measures the time of the first draw right after the given test case is added to an already
 * existing hierarchy.
 */
fun ComposeBenchmarkRule.benchmarkFirstDrawFast(caseFactory: () -> LayeredComposeTestCase) {
    runBenchmarkFor(LayeredCaseAdapter.of(caseFactory)) {
        measureRepeated {
            runWithTimingDisabled {
                doFramesUntilNoChangesPending()
                // Add the content to benchmark
                getTestCase().addMeasuredContent()
                recomposeUntilNoChangesPending()
                requestLayout()
                measure()
                layout()
                drawPrepare()
            }

            draw()

            runWithTimingDisabled {
                drawFinish()
                assertNoPendingChanges()
                disposeContent()
            }
        }
    }
}

/**
 * Runs recompositions until there are no changes pending.
 *
 * @param maxAmountOfStep Max amount of recomposition to perform before giving up and throwing
 * exception.
 * @throws AssertionError if there are still pending changes after [maxAmountOfStep] executed.
 */
fun ComposeExecutionControl.recomposeUntilNoChangesPending(maxAmountOfStep: Int = 10): Int {
    var stepsDone = 0
    while (stepsDone < maxAmountOfStep) {
        recompose()
        stepsDone++
        if (!hasPendingChanges()) {
            // We are stable!
            return stepsDone
        }
    }

    // Still not stable
    throw AssertionError("Changes are still pending after '$maxAmountOfStep' " +
            "frames.")
}

private class LayeredCaseAdapter(private val innerCase: LayeredComposeTestCase) : ComposeTestCase {

    companion object {
        fun of(caseFactory: () -> LayeredComposeTestCase): () -> LayeredCaseAdapter = {
            LayeredCaseAdapter(caseFactory())
        }
    }

    var isComposed by mutableStateOf(false)

    @Composable
    override fun emitContent() {
        innerCase.emitContentWrappers {
            if (isComposed) {
                innerCase.emitMeasuredContent()
            }
        }
    }

    fun addMeasuredContent() {
        assertTrue(!isComposed)
        isComposed = true
    }
}