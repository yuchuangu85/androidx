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

package androidx.compose.testutils

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import android.util.DisplayMetrics
import android.view.DisplayListCanvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.dispatch.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidOwner
import androidx.compose.ui.platform.setContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

/**
 * Factory method to provide implementation of [ComposeBenchmarkScope].
 */
fun <T : ComposeTestCase> createAndroidComposeBenchmarkRunner(
    testCaseFactory: () -> T,
    activity: ComponentActivity
): ComposeBenchmarkScope<T> {
    return AndroidComposeTestCaseRunner(testCaseFactory, activity)
}

internal class AndroidComposeTestCaseRunner<T : ComposeTestCase>(
    private val testCaseFactory: () -> T,
    private val activity: ComponentActivity
) : ComposeBenchmarkScope<T> {

    override val measuredWidth: Int
        get() = view!!.measuredWidth
    override val measuredHeight: Int
        get() = view!!.measuredHeight

    internal var view: View? = null
        private set

    private var composition: Composition? = null

    override var didLastRecomposeHaveChanges = false
        private set

    private val supportsRenderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val supportsMRenderNode = Build.VERSION.SDK_INT < Build.VERSION_CODES.P &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    private val screenWithSpec: Int
    private val screenHeightSpec: Int
    private val capture = if (supportsRenderNode) {
        RenderNodeCapture()
    } else if (supportsMRenderNode) {
        MRenderNodeCapture()
    } else {
        PictureCapture()
    }

    private var canvas: Canvas? = null

    private class AutoFrameClock(
        private val singleFrameTimeNanos: Long = 16_000_000
    ) : MonotonicFrameClock {
        private val lastFrameTime = AtomicLong(0L)

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            onFrame(lastFrameTime.getAndAdd(singleFrameTimeNanos))
    }

    private val frameClock = AutoFrameClock()
    private val recomposer: Recomposer = Recomposer()

    private var simulationState: SimulationState = SimulationState.Initialized

    private var testCase: T? = null

    init {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION") /* defaultDisplay + getMetrics() */
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        screenWithSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST)
        screenHeightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
    }

    override fun createTestCase() {
        require(view == null) { "Content was already set!" }
        require(testCase == null) { "Content was already set!" }
        testCase = testCaseFactory()
        simulationState = SimulationState.TestCaseCreated
    }

    override fun emitContent() {
        require(view == null) { "Content was already set!" }
        require(testCase != null && simulationState == SimulationState.TestCaseCreated) {
            "Need to call onPreEmitContent before emitContent!"
        }

        composition = activity.setContent(recomposer) { testCase!!.emitContent() }
        view = findAndroidOwner(activity)!!.view
        @OptIn(ExperimentalComposeApi::class)
        Snapshot.notifyObjectsInitialized()
        simulationState = SimulationState.EmitContentDone
    }

    // TODO: This method may advance the global snapshot and should be just a getter
    override fun hasPendingChanges(): Boolean {
        if (recomposer.hasPendingChanges() || hasPendingChangesInFrame()) {
            @OptIn(ExperimentalComposeApi::class)
            Snapshot.sendApplyNotifications()
        }

        return recomposer.hasPendingChanges()
    }

    /**
     * The reason we have this method is that if a model gets changed in the same frame as created
     * it won'd trigger pending frame. So [Recompose#hasPendingChanges] stays false. Committing
     * the current frame does not help either. So we need to check this in order to know if we
     * need to recompose.
     */
    private fun hasPendingChangesInFrame(): Boolean {
        @OptIn(ExperimentalComposeApi::class)
        return Snapshot.current.hasPendingChanges()
    }

    override fun measure() {
        getView().measure(screenWithSpec, screenHeightSpec)
        simulationState = SimulationState.MeasureDone
    }

    override fun measureWithSpec(widthSpec: Int, heightSpec: Int) {
        getView().measure(widthSpec, heightSpec)
        simulationState = SimulationState.MeasureDone
    }

    override fun drawPrepare() {
        require(
            simulationState == SimulationState.LayoutDone ||
                    simulationState == SimulationState.DrawDone
        ) {
            "Draw can be only executed after layout or draw, current state is '$simulationState'"
        }
        canvas = capture.beginRecording(getView().width, getView().height)
        simulationState = SimulationState.DrawPrepared
    }

    override fun draw() {
        require(simulationState == SimulationState.DrawPrepared) {
            "You need to call 'drawPrepare' before calling 'draw'."
        }
        getView().draw(canvas)
        simulationState = SimulationState.DrawInProgress
    }

    override fun drawFinish() {
        require(simulationState == SimulationState.DrawInProgress) {
            "You need to call 'draw' before calling 'drawFinish'."
        }
        capture.endRecording()
        simulationState = SimulationState.DrawDone
    }

    override fun drawToBitmap() {
        drawPrepare()
        draw()
        drawFinish()
    }

    override fun requestLayout() {
        getView().requestLayout()
    }

    override fun layout() {
        require(simulationState == SimulationState.MeasureDone) {
            "Layout can be only executed after measure, current state is '$simulationState'"
        }
        val view = getView()
        view.layout(view.left, view.top, view.right, view.bottom)
        simulationState = SimulationState.LayoutDone
    }

    override fun recompose() {
        if (hasPendingChanges()) {
            didLastRecomposeHaveChanges = true
            runBlocking(frameClock) {
                recomposer.recomposeAndApplyChanges(1)
            }
        } else {
            didLastRecomposeHaveChanges = false
        }
        simulationState = SimulationState.RecomposeDone
    }

    override fun launchRecomposeIn(coroutineScope: CoroutineScope): Job =
        coroutineScope.launch(frameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }

    override fun doFrame() {
        if (view == null) {
            setupContent()
        }

        recompose()

        measure()
        layout()
        drawToBitmap()
    }

    override fun invalidateViews() {
        invalidateViews(getView())
    }

    override fun disposeContent() {
        if (view == null) {
            // Already disposed or never created any content
            return
        }

        composition?.dispose()

        // Clear the view
        val rootView = activity.findViewById(android.R.id.content) as ViewGroup
        rootView.removeAllViews()
        // Important so we can set the content again.
        view = null
        testCase = null
        simulationState = SimulationState.Initialized
    }

    override fun capturePreviewPictureToActivity() {
        require(measuredWidth > 0 && measuredHeight > 0) {
            "Preview can't be used on empty view. Did you run measure & layout before calling it?"
        }

        val picture = Picture()
        val canvas = picture.beginRecording(getView().measuredWidth, getView().measuredHeight)
        getView().draw(canvas)
        picture.endRecording()
        val imageView = ImageView(activity)
        val bitmap: Bitmap
        if (Build.VERSION.SDK_INT >= 28) {
            bitmap = Bitmap.createBitmap(picture)
        } else {
            val width = picture.width.coerceAtLeast(1)
            val height = picture.height.coerceAtLeast(1)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawPicture(picture)
        }
        imageView.setImageBitmap(bitmap)
        activity.setContentView(imageView)
    }

    private fun getView(): View {
        require(view != null) { "View was not set! Call setupContent first!" }
        return view!!
    }

    override fun getTestCase(): T {
        return testCase!!
    }
}

