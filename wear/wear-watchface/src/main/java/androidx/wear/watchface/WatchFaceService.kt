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

package androidx.wear.watchface

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.RemoteException
import android.os.Trace
import android.service.wallpaper.WallpaperService
import android.support.wearable.complications.ComplicationData
import android.support.wearable.watchface.Constants
import android.support.wearable.watchface.IWatchFaceCommand
import android.support.wearable.watchface.IWatchFaceService
import android.support.wearable.watchface.toAshmemCompressedImageBundle
import android.support.wearable.watchface.accessibility.ContentDescriptionLabel
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.IntDef
import androidx.wear.complications.SystemProviders.ProviderId
import androidx.wear.watchface.style.UserStyleCategory
import androidx.wear.watchface.style.UserStyleRepository
import java.util.concurrent.CountDownLatch

/**
 * After user code finishes, we need up to 100ms of wake lock holding for the drawing to occur. This
 * isn't the ideal approach, but the framework doesn't expose a callback that would tell us when our
 * Canvas was drawn. 100 ms should give us time for a few frames to be drawn, in case there is a
 * backlog. If we encounter issues with this approach, we should consider asking framework team to
 * expose a callback.
 */
internal const val SURFACE_DRAW_TIMEOUT_MS = 100L

/**
 * Used to parameterize watch face drawing based on the current system state.
 *
 * @hide
 */
@IntDef(
    value = [
        DrawMode.INTERACTIVE,
        DrawMode.LOW_BATTERY_INTERACTIVE,
        DrawMode.MUTE,
        DrawMode.AMBIENT,
        DrawMode.COMPLICATION_SELECT,
        DrawMode.BASE_WATCHFACE,
        DrawMode.UPPER_LAYER
    ]
)
annotation class DrawMode {
    companion object {
        /** This mode is used when the user is interacting with the watch face. */
        const val INTERACTIVE = 0

        /**
         * This mode is used when the user is interacting with the watch face but the battery is
         * low, the watch face should render fewer pixels, ideally with darker colors.
         */
        const val LOW_BATTERY_INTERACTIVE = 1

        /**
         * This mode is used when there's an interruption filter. The watch face should look muted.
         */
        const val MUTE = 2

        /**
         * In this mode as few pixels as possible should be turned on, ideally with darker colors.
         */
        const val AMBIENT = 3

        /**
         * This mode is used when selecting a complication to configure. Complications should stand
         * out visually from other parts of the watch face.
         */
        const val COMPLICATION_SELECT = 4

        /**
         * As {@link INTERACTIVE} but complications shouldn't be drawn, nor should any watch face
         * elements that might occlude complications (e.g. watch hands).  Used by the
         * remote configuration UI.
         */
        const val BASE_WATCHFACE = 5

        /**
         * Related to {@link BASE_WATCHFACE}, only watch face elements that might occlude
         * complications should be drawn (e.g. watch hands).  If nothing can occlude the
         * complications then nothing should be drawn. Used by the remote configuration UI. A screen
         * shot taken in this mode needs to include an alpha channel.
         */
        const val UPPER_LAYER = 6

        fun values(): Collection<Int> = arrayListOf(
            INTERACTIVE,
            LOW_BATTERY_INTERACTIVE,
            MUTE,
            AMBIENT,
            COMPLICATION_SELECT,
            BASE_WATCHFACE,
            UPPER_LAYER
        )
    }
}

/** @hide */
@IntDef(
    value = [
        TapType.TOUCH,
        TapType.TOUCH_CANCEL,
        TapType.TAP
    ]
)
annotation class TapType {
    companion object {
        /** Used in onTapCommand to indicate a "down" touch event on the watch face. */
        const val TOUCH = 0

        /**
         * Used in onTapCommand to indicate that a previous TapType.TOUCH touch event has been
         * canceled. This generally happens when the watch face is touched but then a move or long
         * press occurs.
         */
        const val TOUCH_CANCEL = 1

        /**
         * Used in onTapCommaned to indicate that an "up" event on the watch face has occurred that
         * has not been consumed by another activity. A TapType.TOUCH will always occur first.
         * This event will not occur if a TapType.TOUCH_CANCEL is sent.
         */
        const val TAP = 2
    }
}

