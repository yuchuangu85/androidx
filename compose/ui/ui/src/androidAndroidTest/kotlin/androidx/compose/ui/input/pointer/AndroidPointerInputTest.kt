/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.compose.ui.input.pointer

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.emptyContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.PointerCoords
import androidx.compose.ui.gesture.PointerProperties
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.milliseconds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class AndroidPointerInputTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val rule = androidx.test.rule.ActivityTestRule<AndroidPointerInputTestActivity>(
        AndroidPointerInputTestActivity::class.java
    )

    private lateinit var androidComposeView: AndroidComposeView
    private lateinit var container: ViewGroup

    @Before
    fun setup() {
        val activity = rule.activity
        container = spy(FrameLayout(activity)).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rule.runOnUiThread {
            activity.setContentView(container)
        }
    }

    @Test
    fun dispatchTouchEvent_noPointerInputModifiers_returnsFalse() {

        // Arrange

        countDown { latch ->
            rule.runOnUiThread {
                container.setContent(Recomposer.current()) {
                    FillLayout(Modifier
                        .onPositioned { latch.countDown() })
                }
            }
        }

        rule.runOnUiThread {
            androidComposeView = container.getChildAt(0) as AndroidComposeView

            val motionEvent = MotionEvent(
                0,
                MotionEvent.ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(0f, 0f))
            )

            // Act
            val actual = androidComposeView.dispatchTouchEvent(motionEvent)

            // Assert
            assertThat(actual).isFalse()
        }
    }

    @Test
    fun dispatchTouchEvent_pointerInputModifier_returnsTrue() {

        // Arrange

        countDown { latch ->
            rule.runOnUiThread {
                container.setContent(Recomposer.current()) {
                    FillLayout(Modifier
                        .consumeMovementGestureFilter()
                        .onPositioned { latch.countDown() })
                }
            }
        }

        rule.runOnUiThread {

            androidComposeView = container.getChildAt(0) as AndroidComposeView

            val locationInWindow = IntArray(2).also {
                androidComposeView.getLocationInWindow(it)
            }

            val motionEvent = MotionEvent(
                0,
                MotionEvent.ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(locationInWindow[0].toFloat(), locationInWindow[1].toFloat()))
            )

            // Act
            val actual = androidComposeView.dispatchTouchEvent(motionEvent)

            // Assert
            assertThat(actual).isTrue()
        }
    }

    @Test
    fun dispatchTouchEvent_movementNotConsumed_requestDisallowInterceptTouchEventNotCalled() {
        dispatchTouchEvent_movementConsumptionInCompose(
            consumeMovement = false,
            callsRequestDisallowInterceptTouchEvent = false
        )
    }

    @Test
    fun dispatchTouchEvent_movementConsumed_requestDisallowInterceptTouchEventCalled() {
        dispatchTouchEvent_movementConsumptionInCompose(
            consumeMovement = true,
            callsRequestDisallowInterceptTouchEvent = true
        )
    }

    @Test
    fun dispatchTouchEvent_notMeasuredLayoutsAreMeasuredFirst() {
        val size = mutableStateOf(10)
        val latch = CountDownLatch(1)
        var consumedDownPosition: Offset? = null
        rule.runOnUiThread {
            container.setContent(Recomposer.current()) {
                Layout(
                    {},
                    Modifier
                        .consumeDownGestureFilter {
                            consumedDownPosition = it
                        }
                        .onPositioned {
                            latch.countDown()
                        }
                ) { _, _ ->
                    val sizePx = size.value
                    layout(sizePx, sizePx) {}
                }
            }
        }

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()

        rule.runOnUiThread {
            androidComposeView = container.getChildAt(0) as AndroidComposeView

            // we update size from 10 to 20 pixels
            size.value = 20
            // this call will synchronously mark the LayoutNode as needs remeasure
            @OptIn(ExperimentalComposeApi::class)
            Snapshot.sendApplyNotifications()

            val ownerPosition = androidComposeView.calculatePosition()
            val motionEvent = MotionEvent(
                0,
                MotionEvent.ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(ownerPosition.x + 15f, ownerPosition.y + 15f))
            )

            // we expect it to first remeasure and only then process
            androidComposeView.dispatchTouchEvent(motionEvent)

            assertThat(consumedDownPosition).isEqualTo(Offset(15f, 15f))
        }
    }

    // Currently ignored because it fails when run via command line.  Runs successfully in Android
    // Studio.
    @Test
    // TODO(b/158099918): For some reason, this test fails when run from command line but passes
    //  when run from Android Studio.  This seems to be caused by b/158099918.  Once that is
    //  fixed, @Ignore can be removed.
    @Ignore
    fun dispatchTouchEvent_throughLayersOfAndroidAndCompose_hitsChildPointerInputFilter() {

        // Arrange

        val context = rule.activity

        val log = mutableListOf<List<PointerInputChange>>()

        countDown { latch ->
            rule.runOnUiThread {
                container.setContent(Recomposer.current()) {
                    AndroidWithCompose(context, 1) {
                        AndroidWithCompose(context, 10) {
                            AndroidWithCompose(context, 100) {
                                Layout(
                                    {},
                                    Modifier
                                        .logEventsGestureFilter(log)
                                        .onPositioned {
                                            latch.countDown()
                                        }
                                ) { _, _ ->
                                    layout(5, 5) {}
                                }
                            }
                        }
                    }
                }
            }
        }

        rule.runOnUiThread {

            androidComposeView = container.getChildAt(0) as AndroidComposeView

            val locationInWindow = IntArray(2).also {
                androidComposeView.getLocationInWindow(it)
            }

            val motionEvent = MotionEvent(
                0,
                MotionEvent.ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(
                    PointerCoords(
                        locationInWindow[0].toFloat() + 1 + 10 + 100,
                        locationInWindow[1].toFloat() + 1 + 10 + 100
                    )
                )
            )

            // Act
            androidComposeView.dispatchTouchEvent(motionEvent)

            // Assert
            assertThat(log).hasSize(1)
            assertThat(log[0]).isEqualTo(listOf(down(0, 0.milliseconds, 0f, 0f)))
        }
    }

    private fun dispatchTouchEvent_movementConsumptionInCompose(
        consumeMovement: Boolean,
        callsRequestDisallowInterceptTouchEvent: Boolean
    ) {

        // Arrange

        countDown { latch ->
            rule.runOnUiThread {
                container.setContent(Recomposer.current()) {
                    FillLayout(Modifier
                        .consumeMovementGestureFilter(consumeMovement)
                        .onPositioned { latch.countDown() })
                }
            }
        }

        rule.runOnUiThread {

            androidComposeView = container.getChildAt(0) as AndroidComposeView
            val (x, y) = IntArray(2).let { array ->
                androidComposeView.getLocationInWindow(array)
                array.map { item -> item.toFloat() }
            }

            val down = MotionEvent(
                0,
                MotionEvent.ACTION_DOWN,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(x, y))
            )

            val move = MotionEvent(
                0,
                MotionEvent.ACTION_MOVE,
                1,
                0,
                arrayOf(PointerProperties(0)),
                arrayOf(PointerCoords(x + 1, y))
            )

            androidComposeView.dispatchTouchEvent(down)

            // Act
            androidComposeView.dispatchTouchEvent(move)

            // Assert
            if (callsRequestDisallowInterceptTouchEvent) {
                verify(container).requestDisallowInterceptTouchEvent(true)
            } else {
                verify(container, never()).requestDisallowInterceptTouchEvent(any())
            }
        }
    }
}

