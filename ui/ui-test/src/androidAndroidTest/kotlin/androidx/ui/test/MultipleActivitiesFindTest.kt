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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test

class MultipleActivitiesFindTest {

    @get:Rule
    val rule = createAndroidComposeRule<Activity1>(disableTransitions = true)

    @Test
    fun test() {
        rule.activityRule.scenario.onActivity { it.startNewActivity() }
        rule.onNodeWithTag("activity1").assertDoesNotExist()
        rule.onNodeWithTag("activity2").assertExists()
    }

    class Activity1 : TaggedActivity("activity1")
    class Activity2 : TaggedActivity("activity2")

    open class TaggedActivity(private val tag: String) : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
                Box(Modifier.testTag(tag))
            }
        }

        fun startNewActivity() {
            startActivity(Intent(this, Activity2::class.java))
        }
    }
}