/**
 * WatchFaceService and {@link WatchFace} are a pair of base classes intended to handle much of
 * the boilerplate needed to implement a watch face without being too opinionated. The suggested
 * structure of an WatchFaceService based watch face is:
 *
 * @sample androidx.wear.watchface.samples.kDocCreateExampleWatchFaceService
 *
 * Base classes for complications and styles are provided along with a default UI for configuring
 * them. Complications are optional, however if required, WatchFaceService assumes all
 * complications can be enumerated up front and passed as a collection into WatchFace's constructor.
 * Some watch faces support different configurations (number & position) of complications and this
 * can be achieved by rendering a subset and only marking the ones you need as active. Most watch
 * faces will not animate the location of complications so its recommended to use the
 * WatchFace.UnitSquareBoundsProvider helper.
 *
 * Many watch faces support styles, typically controlling the color and visual look of watch face
 * elements such as numeric fonts, watch hands and ticks. WatchFaceService doesn't take an
 * an opinion on what comprises a style beyond it should be representable as a map of categories to
 * options.
 *
 * It's recommended to avoid putting business logic in sub classes of WatchFaceService, rather
 * that should go in sub classes of WatchFace.
 *
 * To aid debugging watch face animations, WatchFaceService allows you to speed up or slow down
 * time, and to loop between two instants.  This is controlled by MOCK_TIME_INTENT intents
 * with a float extra called "androidx.wear.watchface.extra.MOCK_TIME_SPEED_MULTIPLIE" and to long
 * extras called "androidx.wear.watchface.extra.MOCK_TIME_WRAPPING_MIN_TIME" and
 * "androidx.wear.watchface.extra.MOCK_TIME_WRAPPING_MAX_TIME" (which are UTC time in milliseconds).
 * If minTime is omitted or set to -1 then the current time is sampled as minTime.
 *
 * E.g, to make time go twice as fast:
 *  adb shell am broadcast -a androidx.wear.watchface.MockTime \
 *            --ef androidx.wear.watchface.extra.MOCK_TIME_SPEED_MULTIPLIER 2.0
 *
 *
 * To use the default watch face configuration UI add the following into your watch face's
 * AndroidManifest.xml:
 *
 * ```
 * <activity
 *   android:name="androidx.wear.watchface.ui.WatchFaceConfigActivity"
 *   android:exported="true"
 *   android:label="Config"
 *   android:theme="@android:style/Theme.Translucent.NoTitleBar">
 *   <intent-filter>
 *     <action android:name="com.google.android.clockwork.watchfaces.complication.CONFIG_DIGITAL" />
 *       <category android:name=
 *            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" />
 *       <category android:name="android.intent.category.DEFAULT" />
 *    </intent-filter>
 * </activity>
 * ```
 *
 * To register a WatchFaceService with the system add a <service> tag to the <application> in your
 * watch face's AndroidManifest.xml:
 *
 * ```
 *  <service
 *    android:name=".MyWatchFaceServiceClass"
 *    android:exported="true"
 *    android:label="@string/watch_face_name"
 *    android:permission="android.permission.BIND_WALLPAPER">
 *    <intent-filter>
 *      <action android:name="android.service.wallpaper.WallpaperService" />
 *      <category android:name="com.google.android.wearable.watchface.category.WATCH_FACE" />
 *    </intent-filter>
 *    <meta-data
 *       android:name="com.google.android.wearable.watchface.preview"
 *       android:resource="@drawable/my_watch_preview" />
 *    <meta-data
 *      android:name="com.google.android.wearable.watchface.preview_circular"
 *      android:resource="@drawable/my_watch_circular_preview" />
 *    <meta-data
 *      android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
 *      android:value="com.google.android.clockwork.watchfaces.complication.CONFIG_DIGITAL"/>
 *    <meta-data
 *      android:name="android.service.wallpaper"
 *      android:resource="@xml/watch_face" />
 *  </service>
 * ```
 *
 * Multiple watch faces can be defined in the same package, requiring multiple <service> tags.
 */
