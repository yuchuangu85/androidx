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

import androidx.build.ArtifactsPredicate.All
import androidx.build.ArtifactsPredicate.Benchmark
import androidx.build.ArtifactsPredicate.Exact
import androidx.build.ArtifactsPredicate.Group
import androidx.build.Strategy.Ignore
import androidx.build.Strategy.Prebuilts
import androidx.build.Strategy.TipOfTree

/**
 * Rule set used to generate public documentation.
 */
val RELEASE_RULE = docsRules("public", false) {
    ignore(LibraryGroups.ACTIVITY.group, "activity-lint")
    prebuilts(LibraryGroups.ACTIVITY, "1.2.0-alpha08")
    prebuilts(LibraryGroups.ADS, "1.0.0-alpha04")
    prebuilts(LibraryGroups.ANNOTATION, "annotation", "1.2.0-alpha01")
    prebuilts(LibraryGroups.ANNOTATION, "annotation-experimental", "1.1.0-alpha01")
    prebuilts(LibraryGroups.ANNOTATION, "annotation-experimental-lint", "1.0.0")
    ignore(LibraryGroups.APPCOMPAT.group, "appcompat-lint")
    prebuilts(LibraryGroups.APPCOMPAT, "1.3.0-alpha02")
    prebuilts(LibraryGroups.ARCH_CORE, "2.1.0")
    prebuilts(LibraryGroups.ASYNCLAYOUTINFLATER, "1.0.0")
    prebuilts(LibraryGroups.AUTOFILL, "1.1.0-alpha02")
    ignore(LibraryGroups.BENCHMARK.group, "benchmark-gradle-plugin")
    prebuilts(LibraryGroups.BENCHMARK, "1.1.0-alpha01")
    prebuilts(LibraryGroups.BIOMETRIC, "biometric", "1.1.0-alpha02")
    prebuilts(LibraryGroups.BROWSER, "1.3.0-alpha05")
    ignore(LibraryGroups.CAMERA.group, "camera-camera2-pipe")
    ignore(LibraryGroups.CAMERA.group, "camera-camera2-pipe-integration")
    ignore(LibraryGroups.CAMERA.group, "camera-testing")
    ignore(LibraryGroups.CAMERA.group, "camera-extensions-stub")
    ignore(LibraryGroups.CAMERA.group, "camera-testlib-extensions")
    ignore(LibraryGroups.CAMERA.group, "camera-video")
    prebuilts(LibraryGroups.CAMERA, "camera-view", "1.0.0-alpha16")
    prebuilts(LibraryGroups.CAMERA, "camera-extensions", "1.0.0-alpha16")
            .addStubs("camera/camera-extensions-stub/camera-extensions-stub.jar")
    prebuilts(LibraryGroups.CAMERA, "1.0.0-beta09")
    prebuilts(LibraryGroups.CARDVIEW, "1.0.0")
    prebuilts(LibraryGroups.COLLECTION, "1.1.0")
    prebuilts(LibraryGroups.CONCURRENT, "1.1.0")
    prebuilts(LibraryGroups.CONTENTPAGER, "1.0.0")
    prebuilts(LibraryGroups.COORDINATORLAYOUT, "1.1.0")
    ignore(LibraryGroups.CORE.group, "core-appdigest")
    prebuilts(LibraryGroups.CORE, "core", "1.5.0-alpha03")
    prebuilts(LibraryGroups.CORE, "core-animation", "1.0.0-alpha02")
    prebuilts(LibraryGroups.CORE, "core-animation-testing", "1.0.0-alpha02")
    prebuilts(LibraryGroups.CORE, "core-ktx", "1.5.0-alpha03")
    prebuilts(LibraryGroups.CORE, "core-role", "1.1.0-alpha02")
    prebuilts(LibraryGroups.CURSORADAPTER, "1.0.0")
    prebuilts(LibraryGroups.CUSTOMVIEW, "1.1.0")
    ignore(LibraryGroups.DATASTORE.group, "datastore-preferences-proto")
    ignore(LibraryGroups.DATASTORE.group, "datastore-proto")
    prebuilts(LibraryGroups.DATASTORE, "1.0.0-alpha01")
    prebuilts(LibraryGroups.DOCUMENTFILE, "1.0.0")
    prebuilts(LibraryGroups.DRAWERLAYOUT, "1.1.1")
    prebuilts(LibraryGroups.DYNAMICANIMATION, "dynamicanimation-ktx", "1.0.0-alpha03")
    prebuilts(LibraryGroups.DYNAMICANIMATION, "1.1.0-alpha02")
    prebuilts(LibraryGroups.EMOJI, "1.2.0-alpha01")
    prebuilts(LibraryGroups.ENTERPRISE, "1.1.0-alpha02")
    prebuilts(LibraryGroups.EXIFINTERFACE, "1.3.0")
    ignore(LibraryGroups.FRAGMENT.group, "fragment-lint")
    ignore(LibraryGroups.FRAGMENT.group, "fragment-testing-lint")
    ignore(LibraryGroups.FRAGMENT.group, "fragment-truth")
    prebuilts(LibraryGroups.FRAGMENT, "1.3.0-alpha08")
    prebuilts(LibraryGroups.GRIDLAYOUT, "1.0.0")
    prebuilts(LibraryGroups.HEIFWRITER, "1.1.0-alpha01")
    prebuilts(LibraryGroups.HILT, "1.0.0-alpha02")
    prebuilts(LibraryGroups.INTERPOLATOR, "1.0.0")
    prebuilts(LibraryGroups.LEANBACK, "1.1.0-alpha04")
    prebuilts(LibraryGroups.LEGACY, "1.0.0")
    ignore(LibraryGroups.LIFECYCLE.group, "lifecycle-compiler")
    ignore(LibraryGroups.LIFECYCLE.group, "lifecycle-livedata-core-ktx-lint")
    ignore(LibraryGroups.LIFECYCLE.group, "lifecycle-livedata-core-truth")
    ignore(LibraryGroups.LIFECYCLE.group, "lifecycle-runtime-ktx-lint")
    prebuilts(LibraryGroups.LIFECYCLE, "lifecycle-extensions", "2.2.0") // No longer published
    ignore(LibraryGroups.LIFECYCLE.group, "lifecycle-runtime-testing")
    prebuilts(LibraryGroups.LIFECYCLE, "2.3.0-alpha07")
    ignore(LibraryGroups.LOADER.group, "loader-ktx")
    prebuilts(LibraryGroups.LOADER, "1.1.0")
    prebuilts(LibraryGroups.LOCALBROADCASTMANAGER, "1.1.0-alpha01")
    prebuilts(LibraryGroups.MEDIA, "media", "1.2.0")
    ignore(LibraryGroups.MEDIA2.group, "media2-exoplayer")
    prebuilts(LibraryGroups.MEDIA2, "media2-widget", "1.1.0-beta01")
    prebuilts(LibraryGroups.MEDIA2, "1.1.0-beta01")
    prebuilts(LibraryGroups.MEDIAROUTER, "1.2.0-rc01")
    ignore(LibraryGroups.NAVIGATION.group, "navigation-runtime-truth")
    ignore(LibraryGroups.NAVIGATION.group, "navigation-safe-args-generator")
    ignore(LibraryGroups.NAVIGATION.group, "navigation-safe-args-gradle-plugin")
    prebuilts(LibraryGroups.NAVIGATION, "2.3.0")
    prebuilts(LibraryGroups.PAGING, "3.0.0-alpha06")
    prebuilts(LibraryGroups.PALETTE, "1.0.0")
    // 1.0.1 was created to fix reference docs.  It contains no actual source changes from 1.0.0
    prebuilts(LibraryGroups.PERCENTLAYOUT, "1.0.1")
    prebuilts(LibraryGroups.PREFERENCE, "preference-ktx", "1.1.1")
    prebuilts(LibraryGroups.PREFERENCE, "1.1.1")
    prebuilts(LibraryGroups.PRINT, "1.1.0-alpha01")
    prebuilts(LibraryGroups.RECOMMENDATION, "1.0.0")
    prebuilts(LibraryGroups.RECYCLERVIEW, "recyclerview", "1.2.0-alpha05")
    prebuilts(LibraryGroups.RECYCLERVIEW, "recyclerview-selection", "2.0.0-alpha01")
    ignore(LibraryGroups.RECYCLERVIEW.group, "recyclerview-lint")
    prebuilts(LibraryGroups.REMOTECALLBACK, "1.0.0-alpha02")
    // TODO: Remove this once b/157899389 is resolved
    ignore(LibraryGroups.ROOM.group, "room-compiler")
    ignore(LibraryGroups.ROOM.group, "room-compiler-processing")
    // TODO: Remove during release phase of rxjava3 artifact
    ignore(LibraryGroups.ROOM.group, "room-rxjava3")
    prebuilts(LibraryGroups.ROOM, "2.3.0-alpha02")
    prebuilts(LibraryGroups.SAVEDSTATE, "1.1.0-alpha01")
    // TODO: Remove this ignore once androidx.security:security-biometric:1.0.0-alpha01 is released
    ignore(LibraryGroups.SECURITY.group, "security-biometric")
    prebuilts(LibraryGroups.SECURITY, "security-identity-credential", "1.0.0-alpha01")
    prebuilts(LibraryGroups.SECURITY, "1.1.0-alpha02")
    prebuilts(LibraryGroups.SHARETARGET, "1.0.0")
    prebuilts(LibraryGroups.SLICE, "slice-builders", "1.1.0-alpha01")
    prebuilts(LibraryGroups.SLICE, "slice-builders-ktx", "1.0.0-alpha07")
    prebuilts(LibraryGroups.SLICE, "slice-core", "1.1.0-alpha01")
    // TODO: land prebuilts
//    prebuilts(LibraryGroups.SLICE.group, "slice-test", "1.0.0")
    ignore(LibraryGroups.SLICE.group, "slice-test")
    prebuilts(LibraryGroups.SLICE, "slice-view", "1.1.0-alpha01")
    prebuilts(LibraryGroups.SLIDINGPANELAYOUT, "1.1.0")
    ignore(LibraryGroups.INSPECTION_EXTENSIONS.group, "sqlite-inspection")
    prebuilts(LibraryGroups.SQLITE, "2.1.0")
    prebuilts(LibraryGroups.STARTUP, "1.0.0-beta01")
    prebuilts(LibraryGroups.SWIPEREFRESHLAYOUT, "1.2.0-alpha01")
    prebuilts(LibraryGroups.TEXTCLASSIFIER, "1.0.0-alpha03")
    prebuilts(LibraryGroups.TRACING, "1.0.0-beta01")
    ignore(LibraryGroups.TRANSITION.group, "transition-ktx")
    prebuilts(LibraryGroups.TRANSITION, "1.4.0-beta01")
    prebuilts(LibraryGroups.TVPROVIDER, "1.1.0-alpha01")
    prebuilts(LibraryGroups.VECTORDRAWABLE, "vectordrawable", "1.2.0-alpha02")
    prebuilts(LibraryGroups.VECTORDRAWABLE, "vectordrawable-animated", "1.2.0-alpha01")
    prebuilts(LibraryGroups.VECTORDRAWABLE, "vectordrawable-seekable", "1.0.0-alpha02")
    ignore(LibraryGroups.VERSIONEDPARCELABLE.group, "versionedparcelable-compiler")
    prebuilts(LibraryGroups.VERSIONEDPARCELABLE, "versionedparcelable", "1.1.1")
    prebuilts(LibraryGroups.VIEWPAGER, "1.0.0")
    prebuilts(LibraryGroups.VIEWPAGER2, "1.1.0-alpha01")
    prebuilts(LibraryGroups.WEAR, "wear-input", "1.0.0-alpha01")
    prebuilts(LibraryGroups.WEAR, "wear", "1.1.0-rc03")
            .addStubs("wear/wear_stubs/com.google.android.wearable-stubs.jar")
    ignore(LibraryGroups.WEAR.group, "wear-input-testing")
    prebuilts(LibraryGroups.WEBKIT, "1.4.0-alpha01")
    ignore(LibraryGroups.WINDOW.group, "window-sidecar")
    prebuilts(LibraryGroups.WINDOW, "1.0.0-alpha01")
            .addStubs("window/stubs/window-sidecar-release-0.1.0-alpha01.aar")
    ignore(LibraryGroups.WORK.group, "work-inspection")
    ignore(LibraryGroups.WORK.group, "work-gcm")
    ignore(LibraryGroups.WORK.group, "work-runtime-lint")
    prebuilts(LibraryGroups.WORK, "2.5.0-alpha02")
    default(Ignore)
}

