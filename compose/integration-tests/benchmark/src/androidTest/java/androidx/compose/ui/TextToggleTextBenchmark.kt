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

package androidx.compose.ui

import androidx.test.filters.SmallTest
import androidx.ui.benchmark.ComposeBenchmarkRule
import androidx.ui.benchmark.toggleStateBenchmarkDraw
import androidx.ui.benchmark.toggleStateBenchmarkLayout
import androidx.ui.benchmark.toggleStateBenchmarkMeasure
import androidx.ui.benchmark.toggleStateBenchmarkRecompose
import androidx.ui.integration.test.TextBenchmarkTestRule
import androidx.ui.integration.test.core.text.TextToggleTextTestCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SmallTest
@RunWith(Parameterized::class)
class TextToggleTextBenchmark(
    private val textLength: Int
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "length={0}")
        fun initParameters(): Array<Any> = arrayOf(32, 512)
    }

    @get:Rule
    val textBenchmarkRule = TextBenchmarkTestRule()

    @get:Rule
    val benchmarkRule = ComposeBenchmarkRule()

    private val width = textBenchmarkRule.widthDp.dp
    private val fontSize = textBenchmarkRule.fontSizeSp.sp

    private val caseFactory = {
        textBenchmarkRule.generator { generator ->
            TextToggleTextTestCase(
                textGenerator = generator,
                textLength = textLength,
                textNumber = textBenchmarkRule.repeatTimes,
                width = width,
                fontSize = fontSize
            )
        }
    }

    /**
     * Measure the time taken to recompose the [Text] composable when text gets toggled.
     */
    @Test
    fun toggleText_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose(caseFactory)
    }

    /**
     * Measure the time taken to measure the [Text] composable when text gets toggled.
     */
    @Test
    fun toggleText_measure() {
        benchmarkRule.toggleStateBenchmarkMeasure(caseFactory)
    }

    /**
     * Measure the time taken to layout the [Text] composable when text gets toggled.
     */
    @Test
    fun toggleText_layout() {
        benchmarkRule.toggleStateBenchmarkLayout(caseFactory)
    }

    /**
     * Measure the time taken to draw the [Text] composable when text gets toggled.
     */
    @Test
    fun toggleText_draw() {
        benchmarkRule.toggleStateBenchmarkDraw(caseFactory)
    }
}