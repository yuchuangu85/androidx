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

package androidx.compose.material.icons.generator.tasks

import androidx.compose.material.icons.generator.CoreIcons
import androidx.compose.material.icons.generator.IconWriter
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * Task responsible for converting core icons from xml to a programmatic representation.
 */
open class CoreIconGenerationTask : IconGenerationTask() {
    override fun run() =
        IconWriter(loadIcons()).generateTo(generatedSrcMainDirectory) { it in CoreIcons }

    companion object {
        /**
         * Registers [CoreIconGenerationTask] in [project].
         */
        fun register(project: Project) {
            val task = project.createGenerationTask(
                "generateCoreIcons",
                CoreIconGenerationTask::class.java
            )
            registerIconGenerationTask(project, task)
        }
    }
}

/**
 * Task responsible for converting extended icons from xml to a programmatic representation.
 */
open class ExtendedIconGenerationTask : IconGenerationTask() {
    override fun run() =
        IconWriter(loadIcons()).generateTo(generatedSrcMainDirectory) { it !in CoreIcons }

    companion object {
        /**
         * Registers [ExtendedIconGenerationTask] in [project].
         */
        fun register(project: Project) {
            val task = project.createGenerationTask(
                "generateExtendedIcons",
                ExtendedIconGenerationTask::class.java
            )
            registerIconGenerationTask(project, task)
        }
    }
}

/**
 * Helper to register [task] as the Kotlin source generating task for [project].
 */
private fun registerIconGenerationTask(
    project: Project,
    task: IconGenerationTask
) {
    val sourceSet = project.getMultiplatformSourceSet(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
    sourceSet.kotlin.srcDir(project.files(task.generatedSrcMainDirectory).builtBy(task))
}