/**
 * Rule set used to generate tip-of-tree documentation, typically for local and pre-submit use.
 */
val TIP_OF_TREE = docsRules("tipOfTree", true) {
    ignore(LibraryGroups.COMPOSE.group)
    default(TipOfTree)
}

/**
 * Builds rules describing how to generate documentation for a set of libraries.
 *
 * Rules are resolved in the order in which they were added. So, if you have two rules that specify
 * how docs should be built for a module, the first matching rule will be used.
 *
 * @property name human-readable label for this documentation set
 * @property offline true if generating documentation for local use, false otherwise.
 * @property init lambda that initializes a rule builder.
 * @return rules describing how to generate documentation.
 */
fun docsRules(
    name: String,
    offline: Boolean,
    init: PublishDocsRulesBuilder.() -> Unit
): PublishDocsRules {
    val f = PublishDocsRulesBuilder(name, offline)
    f.init()
    return f.build()
}

/**
 * Builder for rules describing how to generate documentation for a set of libraries.
 *
 * @property name human-readable label for this documentation set
 * @property offline true if generating documentation for local use, false otherwise.
 * @constructor Creates a builder with no rules specified.
 */
class PublishDocsRulesBuilder(private val name: String, private val offline: Boolean) {
    private val rules: MutableList<DocsRule> = mutableListOf(DocsRule(Benchmark, Ignore))

