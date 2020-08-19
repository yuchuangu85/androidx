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

package androidx.ui.benchmark.test

import androidx.compose.foundation.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.filters.LargeTest
import androidx.ui.benchmark.ComposeBenchmarkRule
import androidx.ui.benchmark.benchmarkFirstCompose
import androidx.ui.benchmark.toggleStateBenchmarkRecompose
import androidx.ui.test.ToggleableTestCase
import androidx.ui.test.ComposeTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class TrailingLambdaBenchmark {
    @get:Rule
    val benchmarkRule = ComposeBenchmarkRule()

    @Test
    fun withTrailingLambdas_compose() {
        benchmarkRule.benchmarkFirstCompose { WithTrailingLambdas() }
    }

    @Test
    fun withTrailingLambdas_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose { WithTrailingLambdas() }
    }

    @Test
    fun withoutTrailingLambdas_compose() {
        benchmarkRule.benchmarkFirstCompose { WithoutTrailingLambdas() }
    }

    @Test
    fun withoutTrailingLambdas_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose { WithoutTrailingLambdas() }
    }
}

private sealed class TrailingLambdaTestCase() : ComposeTestCase, ToggleableTestCase {

    var numberState: MutableState<Int>? = null

    @Composable
    override fun emitContent() {
        val number = remember { mutableStateOf(5) }
        numberState = number

        val content = @Composable {
            Box(Modifier.preferredWidth(10.dp))
        }

        Column {
            repeat(10) {
                emitContent(number = number.value, content = content)
            }
        }
    }

    override fun toggleState() {
        with(numberState!!) {
            value = if (value == 5) 10 else 5
        }
    }

    @Composable
    abstract fun emitContent(number: Int, content: @Composable () -> Unit)
}

private class WithTrailingLambdas : TrailingLambdaTestCase() {
    @Composable
    override fun emitContent(number: Int, content: @Composable () -> Unit) {
        EmptyComposable(number = number) {
            content()
        }
    }
}

private class WithoutTrailingLambdas : TrailingLambdaTestCase() {
    @Composable
    override fun emitContent(number: Int, content: @Composable () -> Unit) {
        EmptyComposable(number = number, children = content)
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun EmptyComposable(number: Int, children: @Composable () -> Unit) {
}
