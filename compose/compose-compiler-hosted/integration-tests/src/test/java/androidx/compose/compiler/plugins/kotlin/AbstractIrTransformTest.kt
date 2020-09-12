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

package androidx.compose.compiler.plugins.kotlin

import androidx.compose.compiler.plugins.kotlin.lower.dumpSrc
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.ir.createParameterDeclarations
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensions
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.konan.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.KlibModuleOrigin
import org.jetbrains.kotlin.ir.backend.jvm.serialization.EmptyLoggingContext
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmIrLinker
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmManglerDesc
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.descriptors.IrFunctionFactory
import org.jetbrains.kotlin.ir.linkage.IrDeserializer
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrConfiguration
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File

@Suppress("LeakingThis")
abstract class ComposeIrTransformTest : AbstractIrTransformTest() {
    open val liveLiteralsEnabled get() = false
    open val sourceInformationEnabled get() = true
    private val extension = ComposeIrGenerationExtension(
        liveLiteralsEnabled,
        sourceInformationEnabled
    )
    override fun postProcessingStep(
        module: IrModuleFragment,
        generatorContext: GeneratorContext,
        irLinker: IrDeserializer
    ) {
        extension.generate(
            module,
            IrPluginContextImpl(
                generatorContext.moduleDescriptor,
                generatorContext.bindingContext,
                generatorContext.languageVersionSettings,
                generatorContext.symbolTable,
                generatorContext.typeTranslator,
                generatorContext.irBuiltIns,
                irLinker
            )
        )
    }
}

abstract class AbstractIrTransformTest : AbstractCompilerTest() {
    protected fun sourceFile(name: String, source: String): KtFile {
        val result = createFile(name, source, myEnvironment!!.project)
        val ranges = AnalyzingUtils.getSyntaxErrorRanges(result)
        assert(ranges.isEmpty()) { "Syntax errors found in $name: $ranges" }
        return result
    }

    protected open val additionalPaths = emptyList<File>()

    abstract fun postProcessingStep(
        module: IrModuleFragment,
        generatorContext: GeneratorContext,
        irLinker: IrDeserializer
    )

