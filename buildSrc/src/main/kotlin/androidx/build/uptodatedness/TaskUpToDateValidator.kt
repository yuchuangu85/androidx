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

package androidx.build.uptodatedness

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.util.Date

/**
 * Validates that all tasks (except a temporary exception list) are considered up-to-date.
 * The expected usage of this is that the user will invoke a build with the
 * TaskUpToDateValidator disabled, and then reinvoke the same build with the TaskUpToDateValidator
 * enabled. If the second build actually runs any tasks, then some tasks don't have the correct
 * inputs/outputs declared and are running more often than necessary.
 */

const val DISALLOW_TASK_EXECUTION_FLAG_NAME = "disallowExecution"
const val RECORD_FLAG_NAME = "verifyUpToDate"

// Temporary list of exempt tasks that are known to still be out-of-date after running once
// Entries in this set may be task names (like assembleDebug) or task paths
// (like :core:core:assembleDebug)
val EXEMPT_TASKS = setOf(
    "buildOnServer",
    "checkExternalLicenses",
    "createArchive",
    "createDiffArchiveForAll",
    "createProjectZip",
    "desugarPublicDebugFileDependencies",
    "desugarTipOfTreeDebugFileDependencies",
    "distPublicDokkaDocs",
    "dokkaJavaPublicDocs",
    "dokkaKotlinPublicDocs",
    "externalNativeBuildDebug",
    "externalNativeBuildRelease",
    "generateJsonModelDebug",
    "generateJsonModelRelease",
    "generateMetadataFileForDesktopPublication",
    "generateMetadataFileForMavenPublication",
    "generateMetadataFileForMetadataPublication",
    "generateMetadataFileForKotlinMultiplatformPublication",
    "generateMetadataFileForPluginMavenPublication",
    "generatePomFileForBenchmarkPluginMarkerMavenPublication",
    "generatePomFileForDesktopPublication",
    "generatePomFileForKotlinMultiplatformPublication",
    "generatePomFileForMavenPublication",
    "generatePomFileForPluginMavenPublication",
    "generatePomFileForMetadataPublication",
    "generatePomFileForSafeargsJavaPluginMarkerMavenPublication",
    "generatePomFileForSafeargsKotlinPluginMarkerMavenPublication",
    "jacocoPublicDebug",
    "jacocoTipOfTreeDebug",
    "lint",
    "lintDebug",
    "lintVitalRelease",
    "partiallyDejetifyArchive",
    "postInstrumentCode",
    "publishBenchmarkPluginMarkerMavenPublicationToMavenRepository",
    "publishDesktopPublicationToMavenRepository",
    "publishKotlinMultiplatformPublicationToMavenRepository",
    "publishMavenPublicationToMavenRepository",
    "publishMetadataPublicationToMavenRepository",
    "publishPluginMavenPublicationToMavenRepository",
    "publishSafeargsJavaPluginMarkerMavenPublicationToMavenRepository",
    "publishSafeargsKotlinPluginMarkerMavenPublicationToMavenRepository",
    /**
     * relocateShadowJar is used to configure the ShadowJar hence it does not have any outputs.
     * https://github.com/johnrengelman/shadow/issues/561
     */
    "relocateShadowJar",
    "reportLibraryMetrics",
    "stripArchiveForPartialDejetification",
    "transformClassesWithDexBuilderForPublicDebug",
    "transformClassesWithDexBuilderForTipOfTreeDebug",
    "unzipDokkaPublicDocsDeps",
    "verifyDependencyVersions",
    "verifyReleaseResources",
    "zipEcFiles",
    "zipTestConfigsWithApks",

    ":camera:integration-tests:camera-testapp-uiwidgets:mergeLibDexDebug",
    ":camera:integration-tests:camera-testapp-uiwidgets:packageDebug",
    ":camera:integration-tests:camera-testapp-view:mergeLibDexDebug",
    ":camera:integration-tests:camera-testapp-view:packageDebug"
)
class TaskUpToDateValidator {
    companion object {

        private val BUILD_START_TIME_KEY = "taskUpToDateValidatorSetupTime"

        private fun shouldRecord(project: Project): Boolean {
            return project.hasProperty(RECORD_FLAG_NAME) && !shouldValidate(project)
        }

        private fun shouldValidate(project: Project): Boolean {
            return project.hasProperty(DISALLOW_TASK_EXECUTION_FLAG_NAME)
        }

        private fun isExemptTask(task: Task): Boolean {
            return EXEMPT_TASKS.contains(task.name) || EXEMPT_TASKS.contains(task.path)
        }

        private fun recordBuildStartTime(rootProject: Project) {
            rootProject.extra.set(BUILD_START_TIME_KEY, Date())
        }

        private fun getBuildStartTime(project: Project): Date {
            return project.rootProject.extra.get(BUILD_START_TIME_KEY) as Date
        }

        fun setup(rootProject: Project) {
            recordBuildStartTime(rootProject)
            if (shouldValidate(rootProject)) {
                val taskGraph = rootProject.gradle.taskGraph
                taskGraph.afterTask { task ->
                    if (task.didWork) {
                        if (!isExemptTask(task)) {
                            val message = "Ran two consecutive builds of the same tasks," +
                                " and in the second build, observed $task to be not UP-TO-DATE." +
                                " This indicates that $task does not declare" +
                                " inputs and/or outputs correctly.\n" +
                                tryToExplainTaskExecution(task, taskGraph)
                            throw GradleException(message)
                        }
                    }
                }
            }
            if (shouldRecord(rootProject)) {
                rootProject.gradle.taskGraph.afterTask { task ->
                    if (!isExemptTask(task)) {
                        recordTaskInputs(task)
                    }
                }
            }
        }
        fun recordTaskInputs(task: Task) {
            val text = task.inputs.files.files.joinToString("\n")
            val destFile = getTaskInputListPath(task)
            destFile.parentFile.mkdirs()
            destFile.writeText(text)
        }
        fun getTaskInputListPath(task: Task): File {
            return File(getTasksInputListPath(task.project), task.name)
        }
        fun getTasksInputListPath(project: Project): File {
            return File(project.buildDir, "TaskUpToDateValidator/inputs")
        }
        fun checkForChangingSetOfInputs(task: Task): String {
            val previousInputsFile = getTaskInputListPath(task)
            val previousInputs = previousInputsFile.readLines()
            val currentInputs = task.inputs.files.files.map { f -> f.toString() }
            val addedInputs = currentInputs.minus(previousInputs)
            val removedInputs = previousInputs.minus(currentInputs)
            val addedMessage = if (addedInputs.size > 0) {
                "Added these " + addedInputs.size + " inputs: " +
                    addedInputs.joinToString("\n") + "\n"
            } else {
                ""
            }
            val removedMessage = if (removedInputs.size > 0) {
                "Removed these " + removedInputs.size + " inputs: " +
                    removedInputs.joinToString("\n") + "\n"
            } else {
                ""
            }
            return addedMessage + removedMessage
        }
        fun tryToExplainTaskExecution(task: Task, taskGraph: TaskExecutionGraph): String {
            val numOutputFiles = task.outputs.files.files.size
            val outputsMessage = if (numOutputFiles > 0) {
                task.path + " declares " + numOutputFiles + " output files. This seems fine.\n"
            } else {
                task.path + " declares " + numOutputFiles + " output files. This is probably " +
                    "an error.\n"
            }

            val inputFiles = task.inputs.files.files
            var lastModifiedFile: File? = null
            var lastModifiedWhen = Date(0)
            for (inputFile in inputFiles) {
                val modifiedWhen = Date(inputFile.lastModified())
                if (modifiedWhen.compareTo(lastModifiedWhen) > 0) {
                    lastModifiedFile = inputFile
                    lastModifiedWhen = modifiedWhen
                }
            }

            val inputSetModifiedMessage = checkForChangingSetOfInputs(task)
            val inputsMessage = if (inputSetModifiedMessage != "") {
                inputSetModifiedMessage
            } else {
                if (lastModifiedFile != null) {
                    task.path + " declares " + inputFiles.size + " input files. The " +
                        "last modified input file is\n" + lastModifiedFile + "\nmodified at " +
                        lastModifiedWhen + " (this build started at about " +
                        getBuildStartTime(task.project) + "). " +
                        tryToExplainFileModification(lastModifiedFile, taskGraph)
                } else {
                    task.path + " declares " + inputFiles.size + " input files.\n"
                }
            }

            val reproductionMessage = "\nTo reproduce this error you can try running " +
                "`./gradlew ${task.path} -PverifyUpToDate`\n"
            val readLogsMessage = "\nYou can check why Gradle executed ${task.path} by " +
                "passing the '--info' flag to Gradle and then searching stdout for output " +
                "generated immediately before the task began to execute.\n" +
                "Our best guess for the reason that ${task.path} executed is below.\n"
            return readLogsMessage + outputsMessage + inputsMessage + reproductionMessage
        }

        fun getTaskDeclaringFile(file: File, taskGraph: TaskExecutionGraph): Task? {
            for (task in taskGraph.allTasks) {
                if (task.outputs.files.files.contains(file)) {
                    return task
                }
            }
            return null
        }
        fun tryToExplainFileModification(file: File, taskGraph: TaskExecutionGraph): String {
            // Find the task declaring this file as an output,
            // or the task declaring one of its parent dirs as an output
            var createdByTask: Task? = null
            var declaredFile: File? = file
            while (createdByTask == null && declaredFile != null) {
                createdByTask = getTaskDeclaringFile(declaredFile, taskGraph)
                declaredFile = declaredFile.parentFile
            }
            if (createdByTask == null) {
                return "This file is not declared as the output of any task in this build."
            }
            if (isExemptTask(createdByTask)) {
                return "This file is declared as an output of " + createdByTask +
                    ", which is a task that is not yet validated by the TaskUpToDateValidator"
            } else {
                return "This file is decared as an output of " + createdByTask +
                    ", which is a task that is validated by the TaskUpToDateValidator " +
                    "(and therefore must not have been out-of-date during this build)"
            }
        }
    }
}
