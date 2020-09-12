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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Stack
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.platform.setContent
import androidx.test.filters.MediumTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class CustomActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Stack {
                    Button(onClick = {}) {
                        Text("Hello")
                    }
                }
            }
        }
    }
}

/**
 * Tests that we can launch custom activities via [createAndroidComposeRule].
 */
@MediumTest
@RunWith(JUnit4::class)
class CustomActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<CustomActivity>()

    @Test
    fun launchCustomActivity() {
        rule.onNodeWithText("Hello").assertExists()
    }
}
