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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.support.wearable.complications.ComplicationData
import android.support.wearable.watchface.accessibility.AccessibilityUtils
import android.support.wearable.watchface.accessibility.ContentDescriptionLabel
import androidx.annotation.VisibleForTesting
import androidx.wear.complications.ComplicationHelperActivity
import java.lang.ref.WeakReference

private fun getComponentName(context: Context) = ComponentName(
    context.packageName,
    context.javaClass.typeName
)

/**
 * The set of {@link Complication}s associated with the {@link WatchFace}. Dynamic creation of
 * complications isn't supported, however complications can be enabled and disabled, perhaps as
 * part of a user style see {@link UserStyleCategory} and {@link Renderer#onStyleChanged}.
 */
class ComplicationSlots(
    /**
     * The complications associated with the watch face, may be empty.
     */
    complicationCollection: Collection<Complication>
) {
    interface ComplicationListener {
        /**
         * Called when the user single taps on a complication.
         *
         * @param complicationId The watch face's id for the complication single tapped
         */
        fun onComplicationSingleTapped(complicationId: Int) {}

        /**
         * Called when the user double taps on a complication, launches the complication
         * configuration activity.
         *
         * @param complicationId The watch face's id for the complication double tapped
         */
        fun onComplicationDoubleTapped(complicationId: Int) {}
    }

    private lateinit var systemApi: SystemApi
    private lateinit var calendar: Calendar
    private lateinit var renderer: Renderer
    private lateinit var pendingUpdateActiveComplications: CancellableUniqueTask

    // A map of IDs to complications.
    val complications: Map<Int, Complication> =
        complicationCollection.associateBy(Complication::id)

    private val complicationListeners = HashSet<ComplicationListener>()

    @VisibleForTesting
    constructor(
        complicationCollection: Collection<Complication>,
        renderer: Renderer
    ) : this(complicationCollection) {
        this.renderer = renderer
    }

    internal fun init(
        systemApi: SystemApi,
        calendar: Calendar,
        renderer: Renderer,
        complicationInvalidateCallback: ComplicationRenderer.InvalidateCallback
    ) {
        this.systemApi = systemApi
        this.calendar = calendar
        this.renderer = renderer
        pendingUpdateActiveComplications = CancellableUniqueTask(systemApi.getHandler())

        for ((_, complication) in complications) {
            complication.init(this, complicationInvalidateCallback)

            if (!complication.defaultProvider.isEmpty() &&
                complication.defaultProviderType != WatchFace.DEFAULT_PROVIDER_TYPE_NONE
            ) {
                this.systemApi.setDefaultComplicationProviderWithFallbacks(
                    complication.id,
                    complication.defaultProvider.providers,
                    complication.defaultProvider.systemProviderFallback,
                    complication.defaultProviderType
                )
            }
        }

        // Activate complications.
        scheduleUpdateActiveComplications()
    }

    /** Returns the {@link Complication} corresponding to id or null. */
    operator fun get(id: Int) = complications[id]

    internal fun scheduleUpdateActiveComplications() {
        if (!pendingUpdateActiveComplications.isPending()) {
            pendingUpdateActiveComplications.postUnique(this::updateActiveComplications)
        }
    }

    private fun updateActiveComplications() {
        val activeKeys = mutableListOf<Int>()
        val labels = mutableListOf<ContentDescriptionLabel>()

        // Add a ContentDescriptionLabel for the main clock element.
        labels.add(
            ContentDescriptionLabel(
                renderer.getMainClockElementBounds(),
                AccessibilityUtils.makeTimeAsComplicationText(
                    systemApi.getContext()
                )
            )
        )

        for ((id, complication) in complications) {
            if (complication.enabled) {
                activeKeys.add(id)

                // Generate a ContentDescriptionLabel and send complication bounds for
                // non-background  complications.
                val data = complication.complicationData
                val complicationBounds = complication.boundsProvider.computeBounds(
                    complication, renderer.screenBounds, calendar
                )

                if (complication.boundsProvider is BackgroundComplicationBounds) {
                    systemApi.setComplicationDetails(
                        id,
                        complicationBounds,
                        ComplicationSlotType.BACKGROUND
                    )
                } else {
                    if (data != null) {
                        labels.add(
                            ContentDescriptionLabel(
                                systemApi.getContext(),
                                complicationBounds,
                                data
                            )
                        )
                    }

                    systemApi.setComplicationDetails(
                        id,
                        complicationBounds,
                        ComplicationSlotType.ROUND_RECT
                    )
                }

                systemApi.setComplicationSupportedTypes(
                    id,
                    complication.supportedComplicationDataTypes
                )
            }
        }

        systemApi.setActiveComplications(activeKeys.toIntArray())

        // Register ContentDescriptionLabels which are used to provide accessibility data.
        systemApi.setContentDescriptionLabels(labels.toTypedArray())
    }

    /**
     * Called when new complication data is received.
     *
     * @param watchFaceComplicationId The id of the complication that the data relates to. This
     *     will be an id that was previously sent in a call to {@link #setActiveComplications}.
     * @param data The {@link ComplicationData} that should be displayed in the complication.
     */
    internal fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData) {
        complications[watchFaceComplicationId]?.complicationData = data
    }

    /**
     * Briefly highlights the complication to provide visual feedback when the user has tapped
     * on it.
     *
     * @param complicationId The watch face's ID of the complication to briefly highlight
     */
    fun brieflyHighlightComplication(complicationId: Int) {
        val complication = requireNotNull(complications[complicationId]) {
            "No complication found with ID $complicationId"
        }
        complication.complicationRenderer.setIsHighlighted(true)

        val weakRef = WeakReference(this)
        systemApi.getHandler().postDelayed(
            {
                // The watch face might go away before this can run.
                if (weakRef.get() != null) {
                    complication.complicationRenderer.setIsHighlighted(false)
                }
            },
            WatchFace.CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS
        )
    }

    /**
     * Returns the id of the complication at coordinates x, y or {@code null} if there isn't one.
     *
     * @param x The x coordinate of the point to perform a hit test
     * @param y The y coordinate of the point to perform a hit test
     * @param calendar The current {@link Calendar}, necessary if complications animate
     * @return The complication at coordinates x, y or {@code null} if there isn't one
     */
    fun getComplicationAt(x: Int, y: Int, calendar: Calendar): Complication? {
        return complications.entries.firstOrNull {
            it.value.enabled && it.value.boundsProvider !is BackgroundComplicationBounds &&
                    it.value.boundsProvider.computeBounds(
                        it.value,
                        renderer.screenBounds,
                        calendar
                    ).contains(x, y)
        }?.value
    }

    /**
     * Returns the background complication if there is one or {@code null} otherwise.
     *
     * @return The background complication if there is one or {@code null} otherwise
     */
    fun getBackgroundComplication(): Complication? {
        return complications.entries.firstOrNull {
            it.value.boundsProvider is BackgroundComplicationBounds
        }?.value
    }

    /**
     * Called when the user single taps on a complication, invokes the permission request helper
     * if needed, otherwise s the tap action.
     *
     * @param complicationId The watch face's id for the complication single tapped
     */
    @SuppressWarnings("SyntheticAccessor")
    internal fun onComplicationSingleTapped(complicationId: Int) {
        // Check if the complication is missing permissions.
        val data = complications[complicationId]?.complicationData ?: return
        if (data.type == ComplicationData.TYPE_NO_PERMISSION) {
            systemApi.getContext().startActivity(
                ComplicationHelperActivity.createPermissionRequestHelperIntent(
                    systemApi.getContext(),
                    getComponentName(systemApi.getContext())
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        data.tapAction?.send()
        for (complicationListener in complicationListeners) {
            complicationListener.onComplicationSingleTapped(complicationId)
        }
    }

    /**
     * Called when the user double taps on a complication, launches the complication
     * configuration activity.
     *
     * @param complicationId The watch face's id for the complication double tapped
     */
    @SuppressWarnings("SyntheticAccessor")
    internal fun onComplicationDoubleTapped(complicationId: Int) {
        // Check if the complication is missing permissions.
        val complication = complications[complicationId] ?: return
        val data = complication.complicationData ?: return
        if (data.type == ComplicationData.TYPE_NO_PERMISSION) {
            systemApi.getContext().startActivity(
                ComplicationHelperActivity.createPermissionRequestHelperIntent(
                    systemApi.getContext(),
                    getComponentName(systemApi.getContext())
                )
            )
            return
        }
        systemApi.getContext().startActivity(
            ComplicationHelperActivity.createProviderChooserHelperIntent(
                systemApi.getContext(),
                getComponentName(systemApi.getContext()),
                complicationId,
                complication.supportedComplicationDataTypes
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (complicationListener in complicationListeners) {
            complicationListener.onComplicationDoubleTapped(complicationId)
        }
    }

    /**
     * Adds a {@link ComplicationListener} which is called whenever the user interacts with a
     * complication.
     */
    fun addComplicationListener(complicationListener: ComplicationListener) {
        complicationListeners.add(complicationListener)
    }

    /**
     * Removes a {@link ComplicationListener} previously added by {@link #addComplicationListener}.
     */
    fun removeComplicationListener(complicationListener: ComplicationListener) {
        complicationListeners.remove(complicationListener)
    }
}