    fun verifyComposeIrTransform(
        source: String,
        expectedTransformed: String,
        extra: String = "",
        dumpTree: Boolean = false
    ) {
        val files = listOf(
            sourceFile("Test.kt", source.replace('%', '$')),
            sourceFile("Extra.kt", extra.replace('%', '$'))
        )
        val irModule = generateIrModuleWithJvmResolve(files)
        val keySet = mutableListOf<Int>()
        val actualTransformed = irModule
            .files[0]
            .dumpSrc()
            .replace('$', '%')
            // replace source keys for start group calls
            .replace(
                Regex(
                    "(%composer\\.start(Restart|Movable|Replaceable)Group\\()-?((0b)?[-\\d]+)"
                )
            ) {
                val stringKey = it.groupValues[3]
                val key = if (stringKey.startsWith("0b"))
                    Integer.parseInt(stringKey.drop(2), 2)
                else
                    stringKey.toInt()
                if (key in keySet) {
                    "${it.groupValues[1]}<!DUPLICATE KEY: $key!>"
                } else {
                    keySet.add(key)
                    "${it.groupValues[1]}<>"
                }
            }
            // replace source information with source it references
            .replace(
                Regex("(%composer\\.start(Restart|Movable|Replaceable)Group\\" +
                        "([^\"\\n]*)\"(.*)\"\\)")
            ) {
                "${it.groupValues[1]}\"${
                    generateSourceInfo(it.groupValues[3], source)
                }\")"
            }
            .replace(
                    Regex("(composableLambda[N]?\\" +
                            "([^\"\\n]*)\"(.*)\"\\)")
            ) {
                "${it.groupValues[1]}\"${
                generateSourceInfo(it.groupValues[2], source)
                }\")"
            }
            // replace source keys for joinKey calls
            .replace(
                Regex(
                    "(%composer\\.joinKey\\()([-\\d]+)"
                )
            ) {
                "${it.groupValues[1]}<>"
            }
            // composableLambdaInstance(<>, true)
            .replace(
                Regex(
                    "(composableLambdaInstance\\()([-\\d]+)"
                )
            ) {
                "${it.groupValues[1]}<>"
            }
            // composableLambda(%composer, <>, true)
            .replace(
                Regex(
                    "(composableLambda\\(%composer,\\s)([-\\d]+)"
                )
            ) {
                "${it.groupValues[1]}<>"
            }
            .trimIndent()
            .trimTrailingWhitespacesAndAddNewlineAtEOF()

        if (dumpTree) {
            println(irModule.dump())
        }
        assertEquals(
            expectedTransformed
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
            actualTransformed
        )
    }

    private fun MatchResult.isNumber() = groupValues[1].isNotEmpty()
    private fun MatchResult.number() = groupValues[1].toInt()
    private val MatchResult.text get() = groupValues[0]
    private fun MatchResult.isChar(c: String) = text == c
    private fun MatchResult.isFileName() = groups[4] != null

    private fun generateSourceInfo(sourceInfo: String, source: String): String {
        val r = Regex("(\\d+)|([,])|([*])|([:])|C(\\(.*\\))?|L|(P\\(*\\))|@")
        var current = 0
        var currentResult = r.find(sourceInfo, current)
        var result = ""

        fun next(): MatchResult? {
            currentResult?.let {
                current = it.range.last + 1
                currentResult = it.next()
            }
            return currentResult
        }

        // A location has the format: [<line-number>]['@' <offset> ['L' <length>]]
        // where the named productions are numbers
        fun parseLocation(): String? {
            var mr = currentResult
            if (mr != null && mr.isNumber()) {
                // line number, we ignore the value in during testing.
                mr = next()
            }
            if (mr != null && mr.isChar("@")) {
                // Offset
                mr = next()
                if (mr == null || !mr.isNumber()) {
                    return null
                }
                val offset = mr.number()
                mr = next()
                var ellipsis = ""
                val maxFragment = 6
                val rawLength = if (mr != null && mr.isChar("L")) {
                    mr = next()
                    if (mr == null || !mr.isNumber()) {
                        return null
                    }
                    mr.number().also { next() }
                } else {
                    maxFragment
                }
                val eol = source.indexOf('\n', offset).let {
                    if (it < 0) source.length else it
                }
                val space = source.indexOf(' ', offset).let {
                    if (it < 0) source.length else it
                }
                val maxEnd = offset + maxFragment
                if (eol > maxEnd && space > maxEnd) ellipsis = "..."
                val length = minOf(maxEnd, minOf(offset + rawLength, space, eol)) - offset
                return "<${source.substring(offset, offset + length)}$ellipsis>"
            }
            return null
        }

        while (currentResult != null) {
            val mr = currentResult!!
            if (mr.range.start != current) {
                return "invalid source info at $current: '$sourceInfo'"
            }
            when {
                mr.isNumber() || mr.isChar("@") -> {
                    val fragment = parseLocation()
                        ?: return "invalid source info at $current: '$sourceInfo'"
                    result += fragment
                }
                mr.isFileName() -> {
                    return result + ":" + sourceInfo.substring(mr.range.last + 1)
                }
                else -> {
                    result += mr.text
                    next()
                }
            }
            require(mr != currentResult) { "regex didn't advance" }
        }
        if (current != sourceInfo.length)
            return "invalid source info at $current: '$sourceInfo'"
        return result
    }

    protected fun generateIrModuleWithJvmResolve(files: List<KtFile>): IrModuleFragment {
        val classPath = createClasspath() + additionalPaths
        val configuration = newConfiguration()
        configuration.addJvmClasspathRoots(classPath)
        configuration.put(JVMConfigurationKeys.IR, true)
        configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_1_8)

        val environment = KotlinCoreEnvironment.createForTests(
            myTestRootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
        ).also { setupEnvironment(it) }

        val mangler = JvmManglerDesc(null)
        val signaturer = JvmIdSignatureDescriptor(mangler)

        val psi2ir = Psi2IrTranslator(
            environment.configuration.languageVersionSettings,
            Psi2IrConfiguration(ignoreErrors = false),
            signaturer
        )

        val analysisResult = JvmResolveUtil.analyze(files, environment)
        if (!psi2ir.configuration.ignoreErrors) {
            analysisResult.throwIfError()
            AnalyzingUtils.throwExceptionOnErrors(analysisResult.bindingContext)
        }
        val extensions = JvmGeneratorExtensions()
        val generatorContext = psi2ir.createGeneratorContext(
            analysisResult.moduleDescriptor,
            analysisResult.bindingContext,
            extensions = extensions
        )
        val stubGenerator = DeclarationStubGenerator(
            generatorContext.moduleDescriptor,
            generatorContext.symbolTable,
            generatorContext.irBuiltIns.languageVersionSettings,
            extensions
        )
        val functionFactory = IrFunctionFactory(
            generatorContext.irBuiltIns,
            generatorContext.symbolTable
        )
        generatorContext.irBuiltIns.functionFactory = functionFactory
        val irLinker = JvmIrLinker(
            generatorContext.moduleDescriptor,
            EmptyLoggingContext,
            generatorContext.irBuiltIns,
            generatorContext.symbolTable,
            functionFactory,
            stubGenerator,
            mangler
        )

        generatorContext.moduleDescriptor.allDependencyModules.map {
            val capability = it.getCapability(KlibModuleOrigin.CAPABILITY)
            val kotlinLibrary = (capability as? DeserializedKlibModuleOrigin)?.library
            irLinker.deserializeIrModuleHeader(it, kotlinLibrary)
        }

        val irProviders = listOf(irLinker)

        stubGenerator.setIrProviders(irProviders)

        ExternalDependenciesGenerator(
            generatorContext.symbolTable,
            irProviders,
            generatorContext.languageVersionSettings
        ).generateUnboundSymbolsAsDependencies()

        psi2ir.addPostprocessingStep { module ->
            val old = stubGenerator.unboundSymbolGeneration
            try {
                stubGenerator.unboundSymbolGeneration = true
                postProcessingStep(module, generatorContext, irLinker)
            } finally {
                stubGenerator.unboundSymbolGeneration = old
            }
        }

        val irModuleFragment = psi2ir.generateModuleFragment(
            generatorContext,
            files,
            irProviders,
            IrGenerationExtension.getInstances(myEnvironment!!.project),
            expectDescriptorToSymbol = null
        )
        irLinker.postProcess()
        return irModuleFragment
    }

    fun facadeClassGenerator(source: DeserializedContainerSource): IrClass? {
        val jvmPackagePartSource = source.safeAs<JvmPackagePartSource>() ?: return null
        val facadeName = jvmPackagePartSource.facadeClassName ?: jvmPackagePartSource.className
        return buildClass {
            origin = IrDeclarationOrigin.FILE_CLASS
            name = facadeName.fqNameForTopLevelClassMaybeWithDollars.shortName()
        }.also {
            it.createParameterDeclarations()
        }
    }
}
