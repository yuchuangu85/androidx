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

package androidx.compose.ui.res

import android.annotation.SuppressLint
import android.content.res.Resources
import android.util.TypedValue
import android.util.Xml
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.VectorAsset
import androidx.compose.ui.graphics.vector.compat.createVectorImageBuilder
import androidx.compose.ui.graphics.vector.compat.isAtEnd
import androidx.compose.ui.graphics.vector.compat.parseCurrentVectorNode
import androidx.compose.ui.graphics.vector.compat.seekToStartTag
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.util.trace
import org.xmlpull.v1.XmlPullParserException

/**
 * Effect used to load a [VectorAsset] from an Android resource id
 * This is useful for querying top level properties of the [VectorAsset]
 * such as it's intrinsic width and height to be able to size components
 * based off of it's dimensions appropriately
 *
 * Note: This API is transient and will be likely removed for encouraging async resource loading.
 */
@Composable
fun vectorResource(@DrawableRes id: Int): VectorAsset {
    val context = ContextAmbient.current
    val res = context.resources
    val theme = context.theme
    return remember(id) {
        loadVectorResource(theme, res, id)
    }
}

/**
 * Load the vector drawable in background thread.
 *
 * Until resource loading complete, this function returns deferred vector drawable resource with
 * [PendingResource]. Once the loading finishes, recompose is scheduled and this function will
 * return deferred vector drawable resource with [LoadedResource] or [FailedResource].
 *
 * @param id the resource identifier
 * @param pendingResource an optional resource to be used during loading instead.
 * @param failedResource an optional resource to be used if resource loading failed.
 * @return the deferred vector drawable resource.
 */
@Composable
fun loadVectorResource(
    id: Int,
    pendingResource: VectorAsset? = null,
    failedResource: VectorAsset? = null
): DeferredResource<VectorAsset> {
    val context = ContextAmbient.current
    val res = context.resources
    val theme = context.theme

    val value = remember { TypedValue() }
    res.getValue(id, value, true)
    // We use the file path as a key of the request cache.
    // TODO(nona): Add density to the key?
    val key = value.string!!.toString() // Vector drawable must have path in resource.

    return loadResource(key, pendingResource, failedResource) {
        trace("Vector Resource Loading") {
            loadVectorResource(theme, res, id)
        }
    }
}

@Throws(XmlPullParserException::class)
@SuppressWarnings("RestrictedApi")
internal fun loadVectorResource(
    theme: Resources.Theme? = null,
    res: Resources,
    resId: Int
): VectorAsset {
    @SuppressLint("ResourceType") val parser = res.getXml(resId)
    val attrs = Xml.asAttributeSet(parser)
    val builder = parser.seekToStartTag().createVectorImageBuilder(res, theme, attrs)

    var nestedGroups = 0
    while (!parser.isAtEnd()) {
        nestedGroups = parser.parseCurrentVectorNode(res, attrs, theme, builder, nestedGroups)
        parser.next()
    }
    return builder.build()
}