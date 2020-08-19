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

import androidx.benchmark.gradle.BenchmarkPlugin
import androidx.build.AndroidXPlugin.Companion.CHECK_RELEASE_READY_TASK
import androidx.build.AndroidXPlugin.Companion.TASK_TIMEOUT_MINUTES
import androidx.build.SupportConfig.BUILD_TOOLS_VERSION
import androidx.build.SupportConfig.COMPILE_SDK_VERSION
import androidx.build.SupportConfig.DEFAULT_MIN_SDK_VERSION
import androidx.build.SupportConfig.INSTRUMENTATION_RUNNER
import androidx.build.SupportConfig.TARGET_SDK_VERSION
import androidx.build.dependencyTracker.AffectedModuleDetector
import androidx.build.dokka.Dokka.configureAndroidProjectForDokka
import androidx.build.dokka.Dokka.configureJavaProjectForDokka
import androidx.build.gradle.getByType
import androidx.build.gradle.isRoot
import androidx.build.jacoco.Jacoco
import androidx.build.license.configureExternalDependencyLicenseCheck
import androidx.build.checkapi.JavaApiTaskConfig
import androidx.build.checkapi.LibraryApiTaskConfig
import androidx.build.checkapi.configureProjectForApiTasks
import androidx.build.studio.StudioTask
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.ApkVariant
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A plugin which enables all of the Gradle customizations for AndroidX.
 * This plugin reacts to other plugins being added and adds required and optional functionality.
 */
class AndroidXPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.isRoot) throw Exception("Root project should use AndroidXRootPlugin instead")
        // This has to be first due to bad behavior by DiffAndDocs which is triggered on the root
        // project. It calls evaluationDependsOn on each subproject. This eagerly causes evaluation
        // *during* the root build.gradle evaluation. The subproject then applies this plugin (while
        // we're still halfway through applying it on the root). The check licenses code runs on the
        // subproject which then looks for the root project task to add itself as a dependency of.
        // Without the root project having created the task prior to DiffAndDocs running this fails.
        // TODO(alanv): do not use evaluationDependsOn in DiffAndDocs to break this cycle!
        project.configureExternalDependencyLicenseCheck()

        val extension = project.extensions.create<AndroidXExtension>(EXTENSION_NAME, project)

        // This has to be first due to bad behavior by DiffAndDocs. It fails if this configuration
        // is called after DiffAndDocs.configureDiffAndDocs. b/129762955
        project.configureMavenArtifactUpload(extension)

        if (project.isCoverageEnabled()) {
            project.configureJacoco()
        }

        // Perform different actions based on which plugins have been applied to the project.
        // Many of the actions overlap, ex. API tracking and documentation.
        project.plugins.all { plugin ->
            when (plugin) {
                is JavaPlugin -> configureWithJavaPlugin(project, extension)
                is LibraryPlugin -> configureWithLibraryPlugin(project, extension)
                is AppPlugin -> configureWithAppPlugin(project, extension)
                is KotlinBasePluginWrapper -> configureWithKotlinPlugin(project, plugin)
            }
        }

        project.configureKtlint()

        // Configure all Jar-packing tasks for hermetic builds.
        project.tasks.withType(Jar::class.java).configureEach { it.configureForHermeticBuild() }

        project.tasks.withType(Copy::class.java).configureEach { it.configureForHermeticBuild() }

        // copy host side test results to DIST
        project.tasks.withType(Test::class.java) { task -> configureTestTask(project, task) }

        project.configureTaskTimeouts()
    }

    /**
     * Disables timestamps and ensures filesystem-independent archive ordering to maximize
     * cross-machine byte-for-byte reproducibility of artifacts.
     */
    private fun Jar.configureForHermeticBuild() {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
    }

    private fun Copy.configureForHermeticBuild() {
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    private fun configureTestTask(project: Project, task: Test) {
        AffectedModuleDetector.configureTaskGuard(task)

        // Enable tracing to see results in command line
        task.testLogging.apply {
            events = hashSetOf(
                TestLogEvent.FAILED, TestLogEvent.PASSED,
                TestLogEvent.SKIPPED, TestLogEvent.STANDARD_OUT
            )
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
        val report = task.reports.junitXml
        if (report.isEnabled) {
            val zipTask = project.tasks.register(
                "zipResultsOf${task.name.capitalize()}",
                Zip::class.java
            ) {
                it.destinationDirectory.set(project.getHostTestResultDirectory())
                it.archiveFileName.set("${project.asFilenamePrefix()}_${task.name}.zip")
            }
            if (project.hasProperty(TEST_FAILURES_DO_NOT_FAIL_TEST_TASK)) {
                task.ignoreFailures = true
            }
            task.finalizedBy(zipTask)
            task.doFirst {
                zipTask.configure {
                    it.from(report.destination)
                }
            }
        }
        task.systemProperty("robolectric.offline", "true")
        val robolectricDependencies =
            File(project.getPrebuiltsRoot(), "androidx/external/org/robolectric/android-all")
        task.systemProperty(
            "robolectric.dependency.dir",
            robolectricDependencies.absolutePath
        )
    }

    private fun configureWithKotlinPlugin(
        project: Project,
        plugin: KotlinBasePluginWrapper
    ) {
        project.tasks.withType(KotlinCompile::class.java).configureEach { task ->
            task.kotlinOptions.jvmTarget = "1.8"
            project.configureCompilationWarnings(task)
        }
        if (plugin is KotlinMultiplatformPluginWrapper) {
            project.extensions.findByType<LibraryExtension>()?.apply {
                configureAndroidLibraryWithMultiplatformPluginOptions()
            }
        }
    }

    private fun configureWithAppPlugin(project: Project, extension: AndroidXExtension) {
        val appExtension = project.extensions.getByType<AppExtension>().apply {
            configureAndroidCommonOptions(project, extension)
            configureAndroidApplicationOptions(project)
        }

        // TODO: Replace this with a per-variant packagingOption for androidTest specifically once
        //  b/69953968 is resolved.
        // Workaround for b/161465530 in AGP that fails to strip these <module>.kotlin_module files,
        // which causes mergeDebugAndroidTestJavaResource to fail for sample apps.
        appExtension.packagingOptions.exclude("/META-INF/*.kotlin_module")
        // Workaround a limitation in AGP that fails to merge these META-INF license files.
        appExtension.packagingOptions.pickFirst("/META-INF/AL2.0")
        // In addition to working around the above issue, we exclude the LGPL2.1 license as we're
        // approved to distribute code via AL2.0 and the only dependencies which pull in LGPL2.1
        // are currently dual-licensed with AL2.0 and LGPL2.1. The affected dependencies are:
        //   - net.java.dev.jna:jna:5.5.0
        appExtension.packagingOptions.exclude("/META-INF/LGPL2.1")
    }

    private fun configureWithLibraryPlugin(
        project: Project,
        androidXExtension: AndroidXExtension
    ) {
        val libraryExtension = project.extensions.getByType<LibraryExtension>().apply {
            configureAndroidCommonOptions(project, androidXExtension)
            configureAndroidLibraryOptions(project, androidXExtension)
        }
        libraryExtension.onVariants.withBuildType("release") {
            // Disable unit test for release build type
            unitTest {
                @Suppress("UnstableApiUsage")
                enabled = false
            }
        }
        libraryExtension.packagingOptions {
            // TODO: Replace this with a per-variant packagingOption for androidTest specifically
            //  once b/69953968 is resolved.
            // Workaround for b/161465530 in AGP that fails to merge these META-INF license files
            // for libraries that publish Java resources under the same name.
            pickFirst("/META-INF/AL2.0")
            // In addition to working around the above issue, we exclude the LGPL2.1 license as we're
            // approved to distribute code via AL2.0 and the only dependencies which pull in LGPL2.1
            // currently are dual-licensed with AL2.0 and LGPL2.1. The affected dependencies are:
            //   - net.java.dev.jna:jna:5.5.0
            exclude("/META-INF/LGPL2.1")

            // We need this as a work-around for b/155721209
            // It can be removed when we have a newer plugin version
            // 2nd workaround - this DSL was made saner in a breaking way which hasn't landed
            // yes in AGP 4.1, that will allow just excludes -= "...".
            // This reflection enables us to be source compatible with both for now.

            javaClass.getMethod("setExcludes", Set::class.java).invoke(this, excludes.also {
                it.remove("/META-INF/*.kotlin_module")
            })

            check(!excludes.contains("/META-INF/*.kotlin_module"))
        }

        project.configureSourceJarForAndroid(libraryExtension)
        project.configureVersionFileWriter(libraryExtension, androidXExtension)
        project.addCreateLibraryBuildInfoFileTask(androidXExtension)

        val verifyDependencyVersionsTask = project.createVerifyDependencyVersionsTask()
        val checkReleaseReadyTasks = mutableListOf<TaskProvider<out Task>>()
        if (verifyDependencyVersionsTask != null) {
            checkReleaseReadyTasks.add(verifyDependencyVersionsTask)
        }
        if (checkReleaseReadyTasks.isNotEmpty()) {
            project.createCheckReleaseReadyTask(checkReleaseReadyTasks)
        }

        val reportLibraryMetrics = project.tasks.register<ReportLibraryMetricsTask>(
            REPORT_LIBRARY_METRICS_TASK, ReportLibraryMetricsTask::class.java)
        project.addToBuildOnServer(reportLibraryMetrics)
        libraryExtension.defaultPublishVariant { libraryVariant ->
            reportLibraryMetrics.configure {
                it.jarFiles.from(libraryVariant.packageLibraryProvider.map { zip ->
                    zip.inputs.files
                })
            }

            verifyDependencyVersionsTask?.configure { task ->
                task.dependsOn(libraryVariant.javaCompileProvider)
            }

            libraryVariant.javaCompileProvider.configure { task ->
                project.configureCompilationWarnings(task)
            }
        }

        // Standard lint, docs, resource API, and Metalava configuration for AndroidX projects.
        project.configureAndroidProjectForLint(libraryExtension.lintOptions, androidXExtension)
        if (project.isDocumentationEnabled()) {
            project.configureAndroidProjectForDokka(libraryExtension, androidXExtension)
        }

        project.configureProjectForApiTasks(
            LibraryApiTaskConfig(libraryExtension),
            androidXExtension
        )

        project.addToProjectMap(androidXExtension)
    }

    private fun configureWithJavaPlugin(project: Project, extension: AndroidXExtension) {
        project.configureErrorProneForJava()
        project.configureSourceJarForJava()

        // Force Java 1.8 source- and target-compatibilty for all Java libraries.
        val convention = project.convention.getPlugin<JavaPluginConvention>()
        convention.apply {
            sourceCompatibility = VERSION_1_8
            targetCompatibility = VERSION_1_8
        }

        project.tasks.withType(JavaCompile::class.java) { task ->
            project.configureCompilationWarnings(task)
        }

        project.hideJavadocTask()

        val verifyDependencyVersionsTask = project.createVerifyDependencyVersionsTask()
        verifyDependencyVersionsTask?.configure { task ->
            task.dependsOn(project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME))
        }

        project.addCreateLibraryBuildInfoFileTask(extension)
        if (verifyDependencyVersionsTask != null) {
            project.createCheckReleaseReadyTask(listOf(verifyDependencyVersionsTask))
        }

        // Standard lint, docs, and Metalava configuration for AndroidX projects.
        project.configureNonAndroidProjectForLint(extension)
        if (project.isDocumentationEnabled()) {
            project.configureJavaProjectForDokka(extension)
        }

        project.configureProjectForApiTasks(
            JavaApiTaskConfig,
            extension
        )

        project.afterEvaluate {
            if (extension.publish.shouldRelease()) {
                project.extra.set("publish", true)
            }
        }

        // Workaround for b/120487939 wherein Gradle's default resolution strategy prefers external
        // modules with lower versions over local projects with higher versions.
        project.configurations.all { configuration ->
            configuration.resolutionStrategy.preferProjectModules()
        }

        project.addToProjectMap(extension)
    }

    private fun TestedExtension.configureAndroidCommonOptions(
        project: Project,
        androidXExtension: AndroidXExtension
    ) {
        compileOptions.apply {
            sourceCompatibility = VERSION_1_8
            targetCompatibility = VERSION_1_8
        }

        // Force AGP to use our version of JaCoCo
        jacoco.version = Jacoco.VERSION
        compileSdkVersion(COMPILE_SDK_VERSION)
        buildToolsVersion = BUILD_TOOLS_VERSION
        defaultConfig.targetSdkVersion(TARGET_SDK_VERSION)

        defaultConfig.testInstrumentationRunner = INSTRUMENTATION_RUNNER

        buildTypes.getByName("debug").isTestCoverageEnabled = project.isCoverageEnabled()

        testOptions.animationsDisabled = true
        testOptions.unitTests.isReturnDefaultValues = true

        defaultConfig.minSdkVersion(DEFAULT_MIN_SDK_VERSION)
        project.afterEvaluate {
            val minSdkVersion = defaultConfig.minSdkVersion!!.apiLevel
            check(minSdkVersion >= DEFAULT_MIN_SDK_VERSION) {
                "minSdkVersion $minSdkVersion lower than the default of $DEFAULT_MIN_SDK_VERSION"
            }
            project.configurations.all { configuration ->
                configuration.resolutionStrategy.eachDependency { dep ->
                    val target = dep.target
                    val version = target.version
                    // Enforce the ban on declaring dependencies with version ranges.
                    if (version != null && Version.isDependencyRange(version)) {
                        throw IllegalArgumentException(
                                "Dependency ${dep.target} declares its version as " +
                                        "version range ${dep.target.version} however the use of " +
                                        "version ranges is not allowed, please update the " +
                                        "dependency to list a fixed version.")
                    }
                }
            }

            if (androidXExtension.compilationTarget != CompilationTarget.DEVICE) {
                throw IllegalStateException(
                    "Android libraries must use a compilation target of DEVICE")
            }
        }

        val debugSigningConfig = signingConfigs.getByName("debug")
        // Use a local debug keystore to avoid build server issues.
        debugSigningConfig.storeFile = project.getKeystore()
        buildTypes.all { buildType ->
            // Sign all the builds (including release) with debug key
            buildType.signingConfig = debugSigningConfig
        }

        project.configureErrorProneForAndroid(variants)

        // Set the officially published version to be the debug version with minimum dependency
        // versions.
        defaultPublishConfig(Release.DEFAULT_PUBLISH_CONFIG)

        // workaround for b/120487939
        project.configurations.all { configuration ->
            // Gradle seems to crash on androidtest configurations
            // preferring project modules...
            if (!configuration.name.toLowerCase(Locale.US).contains("androidtest")) {
                configuration.resolutionStrategy.preferProjectModules()
            }
        }

        if (project.isCoverageEnabled()) {
            Jacoco.registerClassFilesTask(project, this)
        }

        val commonExtension = project.extensions.getByType(CommonExtension::class.java)
        if (hasAndroidTestSourceCode(project, this)) {
            commonExtension.configureTestConfigGeneration(project)
        }

        val buildTestApksTask = project.rootProject.tasks.named(BUILD_TEST_APKS_TASK)
        testVariants.all { variant ->
            buildTestApksTask.configure {
                it.dependsOn(variant.assembleProvider)
            }
            variant.configureApkCopy(project, this, true)
        }
    }

    private fun CommonExtension<*, *, *, *, *, *, *, *>
            .configureTestConfigGeneration(project: Project) {
        onVariants {
            val variant = this
            androidTestProperties {
                val generateTestConfigurationTask = project.tasks.register(
                    "${project.name}${GENERATE_TEST_CONFIGURATION_TASK}${variant.name}",
                    GenerateTestConfigurationTask::class.java
                ) {
                    it.testFolder.set(artifacts.get(ArtifactType.APK))
                    it.testLoader.set(artifacts.getBuiltArtifactsLoader())
                    it.outputXml.fileValue(File(project.getTestConfigDirectory(),
                        "${project.asFilenamePrefix()}${variant.name}AndroidTest.xml"))
                }
                project.rootProject.tasks.findByName(ZIP_TEST_CONFIGS_WITH_APKS_TASK)!!
                    .dependsOn(generateTestConfigurationTask)
            }
        }
    }

    private fun ApplicationExtension<*, *, *, *, *>
            .addAppApkToTestConfigGeneration(project: Project) {
        onVariantProperties {
            project.tasks.withType(GenerateTestConfigurationTask::class.java) {
                it.appFolder.set(artifacts.get(ArtifactType.APK))
                it.appLoader.set(artifacts.getBuiltArtifactsLoader())
            }
        }
    }

    private fun hasAndroidTestSourceCode(project: Project, extension: TestedExtension): Boolean {
        // check Java androidTest source set
        extension.sourceSets.findByName("androidTest")?.let { sourceSet ->
            // using getSourceFiles() instead of sourceFiles due to b/150800094
            if (!sourceSet.java.getSourceFiles().isEmpty) return true
        }

        // check kotlin-android androidTest source set
        project.extensions.findByType(KotlinAndroidProjectExtension::class.java)
            ?.sourceSets?.findByName("androidTest")?.let {
            if (it.kotlin.files.isNotEmpty()) return true
        }

        // check kotlin-multiplatform androidAndroidTest source set
        project.multiplatformExtension?.apply {
            sourceSets.findByName("androidAndroidTest")?.let {
                if (it.kotlin.files.isNotEmpty()) return true
            }
        }

        return false
    }

    private fun ApkVariant.configureApkCopy(
        project: Project,
        extension: TestedExtension,
        testApk: Boolean
    ) {
        packageApplicationProvider.get().let { packageTask ->
            AffectedModuleDetector.configureTaskGuard(packageTask)
            // Skip copying AndroidTest apks if they have no source code (no tests to run).
            if (testApk && !hasAndroidTestSourceCode(project, extension)) {
                return
            }

                project.rootProject.tasks.named(ZIP_TEST_CONFIGS_WITH_APKS_TASK)
                    .configure { task ->
                        task as Zip
                        task.from(packageTask.outputDirectory)
                        task.dependsOn(packageTask)
                    }

            packageTask.doLast {
                project.copy {
                    it.from(packageTask.outputDirectory)
                    it.include("*.apk")
                    it.into(File(project.getDistributionDirectory(), "apks"))
                    it.rename { fileName ->
                        renameApkForTesting(fileName, project)
                    }
                }
            }
        }
    }

    /**
     * Guarantees unique names for the APKs, and modifies some of the suffixes. The APK name is used
     * to determine what gets run by our test runner
     */
    private fun renameApkForTesting(fileName: String, project: Project): String {
        return if (fileName.contains("media-test") || fileName.contains("media2-test")) {
            // Exclude media-test-* and media2-test-* modules from
            // existing support library presubmit tests.
            fileName.replace("-debug-androidTest", "")
        } else if (project.plugins.hasPlugin(BenchmarkPlugin::class.java)) {
            val name = fileName.replace("-androidTest", "-androidBenchmark")
            "${project.asFilenamePrefix()}_$name"
        } else {
            "${project.asFilenamePrefix()}_$fileName"
        }
    }

    private fun LibraryExtension.configureAndroidLibraryOptions(
        project: Project,
        androidXExtension: AndroidXExtension
    ) {
        project.configurations.all { config ->
            val isTestConfig = config.name.toLowerCase(Locale.US).contains("test")

            config.dependencyConstraints.configureEach { dependencyConstraint ->
                dependencyConstraint.apply {
                    // Remove strict constraints on test dependencies and listenablefuture:1.0
                    if (isTestConfig ||
                        group == "com.google.guava" &&
                        name == "listenablefuture" &&
                        version == "1.0") {
                        version { versionConstraint ->
                            versionConstraint.strictly("")
                        }
                    }
                }
            }
        }

        project.afterEvaluate {
            if (androidXExtension.publish.shouldRelease()) {
                project.extra.set("publish", true)
            }
            if (!project.rootProject.hasProperty(USE_MAX_DEP_VERSIONS)) {
                defaultPublishVariant { libraryVariant ->
                    libraryVariant.javaCompileProvider.configure { javaCompile ->
                        if (androidXExtension.failOnDeprecationWarnings) {
                            javaCompile.options.compilerArgs.add("-Xlint:deprecation")
                        }
                    }
                }
            }
        }
    }

    private fun TestedExtension.configureAndroidLibraryWithMultiplatformPluginOptions() {
        sourceSets.findByName("main")!!.manifest.srcFile("src/androidMain/AndroidManifest.xml")
        sourceSets.findByName("androidTest")!!
            .manifest.srcFile("src/androidAndroidTest/AndroidManifest.xml")
    }

    private fun AppExtension.configureAndroidApplicationOptions(project: Project) {
        defaultConfig.apply {
            versionCode = 1
            versionName = "1.0"
        }

        lintOptions.apply {
            isAbortOnError = true

            val baseline = project.lintBaseline
            if (baseline.exists()) {
                baseline(baseline)
            }
        }

        val applicationExtension = project.extensions.getByType(ApplicationExtension::class.java)
        applicationExtension.addAppApkToTestConfigGeneration(project)

        val buildTestApksTask = project.rootProject.tasks.named(BUILD_TEST_APKS_TASK)
        applicationVariants.all { variant ->
            // Using getName() instead of name due to b/150427408
            if (variant.buildType.name == "debug") {
                buildTestApksTask.configure {
                    it.dependsOn(variant.assembleProvider)
                }
            }
            variant.configureApkCopy(project, this, false)
        }
    }

    private fun Project.createVerifyDependencyVersionsTask():
            TaskProvider<VerifyDependencyVersionsTask>? {
        /**
         * Ignore -PuseMaxDepVersions when verifying dependency versions because it is a
         * hypothetical build which is only intended to check for forward compatibility.
         */
        if (hasProperty(USE_MAX_DEP_VERSIONS)) {
            return null
        }

        val taskProvider = tasks.register(
            "verifyDependencyVersions",
            VerifyDependencyVersionsTask::class.java
        )
        addToBuildOnServer(taskProvider)
        return taskProvider
    }

    // Task that creates a json file of a project's dependencies
    private fun Project.addCreateLibraryBuildInfoFileTask(extension: AndroidXExtension) {
        afterEvaluate {
            if (extension.publish.shouldRelease()) {
                // Only generate build info files for published libraries.
                val task = tasks.register(
                    CREATE_LIBRARY_BUILD_INFO_FILES_TASK,
                    CreateLibraryBuildInfoFileTask::class.java
                ) {
                    it.outputFile.set(File(project.getBuildInfoDirectory(),
                        "${group}_${name}_build_info.txt"))
                }
                rootProject.tasks.named(CREATE_LIBRARY_BUILD_INFO_FILES_TASK).configure {
                    it.dependsOn(task)
                }
                addTaskToAggregateBuildInfoFileTask(task)
            }
        }
    }

    private fun Project.addTaskToAggregateBuildInfoFileTask(
        task: TaskProvider<CreateLibraryBuildInfoFileTask>
    ) {
        rootProject.tasks.named(CREATE_AGGREGATE_BUILD_INFO_FILES_TASK).configure {
            val aggregateLibraryBuildInfoFileTask: CreateAggregateLibraryBuildInfoFileTask = it
                    as CreateAggregateLibraryBuildInfoFileTask
            aggregateLibraryBuildInfoFileTask.dependsOn(task)
            aggregateLibraryBuildInfoFileTask.libraryBuildInfoFiles.add(
                task.flatMap { task -> task.outputFile }
            )
        }
    }

    private fun Project.configureJacoco() {
        apply(plugin = "jacoco")
        configure<JacocoPluginExtension> {
            toolVersion = Jacoco.VERSION
        }

        val zipEcFilesTask = Jacoco.getZipEcFilesTask(this)

        tasks.withType(JacocoReport::class.java).configureEach { task ->
            zipEcFilesTask.get().dependsOn(task) // zip follows every jacocoReport task being run
            task.reports {
                it.xml.isEnabled = true
                it.html.isEnabled = false
                it.csv.isEnabled = false

                it.xml.destination = File(getHostTestCoverageDirectory(),
                    "${project.asFilenamePrefix()}.xml")
            }
        }
    }

    companion object {
        const val BUILD_ON_SERVER_TASK = "buildOnServer"
        const val BUILD_TEST_APKS_TASK = "buildTestApks"
        const val CHECK_RELEASE_READY_TASK = "checkReleaseReady"
        const val CREATE_LIBRARY_BUILD_INFO_FILES_TASK = "createLibraryBuildInfoFiles"
        const val CREATE_AGGREGATE_BUILD_INFO_FILES_TASK = "createAggregateBuildInfoFiles"
        const val GENERATE_TEST_CONFIGURATION_TASK = "GenerateTestConfiguration"
        const val REPORT_LIBRARY_METRICS_TASK = "reportLibraryMetrics"
        const val ZIP_TEST_CONFIGS_WITH_APKS_TASK = "zipTestConfigsWithApks"

        const val TASK_GROUP_API = "API"

        const val EXTENSION_NAME = "androidx"

        /**
         * Fail the build if a non-Studio task runs for more than 30 minutes.
         */
        const val TASK_TIMEOUT_MINUTES = 30L

        /**
         * Setting this property indicates that a build is being performed to check for forward
         * compatibility.
         */
        // TODO(alanv): This property should be prefixed with `androidx.`.
        const val USE_MAX_DEP_VERSIONS = "useMaxDepVersions"
    }
}