    /**
     * Specifies that docs for projects within [groupName] will be built from sources.
     */
    fun tipOfTree(groupName: String) {
        rules.add(DocsRule(Group(groupName), TipOfTree))
    }

    /**
     * Specifies that docs for a project with the given [groupName] and artifact [name] will be
     * built from sources.
     */
    fun tipOfTree(groupName: String, name: String) {
        rules.add(DocsRule(Exact(groupName, name), TipOfTree))
    }

    /**
     * Specifies that docs for a project with the given [groupName] and artifact [name] will be
     * built from a prebuilt with the given [version].
     */
    fun prebuilts(libraryGroup: LibraryGroup, moduleName: String, version: String): Prebuilts {
        val strategy = Prebuilts(Version(version))
        rules.add(DocsRule(Exact(libraryGroup.group, moduleName), strategy))
        return strategy
    }

    /**
     * Specifies that docs for projects within [groupName] will be built from prebuilts with the
     * given [version].
     */
    fun prebuilts(libraryGroup: LibraryGroup, version: String) =
            prebuilts(libraryGroup, Version(version))

    /**
     * Specifies that docs for projects within [groupName] will be built from prebuilts with the
     * given [version].
     */
    fun prebuilts(libraryGroup: LibraryGroup, version: Version): Prebuilts {
        val strategy = Prebuilts(version)
        rules.add(DocsRule(Group(libraryGroup.group), strategy))
        return strategy
    }

