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

package androidx.wear.watchface.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.icu.util.Calendar
import android.os.Bundle
import android.support.wearable.complications.ComplicationData
import android.view.SurfaceHolder
import androidx.test.core.app.ApplicationProvider
import androidx.wear.complications.SystemProviders
import androidx.wear.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.BackgroundComplicationBounds
import androidx.wear.watchface.Complication
import androidx.wear.watchface.ComplicationDrawableRenderer
import androidx.wear.watchface.ComplicationSlots
import androidx.wear.watchface.FixedBounds
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.SystemState
import androidx.wear.watchface.WatchFaceTestRunner
import androidx.wear.watchface.createComplicationData
import androidx.wear.watchfacestyle.ListViewUserStyleCategory
import androidx.wear.watchfacestyle.UserStyleCategory
import androidx.wear.watchfacestyle.UserStyleManager
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

private const val LEFT_COMPLICATION_ID = 1000
private const val RIGHT_COMPLICATION_ID = 1001
private const val BACKGROUND_COMPLICATION_ID = 1111

@Config(manifest = Config.NONE)
@RunWith(WatchFaceTestRunner::class)
class WatchFaceConfigUiTest {

    companion object {
        val ONE_HUNDRED_BY_ONE_HUNDRED_RECT = Rect(0, 0, 100, 100)
    }

    private val watchFaceConfigDelegate = Mockito.mock(WatchFaceConfigDelegate::class.java)
    private val fragmentController = Mockito.mock(FragmentController::class.java)
    private val surfaceHolder = Mockito.mock(SurfaceHolder::class.java)
    private val systemState = SystemState()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val complicationDrawableLeft = ComplicationDrawable(context)
    private val complicationDrawableRight = ComplicationDrawable(context)

    private val redStyleOption =
        ListViewUserStyleCategory.ListViewOption("red_style", "Red", icon = null)

    private val greenStyleOption =
        ListViewUserStyleCategory.ListViewOption("green_style", "Green", icon = null)

    private val blueStyleOption =
        ListViewUserStyleCategory.ListViewOption("bluestyle", "Blue", icon = null)

    private val colorStyleList = listOf(redStyleOption, greenStyleOption, blueStyleOption)

    private val colorStyleCategory = ListViewUserStyleCategory(
        "color_style_category",
        "Colors",
        "Watchface colorization", /* icon = */
        null,
        colorStyleList
    )

    private val classicStyleOption =
        ListViewUserStyleCategory.ListViewOption("classic_style", "Classic", icon = null)

    private val modernStyleOption =
        ListViewUserStyleCategory.ListViewOption("modern_style", "Modern", icon = null)

    private val gothicStyleOption =
        ListViewUserStyleCategory.ListViewOption("gothic_style", "Gothic", icon = null)

    private val watchHandStyleList =
        listOf(classicStyleOption, modernStyleOption, gothicStyleOption)

    private val watchHandStyleCategory = ListViewUserStyleCategory(
        "hand_style_category",
        "Hand Style",
        "Hand visual look", /* icon = */
        null,
        watchHandStyleList
    )