@TargetApi(26)
abstract class WatchFaceService : WallpaperService() {

    private companion object {
        private const val TAG = "WatchFaceService"

        /** Whether to log every frame. */
        private const val LOG_VERBOSE = false

        /** Whether to enable tracing for each call to {@link Engine#onDraw}. */
        private const val TRACE_DRAW = false
    }

    /** Override this factory method to create your WatchFace. */
    protected abstract fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchFaceHost: WatchFaceHost,
        watchState: WatchState
    ): WatchFace

    final override fun onCreateEngine() = EngineWrapper(getHandler()) as Engine

    // This is open to allow mocking.
    internal open fun getHandler() = Handler(Looper.getMainLooper())

    // This is open to allow mocking.
    internal open fun getSystemState() = WatchState()

    internal inner class EngineWrapper(
        private val _handler: Handler
    ) : WallpaperService.Engine(), WatchFaceHostApi {
        private val _context = this@WatchFaceService as Context

        private lateinit var currentSurfaceHolder: SurfaceHolder
        private var currentSurfaceFormat = 0
        private var currentSurfaceWidth = 0
        private var currentSurfaceHeight = 0

        internal lateinit var iWatchFaceService: IWatchFaceService
        internal lateinit var watchFace: WatchFace

        internal val systemState = getSystemState().apply {
            onVisibilityChanged(isVisible)
        }

        private var timeTickRegistered = false
        private val timeTickReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            @SuppressWarnings("SyntheticAccessor")
            override fun onReceive(context: Context, intent: Intent) {
                watchFace.invalidate()
            }
        }

        private var destroyed = false

        internal lateinit var ambientUpdateWakelock: PowerManager.WakeLock

        private val choreographer = Choreographer.getInstance()

        /**
         * Whether we already have a {@link #frameCallback} posted and waiting in the {@link
         * Choreographer} queue. This protects us from drawing multiple times in a single frame.
         */
        private var frameCallbackPending = false

        private val frameCallback = object : Choreographer.FrameCallback {
            @SuppressWarnings("SyntheticAccessor")
            override fun doFrame(frameTimeNs: Long) {
                if (destroyed) {
                    return
                }
                frameCallbackPending = false
                draw()
            }
        }

        private val invalidateRunnable = Runnable(this::invalidate)

        private val ambientTimeTickFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        private val interactiveTimeTickFilter = IntentFilter(ambientTimeTickFilter).apply {
            addAction(Intent.ACTION_TIME_TICK)
        }

        /**
         * Runs the supplied task on the UI thread.  If we're not on the UI thread a task is posted
         * and we block until it's been processed.
         *
         * AIDL calls are dispatched from a thread pool, but for simplicity WatchFace code is
         * largely single threaded so we need to post tasks to the UI thread and wait for them to
         * execute.
         */
        internal fun <R> runOnUiThread(task: () -> R) =
            if (_handler.looper == Looper.myLooper()) {
                task.invoke()
            } else {
                val latch = CountDownLatch(1)
                var returnVal: R? = null
                var exception: Exception? = null
                if (_handler.post {
                    try {
                        returnVal = task.invoke()
                    } catch (e: Exception) {
                        // Will rethrow on the calling thread.
                        exception = e
                    }
                    latch.countDown()
                }) {
                    latch.await()
                    if (exception != null) {
                        throw exception as Exception
                    }
                }
                returnVal!!
            }

        private val watchFaceCommand = object : IWatchFaceCommand.Stub() {
            override fun getApiVersion() = IWatchFaceCommand.WATCHFACE_COMMAND_API_VERSION

            override fun ambientUpdate() {
                runOnUiThread {
                    if (systemState.isAmbient) {
                        ambientUpdateWakelock.acquire()
                        watchFace.invalidate()
                        ambientUpdateWakelock.acquire(SURFACE_DRAW_TIMEOUT_MS)
                    }
                }
            }

            override fun setSystemState(
                inAmbientMode: Boolean,
                interruptionFilter: Int,
                unreadCount: Int,
                notificationCount: Int
            ) {
                runOnUiThread {
                    if (firstSetSystemState || inAmbientMode != systemState.isAmbient) {
                        systemState.onAmbientModeChanged(inAmbientMode)
                        updateTimeTickReceiver()
                    }

                    if (firstSetSystemState || interruptionFilter !=
                        systemState.interruptionFilter
                    ) {
                        systemState.onInterruptionFilterChanged(interruptionFilter)
                    }

                    if (firstSetSystemState || unreadCount != systemState.unreadNotificationCount) {
                        systemState.onUnreadNotificationCountChanged(unreadCount)
                    }

                    if (firstSetSystemState || notificationCount != systemState.notificationCount) {
                        systemState.onNotificationCountChanged(notificationCount)
                    }

                    firstSetSystemState = false
                }
            }

            override fun setIndicatorState(
                isCharging: Boolean,
                inAirplaneMode: Boolean,
                isConnectedToCompanion: Boolean,
                inTheaterMode: Boolean,
                isGpsActive: Boolean,
                isKeyguardLocked: Boolean
            ) {
                runOnUiThread {
                    if (firstIndicatorState || isCharging != systemState.isCharging) {
                        systemState.onIsChargingChanged(isCharging)
                    }

                    if (firstIndicatorState || inAirplaneMode != systemState.inAirplaneMode) {
                        systemState.onInAirplaneModeChanged(inAirplaneMode)
                    }

                    if (firstIndicatorState || isConnectedToCompanion !=
                        systemState.isConnectedToCompanion
                    ) {
                        systemState.onIsConnectedToCompanionChanged(isConnectedToCompanion)
                    }

                    if (firstIndicatorState || inTheaterMode != systemState.isInTheaterMode) {
                        systemState.onInTheaterModeChanged(inTheaterMode)
                    }

                    if (firstIndicatorState || isGpsActive != systemState.isGpsActive) {
                        systemState.onIsGpsActiveChanged(isGpsActive)
                    }

                    if (firstIndicatorState || isKeyguardLocked != systemState.isKeyguardLocked) {
                        systemState.onIsKeyguardLockedChanged(isKeyguardLocked)
                    }

                    firstIndicatorState = false
                }
            }

            override fun setUserStyle(userStyle: Bundle) {
                runOnUiThread {
                    watchFace.onSetStyleInternal(
                        UserStyleRepository.bundleToStyleMap(
                            userStyle,
                            watchFace.userStyleRepository.userStyleCategories
                        )
                    )
                }
            }

            override fun setImmutableSystemState(
                hasLowBitAmbient: Boolean,
                hasBurnInProtection: Boolean
            ) {
                runOnUiThread {
                    // These properties never change so set them once only.
                    if (!immutableSystemStateDone) {
                        systemState.setHasLowBitAmbient(hasLowBitAmbient)
                        systemState.setHasBurnInProtection(hasBurnInProtection)

                        immutableSystemStateDone = true
                    }
                }
            }

            override fun setComplicationData(complicationId: Int, data: ComplicationData) {
                runOnUiThread {
                    watchFace.onComplicationDataUpdate(complicationId, data)
                }
            }

            override fun requestWatchFaceStyle() {
                runOnUiThread {
                    try {
                        iWatchFaceService.setStyle(watchFace.watchFaceStyle)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Failed to set WatchFaceStyle: ", e)
                    }

                    val activeComplications = lastActiveComplications
                    if (activeComplications != null) {
                        setActiveComplications(activeComplications)
                    }

                    val a11yLabels = lastA11yLabels
                    if (a11yLabels != null) {
                        setContentDescriptionLabels(a11yLabels)
                    }
                }
            }

            override fun takeWatchfaceScreenshot(
                drawMode: Int,
                compressionQuality: Int,
                calendarTimeMillis: Long
            ): Bundle {
                return runOnUiThread {
                    watchFace.renderer.takeScreenshot(
                        Calendar.getInstance().apply {
                            timeInMillis = calendarTimeMillis
                        },
                        drawMode
                    )
                }.toAshmemCompressedImageBundle(
                    compressionQuality
                )
            }

            override fun takeComplicationScreenshot(
                complicationId: Int,
                drawMode: Int,
                compressionQuality: Int,
                calendarTimeMillis: Long,
                complicationData: ComplicationData?
            ): Bundle? {
                return runOnUiThread {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = calendarTimeMillis
                    }
                    val complication = watchFace.complicationsHolder[complicationId]
                    if (complication != null) {
                        val bounds = complication.computeBounds(watchFace.renderer.screenBounds)
                        val complicationBitmap =
                            Bitmap.createBitmap(
                                bounds.width(), bounds.height(),
                                Bitmap.Config.ARGB_8888
                            )

                        var prevComplicationData: ComplicationData? = null
                        if (complicationData != null) {
                            prevComplicationData = complication.renderer.getData()
                            complication.renderer.setData(complicationData)
                        }

                        complication.renderer.onDraw(
                            Canvas(complicationBitmap),
                            Rect(0, 0, bounds.width(), bounds.height()),
                            calendar,
                            drawMode
                        )

                        // Restore previous ComplicationData if required.
                        if (complicationData != null) {
                            complication.renderer.setData(prevComplicationData)
                        }

                        complicationBitmap.toAshmemCompressedImageBundle(
                            compressionQuality
                        )
                    } else {
                        null
                    }
                }
            }

            override fun sendTouchEvent(xPos: Int, yPos: Int, tapType: Int) {
                runOnUiThread {
                    if (watchFaceCreated()) {
                        watchFace.onTapCommand(tapType, xPos, yPos)
                    }
                }
            }
        }

        // Only valid after onSetBinder has been called.
        private var systemApiVersion = -1

        internal var firstSetSystemState = true
        internal var firstIndicatorState = true
        internal var immutableSystemStateDone = false

        internal var lastActiveComplications: IntArray? = null
        internal var lastA11yLabels: Array<ContentDescriptionLabel>? = null

        private var pendingBackgroundAction: Bundle? = null
        private var pendingProperties: Bundle? = null
        private var pendingSetWatchFaceStyle = false
        private var pendingVisibilityChanged: Boolean? = null
        private var pendingComplicationDataUpdates = ArrayList<Bundle>()
        private var complicationsActivated = false

        override fun getContext() = _context

        override fun getHandler() = _handler

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            ambientUpdateWakelock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:[AmbientUpdate]")
            // Disable reference counting for our wake lock so that we can use the same wake lock
            // for user code in invaliate() and after that for having canvas drawn.
            ambientUpdateWakelock.setReferenceCounted(false)
        }

        override fun onDestroy() {
            destroyed = true
            _handler.removeCallbacks(invalidateRunnable)
            choreographer.removeFrameCallback(frameCallback)

            if (timeTickRegistered) {
                timeTickRegistered = false
                unregisterReceiver(timeTickReceiver)
            }

            if (!watchFaceCreated()) {
                watchFace.onDestroy()
            }
            super.onDestroy()
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            when (action) {
                Constants.COMMAND_AMBIENT_UPDATE -> watchFaceCommand.ambientUpdate()
                Constants.COMMAND_BACKGROUND_ACTION -> onBackgroundAction(extras!!)
                Constants.COMMAND_COMPLICATION_DATA -> onComplicationDataUpdate(extras!!)
                Constants.COMMAND_REQUEST_STYLE -> onRequestStyle()
                Constants.COMMAND_SET_BINDER -> onSetBinder(extras!!)
                Constants.COMMAND_SET_PROPERTIES -> onPropertiesChanged(extras!!)
                Constants.COMMAND_SET_USER_STYLE -> watchFaceCommand.setUserStyle(extras!!)
                Constants.COMMAND_TAP -> watchFaceCommand.sendTouchEvent(x, y, TapType.TAP)
                Constants.COMMAND_TOUCH -> watchFaceCommand.sendTouchEvent(x, y, TapType.TOUCH)
                Constants.COMMAND_TOUCH_CANCEL -> watchFaceCommand.sendTouchEvent(
                    x,
                    y,
                    TapType.TOUCH_CANCEL
                )
                else -> {
                }
            }
            return null
        }

        fun onBackgroundAction(extras: Bundle) {
            // We can't guarantee the binder has been set and onSurfaceChanged called before this
            // command.
            if (!watchFaceCreated()) {
                pendingBackgroundAction = extras
                return
            }

            watchFaceCommand.setSystemState(
                extras.getBoolean(Constants.EXTRA_AMBIENT_MODE, systemState.isAmbient),
                extras.getInt(Constants.EXTRA_INTERRUPTION_FILTER, systemState.interruptionFilter),
                extras.getInt(Constants.EXTRA_UNREAD_COUNT, systemState.unreadNotificationCount),
                extras.getInt(Constants.EXTRA_NOTIFICATION_COUNT, systemState.notificationCount)
            )

            val statusBundle = extras.getBundle(Constants.EXTRA_INDICATOR_STATUS)
            if (statusBundle != null) {
                watchFaceCommand.setIndicatorState(
                    statusBundle.getBoolean(Constants.STATUS_CHARGING),
                    statusBundle.getBoolean(Constants.STATUS_AIRPLANE_MODE),
                    statusBundle.getBoolean(Constants.STATUS_CONNECTED),
                    statusBundle.getBoolean(Constants.STATUS_THEATER_MODE),
                    statusBundle.getBoolean(Constants.STATUS_GPS_ACTIVE),
                    statusBundle.getBoolean(Constants.STATUS_KEYGUARD_LOCKED)
                )
            }

            pendingBackgroundAction = null
        }

        private fun onSetBinder(extras: Bundle) {
            val binder = extras.getBinder(Constants.EXTRA_BINDER)
            if (binder == null) {
                Log.w(TAG, "Binder is null.")
                return
            }

            iWatchFaceService = IWatchFaceService.Stub.asInterface(binder)

            try {
                // Note if the implementation doesn't support getVersion this will return zero
                // rather than throwing an exception.
                systemApiVersion = iWatchFaceService.apiVersion
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to getVersion: ", e)
            }

            if (systemApiVersion >= 3) {
                val bundle = Bundle().apply {
                    putBinder(
                        Constants.EXTRA_WATCH_FACE_COMMAND_BINDER,
                        watchFaceCommand.asBinder()
                    )
                }
                iWatchFaceService.registerIWatchFaceCommand(bundle)
            }

            maybeCreateWatchFace()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            currentSurfaceHolder = holder
            currentSurfaceFormat = format
            currentSurfaceWidth = width
            currentSurfaceHeight = height

            if (watchFaceCreated()) {
                watchFace.onSurfaceChanged(holder, format, width, height)
            } else {
                maybeCreateWatchFace()
            }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            if (watchFaceCreated()) {
                watchFace.onSurfaceRedrawNeeded()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            if (watchFaceCreated()) {
                watchFace.renderer.onSurfaceDestroyed(holder)
            }
        }

        private fun maybeCreateWatchFace() {
            // To simplify handling of watch face state, we only construct the {@link WatchFace}
            // once both currentSurfaceHolder and iWatchFaceService have been initialized.
            if (this::currentSurfaceHolder.isInitialized &&
                this::iWatchFaceService.isInitialized && !watchFaceCreated()
            ) {
                val host = WatchFaceHost()
                host.api = this
                watchFace = createWatchFace(
                    currentSurfaceHolder,
                    host,
                    systemState
                )

                // Watchfaces especially OpenGL ones often do initialization in
                // onSurfaceChanged, make sure we send the initial one.
                watchFace.renderer.onSurfaceChanged(
                    currentSurfaceHolder,
                    currentSurfaceFormat,
                    currentSurfaceWidth,
                    currentSurfaceHeight
                )

                val backgroundAction = pendingBackgroundAction
                if (backgroundAction != null) {
                    onBackgroundAction(backgroundAction)
                    pendingBackgroundAction = null
                }
                if (pendingSetWatchFaceStyle) {
                    onRequestStyle()
                }
                val visibility = pendingVisibilityChanged
                if (visibility != null) {
                    onVisibilityChanged(visibility)
                    pendingVisibilityChanged = null
                }
                val properties = pendingProperties
                if (properties != null) {
                    onPropertiesChanged(properties)
                    pendingProperties = null
                }
                for (complicationDataUpdate in pendingComplicationDataUpdates) {
                    onComplicationDataUpdate(complicationDataUpdate)
                }
            }
        }

        private fun onRequestStyle() {
            // We can't guarantee the binder has been set and onSurfaceChanged called before this
            // command.
            if (!watchFaceCreated()) {
                pendingSetWatchFaceStyle = true
                return
            }
            watchFaceCommand.requestWatchFaceStyle()
            pendingSetWatchFaceStyle = false
        }

        /**
         * Registers {@link #timeTickReceiver} if it should be registered and isn't currently, or
         * unregisters it if it shouldn't be registered but currently is. It also applies the right
         * intent filter depending on whether we are in ambient mode or not.
         */
        internal fun updateTimeTickReceiver() {
            if (timeTickRegistered) {
                unregisterReceiver(timeTickReceiver)
                timeTickRegistered = false
            }

            // We only register if we are visible, otherwise it doesn't make sense to waste cycles.
            if (systemState.isVisible) {
                if (systemState.isAmbient) {
                    registerReceiver(timeTickReceiver, ambientTimeTickFilter)
                } else {
                    registerReceiver(timeTickReceiver, interactiveTimeTickFilter)
                }
                timeTickRegistered = true

                // In case we missed a tick while transitioning from ambient to interactive, we
                // want to make sure the watch face doesn't show stale time when in interactive
                // mode.
                watchFace.invalidate()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            // We are requesting state every time the watch face changes its visibility because
            // wallpaper commands have a tendency to be dropped. By requesting it on every
            // visibility change, we ensure that we don't become a victim of some race condition.
            sendBroadcast(
                Intent(Constants.ACTION_REQUEST_STATE).apply {
                    putExtra(Constants.EXTRA_WATCH_FACE_VISIBLE, visible)
                }
            )

            // We can't guarantee the binder has been set and onSurfaceChanged called before this
            // command.
            if (!watchFaceCreated()) {
                pendingVisibilityChanged = visible
                return
            }

            systemState.onVisibilityChanged(visible)
            updateTimeTickReceiver()
            pendingVisibilityChanged = null
        }

        override fun invalidate() {
            if (!frameCallbackPending) {
                if (LOG_VERBOSE) {
                    Log.v(TAG, "invalidate: requesting draw")
                }
                frameCallbackPending = true
                choreographer.postFrameCallback(frameCallback)
            } else {
                if (LOG_VERBOSE) {
                    Log.v(TAG, "invalidate: draw already requested")
                }
            }
        }

        internal fun draw() {
            try {
                if (TRACE_DRAW) {
                    Trace.beginSection("onDraw")
                }
                if (LOG_VERBOSE) {
                    Log.v(WatchFaceService.TAG, "drawing frame")
                }
                watchFace.onDraw()
            } finally {
                if (TRACE_DRAW) {
                    Trace.endSection()
                }
            }
        }

        private fun onComplicationDataUpdate(extras: Bundle) {
            if (!watchFaceCreated()) {
                pendingComplicationDataUpdates.add(extras)
                return
            }
            extras.classLoader = ComplicationData::class.java.classLoader
            watchFaceCommand.setComplicationData(
                extras.getInt(Constants.EXTRA_COMPLICATION_ID),
                (extras.getParcelable(Constants.EXTRA_COMPLICATION_DATA) as ComplicationData?)!!
            )
        }

        internal fun onPropertiesChanged(properties: Bundle) {
            if (!watchFaceCreated()) {
                pendingProperties = properties
                return
            }

            watchFaceCommand.setImmutableSystemState(
                properties.getBoolean(Constants.PROPERTY_LOW_BIT_AMBIENT),
                properties.getBoolean(Constants.PROPERTY_BURN_IN_PROTECTION)
            )
        }

        internal fun watchFaceCreated() = this::watchFace.isInitialized

        override fun setDefaultComplicationProviderWithFallbacks(
            watchFaceComplicationId: Int,
            providers: List<ComponentName>?,
            @ProviderId fallbackSystemProvider: Int,
            type: Int
        ) {
            if (systemApiVersion >= 2) {
                iWatchFaceService.setDefaultComplicationProviderWithFallbacks(
                    watchFaceComplicationId,
                    providers,
                    fallbackSystemProvider,
                    type
                )
            } else {
                // If the implementation doesn't support the new API we emulate its behavior by
                // setting complication providers in the reverse order. This works because if
                // setDefaultComplicationProvider attempts to set a non-existent or incompatible
                // provider it does nothing, which allows us to emulate the same semantics as
                // setDefaultComplicationProviderWithFallbacks albeit with more calls.
                if (fallbackSystemProvider != WatchFace.NO_DEFAULT_PROVIDER) {
                    iWatchFaceService.setDefaultSystemComplicationProvider(
                        watchFaceComplicationId, fallbackSystemProvider, type
                    )
                }

                if (providers != null) {
                    // Iterate in reverse order. This could be O(n^2) but n is expected to be small
                    // and the list is probably an ArrayList so it's probably O(n) in practice.
                    for (i in providers.size - 1 downTo 0) {
                        iWatchFaceService.setDefaultComplicationProvider(
                            watchFaceComplicationId, providers[i], type
                        )
                    }
                }
            }
        }

        override fun setActiveComplications(watchFaceComplicationIds: IntArray) {
            lastActiveComplications = watchFaceComplicationIds
            try {
                iWatchFaceService.setActiveComplications(
                    watchFaceComplicationIds, /* updateAll= */ !complicationsActivated
                )
                complicationsActivated = true
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to set active complications: ", e)
            }
        }

        override fun setContentDescriptionLabels(labels: Array<ContentDescriptionLabel>) {
            lastA11yLabels = labels
            try {
                iWatchFaceService.setContentDescriptionLabels(labels)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to set accessibility labels: ", e)
            }
        }

        override fun registerWatchFaceType(@WatchFaceType watchFaceType: Int) {
            if (systemApiVersion >= 3) {
                iWatchFaceService.registerWatchFaceType(watchFaceType)
            }
        }

        override fun registerUserStyleSchema(styleSchema: List<UserStyleCategory>) {
            if (systemApiVersion >= 3) {
                iWatchFaceService.registerUserStyleSchema(
                    UserStyleRepository.userStyleCategoriesToBundles(styleSchema)
                )
            }
        }

        override fun setCurrentUserStyle(
            userStyle: Map<UserStyleCategory, UserStyleCategory.Option>
        ) {
            if (systemApiVersion >= 3) {
                iWatchFaceService.setCurrentUserStyle(
                    UserStyleRepository.styleMapToBundle(userStyle)
                )
            }
        }

        override fun getStoredUserStyle(
            schema: List<UserStyleCategory>
        ): Map<UserStyleCategory, UserStyleCategory.Option>? {
            if (systemApiVersion < 3) {
                return null
            }
            return UserStyleRepository.bundleToStyleMap(
                iWatchFaceService.storedUserStyle ?: Bundle(),
                schema
            )
        }

        override fun setComplicationDetails(
            complicationId: Int,
            bounds: Rect,
            @ComplicationBoundsType type: Int
        ) {
            if (systemApiVersion >= 3) {
                iWatchFaceService.setComplicationDetails(complicationId, bounds, type)
            }
        }

        override fun setComplicationSupportedTypes(complicationId: Int, types: IntArray) {
            if (systemApiVersion >= 3) {
                iWatchFaceService.setComplicationSupportedTypes(complicationId, types)
            }
        }
    }
}