@Suppress("TestFunctionName")
@Composable
fun AndroidWithCompose(context: Context, androidPadding: Int, children: @Composable () -> Unit) {
    val anotherLayout = FrameLayout(context).also { view ->
        view.setContent(Recomposer.current()) {
            children()
        }
        view.setPadding(androidPadding, androidPadding, androidPadding, androidPadding)
    }
    AndroidView({ anotherLayout })
}

fun Modifier.consumeMovementGestureFilter(consumeMovement: Boolean = false): Modifier = composed {
    val filter = remember(consumeMovement) { ConsumeMovementGestureFilter(consumeMovement) }
    PointerInputModifierImpl(filter)
}

fun Modifier.consumeDownGestureFilter(onDown: (Offset) -> Unit): Modifier = composed {
    val filter = remember { ConsumeDownChangeFilter() }
    filter.onDown = onDown
    this.then(PointerInputModifierImpl(filter))
}

fun Modifier.logEventsGestureFilter(log: MutableList<List<PointerInputChange>>): Modifier =
    composed {
        val filter = remember { LogEventsGestureFilter(log) }
        this.then(PointerInputModifierImpl(filter))
    }

private class PointerInputModifierImpl(override val pointerInputFilter: PointerInputFilter) :
    PointerInputModifier

private class ConsumeMovementGestureFilter(val consumeMovement: Boolean) : PointerInputFilter() {
    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntSize
    ) =
        if (consumeMovement) {
            changes.map { it.consumePositionChange(
                it.positionChange().x,
                it.positionChange().y)
            }
        } else {
            changes
        }

    override fun onCancel() {}
}

private class ConsumeDownChangeFilter : PointerInputFilter() {
    var onDown by mutableStateOf<(Offset) -> Unit>({})
    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntSize
    ) = changes.map {
        if (it.changedToDown()) {
            onDown(it.current.position!!)
            it.consumeDownChange()
        } else {
            it
        }
    }

    override fun onCancel() {}
}

private class LogEventsGestureFilter(val log: MutableList<List<PointerInputChange>>) :
    PointerInputFilter() {

    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntSize
    ): List<PointerInputChange> {
        if (pass == PointerEventPass.Initial) {
            log.add(changes.map { it.copy() })
        }
        return changes
    }

    override fun onCancel() {}
}

@Suppress("TestFunctionName")
@Composable
private fun FillLayout(modifier: Modifier = Modifier) {
    Layout(emptyContent(), modifier) { _, constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {}
    }
}

private fun countDown(block: (CountDownLatch) -> Unit) {
    val countDownLatch = CountDownLatch(1)
    block(countDownLatch)
    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
}

class AndroidPointerInputTestActivity : ComponentActivity()

@Suppress("SameParameterValue", "TestFunctionName")
private fun MotionEvent(
    eventTime: Int,
    action: Int,
    numPointers: Int,
    actionIndex: Int,
    pointerProperties: Array<MotionEvent.PointerProperties>,
    pointerCoords: Array<MotionEvent.PointerCoords>
) = MotionEvent.obtain(
    0,
    eventTime.toLong(),
    action + (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
    numPointers,
    pointerProperties,
    pointerCoords,
    0,
    0,
    0f,
    0f,
    0,
    0,
    0,
    0
)