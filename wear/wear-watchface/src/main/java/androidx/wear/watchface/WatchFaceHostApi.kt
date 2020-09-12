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
import android.graphics.Rect
import android.os.Handler
import android.support.wearable.watchface.accessibility.ContentDescriptionLabel
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.wear.complications.SystemProviders
import androidx.wear.watchface.style.UserStyleCategory

/** @hide */
@IntDef(
    value = [
        ComplicationBoundsType.ROUND_RECT,
        ComplicationBoundsType.BACKGROUND,
        ComplicationBoundsType.EDGE
    ]
)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
annotation class ComplicationBoundsType {
    companion object {
        /** The default, most complications are either circular or rounded rectangles. */
        const val ROUND_RECT = 0

        /** For full screen image complications drawn behind the watch face. */
        const val BACKGROUND = 1

        /** For edge of screen complications. */
        const val EDGE = 2
    }
}

/**
 * The API {@link WatchFace} uses to communicate with the system.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface WatchFaceHostApi {
    /** Returns the watch face's {@link Context}. */
    fun getContext(): Context

    /** Returns the main thread {@link Handler}. */
    fun getHandler(): Handler

    /** Registers whether the watch face has an analog or digital display. */
    fun registerWatchFaceType(@WatchFaceType watchFaceType: Int)

    /** Registers the watch face's user style schema with the system. */
    fun registerUserStyleSchema(styleSchema: List<UserStyleCategory>)

    /** Registers the watch face's current user style with the system. */
    fun setCurrentUserStyle(userStyle: Map<UserStyleCategory, UserStyleCategory.Option>)

    /** Returns the user style stored by the system if there is one or null otherwise. */
    fun getStoredUserStyle(
        schema: List<UserStyleCategory>
    ): Map<UserStyleCategory, UserStyleCategory.Option>?

    /** Registers the current bounds of the specified complication with the system. */
    fun setComplicationDetails(complicationId: Int, bounds: Rect, @ComplicationBoundsType type: Int)

    /** Registers the supported complication types of the specified complication. */
    fun setComplicationSupportedTypes(complicationId: Int, types: IntArray)

    /**
     * Sets ContentDescriptionLabels for text-to-speech screen readers to make your
     * complications, buttons, and any other text on your watchface accessible.
     *
     * <p>Each label is a region of the screen in absolute coordinates, along with
     * time-dependent text. The regions must not overlap.
     *
     * <p>You must set all labels at the same time; previous labels will be cleared. An empty
     * array clears all labels.
     *
     * <p>In addition to labeling your complications, please include a label that will read the
     * current time. You can use {@link
     * android.support.wearable.watchface.accessibility.AccessibilityUtils
     * #makeTimeAsComplicationText} to generate the proper ComplicationText.
     *
     * <p>This is a fairly expensive operation so use it sparingly (e.g. do not call it in
     * onDraw()).
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun setContentDescriptionLabels(labels: Array<ContentDescriptionLabel>)

    /**
     * Sets the complications which are active in the watchface. Complication data will be
     * received for these ids.
     *
     * <p>Any ids not in the provided {@code ids} will be considered inactive.
     *
     * <p>If providers and complication data types have been configured, the data received will
     * match the type chosen by the user. If no provider has been configured, data of type
     * {@link ComplicationData#TYPE_NOT_CONFIGURED} will be received.
     *
     * <p>Ids here are chosen by the watch face to represent each complication and can be any
     * integer.
     */
    fun setActiveComplications(watchFaceComplicationIds: IntArray)

    /**
     * Accepts a list of custom providers to attempt to set as the default provider for the
     * specified watch face complication id. The custom providers are tried in turn, if the
     * first doesn't exist then the next one is tried and so on. If none of them exist then the
     * specified system provider is set as the default instead.
     *
     * <p>This will do nothing if the providers are not installed, or if the specified type is
     * not supported by the providers, or if the user has already selected a provider for the
     * complication.
     *
     * <p>Note that if the watch face has not yet been granted the RECEIVE_COMPLICATION_DATA
     * permission, it will not be able to receive data from the provider unless the provider is
     * from the same app package as the watch face, or the provider lists the watch face as a
     * safe watch face. For system providers that may be used before your watch face has the
     * permission, use {@link #setDefaultSystemComplicationProvider} with a safe provider
     * instead.
     *
     * <p>A provider not satisfying the above conditions may still be set as a default using
     * this method, but the watch face will receive placeholder data of type {@link
     * ComplicationData#TYPE_NO_PERMISSION} until the permission has been granted.
     *
     * @param watchFaceComplicationId The watch face's ID for the complication
     * @param providers The list of non-system providers to try in order before falling back to
     *     fallbackSystemProvider. This list may be null.
     * @param fallbackSystemProvider The system provider to use if none of the providers could
     *     be used.
     * @param type The type of complication data that should be provided. Must be one of the
     *     types defined in {@link ComplicationData}
     */
    fun setDefaultComplicationProviderWithFallbacks(
        watchFaceComplicationId: Int,
        providers: List<ComponentName>?,
        @SystemProviders.ProviderId fallbackSystemProvider: Int,
        type: Int
    )

    /** Schedules a call to {@link Renderer#onDraw} to draw the next frame. */
    @UiThread
    fun invalidate()
}

/**
 * An opaque holder for the internal API {@link WatchFace} for it's host service.
 */
class WatchFaceHost {
    internal var api: WatchFaceHostApi? = null
}