private enum class SimulationState {
    Initialized,
    TestCaseCreated,
    EmitContentDone,
    MeasureDone,
    LayoutDone,
    DrawPrepared,
    DrawInProgress,
    DrawDone,
    RecomposeDone
}

private fun findAndroidOwner(activity: Activity): AndroidOwner? {
    return findAndroidOwner(activity.findViewById(android.R.id.content) as ViewGroup)
}

private fun findAndroidOwner(view: View): AndroidOwner? {
    if (view is AndroidOwner) {
        return view
    }

    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val composeView = findAndroidOwner(view.getChildAt(i))
            if (composeView != null) {
                return composeView
            }
        }
    }
    return null
}

private fun invalidateViews(view: View) {
    view.invalidate()
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            invalidateViews(child)
        }
    }
}

// We must separate the use of RenderNode so that it isn't referenced in any
// way on platforms that don't have it. This extracts RenderNode use to a
// potentially unloaded class, RenderNodeCapture.
private interface DrawCapture {
    fun beginRecording(width: Int, height: Int): Canvas
    fun endRecording()
}

@TargetApi(Build.VERSION_CODES.Q)
private class RenderNodeCapture : DrawCapture {
    private val renderNode = RenderNode("Test")

    override fun beginRecording(width: Int, height: Int): Canvas {
        renderNode.setPosition(0, 0, width, height)
        return renderNode.beginRecording()
    }

    override fun endRecording() {
        renderNode.endRecording()
    }
}

private class PictureCapture : DrawCapture {
    private val picture = Picture()

    override fun beginRecording(width: Int, height: Int): Canvas {
        return picture.beginRecording(width, height)
    }

    override fun endRecording() {
        picture.endRecording()
    }
}

private class MRenderNodeCapture : DrawCapture {
    private var renderNode = android.view.RenderNode.create("Test", null)

    private var canvas: DisplayListCanvas? = null

    override fun beginRecording(width: Int, height: Int): Canvas {
        renderNode.setLeftTopRightBottom(0, 0, width, height)
        canvas = renderNode.start(width, height)
        return canvas!!
    }

    override fun endRecording() {
        renderNode.end(canvas!!)
        canvas = null
    }
}