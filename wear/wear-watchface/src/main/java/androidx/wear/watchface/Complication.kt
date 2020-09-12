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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.icu.util.Calendar
import android.support.wearable.complications.ComplicationData
import androidx.annotation.UiThread
import androidx.wear.complications.SystemProviders
import androidx.wear.complications.rendering.ComplicationDrawable

/** Common interface for rendering complications. */
interface ComplicationRenderer {
    /**
     * Called when the ComplicationRenderer attaches to a {@link Complication}.
     */
    @UiThread
    fun onAttach(complication: Complication)

    /**
     * Called when the ComplicationRenderer detaches from a {@link Complication}.
     */
    @UiThread
    fun onDetach()

    /**
     * Draws the complication into the canvas with the specified bounds. This will usually be
     * called by user watch face drawing code, but the system may also call it for complication
     * selection UI rendering. The width and height will be the same as that computed by
     * computeBounds but the translation and canvas size may differ.
     *
     * @param canvas The {@link Canvas} to render into
     * @param bounds A {@link Rect} describing the bounds of the complication
     * @param calendar The current {@link Calendar}
     * @param drawMode The current {@link DrawMode}
     */
    @UiThread
    fun onDraw(
        canvas: Canvas,
        bounds: Rect,
        calendar: Calendar,
        @DrawMode drawMode: Int
    )

    /**
     * Sets whether the complication should be drawn highlighted. This is to provide visual
     * feedback when the user taps on a complication.
     *
     * @param highlight Whether or not the complication should be drawn highlighted.
     */
    @UiThread
    fun setIsHighlighted(highlight: Boolean)

    /**
     * Sets the current {@link ComplicationData}.
     *
     * @param data The {@link ComplicationData}
     */
    @UiThread
    fun setData(data: ComplicationData?)

    /**
     * Returns the current {@link ComplicationData} associated with the ComplicationRenderer.
     */
    @UiThread
    fun getData(): ComplicationData?

    interface InvalidateCallback {
        /** Requests redraw. */
        @UiThread
        fun onInvalidate()
    }

    /**
     * Called by the {@link WatchFace}
     *
     * @param callback The {@link InvalidateCallback} to register
     */
    @UiThread
    @SuppressLint("ExecutorRegistration")
    fun setInvalidateCallback(callback: InvalidateCallback)
}

/**
 * A complication rendered with ComplicationDrawable which does a lot of hard work for you. This
 * renderer can't be shared by multiple complications.
 */
open class ComplicationDrawableRenderer(
    /** The actual complication. */
    drawable: ComplicationDrawable,

    private val watchState: WatchState
) : ComplicationRenderer {
    private var _drawable = drawable

    var drawable: ComplicationDrawable
        get() = _drawable
        set(value) {
            _drawable = value
            _drawable.inAmbientMode = watchState.isAmbient
            _drawable.lowBitAmbient = watchState.hasLowBitAmbient
            _drawable.setBurnInProtection(watchState.hasBurnInProtection)

            attachedComplication?.scheduleUpdateActiveComplications()
        }

    private inner class SystemStateListener : WatchState.Listener {
        override fun onAmbientModeChanged(isAmbient: Boolean) {
            drawable.inAmbientMode = isAmbient
        }
    }

    private val systemStateListener = SystemStateListener()
    private var attachedComplication: Complication? = null
    private var complicationData: ComplicationData? = null

    /** {@inheritDoc} */
    override fun onAttach(complication: Complication) {
        attachedComplication = complication
        drawable.inAmbientMode = watchState.isAmbient
        drawable.lowBitAmbient = watchState.hasLowBitAmbient
        drawable.setBurnInProtection(watchState.hasBurnInProtection)

        watchState.addListener(systemStateListener)
    }

    /** {@inheritDoc} */
    override fun onDetach() {
        watchState.removeListener(systemStateListener)
        attachedComplication = null
    }

    /** {@inheritDoc} */
    override fun onDraw(
        canvas: Canvas,
        bounds: Rect,
        calendar: Calendar,
        @DrawMode drawMode: Int
    ) {
        drawable.bounds = bounds
        drawable.currentTimeMillis = calendar.timeInMillis
        drawable.draw(canvas)
    }

    /** {@inheritDoc} */
    override fun setIsHighlighted(highlight: Boolean) {
        drawable.highlighted = highlight
    }

    /** {@inheritDoc} */
    override fun setData(data: ComplicationData?) {
        drawable.complicationData = data
        complicationData = data
    }

    /** {@inheritDoc} */
    override fun getData() = complicationData

    /** {@inheritDoc} */
    @SuppressLint("ExecutorRegistration")
    override fun setInvalidateCallback(callback: ComplicationRenderer.InvalidateCallback) {
        drawable.callback = object :
            Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}

            override fun invalidateDrawable(who: Drawable) {
                callback.onInvalidate()
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        }
    }
}

/**
 * Represents a individual complication on the screen. The number of complications is fixed
 * (see {@link ComplicationsHolder}) but complications can be enabled or disabled as needed.
 */
