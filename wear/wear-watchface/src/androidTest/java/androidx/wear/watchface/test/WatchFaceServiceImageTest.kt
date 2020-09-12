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

package androidx.wear.watchface.test

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import android.support.wearable.watchface.Constants
import android.support.wearable.watchface.IWatchFaceCommand
import android.support.wearable.watchface.IWatchFaceService
import android.support.wearable.watchface.ashmemCompressedImageBundleToBitmap
import android.support.wearable.watchface.WatchFaceStyle
import android.support.wearable.watchface.accessibility.ContentDescriptionLabel
import android.view.Surface
import android.view.SurfaceHolder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.wear.complications.SystemProviders
import androidx.wear.watchface.ComplicationBoundsType
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.samples.EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val API_VERSION = 3
private const val BITMAP_WIDTH = 400
private const val BITMAP_HEIGHT = 400
private const val TIMEOUT_MS = 800L

private class WatchFaceServiceStub(
    private val apiVersion: Int,
    private val engineWrapper: WatchFaceService.EngineWrapper,
    private val complicationProviders: Map<Int, ComplicationData>
) : IWatchFaceService.Stub() {
    var iWatchFaceCommand: IWatchFaceCommand? = null

    override fun setStyle(style: WatchFaceStyle) {
    }

    override fun registerUserStyleSchema(styleSchema: MutableList<Bundle>?) {
    }

    override fun setActiveComplications(ids: IntArray, updateAll: Boolean) {
    }

    override fun setComplicationDetails(id: Int, bounds: Rect?, @ComplicationBoundsType type: Int) {
    }

    override fun setDefaultComplicationProvider(
        watchFaceComplicationId: Int,
        provider: ComponentName,
        type: Int
    ) {
    }

    override fun setDefaultSystemComplicationProvider(
        watchFaceComplicationId: Int,
        systemProvider: Int,
        type: Int
    ) {
    }

    override fun registerIWatchFaceCommand(iWatchFaceCommandBundle: Bundle?) {
        this.iWatchFaceCommand = IWatchFaceCommand.Stub.asInterface(
            iWatchFaceCommandBundle
                ?.getBinder(Constants.EXTRA_WATCH_FACE_COMMAND_BINDER)
        )
    }

    override fun setContentDescriptionLabels(labels: Array<ContentDescriptionLabel>) {
    }

    override fun reserved1() {
    }

    override fun setDefaultComplicationProviderWithFallbacks(
        watchFaceComplicationId: Int,
        providers: List<ComponentName>,
        fallbackSystemProvider: Int,
        type: Int
    ) {
        setComplication(watchFaceComplicationId, complicationProviders[fallbackSystemProvider]!!)
    }

    override fun getStoredUserStyle(): Bundle? {
        return null
    }

    override fun setCurrentUserStyle(style: Bundle?) {
    }

    override fun getApiVersion() = apiVersion

    override fun setComplicationSupportedTypes(id: Int, types: IntArray?) {
    }

    private fun setComplication(complicationId: Int, complicationData: ComplicationData) {
        engineWrapper.onCommand(
            Constants.COMMAND_COMPLICATION_DATA,
            0,
            0,
            0,
            Bundle().apply {
                putInt(Constants.EXTRA_COMPLICATION_ID, complicationId)
                putParcelable(Constants.EXTRA_COMPLICATION_DATA, complicationData)
            },
            false
        )
    }

    override fun registerWatchFaceType(watchFaceType: Int) {
    }
}

@RunWith(AndroidJUnit4::class)
@MediumTest
class WatchFaceServiceImageTest {

    @Mock
    private lateinit var surfaceHolder: SurfaceHolder

    private lateinit var watchFaceServiceStub: WatchFaceServiceStub
    private val handler = Handler(Looper.getMainLooper())

    private val complicationProviders = mapOf(
        SystemProviders.DAY_OF_WEEK to
                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortTitle(ComplicationText.plainText("23rd"))
                    .setShortText(ComplicationText.plainText("Mon"))
                    .build(),
        SystemProviders.STEP_COUNT to
                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortTitle(ComplicationText.plainText("Steps"))
                    .setShortText(ComplicationText.plainText("100"))
                    .build()
    )

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule("wear/wear-watchface")

    private val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val renderDoneLatch = CountDownLatch(1)

    private val surfaceTexture = SurfaceTexture(false)

    private lateinit var canvasWatchFaceService: TestCanvasWatchFaceService
    private lateinit var gles2WatchFaceService: TestGles2WatchFaceService
    private lateinit var engineWrapper: WatchFaceService.EngineWrapper

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    private fun initCanvasWatchFace() {
        canvasWatchFaceService = TestCanvasWatchFaceService(
            ApplicationProvider.getApplicationContext<Context>(),
            handler,
            100000
        )
        engineWrapper = canvasWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper

        Mockito.`when`(surfaceHolder.surfaceFrame)
            .thenReturn(Rect(0, 0, BITMAP_WIDTH, BITMAP_HEIGHT))
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, BITMAP_WIDTH, BITMAP_HEIGHT)
        Mockito.`when`(surfaceHolder.lockHardwareCanvas()).thenReturn(canvas)
        Mockito.`when`(surfaceHolder.unlockCanvasAndPost(canvas)).then {
            renderDoneLatch.countDown()
        }

