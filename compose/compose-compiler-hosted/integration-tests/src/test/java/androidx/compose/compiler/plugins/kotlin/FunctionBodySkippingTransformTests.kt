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

import org.junit.Test

class FunctionBodySkippingTransformTests : ComposeIrTransformTest() {
    private fun comparisonPropagation(
        unchecked: String,
        checked: String,
        expectedTransformed: String,
        dumpTree: Boolean = false
    ) = verifyComposeIrTransform(
        """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.ComposableContract

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
    fun testIfInLambda(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0) {}
            @Composable fun Wrap(children: @Composable () -> Unit) {
                children()
            }
        """,
        """
            @Composable
            fun Test(x: Int = 0, y: Int = 0) {
                Wrap {
                    if (x > 0) {
                        A(x)
                    } else {
                        A(x)
                    }
                }
            }
        """,
        """
            @Composable
            fun Test(x: Int, y: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<Wrap>:Test.kt")
              val %dirty = %changed
              val x = x
              val y = y
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(y)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  x = 0
                }
                if (%default and 0b0010 !== 0) {
                  y = 0
                }
                Wrap(composableLambda(%composer, <>, true, "C:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    if (x > 0) {
                      %composer.startReplaceableGroup(<>, "<A(x)>")
                      A(x, 0, %composer, 0b0110 and %dirty, 0b0010)
                      %composer.endReplaceableGroup()
                    } else {
                      %composer.startReplaceableGroup(<>, "<A(x)>")
                      A(x, 0, %composer, 0b0110 and %dirty, 0b0010)
                      %composer.endReplaceableGroup()
                    }
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, y, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testUntrackedLambda(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0) {}
            @Composable fun Wrap(children: @Composable () -> Unit) {
                children()
            }
        """,
        """
            @Composable
            fun Test(x: Int = 0, y: Int = 0) {
                Wrap @ComposableContract(tracked = false) {
                    A(x)
                }
            }
        """,
        """
            @Composable
            fun Test(x: Int, y: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<Wrap>:Test.kt")
              val %dirty = %changed
              val x = x
              val y = y
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(y)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  x = 0
                }
                if (%default and 0b0010 !== 0) {
                  y = 0
                }
                Wrap(composableLambda(%composer, <>, false, "C:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  %composer.startReplaceableGroup(<>, "<A(x)>")
                  A(x, 0, %composer, 0b0110 and %dirty, 0b0010)
                  %composer.endReplaceableGroup()
                }, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, y, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testSimpleColumn(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Stable
            import androidx.compose.runtime.Immutable

            @Stable
            interface Modifier {
              companion object : Modifier { }
            }

            @Immutable
            interface Arrangement {
              @Immutable
              interface Vertical : Arrangement

              object Top : Vertical
            }

            enum class LayoutOrientation {
                Horizontal,
                Vertical
            }

            enum class SizeMode {
                Wrap,
                Expand
            }

            @Immutable
            data class Alignment(
                val verticalBias: Float,
                val horizontalBias: Float
            ) {
                @Immutable
                data class Horizontal(val bias: Float)

                companion object {
                  val Start = Alignment.Horizontal(-1f)
                }
            }
        """,
        """
            @Composable
            fun RowColumnImpl(
              orientation: LayoutOrientation,
              modifier: Modifier = Modifier,
              arrangement: Arrangement.Vertical = Arrangement.Top,
              crossAxisAlignment: Alignment.Horizontal = Alignment.Start,
              crossAxisSize: SizeMode = SizeMode.Wrap,
              children: @Composable() ()->Unit
            ) {
              println()
            }

            @Composable
            fun Column(
                modifier: Modifier = Modifier,
                verticalArrangement: Arrangement.Vertical = Arrangement.Top,
                horizontalGravity: Alignment.Horizontal = Alignment.Start,
                children: @Composable() ()->Unit
            ) {
              RowColumnImpl(
                orientation = LayoutOrientation.Vertical,
                arrangement = verticalArrangement,
                crossAxisAlignment = horizontalGravity,
                crossAxisSize = SizeMode.Wrap,
                modifier = modifier,
                children = children
              )
            }
        """,
        """
            @Composable
            fun RowColumnImpl(orientation: LayoutOrientation, modifier: Modifier?, arrangement: Vertical?, crossAxisAlignment: Horizontal?, crossAxisSize: SizeMode?, children: Function2<Composer<*>, Int, Unit>, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(RowColumnImpl)P(5,4!1,2,3):Test.kt")
              val %dirty = %changed
              val modifier = modifier
              val arrangement = arrangement
              val crossAxisAlignment = crossAxisAlignment
              val crossAxisSize = crossAxisSize
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(orientation)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(modifier)) 0b00010000 else 0b1000
              }
              if (%default and 0b0100 !== 0) {
                %dirty = %dirty or 0b01100000
              } else if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(arrangement)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%default and 0b1000 === 0 && %composer.changed(crossAxisAlignment)) 0b000100000000 else 0b10000000
              }
              if (%default and 0b00010000 !== 0) {
                %dirty = %dirty or 0b011000000000
              } else if (%changed and 0b011000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(crossAxisSize)) 0b010000000000 else 0b001000000000
              }
              if (%default and 0b00100000 !== 0) {
                %dirty = %dirty or 0b0001100000000000
              } else if (%changed and 0b0001100000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(children)) 0b0001000000000000 else 0b100000000000
              }
              if (%dirty and 0b101010101011 xor 0b101010101010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0010 !== 0) {
                    modifier = Companion
                  }
                  if (%default and 0b0100 !== 0) {
                    arrangement = Top
                  }
                  if (%default and 0b1000 !== 0) {
                    crossAxisAlignment = Companion.Start
                    %dirty = %dirty and 0b000110000000.inv()
                  }
                  if (%default and 0b00010000 !== 0) {
                    crossAxisSize = SizeMode.Wrap
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b1000 !== 0) {
                    %dirty = %dirty and 0b000110000000.inv()
                  }
                }
                println()
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                RowColumnImpl(orientation, modifier, arrangement, crossAxisAlignment, crossAxisSize, children, %composer, %changed or 0b0001, %default)
              }
            }
            @Composable
            fun Column(modifier: Modifier?, verticalArrangement: Vertical?, horizontalGravity: Horizontal?, children: Function2<Composer<*>, Int, Unit>, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Column)P(2,3,1)<RowCol...>:Test.kt")
              val %dirty = %changed
              val modifier = modifier
              val verticalArrangement = verticalArrangement
              val horizontalGravity = horizontalGravity
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(modifier)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(verticalArrangement)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%default and 0b0100 === 0 && %composer.changed(horizontalGravity)) 0b01000000 else 0b00100000
              }
              if (%default and 0b1000 !== 0) {
                %dirty = %dirty or 0b000110000000
              } else if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(children)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    modifier = Companion
                  }
                  if (%default and 0b0010 !== 0) {
                    verticalArrangement = Top
                  }
                  if (%default and 0b0100 !== 0) {
                    horizontalGravity = Companion.Start
                    %dirty = %dirty and 0b01100000.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0100 !== 0) {
                    %dirty = %dirty and 0b01100000.inv()
                  }
                }
                val tmp0_orientation = LayoutOrientation.Vertical
                val tmp1_crossAxisSize = SizeMode.Wrap
                RowColumnImpl(tmp0_orientation, modifier, verticalArrangement, horizontalGravity, tmp1_crossAxisSize, children, %composer, 0b011000000110 or 0b00011000 and %dirty shl 0b0010 or 0b01100000 and %dirty shl 0b0010 or 0b000110000000 and %dirty shl 0b0010 or 0b0001100000000000 and %dirty shl 0b0100, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Column(modifier, verticalArrangement, horizontalGravity, children, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testSimplerBox(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Stable

            @Stable
            interface Modifier {
              companion object : Modifier { }
            }
        """,
        """
            @Composable
            fun SimpleBox(modifier: Modifier = Modifier) {
               println()
            }
        """,
        """
            @Composable
            fun SimpleBox(modifier: Modifier?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(SimpleBox):Test.kt")
              val %dirty = %changed
              val modifier = modifier
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(modifier)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  modifier = Companion
                }
                println()
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                SimpleBox(modifier, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testDefaultSkipping(): Unit = comparisonPropagation(
        """
            fun newInt(): Int = 123
        """,
        """
            @Composable
            fun Example(a: Int = newInt()) {
               print(a)
            }
        """,
        """
            @Composable
            fun Example(a: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Example):Test.kt")
              val %dirty = %changed
              val a = a
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%default and 0b0001 === 0 && %composer.changed(a)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    a = newInt()
                    %dirty = %dirty and 0b0110.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0001 !== 0) {
                    %dirty = %dirty and 0b0110.inv()
                  }
                }
                print(a)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(a, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testSimpleBoxWithShape(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Stable

            @Stable
            interface Modifier {
              companion object : Modifier { }
            }

            interface Shape {
            }

            val RectangleShape = object : Shape { }
        """,
        """
            @Composable
            fun SimpleBox(modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
               println()
            }
        """,
        """
            @Composable
            fun SimpleBox(modifier: Modifier?, shape: Shape?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(SimpleBox):Test.kt")
              val %dirty = %changed
              val modifier = modifier
              val shape = shape
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(modifier)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b1000
              }
              if (%default.inv() and 0b0010 !== 0 || %dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    modifier = Companion
                  }
                  if (%default and 0b0010 !== 0) {
                    shape = RectangleShape
                    %dirty = %dirty and 0b00011000.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0010 !== 0) {
                    %dirty = %dirty and 0b00011000.inv()
                  }
                }
                println()
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                SimpleBox(modifier, shape, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testSimpleBox(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Stable

            @Stable
            interface Modifier {
              companion object : Modifier { }
            }
        """,
        """
            @Composable
            fun SimpleBox(modifier: Modifier = Modifier, children: @Composable() () -> Unit = {}) {
               println()
            }
        """,
        """
            @Composable
            fun SimpleBox(modifier: Modifier?, children: Function2<Composer<*>, Int, Unit>?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(SimpleBox)P(1):Test.kt")
              val %dirty = %changed
              val modifier = modifier
              val children = children
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(modifier)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(children)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  modifier = Companion
                }
                if (%default and 0b0010 !== 0) {
                  children = composableLambda(%composer, <>, true, "C:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                    if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                      Unit
                    } else {
                      %composer.skipToGroupEnd()
                    }
                  }
                }
                println()
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                SimpleBox(modifier, children, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testComposableLambdaWithStableParams(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Immutable

            @Immutable class Foo
            @Composable fun A(x: Int) {}
            @Composable fun B(y: Foo) {}
        """,
        """
            val foo = @Composable { x: Int, y: Foo ->
                A(x)
                B(y)
            }
        """,
        """
            val foo: Function4<Int, Foo, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { x: Int, y: Foo, %composer: Composer<*>?, %changed: Int ->
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(y)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b00101011 xor 0b00101010 !== 0 || !%composer.skipping) {
                A(x, %composer, 0b0110 and %dirty)
                B(y, %composer, 0b0110 and %dirty shr 0b0010)
              } else {
                %composer.skipToGroupEnd()
              }
            }
        """
    )

    @Test
    fun testComposableLambdaWithUnstableParams(): Unit = comparisonPropagation(
        """
            class Foo
            @Composable fun A(x: Int) {}
            @Composable fun B(y: Foo) {}
        """,
        """
            val foo = @Composable { x: Int, y: Foo ->
                A(x)
                B(y)
            }
        """,
        """
            val foo: Function4<Int, Foo, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { x: Int, y: Foo, %composer: Composer<*>?, %changed: Int ->
              A(x, %composer, 0b0110 and %changed)
              B(y, %composer, 0b0110 and %changed shr 0b0010)
            }
        """
    )

    @Test
    fun testComposableLambdaWithStableParamsAndReturnValue(): Unit = comparisonPropagation(
        """
        """,
        """
            @Composable fun SomeThing(children: @Composable() () -> Unit) {}

            @Composable
            fun Example() {
                SomeThing {
                    val id = object {}
                }
            }
        """,
        """
            @Composable
            fun SomeThing(children: Function2<Composer<*>, Int, Unit>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(SomeThing):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(children)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                SomeThing(children, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Example)<SomeTh...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                SomeThing(composableLambda(%composer, <>, true, "C:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    val id = object
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testPrimitiveVarargParams(): Unit = comparisonPropagation(
        """
        """,
        """
            @Composable
            fun B(vararg values: Int) {
                print(values)
            }
        """,
        """
            @Composable
            fun B(values: IntArray, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(B):Test.kt")
              val %dirty = %changed
              %composer.startReplaceableGroup(values.size)
              val tmp0_iterator = values.iterator()
              while (tmp0_iterator.hasNext()) {
                val value = tmp0_iterator.next()
                %dirty = %dirty or if (%composer.changed(value)) 0b0100 else 0
              }
              %composer.endReplaceableGroup()
              if (%dirty and 0b0110 === 0) {
                %dirty = %dirty or 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                print(values)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                B(*values, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
fun testStableVarargParams(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Immutable
            @Immutable class Foo
        """,
        """
            @Composable
            fun B(vararg values: Foo) {
                print(values)
            }
        """,
        """
            @Composable
            fun B(values: Array<out Foo>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(B):Test.kt")
              val %dirty = %changed
              %composer.startReplaceableGroup(values.size)
              val tmp0_iterator = values.iterator()
              while (tmp0_iterator.hasNext()) {
                val value = tmp0_iterator.next()
                %dirty = %dirty or if (%composer.changed(value)) 0b0100 else 0
              }
              %composer.endReplaceableGroup()
              if (%dirty and 0b0110 === 0) {
                %dirty = %dirty or 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                print(values)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                B(*values, %composer, %changed or 0b0001)
              }
            }

        """
    )

    @Test
    fun testUnstableVarargParams(): Unit = comparisonPropagation(
        """
            class Foo
        """,
        """
            @Composable
            fun B(vararg values: Foo) {
                print(values)
            }
        """,
        """
            @Composable
            fun B(values: Array<out Foo>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(B):Test.kt")
              print(values)
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                B(*values, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testReceiverParamSkippability(): Unit = comparisonPropagation(
        """
        """,
        """
            class Foo {
             val counter: Int = 0
             @Composable fun A() {
                print("hello world")
             }
             @Composable fun B() {
                print(counter)
             }
            }
        """,
        """
            class Foo {
              val counter: Int = 0
              @Composable
              fun A(%composer: Composer<*>?, %changed: Int) {
                %composer.startRestartGroup(<>, "C(A):Test.kt")
                val %dirty = %changed
                %dirty = %dirty or 0b0110
                if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                  print("hello world")
                } else {
                  %composer.skipToGroupEnd()
                }
                val tmp0_rcvr = <this>
                %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                  tmp0_rcvr.A(%composer, %changed or 0b0001)
                }
              }
              @Composable
              fun B(%composer: Composer<*>?, %changed: Int) {
                %composer.startRestartGroup(<>, "C(B):Test.kt")
                val %dirty = %changed
                print(counter)
                val tmp0_rcvr = <this>
                %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                  tmp0_rcvr.B(%composer, %changed or 0b0001)
                }
              }
            }
        """
    )

    @Test
    fun testComposableParameter(): Unit = comparisonPropagation(
        """
            @Composable fun makeInt(): Int = 10
        """,
        """
            @Composable
            fun Example(a: Int = 0, b: Int = makeInt(), c: Int = 0) {

            }
        """,
        """
            @Composable
            fun Example(a: Int, b: Int, c: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Example)<makeIn...>:Test.kt")
              val %dirty = %changed
              val a = a
              val b = b
              val c = c
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(a)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%default and 0b0010 === 0 && %composer.changed(b)) 0b00010000 else 0b1000
              }
              if (%default and 0b0100 !== 0) {
                %dirty = %dirty or 0b01100000
              } else if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(c)) 0b01000000 else 0b00100000
              }
              if (%dirty and 0b00101011 xor 0b00101010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    a = 0
                  }
                  if (%default and 0b0010 !== 0) {
                    b = makeInt(%composer, 0)
                    %dirty = %dirty and 0b00011000.inv()
                  }
                  if (%default and 0b0100 !== 0) {
                    c = 0
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0010 !== 0) {
                    %dirty = %dirty and 0b00011000.inv()
                  }
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(a, b, c, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testComposableWithAndWithoutDefaultParams(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0) {}
        """,
        """
            @Composable fun Wrap(y: Int, children: @Composable (x: Int) -> Unit) {
                children(y)
            }
            @Composable
            fun Test(x: Int = 0, y: Int = 0) {
                Wrap(10) {
                    A(x)
                }
            }
        """,
        """
            @Composable
            fun Wrap(y: Int, children: Function3<@[ParameterName(name = 'x')] Int, Composer<*>, Int, Unit>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Wrap)P(1)<childr...>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(y)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(children)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                children(y, %composer, 0b0110 and %dirty or 0b00011000 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Wrap(y, children, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test(x: Int, y: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<Wrap(1...>:Test.kt")
              val %dirty = %changed
              val x = x
              val y = y
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(y)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  x = 0
                }
                if (%default and 0b0010 !== 0) {
                  y = 0
                }
                Wrap(10, composableLambda(%composer, <>, true, "C<A(x)>:Test.kt") { it: Int, %composer: Composer<*>?, %changed: Int ->
                  val %dirty = %changed
                  if (%changed and 0b0110 === 0) {
                    %dirty = %dirty or if (%composer.changed(it)) 0b0100 else 0b0010
                  }
                  if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                    A(x, 0, %composer, 0b0110 and %dirty, 0b0010)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b00011110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, y, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testComposableWithReturnValue(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0) {}
        """,
        """
            @Composable
            fun Test(x: Int = 0, y: Int = 0): Int {
                A(x, y)
                return x + y
            }
        """,
        """
            @Composable
            fun Test(x: Int, y: Int, %composer: Composer<*>?, %changed: Int, %default: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Test)<A(x,>:Test.kt")
              val x = if (%default and 0b0001 !== 0) 0 else x
              val y = if (%default and 0b0010 !== 0) 0 else y
              A(x, y, %composer, 0b0110 and %changed or 0b00011000 and %changed, 0)
              val tmp0 = x + y
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testComposableLambda(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0) {}
        """,
        """
            val test = @Composable { x: Int ->
                A(x)
            }
        """,
        """
            val test: Function3<Int, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { x: Int, %composer: Composer<*>?, %changed: Int ->
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                A(x, 0, %composer, 0b0110 and %dirty, 0b0010)
              } else {
                %composer.skipToGroupEnd()
              }
            }
        """
    )

    @Test
    fun testComposableFunExprBody(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0): Int { return 10 }
        """,
        """
            @Composable fun Test(x: Int) = A()
        """,
        """
            @Composable
            fun Test(x: Int, %composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Test)<A()>:Test.kt")
              val tmp0 = A(0, 0, %composer, 0, 0b0011)
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testParamReordering(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0): Int { return 10 }
        """,
        """
            @Composable fun Test(x: Int, y: Int) {
                A(y = y, x = x)
            }
        """,
        """
            @Composable
            fun Test(x: Int, y: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A(y>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(y)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                A(x, y, %composer, 0b0110 and %dirty or 0b00011000 and %dirty, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, y, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testStableUnstableParams(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int = 0, y: Int = 0): Int { return 10 }
            class Foo
        """,
        """
            @Composable fun CanSkip(a: Int = 0, b: Foo = Foo()) {
                print("Hello World")
            }
            @Composable fun CannotSkip(a: Int, b: Foo) {
                print("Hello World")
            }
            @Composable fun NoParams() {
                print("Hello World")
            }
        """,
        """
            @Composable
            fun CanSkip(a: Int, b: Foo?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(CanSkip):Test.kt")
              val %dirty = %changed
              val a = a
              val b = b
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(a)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b1000
              }
              if (%default.inv() and 0b0010 !== 0 || %dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    a = 0
                  }
                  if (%default and 0b0010 !== 0) {
                    b = Foo()
                    %dirty = %dirty and 0b00011000.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0010 !== 0) {
                    %dirty = %dirty and 0b00011000.inv()
                  }
                }
                print("Hello World")
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                CanSkip(a, b, %composer, %changed or 0b0001, %default)
              }
            }
            @Composable
            fun CannotSkip(a: Int, b: Foo, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(CannotSkip):Test.kt")
              print("Hello World")
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                CannotSkip(a, b, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun NoParams(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(NoParams):Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                print("Hello World")
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                NoParams(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testNoParams(): Unit = comparisonPropagation(
        """
            @Composable fun A() {}
        """,
        """
            @Composable
            fun Test() {
                A()
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A()>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                A(%composer, 0)
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
    fun testSingleStableParam(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int) {}
        """,
        """
            @Composable
            fun Test(x: Int) {
                A(x)
            }
        """,
        """
            @Composable
            fun Test(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A(x)>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                A(x, %composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testInlineClassDefaultParameter(): Unit = comparisonPropagation(
        """
            inline class Color(val value: Int) {
                companion object {
                    val Unset = Color(0)
                }
            }
        """,
        """
            @Composable
            fun A(text: String) {
                B(text)
            }

            @Composable
            fun B(text: String, color: Color = Color.Unset) {}
        """,
        """
            @Composable
            fun A(text: String, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(A)<B(text...>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(text)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                B(text, Color(0), %composer, 0b0110 and %dirty, 0b0010)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                A(text, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun B(text: String, color: Color, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(B)P(1,0:Color):Test.kt")
              val %dirty = %changed
              val color = color
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(text)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%default and 0b0010 === 0 && %composer.changed(color.value)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0010 !== 0) {
                    color = Companion.Unset
                    %dirty = %dirty and 0b00011000.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0010 !== 0) {
                    %dirty = %dirty and 0b00011000.inv()
                  }
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                B(text, color, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testStaticDetection(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Stable

            enum class Foo {
                Bar,
                Bam
            }
            const val constInt: Int = 123
            val normInt = 345
            val stableTopLevelProp: Modifier = Modifier
            @Composable fun C(x: Any?) {}
            @Stable
            interface Modifier {
              companion object : Modifier { }
            }
            inline class Dp(val value: Int)
            @Stable
            fun stableFun(x: Int): Int = x * x
            @Stable
            operator fun Dp.plus(other: Dp): Dp = Dp(this.value + other.value)
            @Stable
            val Int.dp: Dp get() = Dp(this)
            @Composable fun D(content: @Composable() () -> Unit) {}
        """,
        """
            // all of these should result in 0b0110
            @Composable fun A() {
                val x = 123
                D {}
                C({})
                C(stableFun(123))
                C(16.dp + 10.dp)
                C(Dp(16))
                C(16.dp)
                C(normInt)
                C(Int.MAX_VALUE)
                C(stableTopLevelProp)
                C(Modifier)
                C(Foo.Bar)
                C(constInt)
                C(123)
                C(123 + 345)
                C(x)
                C(x * 123)
            }
            // all of these should result in 0b0000
            @Composable fun B() {
                C(Math.random())
                C(Math.random() / 100f)
            }
        """,
        """
            @Composable
            fun A(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(A)<D>,<{}>,<C({})>,<C(stab...>,<C(16.d...>,<C(Dp(1...>,<C(16.d...>,<C(norm...>,<C(Int....>,<C(stab...>,<C(Modi...>,<C(Foo....>,<C(cons...>,<C(123)>,<C(123>,<C(x)>,<C(x>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                val x = 123
                D(composableLambda(%composer, <>, true, "C:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    Unit
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
                C(remember({
                  {
                  }
                }, %composer, 0), %composer, 0b0110)
                C(stableFun(123), %composer, 0b0110)
                C(16.dp + 10.dp, %composer, 0b0110)
                C(Dp(16), %composer, 0b0110)
                C(16.dp, %composer, 0b0110)
                C(normInt, %composer, 0b0110)
                C(Companion.MAX_VALUE, %composer, 0b0110)
                C(stableTopLevelProp, %composer, 0b0110)
                C(Companion, %composer, 0b0110)
                C(Foo.Bar, %composer, 0b0110)
                C(constInt, %composer, 0b0110)
                C(123, %composer, 0b0110)
                C(123 + 345, %composer, 0b0110)
                C(x, %composer, 0b0110)
                C(x * 123, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                A(%composer, %changed or 0b0001)
              }
            }
            @Composable
            fun B(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(B)<C(Math...>,<C(Math...>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                C(random(), %composer, 0)
                C(random() / 100.0f, %composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                B(%composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testAnnotationChecker(): Unit = comparisonPropagation(
        """
            @Composable fun D(content: @Composable() () -> Unit) {}
        """,
        """
            @Composable fun Example() {
                D {}
            }
        """,
        """
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Example)<D>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                D(composableLambda(%composer, <>, true, "C:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    Unit
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(%composer, %changed or 0b0001)
              }
            }

        """
    )

    @Test
    fun testSingleStableParamWithDefault(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int) {}
        """,
        """
            @Composable
            fun Test(x: Int = 0) {
                A(x)
            }
        """,
        """
            @Composable
            fun Test(x: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A(x)>:Test.kt")
              val %dirty = %changed
              val x = x
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  x = 0
                }
                A(x, %composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testSingleStableParamWithComposableDefault(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Int) {}
            @Composable fun I(): Int { return 10 }
        """,
        """
            @Composable
            fun Test(x: Int = I()) {
                A(x)
            }
        """,
        """
            @Composable
            fun Test(x: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<I()>,<A(x)>:Test.kt")
              val %dirty = %changed
              val x = x
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%default and 0b0001 === 0 && %composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    x = I(%composer, 0)
                    %dirty = %dirty and 0b0110.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0001 !== 0) {
                    %dirty = %dirty and 0b0110.inv()
                  }
                }
                A(x, %composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testSingleUnstableParam(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Foo) {}
            class Foo
        """,
        """
            @Composable
            fun Test(x: Foo) {
                A(x)
            }
        """,
        """
            @Composable
            fun Test(x: Foo, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A(x)>:Test.kt")
              A(x, %composer, 0b0110 and %changed)
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testSingleUnstableParamWithDefault(): Unit = comparisonPropagation(
        """
            @Composable fun A(x: Foo) {}
            class Foo
        """,
        """
            @Composable
            fun Test(x: Foo = Foo()) {
                A(x)
            }
        """,
        """
            @Composable
            fun Test(x: Foo?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A(x)>:Test.kt")
              val %dirty = %changed
              val x = x
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0010
              }
              if (%default.inv() and 0b0001 !== 0 || %dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    x = Foo()
                    %dirty = %dirty and 0b0110.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0001 !== 0) {
                    %dirty = %dirty and 0b0110.inv()
                  }
                }
                A(x, %composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(x, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testManyNonOptionalParams(): Unit = comparisonPropagation(
        """
            @Composable fun A(a: Int, b: Boolean, c: Int, d: Foo, e: List<Int>) {}
            class Foo
        """,
        """
            @Composable
            fun Test(a: Int, b: Boolean, c: Int = 0, d: Foo = Foo(), e: List<Int> = emptyList()) {
                A(a, b, c, d, e)
            }
        """,
        """
            @Composable
            fun Test(a: Int, b: Boolean, c: Int, d: Foo?, e: List<Int>?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Test)<A(a,>:Test.kt")
              val %dirty = %changed
              val c = c
              val d = d
              val e = e
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(a)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(b)) 0b00010000 else 0b1000
              }
              if (%default and 0b0100 !== 0) {
                %dirty = %dirty or 0b01100000
              } else if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(c)) 0b01000000 else 0b00100000
              }
              if (%default and 0b1000 !== 0) {
                %dirty = %dirty or 0b10000000
              }
              if (%default and 0b00010000 !== 0) {
                %dirty = %dirty or 0b001000000000
              }
              if (%default.inv() and 0b00011000 !== 0 || %dirty and 0b001010101011 xor 0b001010101010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0100 !== 0) {
                    c = 0
                  }
                  if (%default and 0b1000 !== 0) {
                    d = Foo()
                    %dirty = %dirty and 0b000110000000.inv()
                  }
                  if (%default and 0b00010000 !== 0) {
                    e = emptyList()
                    %dirty = %dirty and 0b011000000000.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b1000 !== 0) {
                    %dirty = %dirty and 0b000110000000.inv()
                  }
                  if (%default and 0b00010000 !== 0) {
                    %dirty = %dirty and 0b011000000000.inv()
                  }
                }
                A(a, b, c, d, e, %composer, 0b0110 and %dirty or 0b00011000 and %dirty or 0b01100000 and %dirty or 0b000110000000 and %dirty or 0b011000000000 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(a, b, c, d, e, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testRecursiveCall(): Unit = comparisonPropagation(
        """
        """,
        """
            @Composable
            fun X(x: Int) {
                X(x + 1)
                X(x)
            }
        """,
        """
            @Composable
            fun X(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(X)<X(x>,<X(x)>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                X(x + 1, %composer, 0)
                X(x, %composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                X(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testDifferentParameters(): Unit = comparisonPropagation(
        """
            @Composable fun B(a: Int, b: Int, c: Int, d: Int) {}
            val fooGlobal = 10
        """,
        """
            @Composable
            fun A(x: Int) {
                B(
                    // direct parameter
                    x,
                    // transformation
                    x + 1,
                    // literal
                    123,
                    // expression with no parameter
                    fooGlobal
                )
            }
        """,
        """
            @Composable
            fun A(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(A)<B(>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                B(x, x + 1, 123, fooGlobal, %composer, 0b000111100000 or 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                A(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testReceiverLambdaCall(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.Stable

            interface Foo { val x: Int }
            @Stable
            interface StableFoo { val x: Int }
        """,
        """
            val unstableUnused: @Composable Foo.() -> Unit = {
            }
            val unstableUsed: @Composable Foo.() -> Unit = {
                print(x)
            }
            val stableUnused: @Composable StableFoo.() -> Unit = {
            }
            val stableUsed: @Composable StableFoo.() -> Unit = {
                print(x)
            }
        """,
        """
            val unstableUnused: @[ExtensionFunctionType] Function3<Foo, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { %composer: Composer<*>?, %changed: Int ->
              val %dirty = %changed
              %dirty = %dirty or 0b0110
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                Unit
              } else {
                %composer.skipToGroupEnd()
              }
            }
            val unstableUsed: @[ExtensionFunctionType] Function3<Foo, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { %composer: Composer<*>?, %changed: Int ->
              val %dirty = %changed
              print(x)
            }
            val stableUnused: @[ExtensionFunctionType] Function3<StableFoo, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { %composer: Composer<*>?, %changed: Int ->
              val %dirty = %changed
              %dirty = %dirty or 0b0110
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                Unit
              } else {
                %composer.skipToGroupEnd()
              }
            }
            val stableUsed: @[ExtensionFunctionType] Function3<StableFoo, Composer<*>, Int, Unit> = composableLambdaInstance(<>, true) { %composer: Composer<*>?, %changed: Int ->
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(<this>)) 0b0100 else 0b0010
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                print(x)
              } else {
                %composer.skipToGroupEnd()
              }
            }
        """
    )

    @Test
    fun testNestedCalls(): Unit = comparisonPropagation(
        """
            @Composable fun B(a: Int = 0, b: Int = 0, c: Int = 0) {}
            @Composable fun Provide(children: @Composable (Int) -> Unit) {}
        """,
        """
            @Composable
            fun A(x: Int) {
                Provide { y ->
                    Provide { z ->
                        B(x, y, z)
                    }
                    B(x, y)
                }
                B(x)
            }
        """,
        """
            @Composable
            fun A(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(A)<Provid...>,<B(x)>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                Provide(composableLambda(%composer, <>, true, "C<Provid...>,<B(x,>:Test.kt") { y: Int, %composer: Composer<*>?, %changed: Int ->
                  val %dirty = %changed
                  if (%changed and 0b0110 === 0) {
                    %dirty = %dirty or if (%composer.changed(y)) 0b0100 else 0b0010
                  }
                  if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                    Provide(composableLambda(%composer, <>, true, "C<B(x,>:Test.kt") { z: Int, %composer: Composer<*>?, %changed: Int ->
                      val %dirty = %changed
                      if (%changed and 0b0110 === 0) {
                        %dirty = %dirty or if (%composer.changed(z)) 0b0100 else 0b0010
                      }
                      if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                        B(x, y, z, %composer, 0b0110 and %dirty or 0b00011000 and %dirty shl 0b0010 or 0b01100000 and %dirty shl 0b0100, 0)
                      } else {
                        %composer.skipToGroupEnd()
                      }
                    }, %composer, 0b0110)
                    B(x, y, 0, %composer, 0b0110 and %dirty or 0b00011000 and %dirty shl 0b0010, 0b0100)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
                B(x, 0, 0, %composer, 0b0110 and %dirty, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                A(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testLocalFunction(): Unit = comparisonPropagation(
        """
            @Composable fun B(a: Int, b: Int) {}
        """,
        """
            @Composable
            fun A(x: Int) {
                @Composable fun foo(y: Int) {
                    B(x, y)
                }
                foo(x)
            }
        """,
        """
            @Composable
            fun A(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(A)<foo(x)>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                @Composable
                fun foo(y: Int, %composer: Composer<*>?, %changed: Int) {
                  %composer.startRestartGroup(<>, "C(foo)<B(x,>:Test.kt")
                  val %dirty = %changed
                  if (%changed and 0b0110 === 0) {
                    %dirty = %dirty or if (%composer.changed(y)) 0b0100 else 0b0010
                  }
                  if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    B(x, y, %composer, 0b0110 and %dirty or 0b00011000 and %dirty shl 0b0010)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                  %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                    foo(y, %composer, %changed or 0b0001)
                  }
                }
                foo(x, %composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                A(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun test15Parameters(): Unit = comparisonPropagation(
        """
        """,
        """
            @Composable
            fun Example(
                a00: Int = 0,
                a01: Int = 0,
                a02: Int = 0,
                a03: Int = 0,
                a04: Int = 0,
                a05: Int = 0,
                a06: Int = 0,
                a07: Int = 0,
                a08: Int = 0,
                a09: Int = 0,
                a10: Int = 0,
                a11: Int = 0,
                a12: Int = 0,
                a13: Int = 0,
                a14: Int = 0
            ) {
                // in order
                Example(
                    a00,
                    a01,
                    a02,
                    a03,
                    a04,
                    a05,
                    a06,
                    a07,
                    a08,
                    a09,
                    a10,
                    a11,
                    a12,
                    a13,
                    a14
                )
                // in opposite order
                Example(
                    a14,
                    a13,
                    a12,
                    a11,
                    a10,
                    a09,
                    a08,
                    a07,
                    a06,
                    a05,
                    a04,
                    a03,
                    a02,
                    a01,
                    a00
                )
            }
        """,
        """
            @Composable
            fun Example(a00: Int, a01: Int, a02: Int, a03: Int, a04: Int, a05: Int, a06: Int, a07: Int, a08: Int, a09: Int, a10: Int, a11: Int, a12: Int, a13: Int, a14: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Example)<Exampl...>,<Exampl...>:Test.kt")
              val %dirty = %changed
              val a00 = a00
              val a01 = a01
              val a02 = a02
              val a03 = a03
              val a04 = a04
              val a05 = a05
              val a06 = a06
              val a07 = a07
              val a08 = a08
              val a09 = a09
              val a10 = a10
              val a11 = a11
              val a12 = a12
              val a13 = a13
              val a14 = a14
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(a00)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(a01)) 0b00010000 else 0b1000
              }
              if (%default and 0b0100 !== 0) {
                %dirty = %dirty or 0b01100000
              } else if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(a02)) 0b01000000 else 0b00100000
              }
              if (%default and 0b1000 !== 0) {
                %dirty = %dirty or 0b000110000000
              } else if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a03)) 0b000100000000 else 0b10000000
              }
              if (%default and 0b00010000 !== 0) {
                %dirty = %dirty or 0b011000000000
              } else if (%changed and 0b011000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a04)) 0b010000000000 else 0b001000000000
              }
              if (%default and 0b00100000 !== 0) {
                %dirty = %dirty or 0b0001100000000000
              } else if (%changed and 0b0001100000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a05)) 0b0001000000000000 else 0b100000000000
              }
              if (%default and 0b01000000 !== 0) {
                %dirty = %dirty or 0b0110000000000000
              } else if (%changed and 0b0110000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a06)) 0b0100000000000000 else 0b0010000000000000
              }
              if (%default and 0b10000000 !== 0) {
                %dirty = %dirty or 0b00011000000000000000
              } else if (%changed and 0b00011000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a07)) 0b00010000000000000000 else 0b1000000000000000
              }
              if (%default and 0b000100000000 !== 0) {
                %dirty = %dirty or 0b01100000000000000000
              } else if (%changed and 0b01100000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a08)) 0b01000000000000000000 else 0b00100000000000000000
              }
              if (%default and 0b001000000000 !== 0) {
                %dirty = %dirty or 0b000110000000000000000000
              } else if (%changed and 0b000110000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a09)) 0b000100000000000000000000 else 0b10000000000000000000
              }
              if (%default and 0b010000000000 !== 0) {
                %dirty = %dirty or 0b011000000000000000000000
              } else if (%changed and 0b011000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a10)) 0b010000000000000000000000 else 0b001000000000000000000000
              }
              if (%default and 0b100000000000 !== 0) {
                %dirty = %dirty or 0b0001100000000000000000000000
              } else if (%changed and 0b0001100000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a11)) 0b0001000000000000000000000000 else 0b100000000000000000000000
              }
              if (%default and 0b0001000000000000 !== 0) {
                %dirty = %dirty or 0b0110000000000000000000000000
              } else if (%changed and 0b0110000000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a12)) 0b0100000000000000000000000000 else 0b0010000000000000000000000000
              }
              if (%default and 0b0010000000000000 !== 0) {
                %dirty = %dirty or 0b00011000000000000000000000000000
              } else if (%changed and 0b00011000000000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a13)) 0b00010000000000000000000000000000 else 0b1000000000000000000000000000
              }
              if (%default and 0b0100000000000000 !== 0) {
                %dirty = %dirty or 0b01100000000000000000000000000000
              } else if (%changed and 0b01100000000000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a14)) 0b01000000000000000000000000000000 else 0b00100000000000000000000000000000
              }
              if (%dirty and 0b00101010101010101010101010101011 xor 0b00101010101010101010101010101010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  a00 = 0
                }
                if (%default and 0b0010 !== 0) {
                  a01 = 0
                }
                if (%default and 0b0100 !== 0) {
                  a02 = 0
                }
                if (%default and 0b1000 !== 0) {
                  a03 = 0
                }
                if (%default and 0b00010000 !== 0) {
                  a04 = 0
                }
                if (%default and 0b00100000 !== 0) {
                  a05 = 0
                }
                if (%default and 0b01000000 !== 0) {
                  a06 = 0
                }
                if (%default and 0b10000000 !== 0) {
                  a07 = 0
                }
                if (%default and 0b000100000000 !== 0) {
                  a08 = 0
                }
                if (%default and 0b001000000000 !== 0) {
                  a09 = 0
                }
                if (%default and 0b010000000000 !== 0) {
                  a10 = 0
                }
                if (%default and 0b100000000000 !== 0) {
                  a11 = 0
                }
                if (%default and 0b0001000000000000 !== 0) {
                  a12 = 0
                }
                if (%default and 0b0010000000000000 !== 0) {
                  a13 = 0
                }
                if (%default and 0b0100000000000000 !== 0) {
                  a14 = 0
                }
                Example(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, %composer, 0b0110 and %dirty or 0b00011000 and %dirty or 0b01100000 and %dirty or 0b000110000000 and %dirty or 0b011000000000 and %dirty or 0b0001100000000000 and %dirty or 0b0110000000000000 and %dirty or 0b00011000000000000000 and %dirty or 0b01100000000000000000 and %dirty or 0b000110000000000000000000 and %dirty or 0b011000000000000000000000 and %dirty or 0b0001100000000000000000000000 and %dirty or 0b0110000000000000000000000000 and %dirty or 0b00011000000000000000000000000000 and %dirty or 0b01100000000000000000000000000000 and %dirty, 0)
                Example(a14, a13, a12, a11, a10, a09, a08, a07, a06, a05, a04, a03, a02, a01, a00, %composer, 0b0110 and %dirty shr 0b00011100 or 0b00011000 and %dirty shr 0b00011000 or 0b01100000 and %dirty shr 0b00010100 or 0b000110000000 and %dirty shr 0b00010000 or 0b011000000000 and %dirty shr 0b1100 or 0b0001100000000000 and %dirty shr 0b1000 or 0b0110000000000000 and %dirty shr 0b0100 or 0b00011000000000000000 and %dirty or 0b01100000000000000000 and %dirty shl 0b0100 or 0b000110000000000000000000 and %dirty shl 0b1000 or 0b011000000000000000000000 and %dirty shl 0b1100 or 0b0001100000000000000000000000 and %dirty shl 0b00010000 or 0b0110000000000000000000000000 and %dirty shl 0b00010100 or 0b00011000000000000000000000000000 and %dirty shl 0b00011000 or 0b01100000000000000000000000000000 and %dirty shl 0b00011100, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun test16Parameters(): Unit = comparisonPropagation(
        """
        """,
        """
            @Composable
            fun Example(
                a00: Int = 0,
                a01: Int = 0,
                a02: Int = 0,
                a03: Int = 0,
                a04: Int = 0,
                a05: Int = 0,
                a06: Int = 0,
                a07: Int = 0,
                a08: Int = 0,
                a09: Int = 0,
                a10: Int = 0,
                a11: Int = 0,
                a12: Int = 0,
                a13: Int = 0,
                a14: Int = 0,
                a15: Int = 0
            ) {
                // in order
                Example(
                    a00,
                    a01,
                    a02,
                    a03,
                    a04,
                    a05,
                    a06,
                    a07,
                    a08,
                    a09,
                    a10,
                    a11,
                    a12,
                    a13,
                    a14,
                    a15
                )
                // in opposite order
                Example(
                    a15,
                    a14,
                    a13,
                    a12,
                    a11,
                    a10,
                    a09,
                    a08,
                    a07,
                    a06,
                    a05,
                    a04,
                    a03,
                    a02,
                    a01,
                    a00
                )
            }
        """,
        """
            @Composable
            fun Example(a00: Int, a01: Int, a02: Int, a03: Int, a04: Int, a05: Int, a06: Int, a07: Int, a08: Int, a09: Int, a10: Int, a11: Int, a12: Int, a13: Int, a14: Int, a15: Int, %composer: Composer<*>?, %changed: Int, %changed1: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Example)<Exampl...>,<Exampl...>:Test.kt")
              val %dirty = %changed
              val %dirty1 = %changed1
              val a00 = a00
              val a01 = a01
              val a02 = a02
              val a03 = a03
              val a04 = a04
              val a05 = a05
              val a06 = a06
              val a07 = a07
              val a08 = a08
              val a09 = a09
              val a10 = a10
              val a11 = a11
              val a12 = a12
              val a13 = a13
              val a14 = a14
              val a15 = a15
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(a00)) 0b0100 else 0b0010
              }
              if (%default and 0b0010 !== 0) {
                %dirty = %dirty or 0b00011000
              } else if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(a01)) 0b00010000 else 0b1000
              }
              if (%default and 0b0100 !== 0) {
                %dirty = %dirty or 0b01100000
              } else if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(a02)) 0b01000000 else 0b00100000
              }
              if (%default and 0b1000 !== 0) {
                %dirty = %dirty or 0b000110000000
              } else if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a03)) 0b000100000000 else 0b10000000
              }
              if (%default and 0b00010000 !== 0) {
                %dirty = %dirty or 0b011000000000
              } else if (%changed and 0b011000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a04)) 0b010000000000 else 0b001000000000
              }
              if (%default and 0b00100000 !== 0) {
                %dirty = %dirty or 0b0001100000000000
              } else if (%changed and 0b0001100000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a05)) 0b0001000000000000 else 0b100000000000
              }
              if (%default and 0b01000000 !== 0) {
                %dirty = %dirty or 0b0110000000000000
              } else if (%changed and 0b0110000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a06)) 0b0100000000000000 else 0b0010000000000000
              }
              if (%default and 0b10000000 !== 0) {
                %dirty = %dirty or 0b00011000000000000000
              } else if (%changed and 0b00011000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a07)) 0b00010000000000000000 else 0b1000000000000000
              }
              if (%default and 0b000100000000 !== 0) {
                %dirty = %dirty or 0b01100000000000000000
              } else if (%changed and 0b01100000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a08)) 0b01000000000000000000 else 0b00100000000000000000
              }
              if (%default and 0b001000000000 !== 0) {
                %dirty = %dirty or 0b000110000000000000000000
              } else if (%changed and 0b000110000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a09)) 0b000100000000000000000000 else 0b10000000000000000000
              }
              if (%default and 0b010000000000 !== 0) {
                %dirty = %dirty or 0b011000000000000000000000
              } else if (%changed and 0b011000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a10)) 0b010000000000000000000000 else 0b001000000000000000000000
              }
              if (%default and 0b100000000000 !== 0) {
                %dirty = %dirty or 0b0001100000000000000000000000
              } else if (%changed and 0b0001100000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a11)) 0b0001000000000000000000000000 else 0b100000000000000000000000
              }
              if (%default and 0b0001000000000000 !== 0) {
                %dirty = %dirty or 0b0110000000000000000000000000
              } else if (%changed and 0b0110000000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a12)) 0b0100000000000000000000000000 else 0b0010000000000000000000000000
              }
              if (%default and 0b0010000000000000 !== 0) {
                %dirty = %dirty or 0b00011000000000000000000000000000
              } else if (%changed and 0b00011000000000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a13)) 0b00010000000000000000000000000000 else 0b1000000000000000000000000000
              }
              if (%default and 0b0100000000000000 !== 0) {
                %dirty = %dirty or 0b01100000000000000000000000000000
              } else if (%changed and 0b01100000000000000000000000000000 === 0) {
                %dirty = %dirty or if (%composer.changed(a14)) 0b01000000000000000000000000000000 else 0b00100000000000000000000000000000
              }
              if (%default and 0b1000000000000000 !== 0) {
                %dirty1 = %dirty1 or 0b0110
              } else if (%changed1 and 0b0110 === 0) {
                %dirty1 = %dirty1 or if (%composer.changed(a15)) 0b0100 else 0b0010
              }
              if (%dirty and 0b00101010101010101010101010101011 xor 0b00101010101010101010101010101010 !== 0 || %dirty1 and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                if (%default and 0b0001 !== 0) {
                  a00 = 0
                }
                if (%default and 0b0010 !== 0) {
                  a01 = 0
                }
                if (%default and 0b0100 !== 0) {
                  a02 = 0
                }
                if (%default and 0b1000 !== 0) {
                  a03 = 0
                }
                if (%default and 0b00010000 !== 0) {
                  a04 = 0
                }
                if (%default and 0b00100000 !== 0) {
                  a05 = 0
                }
                if (%default and 0b01000000 !== 0) {
                  a06 = 0
                }
                if (%default and 0b10000000 !== 0) {
                  a07 = 0
                }
                if (%default and 0b000100000000 !== 0) {
                  a08 = 0
                }
                if (%default and 0b001000000000 !== 0) {
                  a09 = 0
                }
                if (%default and 0b010000000000 !== 0) {
                  a10 = 0
                }
                if (%default and 0b100000000000 !== 0) {
                  a11 = 0
                }
                if (%default and 0b0001000000000000 !== 0) {
                  a12 = 0
                }
                if (%default and 0b0010000000000000 !== 0) {
                  a13 = 0
                }
                if (%default and 0b0100000000000000 !== 0) {
                  a14 = 0
                }
                if (%default and 0b1000000000000000 !== 0) {
                  a15 = 0
                }
                Example(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, %composer, 0b0110 and %dirty or 0b00011000 and %dirty or 0b01100000 and %dirty or 0b000110000000 and %dirty or 0b011000000000 and %dirty or 0b0001100000000000 and %dirty or 0b0110000000000000 and %dirty or 0b00011000000000000000 and %dirty or 0b01100000000000000000 and %dirty or 0b000110000000000000000000 and %dirty or 0b011000000000000000000000 and %dirty or 0b0001100000000000000000000000 and %dirty or 0b0110000000000000000000000000 and %dirty or 0b00011000000000000000000000000000 and %dirty or 0b01100000000000000000000000000000 and %dirty, 0b0110 and %dirty1, 0)
                Example(a15, a14, a13, a12, a11, a10, a09, a08, a07, a06, a05, a04, a03, a02, a01, a00, %composer, 0b0110 and %dirty1 or 0b00011000 and %dirty shr 0b00011010 or 0b01100000 and %dirty shr 0b00010110 or 0b000110000000 and %dirty shr 0b00010010 or 0b011000000000 and %dirty shr 0b1110 or 0b0001100000000000 and %dirty shr 0b1010 or 0b0110000000000000 and %dirty shr 0b0110 or 0b00011000000000000000 and %dirty shr 0b0010 or 0b01100000000000000000 and %dirty shl 0b0010 or 0b000110000000000000000000 and %dirty shl 0b0110 or 0b011000000000000000000000 and %dirty shl 0b1010 or 0b0001100000000000000000000000 and %dirty shl 0b1110 or 0b0110000000000000000000000000 and %dirty shl 0b00010010 or 0b00011000000000000000000000000000 and %dirty shl 0b00010110 or 0b01100000000000000000000000000000 and %dirty shl 0b00011010, 0b0110 and %dirty, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, %composer, %changed or 0b0001, %changed1, %default)
              }
            }
        """
    )

    @Test
    fun testGrouplessProperty(): Unit = comparisonPropagation(
        """
        """,
        """
            import androidx.compose.runtime.currentComposer
            import androidx.compose.runtime.ExperimentalComposeApi

            open class Foo {
                @ComposableContract(readonly = true)
                @Composable
                inline val current: Int get() = currentComposer.hashCode()

                @ComposableContract(readonly = true)
                @Composable
                fun getHashCode(): Int = currentComposer.hashCode()
            }

            @ComposableContract(readonly = true)
            @Composable
            fun getHashCode(): Int = currentComposer.hashCode()
        """,
        """
            open class Foo {
              val current: Int
                get() {
                  val tmp0 = %composer.hashCode()
                  return tmp0
                }
              @ComposableContract(readonly = true)
              @Composable
              fun getHashCode(%composer: Composer<*>?, %changed: Int): Int {
                val tmp0 = %composer.hashCode()
                return tmp0
              }
            }
            @ComposableContract(readonly = true)
            @Composable
            fun getHashCode(%composer: Composer<*>?, %changed: Int): Int {
              val tmp0 = %composer.hashCode()
              return tmp0
            }
        """
    )

    @Test
    fun testStaticAndNonStaticDefaultValueSkipping(): Unit = comparisonPropagation(
        """
            import androidx.compose.runtime.ambientOf

            val AmbientColor = ambientOf { 123 }
            @Composable fun A(a: Int) {}
        """,
        """
            @Composable
            fun Example(
                wontChange: Int = 123,
                mightChange: Int = AmbientColor.current
            ) {
                A(wontChange)
                A(mightChange)
            }
        """,
        """
            @Composable
            fun Example(wontChange: Int, mightChange: Int, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Example)P(1)<curren...>,<A(wont...>,<A(migh...>:Test.kt")
              val %dirty = %changed
              val wontChange = wontChange
              val mightChange = mightChange
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(wontChange)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%default and 0b0010 === 0 && %composer.changed(mightChange)) 0b00010000 else 0b1000
              }
              if (%dirty and 0b1011 xor 0b1010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    wontChange = 123
                  }
                  if (%default and 0b0010 !== 0) {
                    mightChange = AmbientColor.current
                    %dirty = %dirty and 0b00011000.inv()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0010 !== 0) {
                    %dirty = %dirty and 0b00011000.inv()
                  }
                }
                A(wontChange, %composer, 0b0110 and %dirty)
                A(mightChange, %composer, 0b0110 and %dirty shr 0b0010)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(wontChange, mightChange, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )

    @Test
    fun testComposableLambdaInvoke(): Unit = comparisonPropagation(
        """
        """,
        """
            @Composable fun Example(content: @Composable() () -> Unit) {
                content.invoke()
            }
        """,
        """
            @Composable
            fun Example(content: Function2<Composer<*>, Int, Unit>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Example)<invoke...>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(content)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                content(%composer, 0b0110 and %dirty)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(content, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testComposableLambdasWithReturnGetGroups(): Unit = comparisonPropagation(
        """
        """,
        """
            fun A(factory: @Composable () -> Int): Unit {}
            fun B() = A { 123 }
        """,
        """
            fun A(factory: Function2<Composer<*>, Int, Int>) { }
            fun B() {
              return A { %composer: Composer<*>?, %changed: Int ->
                %composer.startReplaceableGroup(<>)
                val tmp0 = 123
                %composer.endReplaceableGroup()
                tmp0
              }
            }
        """
    )

    @Test
    fun testDefaultsIssue(): Unit = comparisonPropagation(
        """
        """,
        """
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.Dp
            import androidx.compose.runtime.emptyContent

            @Composable
            fun Box2(
                modifier: Modifier = Modifier,
                paddingStart: Dp = Dp.Unspecified,
                children: @Composable () -> Unit = emptyContent()
            ) {

            }
        """,
        """
            @Composable
            fun Box2(modifier: Modifier?, paddingStart: Dp, children: Function2<Composer<*>, Int, Unit>?, %composer: Composer<*>?, %changed: Int, %default: Int) {
              %composer.startRestartGroup(<>, "C(Box2)P(1,2:c#ui.unit.Dp):Test.kt")
              val %dirty = %changed
              val modifier = modifier
              val paddingStart = paddingStart
              val children = children
              if (%default and 0b0001 !== 0) {
                %dirty = %dirty or 0b0110
              } else if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(modifier)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%default and 0b0010 === 0 && %composer.changed(paddingStart.value)) 0b00010000 else 0b1000
              }
              if (%default and 0b0100 !== 0) {
                %dirty = %dirty or 0b01100000
              } else if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(children)) 0b01000000 else 0b00100000
              }
              if (%dirty and 0b00101011 xor 0b00101010 !== 0 || !%composer.skipping) {
                if (%changed and 0b0001 === 0 || %composer.defaultsInvalid) {
                  %composer.startDefaults()
                  if (%default and 0b0001 !== 0) {
                    modifier = Companion
                  }
                  if (%default and 0b0010 !== 0) {
                    paddingStart = Companion.Unspecified
                    %dirty = %dirty and 0b00011000.inv()
                  }
                  if (%default and 0b0100 !== 0) {
                    children = emptyContent()
                  }
                  %composer.endDefaults()
                } else {
                  %composer.skipCurrentGroup()
                  if (%default and 0b0010 !== 0) {
                    %dirty = %dirty and 0b00011000.inv()
                  }
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Box2(modifier, paddingStart, children, %composer, %changed or 0b0001, %default)
              }
            }
        """
    )
}