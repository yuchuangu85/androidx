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

package androidx.room.compiler.processing.javac

import androidx.room.compiler.processing.XTypeElement
import androidx.room.compiler.processing.XVariableElement
import androidx.room.compiler.processing.javac.kotlin.KotlinMetadataElement
import com.google.auto.common.MoreElements
import com.squareup.javapoet.ClassName
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.util.ElementFilter

internal class JavacTypeElement(
    env: JavacProcessingEnv,
    override val element: TypeElement
) : JavacElement(env, element), XTypeElement {

    val kotlinMetadata by lazy {
        KotlinMetadataElement.createFor(element)
    }

    override val qualifiedName by lazy {
        element.qualifiedName.toString()
    }

    override val className: ClassName by lazy {
        ClassName.get(element)
    }

    override fun isInterface() = element.kind == ElementKind.INTERFACE

    private val _allFieldsIncludingPrivateSupers by lazy {
        element.getAllFieldsIncludingPrivateSupers(
            env.elementUtils
        ).map {
            JavacFieldElement(
                env = env,
                element = it,
                containing = this
            )
        }
    }

    override fun getAllFieldsIncludingPrivateSupers(): List<XVariableElement> {
        return _allFieldsIncludingPrivateSupers
    }

    override fun isKotlinObject() = kotlinMetadata?.isObject() == true

    override fun findPrimaryConstructor(): JavacConstructorElement? {
        val primarySignature = kotlinMetadata?.findPrimaryConstructorSignature() ?: return null
        return getConstructors().firstOrNull {
            primarySignature == it.descriptor
        }
    }

    override fun getDeclaredMethods(): List<JavacMethodElement> {
        return ElementFilter.methodsIn(element.enclosedElements).map {
            JavacMethodElement(
                env = env,
                containing = this,
                element = it
            )
        }
    }

    override fun getAllMethods(): List<JavacMethodElement> {
        return ElementFilter.methodsIn(env.elementUtils.getAllMembers(element)).map {
            JavacMethodElement(
                env = env,
                containing = this,
                element = it
            )
        }
    }

    override fun getAllNonPrivateInstanceMethods(): List<JavacMethodElement> {
        return MoreElements.getLocalAndInheritedMethods(
            element,
            env.typeUtils,
            env.elementUtils
        ).map {
            JavacMethodElement(
                env = env,
                containing = this,
                element = it
            )
        }
    }

    override fun getConstructors(): List<JavacConstructorElement> {
        return ElementFilter.constructorsIn(element.enclosedElements).map {
            JavacConstructorElement(
                env = env,
                containing = this,
                element = it
            )
        }
    }

    override val type: JavacDeclaredType by lazy {
        env.wrap<JavacDeclaredType>(
            typeMirror = element.asType(),
            kotlinType = kotlinMetadata?.kmType,
            elementNullability = element.nullability
        )
    }

    override val superType: JavacType? by lazy {
        // javac models non-existing types as TypeKind.NONE but we prefer to make it nullable.
        // just makes more sense and safer as we don't need to check for none.

        // The result value is a JavacType instead of JavacDeclaredType to gracefully handle
        // cases where super is an error type.
        val superClass = element.superclass
        if (superClass.kind == TypeKind.NONE) {
            null
        } else {
            env.wrap<JavacType>(
                typeMirror = superClass,
                kotlinType = kotlinMetadata?.superType,
                elementNullability = element.nullability
            )
        }
    }

    override val equalityItems: Array<out Any?> by lazy {
        arrayOf(element)
    }
}