    private val leftComplication =
        Complication(
            LEFT_COMPLICATION_ID,
            FixedBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f)),
            ComplicationDrawableRenderer(complicationDrawableLeft, systemState),
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_LONG_TEXT,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SMALL_IMAGE
            ),
            Complication.DefaultComplicationProvider(SystemProviders.SUNRISE_SUNSET),
            ComplicationData.TYPE_SHORT_TEXT
        ).apply { complicationData = createComplicationData() }

    private val rightComplication =
        Complication(
            RIGHT_COMPLICATION_ID,
            FixedBounds(RectF(0.6f, 0.4f, 0.8f, 0.6f)),
            ComplicationDrawableRenderer(complicationDrawableRight, systemState),
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_LONG_TEXT,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SMALL_IMAGE
            ),
            Complication.DefaultComplicationProvider(SystemProviders.DAY_OF_WEEK),
            ComplicationData.TYPE_SHORT_TEXT
        ).apply { complicationData = createComplicationData() }

    private val backgroundComplication =
        Complication(
            BACKGROUND_COMPLICATION_ID,
            BackgroundComplicationBounds(),
            ComplicationDrawableRenderer(complicationDrawableRight, systemState),
            intArrayOf(
                ComplicationData.TYPE_LARGE_IMAGE
            ),
            Complication.DefaultComplicationProvider(),
            ComplicationData.TYPE_LARGE_IMAGE
        ).apply { complicationData = createComplicationData() }

    private val sharedPreferences =
        context.getSharedPreferences("testPreferences", Context.MODE_PRIVATE).apply {
            edit().clear().commit()
        }

    private val calendar = Calendar.getInstance().apply {
        timeInMillis = 1000L
    }

    private val configActivity = WatchFaceConfigActivity()

    private lateinit var userStyleManager: UserStyleManager

    private fun initConfigActivity(
        complications: List<Complication>,
        userStyleCategories: List<UserStyleCategory>
    ) {
        Mockito.`when`(surfaceHolder.surfaceFrame)
            .thenReturn(ONE_HUNDRED_BY_ONE_HUNDRED_RECT)

        userStyleManager = UserStyleManager(userStyleCategories)

        val complicationSlots = ComplicationSlots(
            complications,
            object : Renderer(surfaceHolder, userStyleManager) {
                override fun onDrawInternal(calendar: Calendar) {}

                override fun takeScreenshot(calendar: Calendar, drawMode: Int): Bitmap {
                    throw RuntimeException("Not Implemented!")
                }
            })

        val watchFaceComponentName = ComponentName(
            context.packageName,
            context.javaClass.typeName
        )
        WatchFaceConfigActivity.registerWatchFace(
            watchFaceComponentName,
            object : WatchFaceConfigDelegate {
                override fun getUserStyleSchema() =
                    UserStyleCategory.userStyleCategoryListToBundles(userStyleCategories)

                override fun getUserStyle() =
                    UserStyleCategory.styleMapToBundle(userStyleManager.userStyle)

                override fun setUserStyle(style: Bundle) {
                    userStyleManager.userStyle =
                        UserStyleCategory.bundleToStyleMap(style, userStyleCategories)
                }

                override fun getBackgroundComplicationId() =
                    complicationSlots.getBackgroundComplication()?.id

                override fun getComplicationsMap() = complicationSlots.complications

                override fun getCalendar() = calendar

                override fun getComplicationIdAt(tapX: Int, tapY: Int, calendar: Calendar) =
                    complicationSlots.getComplicationAt(tapX, tapY, calendar)?.id

                override fun brieflyHighlightComplicationId(complicationId: Int) {
                    watchFaceConfigDelegate.brieflyHighlightComplicationId(complicationId)
                }

                override fun drawComplicationSelect(
                    canvas: Canvas,
                    drawRect: Rect,
                    calendar: Calendar
                ) {
                    watchFaceConfigDelegate.drawComplicationSelect(canvas, drawRect, calendar)
                }
            })

        configActivity.init(watchFaceComponentName, fragmentController)
    }

    @After
    fun validate() {
        Mockito.validateMockitoUsage()
    }

    @Test
    fun brieflyHighlightComplicationId_calledWhenComplicationSelected() {
        initConfigActivity(listOf(leftComplication, rightComplication), emptyList())
        val view = ConfigView(context, configActivity)

        // Tap left complication.
        view.onTap(30, 50, calendar)
        verify(watchFaceConfigDelegate).brieflyHighlightComplicationId(LEFT_COMPLICATION_ID)

        // Tap right complication.
        view.onTap(70, 50, calendar)
        verify(watchFaceConfigDelegate).brieflyHighlightComplicationId(RIGHT_COMPLICATION_ID)
    }

    @Test
    fun brieflyHighlightComplicationId_notCalledWhenBlankSpaceTapped() {
        initConfigActivity(listOf(leftComplication, rightComplication), emptyList())
        val view = ConfigView(context, configActivity)

        // Tap on blank space.
        view.onTap(1, 1, calendar)
        verify(watchFaceConfigDelegate, times(0)).brieflyHighlightComplicationId(anyInt())
    }

    @Test
    fun onInitWithOneComplicationCalls_showComplicationConfig() {
        initConfigActivity(listOf(leftComplication), emptyList())

        verify(fragmentController).showComplicationConfig(
            LEFT_COMPLICATION_ID,
            *leftComplication.supportedComplicationDataTypes
        )
    }

    @Test
    fun onInitWithOneBackgroundComplicationCalls_showComplicationConfig() {
        initConfigActivity(listOf(backgroundComplication), emptyList())

        verify(fragmentController).showComplicationConfig(
            BACKGROUND_COMPLICATION_ID,
            *backgroundComplication.supportedComplicationDataTypes
        )
    }

    @Test
    fun onInitWithTwoComplicationsCalls_showComplicationConfigSelectionFragment() {
        initConfigActivity(listOf(leftComplication, rightComplication), emptyList())
        verify(fragmentController).showComplicationConfigSelectionFragment()
    }

    @Test
    fun onInitWithOneNormalAndOneBackgroundComplicationsCalls_showConfigFragment() {
        initConfigActivity(
            listOf(leftComplication, backgroundComplication),
            emptyList()
        )
        verify(fragmentController).showConfigFragment()
    }

    @Test
    fun onInitWithStylesCalls_showConfigFragment() {
        initConfigActivity(listOf(leftComplication), listOf(colorStyleCategory))
        verify(fragmentController).showConfigFragment()
    }

    @Test
    fun onInitWithNoComplicationsAndTwoStylesCalls_showConfigFragment() {
        initConfigActivity(
            emptyList(),
            listOf(colorStyleCategory, watchHandStyleCategory)
        )
        verify(fragmentController).showConfigFragment()
    }

    @Test
    fun onInitWithNoComplicationsAndOneStyleCalls_showConfigFragment() {
        initConfigActivity(emptyList(), listOf(colorStyleCategory))

        // Note colorStyleCategory gets serialised and deserialised so we can't test equality with
        // colorStyleCategory.
        verify(fragmentController).showStyleConfigFragment(
            colorStyleCategory.id, configActivity.styleSchema.first().options
        )
    }

    @Test
    fun styleConfigFragment_onItemClick_modifiesTheStyleCorrectly() {
        initConfigActivity(
            listOf(leftComplication, backgroundComplication),
            listOf(colorStyleCategory, watchHandStyleCategory)
        )
        val catergoryIndex = 0
        var styleConfigFragment = StyleConfigFragment()
        styleConfigFragment.watchFaceConfigActivity = configActivity
        styleConfigFragment.categoryKey = configActivity.styleSchema[catergoryIndex].id

        Truth.assertThat(userStyleManager.userStyle[colorStyleCategory]!!.id).isEqualTo(
            redStyleOption.id
        )
        Truth.assertThat(userStyleManager.userStyle[watchHandStyleCategory]!!.id).isEqualTo(
            classicStyleOption.id
        )

        styleConfigFragment.onItemClick(configActivity.styleSchema[catergoryIndex].options[1])

        Truth.assertThat(userStyleManager.userStyle[colorStyleCategory]!!.id).isEqualTo(
            greenStyleOption.id
        )
        Truth.assertThat(userStyleManager.userStyle[watchHandStyleCategory]!!.id).isEqualTo(
            classicStyleOption.id
        )
    }
}