    /**
     * Specifies the default strategy for building docs.
     *
     * This method should be called last, as it matches all candidates. No rules specified after
     * calling this method will have any effect.
     */
    fun default(strategy: Strategy) {
        rules.add(DocsRule(All, strategy))
    }

    /**
     * Specifies that docs for projects with the given [groupName] won't be built.
     */
    fun ignore(groupName: String) {
        rules.add(DocsRule(Group(groupName), Ignore))
    }

    /**
     * Specifies that docs for a project with the given [groupName] and artifact [name] won't be
     * built.
     */
    fun ignore(groupName: String, name: String) {
        rules.add(DocsRule(Exact(groupName, name), Ignore))
    }

    /**
     * Builds a fully-initialized set of documentation rules.
     */
    fun build() = PublishDocsRules(name, offline, rules)
}

/**
 * ArtifactsPredicates are used to match libraries.
 */
sealed class ArtifactsPredicate {
    /**
     * Returns true if the predicate matches the specified library project.
     *
     * @param inGroup the library Maven groupId to be matched.
     * @param inName the library Maven artifact name to be matched.
     * @return true if the predicate matches the library.
     */
    abstract fun apply(inGroup: String, inName: String): Boolean

    /**
     * Predicate that matches all library projects.
     */
    object All : ArtifactsPredicate() {
        override fun apply(inGroup: String, inName: String) = true
    }