class Complication internal constructor(
    internal val id: Int,
    @ComplicationBoundsType internal val boundsType: Int,
    val unitSquareBounds: RectF,
    renderer: ComplicationRenderer,
    internal val supportedTypes: IntArray,
    internal val defaultProvider: DefaultComplicationProvider,
    internal val defaultProviderType: Int
) {
    private companion object {
        internal val unitSquare = RectF(0f, 0f, 1f, 1f)
    }

    class Builder(
        /** The watch face's ID for this complication. */
        private val id: Int,

        /**
         * The renderer for this Complication. Renderers may not be sharable between complications.
         */
        private val renderer: ComplicationRenderer,

        /**
         * The types of complication supported by this Complication. Passed into {@link
         * ComplicationHelperActivity#createProviderChooserHelperIntent} during complication
         * configuration.
         */
        private val supportedTypes: IntArray,

        /** Default complication provider. */
        private val defaultProvider: DefaultComplicationProvider
    ) {
        @ComplicationBoundsType
        private var boundsType: Int = ComplicationBoundsType.ROUND_RECT
        private lateinit var unitSquareBounds: RectF

        private var defaultProviderType: Int = WatchFace.DEFAULT_PROVIDER_TYPE_NONE

        /** Sets the default complication provider data type. */
        fun setDefaultProviderType(defaultProviderType: Int): Builder {
            this.defaultProviderType = defaultProviderType
            return this
        }

        /**
         * Fractional bounds for the complication, clamped to the unit square [0..1], which get
         * converted to screen space coordinates. NB 0 and 1 are included in the unit square.
         */
        fun setUnitSquareBounds(unitSquareBounds: RectF): Builder {
            boundsType = ComplicationBoundsType.ROUND_RECT

            this.unitSquareBounds = RectF().apply {
                setIntersect(
                    unitSquareBounds,
                    unitSquare
                )
            }
            return this
        }

        /**
         * A background complication is for watch faces that wish to have a full screen user
         * selectable backdrop. This sort of complication isn't clickable and at most one may be
         * present in the list of complications.
         */
        fun setBackgroundComplication(): Builder {
            boundsType = ComplicationBoundsType.BACKGROUND
            this.unitSquareBounds = RectF(0f, 0f, 1f, 1f)
            return this
        }

        fun build() = Complication(
            id,
            boundsType,
            unitSquareBounds,
            renderer,
            supportedTypes,
            defaultProvider,
            defaultProviderType
        )
    }

    init {
        renderer.onAttach(this)
    }

    /**
     * A watch face may wish to try and set one or more non-system providers as the default provider
     * for a complication. If a provider can't be used for some reason (e.g. it isn't installed or
     * it doesn't support the requested type, or the watch face lacks the necessary permission)
     * then the next one will be tried. A system provider acts as a final fallback in case no
     * non-system providers can be used.
     *
     * If the DefaultComplicationProvider is empty then no default is set.
     */
    class DefaultComplicationProvider(
        /** List of up to two non-system providers to be tried in turn. This may be empty. */
        val providers: List<ComponentName> = listOf(),

        /** Fallback in case none of the non-system providers could be used. */
        @SystemProviders.ProviderId val systemProviderFallback: Int = WatchFace.NO_DEFAULT_PROVIDER
    ) {
        constructor(systemProviderFallback: Int) : this(listOf(), systemProviderFallback)

        fun isEmpty() =
            providers.isEmpty() && systemProviderFallback == WatchFace.NO_DEFAULT_PROVIDER
    }

    private lateinit var complicationsHolder: ComplicationsHolder
    private lateinit var invalidateCallback: ComplicationRenderer.InvalidateCallback
    private var _enabled = true

    var enabled: Boolean
        @JvmName("isEnabled")
        @UiThread
        get() = _enabled
        @UiThread
        set(value) {
            _enabled = value

            // The caller might enable/disable a number of complications. For efficiency we need
            // to coalesce these into one update task.
            complicationsHolder.scheduleUpdateActiveComplications()
        }

    private var _renderer = renderer

    var renderer: ComplicationRenderer
        @UiThread
        get() = _renderer
        @UiThread
        set(value) {
            renderer.onDetach()
            value.setData(renderer.getData())
            _renderer = value
            value.onAttach(this)
            initRenderer()
        }

    /**
     * Watch faces should use this method to render a complication. Note the system may call this.
     *
     * @param canvas The {@link Canvas} to render into
     * @param calendar The current {@link Calendar}
     * @param drawMode The current {@link DrawMode}
     */
    @UiThread
    fun draw(
        canvas: Canvas,
        calendar: Calendar,
        @DrawMode drawMode: Int
    ) {
        val bounds = computeBounds(Rect(0, 0, canvas.width, canvas.height))
        renderer.onDraw(canvas, bounds, calendar, drawMode)
    }

    /**
     * Sets whether the complication should be drawn highlighted or not. This is to provide visual
     * feedback when the user taps on a complication.
     *
     * @param highlight Whether or not the complication should be drawn highlighted.
     */
    internal fun setIsHighlighted(highlight: Boolean) {
        renderer.setIsHighlighted(highlight)
    }

    private fun initRenderer() {
        // Renderers may register a user style listener during their initializer which can call
        // setComplicationRenderer() before complicationInvalidateCallback has been initialized.
        if (this::invalidateCallback.isInitialized) {
            renderer.setInvalidateCallback(invalidateCallback)
        }
    }

    internal fun init(
        complicationsHolder: ComplicationsHolder,
        invalidateCallback: ComplicationRenderer.InvalidateCallback
    ) {
        this.complicationsHolder = complicationsHolder
        this.invalidateCallback = invalidateCallback
        initRenderer()
    }

    internal fun scheduleUpdateActiveComplications() {
        // In tests this may not be initialized.
        if (this::complicationsHolder.isInitialized) {
            // Update active complications to ensure accessibility data is up to date.
            complicationsHolder.scheduleUpdateActiveComplications()
        }
    }

    /** Computes the bounds of the complication by converting the unitSquareBounds to pixels. */
    fun computeBounds(screen: Rect) =
        Rect(
            (unitSquareBounds.left * screen.width()).toInt(),
            (unitSquareBounds.top * screen.height()).toInt(),
            (unitSquareBounds.right * screen.width()).toInt(),
            (unitSquareBounds.bottom * screen.height()).toInt()
        )
}
