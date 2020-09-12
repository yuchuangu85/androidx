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

import org.junit.Ignore
import org.junit.Test

@Ignore("Pending fix for b/162464429")
class RememberIntrinsicTransformTests : ComposeIrTransformTest() {
    private fun comparisonPropagation(
        unchecked: String,
        checked: String,
        expectedTransformed: String,
        dumpTree: Boolean = false
    ) = verifyComposeIrTransform(
        """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember

            $checked
        """.trimIndent(),
        expectedTransformed,
        """
            import androidx.compose.runtime.Composable

            $unchecked
        """.trimIndent(),
        dumpTree
    )

    @Test
    fun testPassedArgs(): Unit = comparisonPropagation(
        """
            class Foo(val a: Int, val b: Int)
        """,
        """
            @Composable
            fun rememberFoo(a: Int, b: Int) = remember(a, b) { Foo(a, b) }
        """,
        """
            @Composable
            fun rememberFoo(a: Int, b: Int, %composer: Composer<*>?, %changed: Int): Foo {
              %composer.startReplaceableGroup(<>, "C(rememberFoo):Test.kt")
              val tmp0 = %composer.cache(%changed and 0b0110 === 0 && %composer.changed(a) || %changed and 0b0110 === 0b0100 or %changed and 0b00011000 === 0 && %composer.changed(b) || %changed and 0b00011000 === 0b00010000) {
                val tmp0_return = Foo(a, b)
                tmp0_return
              }
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testNoArgs(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun A(){}
        """,
        """
            @Composable
            fun Test() {
                val foo = remember { Foo() }
                val bar = remember { Foo() }
                A()
                val bam = remember { Foo() }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A()>,<rememb...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val foo = %composer.cache(false) {
                  val tmp0_return = Foo()
                  tmp0_return
                }
                val bar = %composer.cache(false) {
                  val tmp0_return = Foo()
                  tmp0_return
                }
                A(%composer, 0)
                val bam = remember({
                  val tmp0_return = Foo()
                  tmp0_return
                }, %composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testNonArgs(): Unit = comparisonPropagation(
        """
            class Foo(val a: Int, val b: Int)
            fun someInt(): Int = 123
        """,
        """
            @Composable
            fun Test() {
                val a = someInt()
                val b = someInt()
                val foo = remember(a, b) { Foo(a, b) }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test):Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val a = someInt()
                val b = someInt()
                val foo = %composer.cache(%composer.changed(a) or %composer.changed(b)) {
                  val tmp0_return = Foo(a, b)
                  tmp0_return
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testComposableCallInArgument(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun CInt(): Int { return 123 }
        """,
        """
            @Composable
            fun Test() {
                val foo = remember(CInt()) { Foo() }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<CInt()...>,<rememb...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val foo = remember(CInt(%composer, 0), {
                  val tmp0_return = Foo()
                  tmp0_return
                }, %composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testAmbientCallBeforeRemember(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.ambientOf

            class Foo
            class Bar
            val ambientBar = ambientOf<Bar> { Bar() }
        """,
        """
            @Composable
            fun Test() {
                val bar = ambientBar.current
                val foo = remember(bar) { Foo() }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<curren...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val bar = ambientBar.current
                val foo = %composer.cache(%composer.changed(bar)) {
                  val tmp0_return = Foo()
                  tmp0_return
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testAmbientCallAsInput(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.ambientOf

            class Foo
            class Bar
            val ambientBar = ambientOf<Bar> { Bar() }
        """,
        """
            @Composable
            fun Test() { 
                val foo = remember(ambientBar.current) { Foo() }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<curren...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val foo = %composer.cache(%composer.changed(ambientBar.current)) {
                  val tmp0_return = Foo()
                  tmp0_return
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testComposableCallBeforeRemember(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun A() { }
        """,
        """
            @Composable
            fun Test() {
                A()
                val foo = remember { Foo() }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A()>,<rememb...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                A(%composer, 0)
                val foo = remember({
                  val tmp0_return = Foo()
                  tmp0_return
                }, %composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testRememberInsideOfIf(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun A() {}
        """,
        """
            @Composable
            fun Test(condition: Boolean) {
                A()
                if (condition) {
                    val foo = remember { Foo() }
                }
            }
        """,
        """
            @Composable
            fun Test(condition: Boolean, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A()>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(condition)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                A(%composer, 0)
                if (condition) {
                  %composer.startReplaceableGroup(<>)
                  val foo = %composer.cache(false) {
                    val tmp0_return = Foo()
                    tmp0_return
                  }
                  %composer.endReplaceableGroup()
                } else {
                  %composer.startReplaceableGroup(<>)
                  %composer.endReplaceableGroup()
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(condition, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testRememberInsideOfIfWithComposableCallBefore(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun A() {}
        """,
        """
            @Composable
            fun Test(condition: Boolean) {
                if (condition) {
                    A()
                    val foo = remember { Foo() }
                }
            }
        """,
        """
            @Composable
            fun Test(condition: Boolean, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(condition)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (condition) {
                  %composer.startReplaceableGroup(<>, "<A()>,<rememb...>")
                  A(%composer, 0)
                  val foo = remember({
                    val tmp0_return = Foo()
                    tmp0_return
                  }, %composer, 0)
                  %composer.endReplaceableGroup()
                } else {
                  %composer.startReplaceableGroup(<>)
                  %composer.endReplaceableGroup()
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(condition, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testRememberInsideOfWhileWithOnlyRemembers(): Unit = comparisonPropagation(
        """
            class Foo
        """,
        """
            @Composable
            fun Test(items: List<Int>) {
                for (item in items) {
                    val foo = remember { Foo() }
                    print(foo)
                    print(item)
                }
            }
        """,
        """
            @Composable
            fun Test(items: List<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)*<rememb...>:Test.kt")
              val tmp0_iterator = items.iterator()
              while (tmp0_iterator.hasNext()) {
                val item = tmp0_iterator.next()
                val foo = remember({
                  val tmp0_return = Foo()
                  tmp0_return
                }, %composer, 0)
                print(foo)
                print(item)
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(items, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testRememberInsideOfWhileWithCallsAfter(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun A() {}
        """,
        """
            @Composable
            fun Test(items: List<Int>) {
                for (item in items) {
                    val foo = remember { Foo() }
                    A()
                    print(foo)
                    print(item)
                }
            }
        """,
        """
            @Composable
            fun Test(items: List<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)*<rememb...>,<A()>:Test.kt")
              val tmp0_iterator = items.iterator()
              while (tmp0_iterator.hasNext()) {
                val item = tmp0_iterator.next()
                val foo = remember({
                  val tmp0_return = Foo()
                  tmp0_return
                }, %composer, 0)
                A(%composer, 0)
                print(foo)
                print(item)
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(items, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testZeroArgRemember(): Unit = comparisonPropagation(
        """
            class Foo
        """,
        """
            @Composable
            fun Test(items: List<Int>) {
                val foo = remember { Foo() }
            }
        """,
        """
            @Composable
            fun Test(items: List<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test):Test.kt")
              val foo = %composer.cache(false) {
                val tmp0_return = Foo()
                tmp0_return
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(items, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testRememberWithNArgs(): Unit = comparisonPropagation(
        """
            class Foo
            class Bar
        """,
        """
            @Composable
            fun Test(a: Int, b: Int, c: Bar, d: Boolean) {
                val foo = remember(a, b, c, d) { Foo() }
            }
        """,
        """
            @Composable
            fun Test(a: Int, b: Int, c: Bar, d: Boolean, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test):Test.kt")
              val foo = %composer.cache(%changed and 0b0110 === 0 && %composer.changed(a) || %changed and 0b0110 === 0b0100 or %changed and 0b00011000 === 0 && %composer.changed(b) || %changed and 0b00011000 === 0b00010000 or %changed and 0b01100000 === 0 && %composer.changed(c) || %changed and 0b01100000 === 0b01000000 or %changed and 0b000110000000 === 0 && %composer.changed(d) || %changed and 0b000110000000 === 0b000100000000) {
                val tmp0_return = Foo()
                tmp0_return
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(a, b, c, d, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testVarargWithSpread(): Unit = comparisonPropagation(
        """
            class Foo
            class Bar
        """,
        """
            @Composable
            fun Test(items: Array<Bar>) {
                val foo = remember(*items) { Foo() }
            }
        """,
        """
            @Composable
            fun Test(items: Array<Bar>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<rememb...>:Test.kt")
              val foo = remember(*items, {
                val tmp0_return = Foo()
                tmp0_return
              }, %composer, 0)
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(items, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testRememberWithInlineClassInput(): Unit = comparisonPropagation(
        """
            class Foo
            inline class InlineInt(val value: Int)
        """,
        """
            @Composable
            fun Test(inlineInt: InlineInt) {
                val a = InlineInt(123)
                val foo = remember(inlineInt, a) { Foo() }
            }
        """,
        """
            @Composable
            fun Test(inlineInt: InlineInt, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)P(0:InlineInt):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(inlineInt.value)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                val a = InlineInt(123)
                val foo = %composer.cache(%dirty and 0b0110 === 0b0100 or false) {
                  val tmp0_return = Foo()
                  tmp0_return
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(inlineInt, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testMultipleRememberCallsInARow(): Unit = comparisonPropagation(
        """
            class Foo(val a: Int, val b: Int)
            fun someInt(): Int = 123
        """,
        """
            @Composable
            fun Test() {
                val a = someInt()
                val b = someInt()
                val foo = remember(a, b) { Foo(a, b) }
                val c = someInt()
                val d = someInt()
                val bar = remember(c, d) { Foo(c, d) }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test):Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val a = someInt()
                val b = someInt()
                val foo = %composer.cache(%composer.changed(a) or %composer.changed(b)) {
                  val tmp0_return = Foo(a, b)
                  tmp0_return
                }
                val c = someInt()
                val d = someInt()
                val bar = %composer.cache(%composer.changed(c) or %composer.changed(d)) {
                  val tmp0_return = Foo(c, d)
                  tmp0_return
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testParamAndNonParamInputsInRestartableFunction(): Unit = comparisonPropagation(
        """
            class Foo(val a: Int, val b: Int)
            fun someInt(): Int = 123
        """,
        """
            @Composable
            fun Test(a: Int) {
                val b = someInt()
                val foo = remember(a, b) { Foo(a, b) }
            }
        """,
        """
            @Composable
            fun Test(a: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(a)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                val b = someInt()
                val foo = %composer.cache(%dirty and 0b0110 === 0b0100 or %composer.changed(b)) {
                  val tmp0_return = Foo(a, b)
                  tmp0_return
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(a, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testParamAndNonParamInputsInDirectFunction(): Unit = comparisonPropagation(
        """
            class Foo(val a: Int, val b: Int)
            fun someInt(): Int = 123
        """,
        """
            @Composable
            fun Test(a: Int): Foo {
                val b = someInt()
                return remember(a, b) { Foo(a, b) }
            }
        """,
        """
            @Composable
            fun Test(a: Int, %composer: Composer<*>?, %changed: Int): Foo {
              %composer.startReplaceableGroup(<>, "C(Test):Test.kt")
              val b = someInt()
              val tmp0 = %composer.cache(%changed and 0b0110 === 0 && %composer.changed(a) || %changed and 0b0110 === 0b0100 or %composer.changed(b)) {
                val tmp0_return = Foo(a, b)
                tmp0_return
              }
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )
}