    /**
     * Predicate that matches library projects with the specified Maven groupId.
     *
     * @property group the Maven groupId to be matched.
     * @constructor Creates a predicate to match the specified Maven groupId.
     */
    class Group(val group: String) : ArtifactsPredicate() {
        override fun apply(inGroup: String, inName: String) = inGroup == group
        override fun toString() = "\"$group\""
    }

    /**
     * Predicate that matches library projects with the specified Maven groupId and artifact name.
     *
     * @property group the Maven groupId to be matched.
     * @peoperty name the Maven artifact name to be matched.
     * @constructor Creates a predicate to match the specified Maven groupId and artifact name.
     */
    class Exact(val group: String, val name: String) : ArtifactsPredicate() {
        override fun apply(inGroup: String, inName: String) = group == inGroup && name == inName
        override fun toString() = "\"$group\", \"$name\""
    }

    /**
     * Predicate that matches all benchmark projects, e.g. all library projects where the project
     * name is suffixed with "-benchmark".
     */
    object Benchmark : ArtifactsPredicate() {
        override fun apply(inGroup: String, inName: String) = inName.endsWith("-benchmark")
    }
}

/**
 * Rule associating a [predicate] -- used to match libraries -- with a documentation strategy.
 *
 * @property predicate the predicate used to match libraries.
 * @property strategy the strategy used to generate documentation.
 */
data class DocsRule(val predicate: ArtifactsPredicate, val strategy: Strategy) {
    override fun toString(): String {
        if (predicate is All) {
            return "default($strategy)"
        }
        return when (strategy) {
            is Prebuilts -> "prebuilts($predicate, \"${strategy.version}\")"
            is Ignore -> "ignore($predicate)"
            is TipOfTree -> "tipOfTree($predicate)"
        }
    }
}

/**
 * Strategies are used to inform the build of which source set should be used when generating
 * documentation.
 */
sealed class Strategy {
    /**
     * Strategy that uses tip-of-tree source code, equivalent to a project() dependency.
     */
    object TipOfTree : Strategy()

    /**
     * Strategy that does not generate documentation.
     */
    object Ignore : Strategy()

    /**
     * Strategy that uses a versioned prebuilt, equivalent to a Maven coordinate dependency.
     */
    class Prebuilts(val version: Version) : Strategy() {
        /**
         * List of stub JARs that should be made available on the documentation generator's
         * classpath.
         */
        var stubs: MutableList<String>? = null

        /**
         * Adds a stub JAR to the documentation generation tool's classpath.
         *
         * Useful for generating documentation for libraries that depend on sidecar JARs or other
         * run-time dependencies that would not otherwise be available on the classpath for the
         * documentation generation tool.
         *
         * @param path the path to the stub JAR relative to the top-level AndroidX project root.
         */
        fun addStubs(path: String) {
            if (stubs == null) {
                stubs = mutableListOf()
            }
            stubs!!.add(path)
        }

        override fun toString() = "Prebuilts(\"$version\")"

        /**
         * Returns a Maven dependency spec for the specified library [extension].
         */
        fun dependency(extension: AndroidXExtension): String {
            return "${extension.mavenGroup?.group}:${extension.project.name}:$version"
        }
    }
}

/**
 * Rules describing how to generate documentation for a set of libraries.
 *
 * @property name human-readable label for this documentation set
 * @property offline true if generating documentation for local use, false otherwise.
 * @constructor Creates a documentation rule set.
 */
class PublishDocsRules(val name: String, val offline: Boolean, private val rules: List<DocsRule>) {
    /**
     * Resolves a rule describing how to generate documentation for the given library.
     *
     * If multiple rules match, the matching first rule is returned.
     *
     * @return the documentation rule
     */
    fun resolve(extension: AndroidXExtension): DocsRule? {
        val mavenGroup = extension.mavenGroup
        return if (mavenGroup == null) null else resolve(mavenGroup.group, extension.project.name)
    }

    /**
     * Resolves a rule describing how to generate documentation for a given Maven group and module
     * name.
     *
     * If multiple rules match, the matching first rule is returned.
     *
     * @return the documentation rule
     */
    fun resolve(groupName: String, moduleName: String): DocsRule {
        return rules.find { it.predicate.apply(groupName, moduleName) } ?: throw Error()
    }
}