        setBinder()
    }

    private fun initGles2WatchFace() {
        gles2WatchFaceService = TestGles2WatchFaceService(
            ApplicationProvider.getApplicationContext<Context>(),
            handler,
            100000
        )
        engineWrapper = gles2WatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper

        surfaceTexture.setDefaultBufferSize(BITMAP_WIDTH, BITMAP_HEIGHT)

        Mockito.`when`(surfaceHolder.surfaceFrame)
            .thenReturn(Rect(0, 0, BITMAP_WIDTH, BITMAP_HEIGHT))
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, BITMAP_WIDTH, BITMAP_HEIGHT)

        Mockito.`when`(surfaceHolder.surface).thenReturn(Surface(surfaceTexture))
        setBinder()
    }

    private fun setBinder() {
        watchFaceServiceStub = WatchFaceServiceStub(
            API_VERSION,
            engineWrapper,
            complicationProviders
        )

        engineWrapper.onCommand(
            Constants.COMMAND_SET_BINDER,
            0,
            0,
            0,
            Bundle().apply {
                putBinder(
                    Constants.EXTRA_BINDER,
                    watchFaceServiceStub.asBinder()
                )
            },
            false
        )
    }

    private fun setAmbient(ambient: Boolean) {
        watchFaceServiceStub.iWatchFaceCommand!!.setSystemState(
            ambient,
            0,
            0,
            0
        )
    }

    @Test
    fun testActiveScreenshot() {
        handler.post {
            initCanvasWatchFace()
            engineWrapper.draw()
        }

        renderDoneLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap.assertAgainstGolden(screenshotRule, "active_screenshot")
    }

    @Test
    fun testAmbientScreenshot() {
        handler.post {
            initCanvasWatchFace()
            setAmbient(true)
            engineWrapper.draw()
        }

        renderDoneLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap.assertAgainstGolden(screenshotRule, "ambient_screenshot2")
    }

    @Test
    fun testCommandTakeScreenShot() {
        val latch = CountDownLatch(1)

        handler.post(this::initCanvasWatchFace)
        var bitmap: Bitmap? = null
        handler.post {
            bitmap = watchFaceServiceStub.iWatchFaceCommand!!.takeWatchfaceScreenshot(
                DrawMode.AMBIENT,
                100,
                123456789
            ).ashmemCompressedImageBundleToBitmap(

            )
            latch.countDown()
        }

        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap!!.assertAgainstGolden(
            screenshotRule,
            "ambient_screenshot"
        )
    }

    @Test
    fun testCommandTakeComplicationScreenShot() {
        val latch = CountDownLatch(1)

        handler.post(this::initCanvasWatchFace)
        var bitmap: Bitmap? = null
        handler.post {
            bitmap = watchFaceServiceStub.iWatchFaceCommand!!.takeComplicationScreenshot(
                EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID,
                DrawMode.AMBIENT,
                100,
                123456789,
                null
            ).ashmemCompressedImageBundleToBitmap(

            )
            latch.countDown()
        }

        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap!!.assertAgainstGolden(
            screenshotRule,
            "leftComplication"
        )
    }

    @Test
    fun testCommandTakeOpenGLScreenShot() {
        val latch = CountDownLatch(1)

        handler.post(this::initGles2WatchFace)
        var bitmap: Bitmap? = null
        handler.post {
            bitmap = watchFaceServiceStub.iWatchFaceCommand!!.takeWatchfaceScreenshot(
                DrawMode.INTERACTIVE,
                100,
                123456789
            ).ashmemCompressedImageBundleToBitmap()!!
            latch.countDown()
        }

        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap!!.assertAgainstGolden(
            screenshotRule,
            "ambient_gl_screenshot"
        )
    }

    @Test
    fun testCommandTakeComplicationScreenShotWithAndWithoutComplicationData() {
        val latch = CountDownLatch(1)

        handler.post(this::initCanvasWatchFace)
        var bitmap: Bitmap? = null
        handler.post {
            bitmap = watchFaceServiceStub.iWatchFaceCommand!!.takeComplicationScreenshot(
                EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID,
                DrawMode.INTERACTIVE,
                100,
                123456789,
                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortTitle(ComplicationText.plainText("Title"))
                    .setShortText(ComplicationText.plainText("Text"))
                    .build()
            ).ashmemCompressedImageBundleToBitmap(

            )
            latch.countDown()
        }

        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap!!.assertAgainstGolden(
            screenshotRule,
            "leftComplicationOverride"
        )

        // Rendering again with complicationData = null should result in a different image.
        val latch2 = CountDownLatch(1)

        handler.post(this::initCanvasWatchFace)
        var bitmap2: Bitmap? = null
        handler.post {
            bitmap2 = watchFaceServiceStub.iWatchFaceCommand!!.takeComplicationScreenshot(
                EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID,
                DrawMode.INTERACTIVE,
                100,
                123456789,
                null
            ).ashmemCompressedImageBundleToBitmap()
            latch2.countDown()
        }

        latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap2!!.assertAgainstGolden(
            screenshotRule,
            "leftComplication"
        )
    }

    @Test
    fun testSetGreenStyle() {
        handler.post {
            initCanvasWatchFace()
            engineWrapper.onCommand(
                Constants.COMMAND_SET_USER_STYLE,
                0,
                0,
                0,
                Bundle().apply {
                    putString("color_style_category", "green_style")
                },
                false
            )
            engineWrapper.draw()
        }

        renderDoneLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap.assertAgainstGolden(screenshotRule, "green_screenshot")
    }
}