/**
 * Hides a project's Javadoc tasks from the output of `./gradlew tasks` by setting their group to
 * `null`.
 *
 * AndroidX projects do not use the Javadoc task for docs generation, so we don't want them
 * cluttering up the task overview.
 */
private fun Project.hideJavadocTask() {
    tasks.withType(Javadoc::class.java).configureEach {
        if (it.name == "javadoc") {
            it.group = null
        }
    }
}

private fun Project.addToProjectMap(extension: AndroidXExtension) {
    // TODO(alanv): Move this out of afterEvaluate
    afterEvaluate {
        if (extension.publish.shouldRelease()) {
            val group = extension.mavenGroup?.group
            if (group != null) {
                val module = "$group:$name"
                @Suppress("UNCHECKED_CAST")
                val projectModules = getProjectsMap()
                projectModules[module] = path
            }
        }
    }
}

val Project.multiplatformExtension
    get() = extensions.findByType(KotlinMultiplatformExtension::class.java)

/**
 * Creates the [CHECK_RELEASE_READY_TASK], which aggregates tasks that must pass for a
 * project to be considered ready for public release.
 */
private fun Project.createCheckReleaseReadyTask(taskProviderList: List<TaskProvider<out Task>>) {
    tasks.register(CHECK_RELEASE_READY_TASK) {
        for (taskProvider in taskProviderList) {
            it.dependsOn(taskProvider)
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Project.getProjectsMap(): ConcurrentHashMap<String, String> {
    return rootProject.extra.get("projects") as ConcurrentHashMap<String, String>
}

/**
 * Configures all non-Studio tasks in a project (see b/153193718 for background) to time out after
 * [TASK_TIMEOUT_MINUTES].
 */
private fun Project.configureTaskTimeouts() {
    tasks.configureEach { t ->
        // skip adding a timeout for some tasks that both take a long time and
        // that we can count on the user to monitor
        if (t !is StudioTask) {
            t.timeout.set(Duration.ofMinutes(TASK_TIMEOUT_MINUTES))
        }
    }
}

private fun Project.configureCompilationWarnings(task: JavaCompile) {
    if (hasProperty(ALL_WARNINGS_AS_ERRORS)) {
        task.options.compilerArgs.add("-Werror")
        task.options.compilerArgs.add("-Xlint:unchecked")
    }
}

private fun Project.configureCompilationWarnings(task: KotlinCompile) {
    if (hasProperty(ALL_WARNINGS_AS_ERRORS)) {
        task.kotlinOptions.allWarningsAsErrors = true
    }
    task.kotlinOptions.freeCompilerArgs += listOf(
        "-Xskip-runtime-version-check",
        "-Xskip-metadata-version-check",
        "-XXLanguage:-NewInference"
    )
}

/**
 * Returns a string that is a valid filename and loosely based on the project name
 * The value returned for each project will be distinct
 */
fun Project.asFilenamePrefix(): String {
    return project.path.substring(1).replace(':', '-')
}

/**
 * Sets the specified [task] as a dependency of the top-level `check` task, ensuring that it runs
 * as part of `./gradlew check`.
 */
fun <T : Task> Project.addToCheckTask(task: TaskProvider<T>) {
    project.tasks.named("check").configure {
        it.dependsOn(task)
    }
}
