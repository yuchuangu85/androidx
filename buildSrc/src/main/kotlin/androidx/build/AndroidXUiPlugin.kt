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

package androidx.build

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Plugin to apply options across all of the androidx.ui projects
 */
class AndroidXUiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.all { plugin ->
            when (plugin) {
                is LibraryPlugin -> {
                    val library = project.extensions.findByType(LibraryExtension::class.java)
                        ?: throw Exception("Failed to find Android extension")

                    library.defaultConfig.minSdkVersion(21)

                    // TODO(148540713): remove this exclusion when Lint can support using multiple lint jars
                    project.configurations.getByName("lintChecks").exclude(
                        mapOf("module" to "lint-checks")
                    )
                    // TODO: figure out how to apply this to multiplatform modules
                    project.dependencies.add(
                        "lintChecks",
                        project.dependencies.project(mapOf(
                            "path" to ":compose:internal-lint-checks", "configuration" to "shadow"
                        ))
                    )

                    library.lintOptions.apply {
                        // Too many Kotlin features require synthetic accessors - we want to rely on R8 to
                        // remove these accessors
                        disable("SyntheticAccessor")
                    }
                }
                is KotlinBasePluginWrapper -> {
                    val conf = project.configurations.create("kotlinPlugin")

                    project.tasks.withType(KotlinCompile::class.java).configureEach { compile ->
                        // TODO(b/157230235): remove when this is enabled by default
                        compile.kotlinOptions.freeCompilerArgs +=
                            "-Xopt-in=kotlin.RequiresOptIn"
                        compile.dependsOn(conf)
                        compile.doFirst {
                            if (!conf.isEmpty) {
                                compile.kotlinOptions.freeCompilerArgs +=
                                    "-Xplugin=${conf.files.first()}"
                            }
                        }
                    }

                    if (plugin is KotlinMultiplatformPluginWrapper) {
                        project.configureForMultiplatform()
                    }
                }
            }
        }
    }
}

/**
 * General configuration for MPP projects. In the future, these workarounds should either be
 * generified and added to AndroidXPlugin, or removed as/when the underlying issues have been
 * resolved.
 */
private fun Project.configureForMultiplatform() {
    val libraryExtension = project.extensions.findByType<LibraryExtension>() ?: return

    // TODO: b/148416113: AGP doesn't know about Kotlin-MPP's sourcesets yet, so add
    // them to its source directories (this fixes lint, and code completion in
    // Android Studio on versions >= 4.0canary8)
    libraryExtension.apply {
        sourceSets.findByName("main")?.apply {
            java.srcDirs("src/commonMain/kotlin", "src/jvmMain/kotlin",
                "src/androidMain/kotlin")
            res.srcDirs("src/androidMain/res")
        }
        sourceSets.findByName("test")?.apply {
            java.srcDirs("src/test/kotlin")
            res.srcDirs("src/test/res")
        }
        sourceSets.findByName("androidTest")?.apply {
            java.srcDirs("src/androidAndroidTest/kotlin")
            res.srcDirs("src/androidAndroidTest/res")
        }
    }

    /*
    The following configures source sets - note:

    1. The common unit test source set, commonTest, is included by default in both android
    unit and instrumented tests. This causes unnecessary duplication, so we explicitly do
    _not_ use commonTest, instead choosing to just use the unit test variant.
    TODO: Consider using commonTest for unit tests if a usable feature is added for
    https://youtrack.jetbrains.com/issue/KT-34662.

    2. The default (android) unit test source set is named 'androidTest', which conflicts / is
    confusing as this shares the same name / expected directory as AGP's 'androidTest', which
    represents _instrumented_ tests.
    TODO: Consider changing unitTest to androidLocalTest and androidAndroidTest to
    androidDeviceTest when https://github.com/JetBrains/kotlin/pull/2829 rolls in.
    */
    multiplatformExtension!!.sourceSets {
        // Allow all experimental APIs, since MPP projects are themselves experimental
        (this as DomainObjectCollection<KotlinSourceSet>).all {
            it.languageSettings.apply {
                useExperimentalAnnotation("kotlin.Experimental")
                useExperimentalAnnotation("kotlin.ExperimentalMultiplatform")
            }
        }
    }
}
