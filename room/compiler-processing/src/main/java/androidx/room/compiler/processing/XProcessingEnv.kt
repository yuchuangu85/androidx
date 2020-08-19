/*
 * Copyright (C) 2020 The Android Open Source Project
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

package androidx.room.compiler.processing

import androidx.room.compiler.processing.javac.JavacProcessingEnv
import com.squareup.javapoet.TypeName
import javax.annotation.processing.Filer
import javax.annotation.processing.ProcessingEnvironment
import kotlin.reflect.KClass

/**
 * API for a Processor that is either backed by Java's Annotation Processing API or KSP.
 */
interface XProcessingEnv {

    /**
     * The logger interface to log messages
     */
    val messager: XMessager

    /**
     * List of options passed into the annotation processor
     */
    val options: Map<String, String>

    /**
     * The API to generate files
     */
    val filer: Filer

    /**
     * Looks for the [XTypeElement] with the given qualified name and returns `null` if it does not
     * exist.
     */
    fun findTypeElement(qName: String): XTypeElement?

    /**
     * Looks for the [XType] with the given qualified name and returns `null` if it does not exist.
     */
    fun findType(qName: String): XType?

    /**
     * Returns the [XType] with the given qualified name or throws an exception if it does not
     * exist.
     */
    fun requireType(qName: String): XType = checkNotNull(findType(qName)) {
        "cannot find required type $qName"
    }

    /**
     * Returns the [XTypeElement] for the annotation that should be added to the generated code.
     */
    fun findGeneratedAnnotation(): XTypeElement?

    /**
     * Returns an [XDeclaredType] for the given [type] element with the type arguments specified
     * as in [types].
     */
    fun getDeclaredType(type: XTypeElement, vararg types: XType): XDeclaredType

    /**
     * Return an [XArrayType] that has [type] as the [XArrayType.componentType].
     */
    fun getArrayType(type: XType): XArrayType

    /**
     * Returns the [XTypeElement] with the given qualified name or throws an exception if it does
     * not exist.
     */
    fun requireTypeElement(qName: String): XTypeElement {
        return checkNotNull(findTypeElement(qName)) {
            "Cannot find required type element $qName"
        }
    }

    // helpers for smooth migration, these could be extension methods
    fun requireType(typeName: TypeName) = requireType(typeName.toString())

    fun requireType(klass: KClass<*>) = requireType(klass.java.canonicalName!!)

    fun findType(typeName: TypeName) = findType(typeName.toString())

    fun findType(klass: KClass<*>) = findType(klass.java.canonicalName!!)

    fun requireTypeElement(typeName: TypeName) = requireTypeElement(typeName.toString())

    fun requireTypeElement(klass: KClass<*>) = requireTypeElement(klass.java.canonicalName!!)

    fun findTypeElement(typeName: TypeName) = findTypeElement(typeName.toString())

    fun findTypeElement(klass: KClass<*>) = findTypeElement(klass.java.canonicalName!!)

    fun getArrayType(typeName: TypeName) = getArrayType(requireType(typeName))

    companion object {
        /**
         * Creates a new [XProcessingEnv] implementation derived from the given Java [env].
         */
        fun create(env: ProcessingEnvironment): XProcessingEnv = JavacProcessingEnv(env)
    }
}
