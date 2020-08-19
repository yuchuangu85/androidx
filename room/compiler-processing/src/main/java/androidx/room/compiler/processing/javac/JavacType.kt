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

import androidx.room.compiler.processing.XEquality
import androidx.room.compiler.processing.XNullability
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XTypeElement
import androidx.room.compiler.processing.javac.kotlin.KmType
import androidx.room.compiler.processing.safeTypeName
import com.google.auto.common.MoreTypes
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass

internal abstract class JavacType(
    protected val env: JavacProcessingEnv,
    open val typeMirror: TypeMirror
) : XType, XEquality {
    // Kotlin type information about the type if this type is driven from kotlin code.
    abstract val kotlinType: KmType?

    override fun isError() = typeMirror.kind == TypeKind.ERROR

    override val typeName by lazy {
        typeMirror.safeTypeName()
    }

    override fun equals(other: Any?): Boolean {
        return XEquality.equals(this, other)
    }

    override fun hashCode(): Int {
        return XEquality.hashCode(equalityItems)
    }

    override fun defaultValue(): String {
        return when (typeMirror.kind) {
            TypeKind.BOOLEAN -> "false"
            TypeKind.BYTE, TypeKind.SHORT, TypeKind.INT, TypeKind.LONG, TypeKind.CHAR -> "0"
            TypeKind.FLOAT -> "0f"
            TypeKind.DOUBLE -> "0.0"
            else -> "null"
        }
    }

    override fun boxed(): XType {
        return if (typeMirror.kind.isPrimitive) {
            env.wrap(
                typeMirror = env.typeUtils.boxedClass(MoreTypes.asPrimitiveType(typeMirror))
                    .asType(),
                kotlinType = kotlinType,
                elementNullability = XNullability.NULLABLE
            )
        } else {
            this
        }
    }

    override fun asTypeElement(): XTypeElement {
        return env.wrapTypeElement(
            MoreTypes.asTypeElement(typeMirror)
        )
    }

    override fun isNone() = typeMirror.kind == TypeKind.NONE

    override fun toString(): String {
        return typeMirror.toString()
    }

    override fun extendsBound(): XType? {
        return typeMirror.extendsBound()?.let {
            env.wrap<JavacType>(
                typeMirror = it,
                kotlinType = kotlinType?.extendsBound,
                elementNullability = nullability
            )
        }
    }

    override fun isAssignableFrom(other: XType): Boolean {
        return other is JavacType && env.typeUtils.isAssignable(
            other.typeMirror,
            typeMirror
        )
    }

    override fun erasure(): JavacType {
        return env.wrap(
            typeMirror = env.typeUtils.erasure(typeMirror),
            kotlinType = kotlinType?.erasure(),
            elementNullability = nullability
        )
    }

    override fun isTypeOf(other: KClass<*>): Boolean {
        return MoreTypes.isTypeOf(
            other.java,
            typeMirror
        )
    }

    override fun isSameType(other: XType): Boolean {
        return other is JavacType && env.typeUtils.isSameType(typeMirror, other.typeMirror)
    }

    override fun isType(): Boolean {
        return MoreTypes.isType(typeMirror)
    }
}
