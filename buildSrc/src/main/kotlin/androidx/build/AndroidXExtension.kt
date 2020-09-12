/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.build

import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.util.ArrayList

/**
 * Extension for [AndroidXPlugin] that's responsible for holding configuration options.
 */
open class AndroidXExtension(val project: Project) {
    var name: String? = null
    var mavenVersion: Version? = null
        set(value) {
            field = value
            chooseProjectVersion()
        }
    var mavenGroup: LibraryGroup? = null
        set(value) {
            field = value
            chooseProjectVersion()
        }

    private fun chooseProjectVersion() {
        val version: Version
        val group: String? = mavenGroup?.group
        val groupVersion: Version? = mavenGroup?.forcedVersion
        val mavenVersion: Version? = mavenVersion
        if (mavenVersion != null) {
            if (groupVersion != null && !isGroupVersionOverrideAllowed()) {
                throw GradleException("Cannot set mavenVersion (" + mavenVersion +
                    ") for a project (" + project +
                    ") whose mavenGroup already specifies forcedVersion (" + groupVersion +
                ")")
            } else {
                version = mavenVersion
            }
        } else {
            if (groupVersion != null) {
                version = groupVersion
            } else {
                return
            }
        }
        if (group != null) {
            project.group = group
        }
        project.version = if (isSnapshotBuild()) version.copy(extra = "-SNAPSHOT") else version
        versionIsSet = true
    }

    private fun isGroupVersionOverrideAllowed(): Boolean {
        // Grant an exception to the same-version-group policy for artifacts that haven't shipped a
        // stable API surface, e.g. 1.0.0-alphaXX, to allow for rapid early-stage development.
        val version = mavenVersion
        return version != null && version.major == 1 && version.minor == 0 && version.patch == 0 &&
                version.isAlpha()
    }

    private var versionIsSet = false
    fun isVersionSet(): Boolean {
        return versionIsSet
    }
    var description: String? = null
    var inceptionYear: String? = null
    /**
     * targetsJavaConsumers = true, if project is intended to be accessed from Java-language
     * source code.
     */
    var targetsJavaConsumers = true
        get() {
            when (project.path) {
                // add per-project overrides here
                // for example
                // the following project is intended to be accessed from Java
                // ":compose:internal-lint-checks" -> return true
                // the following project is not intended to be accessed from Java
                // ":annotation:annotation" -> return false
            }
            if (project.path.contains("-ktx")) return false
            if (project.path.startsWith(":compose")) return false
            if (project.path.startsWith(":ui")) return false
            return field
        }
    private var licenses: MutableCollection<License> = ArrayList()
    var publish: Publish = Publish.NONE

    /**
     * Whether to run API tasks such as tracking and linting. The default value is
     * [RunApiTasks.Auto], which automatically picks based on the project's properties.
     */
    var runApiTasks: RunApiTasks = RunApiTasks.Auto
    var failOnDeprecationWarnings = true
    var compilationTarget: CompilationTarget = CompilationTarget.DEVICE

    /**
     * It disables docs generation and api tracking for tooling modules like annotation processors.
     * We don't expect such modules to be used by developers as libraries, so we don't guarantee
     * any api stability and don't expose any docs about them.
     */
    var toolingProject = false

    /**
     * Disables just docs generation for modules that are published and should have their API
     * tracked to ensure intra-library versioning compatibility, but are not expected to be
     * directly used by developers.
     */
    var generateDocs = true
        get() {
            if (toolingProject) return false
            if (!publish.shouldRelease()) return false
            return field
        }

    fun license(closure: Closure<*>): License {
        val license = project.configure(License(), closure) as License
        licenses.add(license)
        return license
    }

    fun getLicenses(): Collection<License> {
        return licenses
    }

    companion object {
        const val DEFAULT_UNSPECIFIED_VERSION = "unspecified"
    }
}

enum class CompilationTarget {
    /** This library is meant to run on the host machine (like an annotation processor). */
    HOST,
    /** This library is meant to run on an Android device. */
    DEVICE
}

/**
 * Publish Enum:
 * Publish.NONE -> Generates no aritfacts; does not generate snapshot artifacts
 *                 or releasable maven artifacts
 * Publish.SNAPSHOT_ONLY -> Only generates snapshot artifacts
 * Publish.SNAPSHOT_AND_RELEASE -> Generates both snapshot artifacts and releasable maven artifact
*/
enum class Publish {
    NONE, SNAPSHOT_ONLY, SNAPSHOT_AND_RELEASE;

    fun shouldRelease() = this == SNAPSHOT_AND_RELEASE
    fun shouldPublish() = this == SNAPSHOT_ONLY || this == SNAPSHOT_AND_RELEASE
}

sealed class RunApiTasks {
    /** Automatically determine whether API tasks should be run. */
    object Auto : RunApiTasks()
    /** Always run API tasks regardless of other project properties. */
    data class Yes(val reason: String? = null) : RunApiTasks()
    /** Do not run any API tasks. */
    data class No(val reason: String) : RunApiTasks()
}

class License {
    var name: String? = null
    var url: String? = null
}
