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

class ControlFlowTransformTests : AbstractControlFlowTransformTests() {

    @Test
    fun testIfNonComposable(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // No composable calls, so no group generated except for at function boundary
                if (x > 0) {
                    NA()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                NA()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testIfWithCallsInBranch(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // Composable calls in the result blocks, so we can determine static number of
                // groups executed. This means we put a group around the "then" and the implicit
                // "else" blocks
                if (x > 0) {
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A()>")
                A(%composer, 0)
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testIfElseWithCallsInBranch(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // Composable calls in the result blocks, so we can determine static number of
                // groups executed. This means we put a group around the "then" and the
                // "else" blocks
                if (x > 0) {
                    A(a)
                } else {
                    A(b)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A(a)>")
                A(a, %composer, 0)
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>, "<A(b)>")
                A(b, %composer, 0)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testIfWithCallInCondition(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // Since the first condition of an if/else is unconditionally executed, it does not
                // necessitate a group of any kind, so we just end up with the function boundary
                // group
                if (B()) {
                    NA()
                } else {
                    NA()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<B()>:Test.kt")
              if (B(%composer, 0)) {
                NA()
              } else {
                NA()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testIfElseWithCallsInConditions(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // Since the condition in the else-if is conditionally executed, it means we have
                // dynamic execution and we can't statically guarantee the number of groups. As a
                // result, we generate a group around the if statement in addition to a group around
                // each of the conditions with composable calls in them. Note that no group is
                // needed around the else condition
                if (B(a)) {
                    NA()
                } else if (B(b)) {
                    NA()
                } else {
                    NA()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (%composer.startReplaceableGroup(<>, "<B(a)>")
              val tmp0_group = B(a, %composer, 0)
              %composer.endReplaceableGroup()
              tmp0_group) {
                NA()
              } else if (%composer.startReplaceableGroup(<>, "<B(b)>")
              val tmp1_group = B(b, %composer, 0)
              %composer.endReplaceableGroup()
              tmp1_group) {
                NA()
              } else {
                NA()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithSubjectAndNoCalls(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // nothing needed except for the function boundary group
                when (x) {
                    0 -> 8
                    1 -> 10
                    else -> x
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              val tmp0_subject = x
              when {
                tmp0_subject == 0 -> {
                  8
                }
                tmp0_subject == 0b0001 -> {
                  10
                }
                else -> {
                  x
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithSubjectAndCalls(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // calls only in the result block, which means we can statically guarantee the
                // number of groups, so no group around the when is needed, just groups around the
                // result blocks.
                when (x) {
                    0 -> A(a)
                    1 -> A(b)
                    else -> A(c)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              val tmp0_subject = x
              when {
                tmp0_subject == 0 -> {
                  %composer.startReplaceableGroup(<>, "<A(a)>")
                  A(a, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                tmp0_subject == 0b0001 -> {
                  %composer.startReplaceableGroup(<>, "<A(b)>")
                  A(b, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                else -> {
                  %composer.startReplaceableGroup(<>, "<A(c)>")
                  A(c, %composer, 0)
                  %composer.endReplaceableGroup()
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithSubjectAndCallsWithResult(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // no need for a group around the when expression overall, but since the result
                // of the expression is now being used, we need to generate temporary variables to
                // capture the result but still do the execution of the expression inside of groups.
                val y = when (x) {
                    0 -> R(a)
                    1 -> R(b)
                    else -> R(c)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              val y = val tmp0_subject = x
              when {
                tmp0_subject == 0 -> {
                  %composer.startReplaceableGroup(<>, "<R(a)>")
                  val tmp0_group = R(a, %composer, 0)
                  %composer.endReplaceableGroup()
                  tmp0_group
                }
                tmp0_subject == 0b0001 -> {
                  %composer.startReplaceableGroup(<>, "<R(b)>")
                  val tmp1_group = R(b, %composer, 0)
                  %composer.endReplaceableGroup()
                  tmp1_group
                }
                else -> {
                  %composer.startReplaceableGroup(<>, "<R(c)>")
                  val tmp2_group = R(c, %composer, 0)
                  %composer.endReplaceableGroup()
                  tmp2_group
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithCalls(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // result blocks have composable calls, so we generate groups round them. It's a
                // statically guaranteed number of groups at execution, so no wrapping group is
                // needed.
                when {
                    x < 0 -> A(a)
                    x > 30 -> A(b)
                    else -> A(c)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              when {
                x < 0 -> {
                  %composer.startReplaceableGroup(<>, "<A(a)>")
                  A(a, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                x > 30 -> {
                  %composer.startReplaceableGroup(<>, "<A(b)>")
                  A(b, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                else -> {
                  %composer.startReplaceableGroup(<>, "<A(c)>")
                  A(c, %composer, 0)
                  %composer.endReplaceableGroup()
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithCallsInSomeResults(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // result blocks have composable calls, so we generate groups round them. It's a
                // statically guaranteed number of groups at execution, so no wrapping group is
                // needed.
                when {
                    x < 0 -> A(a)
                    x > 30 -> NA()
                    else -> A(b)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              when {
                x < 0 -> {
                  %composer.startReplaceableGroup(<>, "<A(a)>")
                  A(a, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                x > 30 -> {
                  %composer.startReplaceableGroup(<>)
                  %composer.endReplaceableGroup()
                  NA()
                }
                else -> {
                  %composer.startReplaceableGroup(<>, "<A(b)>")
                  A(b, %composer, 0)
                  %composer.endReplaceableGroup()
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithCallsInConditions(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // composable calls are in the condition blocks of the when statement. Since these
                // are conditionally executed, we can't statically know the number of groups during
                // execution. as a result, we must wrap the when clause with a group. Since there
                // are no other composable calls, the function body group will suffice.
                when {
                    x == R(a) -> NA()
                    x > R(b) -> NA()
                    else -> NA()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              when {
                %composer.startReplaceableGroup(<>, "<R(a)>")
                val tmp0_group = x == R(a, %composer, 0)
                %composer.endReplaceableGroup()
                tmp0_group -> {
                  NA()
                }
                %composer.startReplaceableGroup(<>, "<R(b)>")
                val tmp1_group = x > R(b, %composer, 0)
                %composer.endReplaceableGroup()
                tmp1_group -> {
                  NA()
                }
                else -> {
                  NA()
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhenWithCallsInConditionsAndCallAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // composable calls are in the condition blocks of the when statement. Since these
                // are conditionally executed, we can't statically know the number of groups during
                // execution. as a result, we must wrap the when clause with a group.
                when {
                    x == R(a) -> NA()
                    x > R(b) -> NA()
                    else -> NA()
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "")
              when {
                %composer.startReplaceableGroup(<>, "<R(a)>")
                val tmp0_group = x == R(a, %composer, 0)
                %composer.endReplaceableGroup()
                tmp0_group -> {
                  NA()
                }
                %composer.startReplaceableGroup(<>, "<R(b)>")
                val tmp1_group = x > R(b, %composer, 0)
                %composer.endReplaceableGroup()
                tmp1_group -> {
                  NA()
                }
                else -> {
                  NA()
                }
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testSafeCall(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int?) {
                // the composable call is made conditionally, which means it is like an if, but one
                // with static groups, so no wrapping group needed.
                x?.A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int?, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              val tmp0_safe_receiver = x
              when {
                tmp0_safe_receiver == null -> {
                  %composer.startReplaceableGroup(<>)
                  %composer.endReplaceableGroup()
                  null
                }
                else -> {
                  %composer.startReplaceableGroup(<>, "<A()>")
                  tmp0_safe_receiver.A(%composer, 0b0110 and %changed)
                  %composer.endReplaceableGroup()
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testElvis(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int?) {
                // the composable call is made conditionally, which means it is like an if, but one
                // with static groups, so no wrapping group needed.
                val y = x ?: R()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int?, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              val y = val tmp0_elvis_lhs = x
              when {
                tmp0_elvis_lhs == null -> {
                  %composer.startReplaceableGroup(<>, "<R()>")
                  val tmp0_group = R(%composer, 0)
                  %composer.endReplaceableGroup()
                  tmp0_group
                }
                else -> {
                  %composer.startReplaceableGroup(<>)
                  %composer.endReplaceableGroup()
                  tmp0_elvis_lhs
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testForLoopWithCallsInBody(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: List<Int>) {
                // The composable call is made a conditional number of times, so we need to wrap
                // the loop with a dynamic wrapping group. Since there are no other calls, the
                // function body group will suffice.
                for (i in items) {
                    P(i)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: List<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>:Test.kt")
              val tmp0_iterator = items.iterator()
              while (tmp0_iterator.hasNext()) {
                val i = tmp0_iterator.next()
                P(i, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testForLoopWithCallsInBodyAndCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: List<Int>) {
                // The composable call is made a conditional number of times, so we need to wrap
                // the loop with a dynamic wrapping group.
                for (i in items) {
                    P(i)
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: List<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<P(i)>")
              val tmp0_iterator = items.iterator()
              while (tmp0_iterator.hasNext()) {
                val i = tmp0_iterator.next()
                P(i, %composer, 0)
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testForLoopWithCallsInSubject(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example() {
                // The for loop's subject expression is only executed once, so we don't need any
                // additional groups
                for (i in L()) {
                    print(i)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<L()>:Test.kt")
              val tmp0_iterator = L(%composer, 0).iterator()
              while (tmp0_iterator.hasNext()) {
                val i = tmp0_iterator.next()
                print(i)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileLoopWithCallsInBody(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: MutableList<Int>) {
                // since we have a composable call which is called a conditional number of times,
                // we need to generate groups around the loop's block as well as a group around the
                // overall statement. Since there are no calls after the while loop, the function
                // body group will suffice.
                while (items.isNotEmpty()) {
                    val item = items.removeAt(items.size - 1)
                    P(item)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: MutableList<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(item...>:Test.kt")
              while (items.isNotEmpty()) {
                val item = items.removeAt(items.size - 1)
                P(item, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileLoopWithCallsInBodyAndCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: MutableList<Int>) {
                // since we have a composable call which is called a conditional number of times,
                // we need to generate groups around the loop's block as well as a group around the
                // overall statement.
                while (items.isNotEmpty()) {
                    val item = items.removeAt(items.size - 1)
                    P(item)
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: MutableList<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<P(item...>")
              while (items.isNotEmpty()) {
                val item = items.removeAt(items.size - 1)
                P(item, %composer, 0)
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileLoopWithCallsInCondition(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example() {
                // A while loop's condition block gets executed a conditional number of times, so
                // so we must generate a group around the while expression overall. The function
                // body group will suffice.
                while (B()) {
                    print("hello world")
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<B()>:Test.kt")
              while (B(%composer, 0)) {
                print("hello world")
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileLoopWithCallsInConditionAndCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example() {
                // A while loop's condition block gets executed a conditional number of times, so
                // so we must generate a group around the while expression overall.
                while (B()) {
                    print("hello world")
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<B()>")
              while (B(%composer, 0)) {
                print("hello world")
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileLoopWithCallsInConditionAndBody(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example() {
                // Both the condition and the body of the loop get groups because they have
                // composable calls in them. We must generate a group around the while statement
                // overall, but the function body group will suffice.
                while (B()) {
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<B()>,<A()>:Test.kt")
              while (B(%composer, 0)) {
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileLoopWithCallsInConditionAndBodyAndCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example() {
                // Both the condition and the body of the loop get groups because they have
                // composable calls in them. We must generate a group around the while statement
                // overall.
                while (B()) {
                    A(a)
                }
                A(b)
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A(b)>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<B()>,<A(a)>")
              while (B(%composer, 0)) {
                A(a, %composer, 0)
              }
              %composer.endReplaceableGroup()
              A(b, %composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testEarlyReturnWithCallsBeforeButNotAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // in the early return path, we need only close out the opened groups
                if (x > 0) {
                    A()
                    return
                }
                print("hello")
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A()>")
                A(%composer, 0)
                %composer.endReplaceableGroup()
                %composer.endReplaceableGroup()
                return
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              print("hello")
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testEarlyReturnWithCallsAfterButNotBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                // we can just close out the open groups at the return.
                if (x > 0) {
                    return
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              if (x > 0) {
                %composer.endReplaceableGroup()
                return
              }
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testEarlyReturnValue(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int): Int {
                if (x > 0) {
                    A()
                    return 1
                }
                return 2
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A()>")
                A(%composer, 0)
                val tmp1_return = 1
                %composer.endReplaceableGroup()
                %composer.endReplaceableGroup()
                return tmp1_return
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              val tmp0 = 2
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testEarlyReturnValueWithCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int): Int {
                if (x > 0) {
                    return 1
                }
                A()
                return 2
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              if (x > 0) {
                val tmp1_return = 1
                %composer.endReplaceableGroup()
                return tmp1_return
              }
              A(%composer, 0)
              val tmp0 = 2
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testReturnCallValue(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(): Int {
                // since the return expression is a composable call, we need to generate a
                // temporary variable and then return it after ending the open groups.
                A()
                return R()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(%composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>,<R()>:Test.kt")
              A(%composer, 0)
              val tmp0 = R(%composer, 0)
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testEarlyReturnCallValue(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int): Int {
                if (x > 0) {
                    return R()
                }
                return R()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Example)<R()>:Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<R()>")
                val tmp1_return = R(%composer, 0)
                %composer.endReplaceableGroup()
                %composer.endReplaceableGroup()
                return tmp1_return
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              val tmp0 = R(%composer, 0)
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testReturnFromLoop(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    val j = i
                    val k = i
                    val l = i
                    P(i)
                    if (i == 0) {
                        P(j)
                        return
                    } else {
                        P(k)
                    }
                    P(l)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>,<P(l)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                val j = i
                val k = i
                val l = i
                P(i, %composer, 0)
                if (i == 0) {
                  %composer.startReplaceableGroup(<>, "<P(j)>")
                  P(j, %composer, 0)
                  %composer.endReplaceableGroup()
                  %composer.endReplaceableGroup()
                  return
                } else {
                  %composer.startReplaceableGroup(<>, "<P(k)>")
                  P(k, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                P(l, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testOrderingOfPushedEndCallsWithEarlyReturns(): Unit = controlFlow(
        """
            @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    val j = i
                    val k = i
                    val l = i
                    P(i)
                    if (i == 0) {
                        P(j)
                        return
                    } else {
                        P(k)
                    }
                    P(l)
                }
            }
        """,
        """
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Example)*<P(i)>,<P(l)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                val j = i
                val k = i
                val l = i
                P(i, %composer, 0)
                if (i == 0) {
                  %composer.startReplaceableGroup(<>, "<P(j)>")
                  P(j, %composer, 0)
                  %composer.endReplaceableGroup()
                  %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                    Example(items, %composer, %changed or 0b0001)
                  }
                  return
                } else {
                  %composer.startReplaceableGroup(<>, "<P(k)>")
                  P(k, %composer, 0)
                  %composer.endReplaceableGroup()
                }
                P(l, %composer, 0)
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(items, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testBreakWithCallsAfter(): Unit = controlFlow(
            """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    if (i == 0) {
                        break
                    }
                    P(i)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                if (i == 0) {
                  break
                }
                P(i, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
        )

    @Test
    fun testBreakWithCallsBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    P(i)
                    if (i == 0) {
                        break
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                P(i, %composer, 0)
                if (i == 0) {
                  break
                }
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testBreakWithCallsBeforeAndAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                // a group around while is needed here, but the function body group will suffice
                while (items.hasNext()) {
                    val i = items.next()
                    val j = i
                    P(i)
                    if (i == 0) {
                        break
                    }
                    P(j)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>,<P(j)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                val j = i
                P(i, %composer, 0)
                if (i == 0) {
                  break
                }
                P(j, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testBreakWithCallsBeforeAndAfterAndCallAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                // a group around while is needed here
                while (items.hasNext()) {
                    val i = items.next()
                    P(i)
                    if (i == 0) {
                        break
                    }
                    P(i)
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<P(i)>,<P(i)>")
              while (items.hasNext()) {
                val i = items.next()
                P(i, %composer, 0)
                if (i == 0) {
                  break
                }
                P(i, %composer, 0)
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testContinueWithCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    if (i == 0) {
                        continue
                    }
                    P(i)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                if (i == 0) {
                  continue
                }
                P(i, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testContinueWithCallsBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    P(i)
                    if (i == 0) {
                        continue
                    }
                    print(i)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                P(i, %composer, 0)
                if (i == 0) {
                  continue
                }
                print(i)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testContinueWithCallsBeforeAndAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(items: Iterator<Int>) {
                while (items.hasNext()) {
                    val i = items.next()
                    P(i)
                    if (i == 0) {
                        continue
                    }
                    P(i)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(items: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<P(i)>,<P(i)>:Test.kt")
              while (items.hasNext()) {
                val i = items.next()
                P(i, %composer, 0)
                if (i == 0) {
                  continue
                }
                P(i, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testLoopWithReturn(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>) {
                while (a.hasNext()) {
                    val x = a.next()
                    if (x > 100) {
                        return
                    }
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<A()>:Test.kt")
              while (a.hasNext()) {
                val x = a.next()
                if (x > 100) {
                  %composer.endReplaceableGroup()
                  return
                }
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testLoopWithBreak(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>) {
                a@while (a.hasNext()) {
                    val x = a.next()
                    b@while (b.hasNext()) {
                        val y = b.next()
                        if (y == x) {
                            break@a
                        }
                        A()
                    }
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<A()>:Test.kt")
              a@while (a.hasNext()) {
                val x = a.next()
                %composer.startReplaceableGroup(<>, "*<A()>")
                b@while (b.hasNext()) {
                  val y = b.next()
                  if (y == x) {
                    %composer.endReplaceableGroup()
                    break@a
                  }
                  A(%composer, 0)
                }
                %composer.endReplaceableGroup()
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testNestedLoopsAndBreak(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>) {
                a@while (a.hasNext()) {
                    val x = a.next()
                    if (x == 0) {
                        break
                    }
                    b@while (b.hasNext()) {
                        val y = b.next()
                        if (y == 0) {
                            break
                        }
                        if (y == x) {
                            break@a
                        }
                        if (y > 100) {
                            return
                        }
                        A()
                    }
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<A()>:Test.kt")
              a@while (a.hasNext()) {
                val x = a.next()
                if (x == 0) {
                  break
                }
                %composer.startReplaceableGroup(<>, "*<A()>")
                b@while (b.hasNext()) {
                  val y = b.next()
                  if (y == 0) {
                    break
                  }
                  if (y == x) {
                    %composer.endReplaceableGroup()
                    break@a
                  }
                  if (y > 100) {
                    %composer.endReplaceableGroup()
                    %composer.endReplaceableGroup()
                    return
                  }
                  A(%composer, 0)
                }
                %composer.endReplaceableGroup()
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testNestedLoops(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>) {
                a@while (a.hasNext()) {
                    b@while (b.hasNext()) {
                        A()
                    }
                    A()
                }
                A()
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(a: Iterator<Int>, b: Iterator<Int>, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<A()>")
              a@while (a.hasNext()) {
                %composer.startReplaceableGroup(<>, "*<A()>")
                b@while (b.hasNext()) {
                  A(%composer, 0)
                }
                %composer.endReplaceableGroup()
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileInsideIfAndCallAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                if (x > 0) {
                    while (x > 0) {
                        A()
                    }
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A()>")
                %composer.startReplaceableGroup(<>, "*<A()>")
                while (x > 0) {
                  A(%composer, 0)
                }
                %composer.endReplaceableGroup()
                A(%composer, 0)
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileInsideIfAndCallBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                if (x > 0) {
                    A()
                    while (x > 0) {
                        A()
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A()>,*<A()>")
                A(%composer, 0)
                while (x > 0) {
                  A(%composer, 0)
                }
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileInsideIf(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                if (x > 0) {
                    while (x > 0) {
                        A()
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "*<A()>")
                while (x > 0) {
                  A(%composer, 0)
                }
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileWithKey(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                while (x > 0) {
                    key(x) {
                        A()
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              while (x > 0) {
                %composer.startMovableGroup(<>, x, "<A()>")
                A(%composer, 0)
                %composer.endMovableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileWithTwoKeys(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                while (x > 0) {
                    key(x) {
                        A(a)
                    }
                    key(x+1) {
                        A(b)
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              while (x > 0) {
                %composer.startMovableGroup(<>, x, "<A(a)>")
                A(a, %composer, 0)
                %composer.endMovableGroup()
                %composer.startMovableGroup(<>, x + 1, "<A(b)>")
                A(b, %composer, 0)
                %composer.endMovableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileWithKeyAndCallAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                while (x > 0) {
                    key(x) {
                        A(a)
                    }
                    A(b)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<A(b)>:Test.kt")
              while (x > 0) {
                %composer.startMovableGroup(<>, x, "<A(a)>")
                A(a, %composer, 0)
                %composer.endMovableGroup()
                A(b, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileWithKeyAndCallBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                while (x > 0) {
                    A(a)
                    key(x) {
                        A(b)
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<A(a)>:Test.kt")
              while (x > 0) {
                A(a, %composer, 0)
                %composer.startMovableGroup(<>, x, "<A(b)>")
                A(b, %composer, 0)
                %composer.endMovableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testWhileWithKeyAndCallBeforeAndAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                while (x > 0) {
                    A(a)
                    key(x) {
                        A(b)
                    }
                    A(c)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<A(a)>,<A(c)>:Test.kt")
              while (x > 0) {
                A(a, %composer, 0)
                %composer.startMovableGroup(<>, x, "<A(b)>")
                A(b, %composer, 0)
                %composer.endMovableGroup()
                A(c, %composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyAtRootLevel(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                key(x) {
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              %composer.startMovableGroup(<>, x, "<A()>")
              A(%composer, 0)
              %composer.endMovableGroup()
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyAtRootLevelAndCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                key(x) {
                    A(a)
                }
                A(b)
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A(b)>:Test.kt")
              %composer.startMovableGroup(<>, x, "<A(a)>")
              A(a, %composer, 0)
              %composer.endMovableGroup()
              A(b, %composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyAtRootLevelAndCallsBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                A(a)
                key(x) {
                    A(b)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<A(a)>:Test.kt")
              A(a, %composer, 0)
              %composer.startMovableGroup(<>, x, "<A(b)>")
              A(b, %composer, 0)
              %composer.endMovableGroup()
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyInIf(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                if (x > 0) {
                    key(x) {
                        A()
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "")
                %composer.startMovableGroup(<>, x, "<A()>")
                A(%composer, 0)
                %composer.endMovableGroup()
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyInIfAndCallsAfter(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                if (x > 0) {
                    key(x) {
                        A(a)
                    }
                    A(b)
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A(b)>")
                %composer.startMovableGroup(<>, x, "<A(a)>")
                A(a, %composer, 0)
                %composer.endMovableGroup()
                A(b, %composer, 0)
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyInIfAndCallsBefore(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                if (x > 0) {
                    A(a)
                    key(x) {
                        A(b)
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              if (x > 0) {
                %composer.startReplaceableGroup(<>, "<A(a)>")
                A(a, %composer, 0)
                %composer.startMovableGroup(<>, x, "<A(b)>")
                A(b, %composer, 0)
                %composer.endMovableGroup()
                %composer.endReplaceableGroup()
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyWithLotsOfValues(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(a: Int, b: Int, c: Int, d: Int) {
                key(a, b, c, d) {
                    A()
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(a: Int, b: Int, c: Int, d: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              %composer.startMovableGroup(<>, %composer.joinKey(%composer.joinKey(%composer.joinKey(a, b), c), d), "<A()>")
              A(%composer, 0)
              %composer.endMovableGroup()
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyWithComposableValue(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                while(x > 0) {
                    key(R()) {
                        A()
                    }
                }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)*<R()>:Test.kt")
              while (x > 0) {
                %composer.startMovableGroup(<>, R(%composer, 0), "<A()>")
                A(%composer, 0)
                %composer.endMovableGroup()
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testKeyAsAValue(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int) {
                val y = key(x) { R() }
                P(y)
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Example)<P(y)>:Test.kt")
              val y =
              %composer.startMovableGroup(<>, x, "<R()>")
              val tmp0 = R(%composer, 0)
              %composer.endMovableGroup()
              tmp0
              P(y, %composer, 0)
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testDynamicWrappingGroupWithReturnValue(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false) @Composable
            fun Example(x: Int): Int {
                return if (x > 0) {
                    if (B()) 1
                    else if (B()) 2
                    else 3
                } else 4
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Example(x: Int, %composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Example):Test.kt")
              val tmp0 = if (x > 0) {
                %composer.startReplaceableGroup(<>, "")
                val tmp4_group =
                val tmp3_group = if (%composer.startReplaceableGroup(<>, "<B()>")
                val tmp1_group = B(%composer, 0)
                %composer.endReplaceableGroup()
                tmp1_group) 1 else if (%composer.startReplaceableGroup(<>, "<B()>")
                val tmp2_group = B(%composer, 0)
                %composer.endReplaceableGroup()
                tmp2_group) 2 else 3
                tmp3_group
                %composer.endReplaceableGroup()
                tmp4_group
              } else {
                %composer.startReplaceableGroup(<>)
                %composer.endReplaceableGroup()
                4
              }
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testTheThing(): Unit = controlFlow(
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Simple() {
              // this has a composable call in it, and since we don't know the number of times the
              // lambda will get called, we place a group around the whole call
              run {
                A()
              }
              A()
            }

            @ComposableContract(restartable = false)
            @Composable
            fun WithReturn() {
              // this has an early return in it, so it needs to end all of the groups present.
              run {
                A()
                return@WithReturn
              }
              A()
            }

            @ComposableContract(restartable = false)
            @Composable
            fun NoCalls() {
              // this has no composable calls in it, so shouldn't cause any groups to get created
              run {
                println("hello world")
              }
              A()
            }

            @ComposableContract(restartable = false)
            @Composable
            fun NoCallsAfter() {
              // this has a composable call in the lambda, but not after it, which means the
              // group should be able to be coalesced into the group of the function
              run {
                A()
              }
            }
        """,
        """
            @ComposableContract(restartable = false)
            @Composable
            fun Simple(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(Simple)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<A()>")
              run {
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
            @ComposableContract(restartable = false)
            @Composable
            fun WithReturn(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(WithReturn)<A()>:Test.kt")
              %composer.startReplaceableGroup(<>, "*<A()>")
              run {
                A(%composer, 0)
                %composer.endReplaceableGroup()
                %composer.endReplaceableGroup()
                return
              }
              %composer.endReplaceableGroup()
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
            @ComposableContract(restartable = false)
            @Composable
            fun NoCalls(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(NoCalls)<A()>:Test.kt")
              run {
                println("hello world")
              }
              A(%composer, 0)
              %composer.endReplaceableGroup()
            }
            @ComposableContract(restartable = false)
            @Composable
            fun NoCallsAfter(%composer: Composer<*>?, %changed: Int) {
              %composer.startReplaceableGroup(<>, "C(NoCallsAfter)*<A()>:Test.kt")
              run {
                A(%composer, 0)
              }
              %composer.endReplaceableGroup()
            }
        """
    )

    @Test
    fun testLetWithComposableCalls(): Unit = controlFlow(
        """
            @Composable
            fun Example(x: Int?) {
              x?.let {
                if (it > 0) {
                  A(a)
                }
                A(b)
              }
              A(c)
            }
        """,
        """
            @Composable
            fun Example(x: Int?, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Example)<A(c)>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                val tmp0_safe_receiver = x
                when {
                  tmp0_safe_receiver == null -> {
                    %composer.startReplaceableGroup(<>)
                    %composer.endReplaceableGroup()
                    null
                  }
                  else -> {
                    %composer.startReplaceableGroup(<>, "*<A(b)>")
                    tmp0_safe_receiver.let { it: Int ->
                      if (it > 0) {
                        %composer.startReplaceableGroup(<>, "<A(a)>")
                        A(a, %composer, 0)
                        %composer.endReplaceableGroup()
                      } else {
                        %composer.startReplaceableGroup(<>)
                        %composer.endReplaceableGroup()
                      }
                      A(b, %composer, 0)
                    }
                    %composer.endReplaceableGroup()
                  }
                }
                A(c, %composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testLetWithoutComposableCalls(): Unit = controlFlow(
        """
            @Composable
            fun Example(x: Int?) {
              x?.let {
                if (it > 0) {
                  NA()
                }
                NA()
              }
              A()
            }
        """,
        """
            @Composable
            fun Example(x: Int?, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Example)<A()>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(x)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                x?.let { it: Int ->
                  if (it > 0) {
                    NA()
                  }
                  NA()
                }
                A(%composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Example(x, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testApplyOnComposableCallResult(): Unit = controlFlow(
        """
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.State

            @Composable
            fun <T> provided(value: T): State<T> = remember { mutableStateOf(value) }.apply {
                this.value = value
            }
        """,
        """
            @Composable
            fun <T> provided(value: T, %composer: Composer<*>?, %changed: Int): State<T> {
              %composer.startReplaceableGroup(<>, "C(provided)*<rememb...>:Test.kt")
              val tmp0 = remember({
                val tmp0_return = mutableStateOf(
                  value = value
                )
                tmp0_return
              }, %composer, 0).apply {
                value = value
              }
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testReturnInlinedExpressionWithCall(): Unit = controlFlow(
        """
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.State

            @Composable
            fun Test(x: Int): Int {
                return x.let {
                    A()
                    123
                }
            }
        """,
        """
            @Composable
            fun Test(x: Int, %composer: Composer<*>?, %changed: Int): Int {
              %composer.startReplaceableGroup(<>, "C(Test)*<A()>:Test.kt")
              val tmp0 =
              val tmp1_group = x.let { it: Int ->
                A(%composer, 0)
                val tmp0_return = 123
                tmp0_return
              }
              tmp1_group
              %composer.endReplaceableGroup()
              return tmp0
            }
        """
    )

    @Test
    fun testCallingAWrapperComposable(): Unit = controlFlow(
        """
            @Composable
            fun Test() {
              W {
                A()
              }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<W>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                W(composableLambda(%composer, <>, true, "C<A()>:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    A(%composer, 0)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
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
    fun testCallingAnInlineWrapperComposable(): Unit = controlFlow(
        """
            @Composable
            fun Test() {
              IW {
                A()
              }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<IW>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                IW({ %composer: Composer<*>?, %changed: Int ->
                  %composer.startReplaceableGroup(<>, "C<A()>:Test.kt")
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    A(%composer, 0)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                  %composer.endReplaceableGroup()
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
    fun testRepeatedCallsToEffects(): Unit = verifyComposeIrTransform(
        """
            import androidx.compose.runtime.Composable

            @Composable
            fun Test() {
                Wrap {
                    repeat(number) {
                        effects[it] = effect { 0 }
                    }
                    outside = effect { "0" }
                }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<Wrap>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                Wrap(composableLambda(%composer, <>, true, "C<{>,<effect>:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    %composer.startReplaceableGroup(<>, "*<{>,<effect>")
                    repeat(number) { it: Int ->
                      effects[it] = effect(remember({
                        {
                          0
                        }
                      }, %composer, 0), %composer, 0b0110)
                    }
                    %composer.endReplaceableGroup()
                    outside = effect(remember({
                      {
                        "0"
                      }
                    }, %composer, 0), %composer, 0b0110)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """,
        """
            import androidx.compose.runtime.Composable

            var effects = mutableListOf<Any>()
            var outside: Any = ""
            var number = 1

            @Composable fun Wrap(block: @Composable () -> Unit) =  block()
            @Composable fun <T> effect(block: () -> T): T = block()
        """

    )

    @Test
    fun testComposableWithInlineClass(): Unit = controlFlow(
        """
            @Composable
            fun Test(value: InlineClass) {
                A()
            }
        """,
        """
            @Composable
            fun Test(value: InlineClass, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)P(0:InlineClass)<A()>:Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(value.value)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                A(%composer, 0)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(value, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testParameterOrderInformation(): Unit = controlFlow(
        """
            @Composable fun Test01(p0: Int, p1: Int, p2: Int, p3: Int) { }
            @Composable fun Test02(p0: Int, p1: Int, p3: Int, p2: Int) { }
            @Composable fun Test03(p0: Int, p2: Int, p1: Int, p3: Int) { }
            @Composable fun Test04(p0: Int, p2: Int, p3: Int, p1: Int) { }
            @Composable fun Test05(p0: Int, p3: Int, p1: Int, p2: Int) { }
            @Composable fun Test06(p0: Int, p3: Int, p2: Int, p1: Int) { }
            @Composable fun Test07(p1: Int, p0: Int, p2: Int, p3: Int) { }
            @Composable fun Test08(p1: Int, p0: Int, p3: Int, p2: Int) { }
            @Composable fun Test09(p1: Int, p2: Int, p0: Int, p3: Int) { }
            @Composable fun Test00(p1: Int, p2: Int, p3: Int, p0: Int) { }
            @Composable fun Test11(p1: Int, p3: Int, p0: Int, p2: Int) { }
            @Composable fun Test12(p1: Int, p3: Int, p2: Int, p0: Int) { }
            @Composable fun Test13(p2: Int, p0: Int, p1: Int, p3: Int) { }
            @Composable fun Test14(p2: Int, p0: Int, p3: Int, p1: Int) { }
            @Composable fun Test15(p2: Int, p1: Int, p0: Int, p3: Int) { }
            @Composable fun Test16(p2: Int, p1: Int, p3: Int, p0: Int) { }
            @Composable fun Test17(p2: Int, p3: Int, p0: Int, p1: Int) { }
            @Composable fun Test18(p2: Int, p3: Int, p1: Int, p0: Int) { }
            @Composable fun Test19(p3: Int, p0: Int, p1: Int, p2: Int) { }
            @Composable fun Test20(p3: Int, p0: Int, p2: Int, p1: Int) { }
            @Composable fun Test21(p3: Int, p1: Int, p0: Int, p2: Int) { }
            @Composable fun Test22(p3: Int, p1: Int, p2: Int, p0: Int) { }
            @Composable fun Test23(p3: Int, p2: Int, p0: Int, p1: Int) { }
            @Composable fun Test24(p3: Int, p2: Int, p1: Int, p0: Int) { }
        """,
        """
            @Composable
            fun Test01(p0: Int, p1: Int, p2: Int, p3: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test01):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test01(p0, p1, p2, p3, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test02(p0: Int, p1: Int, p3: Int, p2: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test02)P(!2,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test02(p0, p1, p3, p2, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test03(p0: Int, p2: Int, p1: Int, p3: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test03)P(!1,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test03(p0, p2, p1, p3, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test04(p0: Int, p2: Int, p3: Int, p1: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test04)P(!1,2,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test04(p0, p2, p3, p1, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test05(p0: Int, p3: Int, p1: Int, p2: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test05)P(!1,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test05(p0, p3, p1, p2, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test06(p0: Int, p3: Int, p2: Int, p1: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test06)P(!1,3,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test06(p0, p3, p2, p1, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test07(p1: Int, p0: Int, p2: Int, p3: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test07)P(1):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test07(p1, p0, p2, p3, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test08(p1: Int, p0: Int, p3: Int, p2: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test08)P(1!1,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test08(p1, p0, p3, p2, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test09(p1: Int, p2: Int, p0: Int, p3: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test09)P(1,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test09(p1, p2, p0, p3, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test00(p1: Int, p2: Int, p3: Int, p0: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test00)P(1,2,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test00(p1, p2, p3, p0, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test11(p1: Int, p3: Int, p0: Int, p2: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test11)P(1,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test11(p1, p3, p0, p2, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test12(p1: Int, p3: Int, p2: Int, p0: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test12)P(1,3,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test12(p1, p3, p2, p0, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test13(p2: Int, p0: Int, p1: Int, p3: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test13)P(2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test13(p2, p0, p1, p3, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test14(p2: Int, p0: Int, p3: Int, p1: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test14)P(2!1,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test14(p2, p0, p3, p1, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test15(p2: Int, p1: Int, p0: Int, p3: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test15)P(2,1):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test15(p2, p1, p0, p3, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test16(p2: Int, p1: Int, p3: Int, p0: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test16)P(2,1,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test16(p2, p1, p3, p0, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test17(p2: Int, p3: Int, p0: Int, p1: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test17)P(2,3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test17(p2, p3, p0, p1, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test18(p2: Int, p3: Int, p1: Int, p0: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test18)P(2,3,1):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test18(p2, p3, p1, p0, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test19(p3: Int, p0: Int, p1: Int, p2: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test19)P(3):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test19(p3, p0, p1, p2, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test20(p3: Int, p0: Int, p2: Int, p1: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test20)P(3!1,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test20(p3, p0, p2, p1, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test21(p3: Int, p1: Int, p0: Int, p2: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test21)P(3,1):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test21(p3, p1, p0, p2, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test22(p3: Int, p1: Int, p2: Int, p0: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test22)P(3,1,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test22(p3, p1, p2, p0, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test23(p3: Int, p2: Int, p0: Int, p1: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test23)P(3,2):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test23(p3, p2, p0, p1, %composer, %changed or 0b0001)
              }
            }
            @Composable
            fun Test24(p3: Int, p2: Int, p1: Int, p0: Int, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test24)P(3,2,1):Test.kt")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(p3)) 0b0100 else 0b0010
              }
              if (%changed and 0b00011000 === 0) {
                %dirty = %dirty or if (%composer.changed(p2)) 0b00010000 else 0b1000
              }
              if (%changed and 0b01100000 === 0) {
                %dirty = %dirty or if (%composer.changed(p1)) 0b01000000 else 0b00100000
              }
              if (%changed and 0b000110000000 === 0) {
                %dirty = %dirty or if (%composer.changed(p0)) 0b000100000000 else 0b10000000
              }
              if (%dirty and 0b10101011 xor 0b10101010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test24(p3, p2, p1, p0, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testSourceInformationWithPackageName(): Unit = verifyComposeIrTransform(
        source = """
            package androidx.compose.runtime.tests

            import androidx.compose.runtime.Composable

            @Composable
            fun Test(value: LocalInlineClass) {

            }
        """,
        extra = """
            package androidx.compose.runtime.tests

            inline class LocalInlineClass(val value: Int)
        """,
        expectedTransformed = """
            @Composable
            fun Test(value: LocalInlineClass, %composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)P(0:c#runtime.tests.LocalInlineClass):Test.kt#992ot2")
              val %dirty = %changed
              if (%changed and 0b0110 === 0) {
                %dirty = %dirty or if (%composer.changed(value.value)) 0b0100 else 0b0010
              }
              if (%dirty and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(value, %composer, %changed or 0b0001)
              }
            }
        """
    )

    @Test
    fun testSourceOffsetOrderForParameterExpressions(): Unit = verifyComposeIrTransform(
        source = """
            import androidx.compose.runtime.Composable

            @Composable
            fun Test() {
                A(b(), c(), d())
                B()
            }
        """,
        extra = """
            import androidx.compose.runtime.Composable

            @Composable fun A(a: Int, b: Int, c: Int) { }
            @Composable fun B() { }
            @Composable fun b(): Int = 1
            @Composable fun c(): Int = 1
            @Composable fun d(): Int = 1
        """,
        expectedTransformed = """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<b()>,<c()>,<d()>,<A(b(),>,<B()>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                A(b(%composer, 0), c(%composer, 0), d(%composer, 0), %composer, 0)
                B(%composer, 0)
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
    fun testSourceLineInformationForNormalInline(): Unit = verifyComposeIrTransform(
        source = """
            import androidx.compose.runtime.Composable

            @Composable
            fun Test() {
              W {
                IW {
                    T(2)
                    repeat(3) {
                        T(3)
                    }
                    T(4)
                }
              }
            }
        """,
        extra = """
            import androidx.compose.runtime.Composable

            @Composable fun W(block: @Composable () -> Unit) = block()
            @Composable inline fun IW(block: @Composable () -> Unit) = block()
            @Composable fun T(value: Int) { }
        """,
        expectedTransformed = """
            @Composable
            fun Test(%composer: Composer<*>?, %changed: Int) {
              %composer.startRestartGroup(<>, "C(Test)<W>:Test.kt")
              if (%changed !== 0 || !%composer.skipping) {
                W(composableLambda(%composer, <>, true, "C<IW>:Test.kt") { %composer: Composer<*>?, %changed: Int ->
                  if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                    IW({ %composer: Composer<*>?, %changed: Int ->
                      %composer.startReplaceableGroup(<>, "C<T(2)>,<T(4)>:Test.kt")
                      if (%changed and 0b0011 xor 0b0010 !== 0 || !%composer.skipping) {
                        T(2, %composer, 0b0110)
                        %composer.startReplaceableGroup(<>, "*<T(3)>")
                        repeat(3) { it: Int ->
                          T(3, %composer, 0b0110)
                        }
                        %composer.endReplaceableGroup()
                        T(4, %composer, 0b0110)
                      } else {
                        %composer.skipToGroupEnd()
                      }
                      %composer.endReplaceableGroup()
                    }, %composer, 0)
                  } else {
                    %composer.skipToGroupEnd()
                  }
                }, %composer, 0b0110)
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer<*>?, %force: Int ->
                Test(%composer, %changed or 0b0001)
              }
            }
        """
    )
}
