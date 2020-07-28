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

package androidx.compose.ui.platform

import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.runtime.ambientOf
import androidx.compose.runtime.staticAmbientOf
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.CoroutineContext

/**
 * The default animation clock used for animations when an explicit clock isn't provided.
 */
val AnimationClockAmbient = staticAmbientOf<AnimationClockObservable>()

/**
 * The ambient that can be used to trigger autofill actions. Eg. [Autofill.requestAutofillForNode].
 */
val AutofillAmbient = staticAmbientOf<Autofill?>()

/**
 * The ambient that can be used to add
 * [AutofillNode][import androidx.compose.ui.autofill.AutofillNode]s to the autofill tree. The
 * [AutofillTree] is a temporary data structure that will be replaced by Autofill Semantics
 * (b/138604305).
 */
val AutofillTreeAmbient = staticAmbientOf<AutofillTree>()

/**
 * The ambient to provide communication with platform clipboard service.
 */
val ClipboardManagerAmbient = staticAmbientOf<ClipboardManager>()

/**
 * Don't use this.
 * @suppress
 */
@Deprecated(message = "This will be replaced with something more appropriate when suspend works.")
val CoroutineContextAmbient = ambientOf<CoroutineContext>()

/**
 * Provides the [Density] to be used to transform between [density-independent pixel
 * units (DP)][androidx.compose.ui.unit.Dp] and [pixel units][androidx.compose.ui.unit.Px] or
 * [scale-independent pixel units (SP)][androidx.compose.ui.unit.TextUnit] and
 * [pixel units][androidx.compose.ui.unit.Px]. This is typically used when a [DP][androidx.compose.ui.unit.Dp]
 * is provided and it must be converted in the body of [Layout] or [DrawModifier].
 */
val DensityAmbient = staticAmbientOf<Density>()

/**
 * The ambient to provide platform font loading methods.
 *
 * Use [androidx.compose.ui.res.fontResource] instead.
 * @suppress
 */
val FontLoaderAmbient = staticAmbientOf<Font.ResourceLoader>()

/**
 * The ambient to provide haptic feedback to the user.
 */
val HapticFeedBackAmbient = staticAmbientOf<HapticFeedback>()

/**
 * The ambient to provide communication with platform text input service.
 */
val TextInputServiceAmbient = staticAmbientOf<TextInputService?>()

/**
 * The ambient to provide text-related toolbar.
 */
val TextToolbarAmbient = staticAmbientOf<TextToolbar>()

/**
 * The ambient to provide functionality related to URL, e.g. open URI.
 */
val UriHandlerAmbient = staticAmbientOf<UriHandler>()

/**
 * The ambient to provide the layout direction.
 */
val LayoutDirectionAmbient = staticAmbientOf<LayoutDirection>()