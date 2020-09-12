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

package androidx.room.compiler.processing

import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.getDeclaredMethod
import androidx.room.compiler.processing.util.getField
import androidx.room.compiler.processing.util.getMethod
import androidx.room.compiler.processing.util.runProcessorTest
import androidx.room.compiler.processing.util.runProcessorTestForFailedCompilation
import com.google.common.truth.Truth.assertThat
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeVariableName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class XTypeTest {
    @Test
    fun declaredTypeArguments() {
        val parent = Source.java(
            "foo.bar.Parent", """
            package foo.bar;
            import java.io.InputStream;
            import java.util.Set;
            class Parent<InputStreamType extends InputStream> {
                public void wildcardParam(Set<?> param1) {}
            }
        """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(parent)
        ) {
            val type = it.processingEnv.requireType("foo.bar.Parent") as XDeclaredType
            val className = ClassName.get("foo.bar", "Parent")
            assertThat(type.typeName).isEqualTo(
                ParameterizedTypeName.get(
                    className,
                    ClassName.get("", "InputStreamType")
                )
            )
            assertThat(type.isDeclared()).isTrue()

            val typeArguments = type.typeArguments
            assertThat(typeArguments).hasSize(1)
            typeArguments.first().let { firstType ->
                assertThat(firstType.isDeclared()).isFalse()
                val expected = TypeVariableName.get(
                    "InputStreamType",
                    ClassName.get("java.io", "InputStream")
                )
                assertThat(firstType.typeName).isEqualTo(expected)
            }

            type.asTypeElement().getMethod("wildcardParam").let { method ->
                val wildcardParam = method.parameters.first()
                val extendsBoundOrSelf = wildcardParam.type.extendsBoundOrSelf()
                assertThat(extendsBoundOrSelf.rawType)
                    .isEqualTo(
                        it.processingEnv.requireType("java.util.Set").rawType
                    )
            }
        }
    }

    @Test
    fun errorType() {
        val missingTypeRef = Source.java(
            "foo.bar.Baz",
            """
                package foo.bar;
                public class Baz {
                    NotExistingType badField;
                    NotExistingType badMethod() {
                        throw new RuntimeException("Stub");
                    }
                }
            """.trimIndent()
        )
        runProcessorTestForFailedCompilation(
            sources = listOf(missingTypeRef)
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            element.getField("badField").let { field ->
                assertThat(field.type.isError()).isTrue()
                assertThat(field.type.typeName).isEqualTo(
                    ClassName.get("", "NotExistingType")
                )
            }
            element.getDeclaredMethod("badMethod").let { method ->
                assertThat(method.returnType.isError()).isTrue()
                assertThat(method.returnType.typeName).isEqualTo(
                    ClassName.get("", "NotExistingType")
                )
            }
        }
    }

    @Test
    fun sameType() {
        val subject = Source.java(
            "foo.bar.Baz", """
            package foo.bar;
            interface Baz {
                void method(String... inputs);
            }
        """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(subject)
        ) {
            val type = it.processingEnv.requireType("foo.bar.Baz")
            val list = it.processingEnv.requireType("java.util.List")
            val string = it.processingEnv.requireType("java.lang.String")
            assertThat(type.isSameType(type)).isTrue()
            assertThat(type.isSameType(list)).isFalse()
            assertThat(list.isSameType(string)).isFalse()
        }
    }

    @Test
    fun isCollection() {
        runProcessorTest {
            it.processingEnv.requireType("java.util.List").let { list ->
                assertThat(list.isCollection()).isTrue()
            }
            it.processingEnv.requireType("java.util.Set").let { list ->
                assertThat(list.isCollection()).isTrue()
            }
            it.processingEnv.requireType("java.util.Map").let { list ->
                assertThat(list.isCollection()).isFalse()
            }
        }
    }

    @Test
    fun toStringMatchesUnderlyingElement() {
        runProcessorTest {
            it.processingEnv.requireType("java.lang.Integer").let { map ->
                assertThat(map.toString()).isEqualTo("java.lang.Integer")
            }
        }
    }

    @Test
    fun errorTypeForSuper() {
        val missingTypeRef = Source.java(
            "foo.bar.Baz",
            """
                package foo.bar;
                public class Baz extends IDontExist {
                    NotExistingType foo() {
                        throw new RuntimeException("Stub");
                    }
                }
            """.trimIndent()
        )
        runProcessorTestForFailedCompilation(
            sources = listOf(missingTypeRef)
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            assertThat(element.superType?.isError()).isTrue()
        }
    }

    @Test
    fun defaultValues() {
        runProcessorTest {
            assertThat(
                it.processingEnv.requireType("int").defaultValue()
            ).isEqualTo("0")
            assertThat(
                it.processingEnv.requireType("java.lang.String").defaultValue()
            ).isEqualTo("null")
            assertThat(
                it.processingEnv.requireType("double").defaultValue()
            ).isEqualTo("0.0")
            assertThat(
                it.processingEnv.requireType("float").defaultValue()
            ).isEqualTo("0f")
            assertThat(
                it.processingEnv.requireType("char").defaultValue()
            ).isEqualTo("0")
        }
    }

    @Test
    fun boxed() {
        runProcessorTest {
            assertThat(
                it.processingEnv.requireType("int").boxed().typeName
            ).isEqualTo(TypeName.get(java.lang.Integer::class.java))
            assertThat(
                it.processingEnv.requireType("java.lang.String").boxed().typeName
            ).isEqualTo(TypeName.get(String::class.java))
        }
    }

    @Test
    fun rawType() {
        runProcessorTest {
            val subject = it.processingEnv.getDeclaredType(
                it.processingEnv.requireTypeElement(List::class),
                it.processingEnv.requireType(String::class)
            )
            val listClassName = ClassName.get(List::class.java)
            assertThat(subject.typeName).isEqualTo(
                ParameterizedTypeName.get(listClassName, TypeName.get(String::class.java))
            )
            assertThat(subject.rawType.typeName).isEqualTo(listClassName)

            val listOfInts = it.processingEnv.getDeclaredType(
                it.processingEnv.requireTypeElement(List::class),
                it.processingEnv.requireType(Integer::class)
            )
            assertThat(subject.rawType).isEqualTo(listOfInts.rawType)

            val setOfStrings = it.processingEnv.getDeclaredType(
                it.processingEnv.requireTypeElement(Set::class),
                it.processingEnv.requireType(String::class)
            )
            assertThat(subject.rawType).isNotEqualTo(setOfStrings.rawType)
        }
    }
}
