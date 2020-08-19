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

package androidx.compose.ui.text.platform.extensions

import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.isSet
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.platform.TypefaceAdapter
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

internal fun TextPaint.applySpanStyle(
    style: SpanStyle,
    typefaceAdapter: TypefaceAdapter,
    density: Density
): SpanStyle {
    when (style.fontSize.type) {
        TextUnitType.Sp -> with(density) {
            textSize = style.fontSize.toPx()
        }
        TextUnitType.Em -> {
            textSize *= style.fontSize.value
        }
        TextUnitType.Inherit -> {} // Do nothing
    }

    if (style.hasFontAttributes()) {
        typeface = createTypeface(style, typefaceAdapter)
    }

    if (style.localeList != null && style.localeList != LocaleList.current) {
        if (Build.VERSION.SDK_INT >= 24) {
            textLocales = style.localeList.toAndroidLocaleList()
        } else {
            val locale = if (style.localeList.isEmpty()) {
                Locale.current
            } else {
                style.localeList[0]
            }
            textLocale = locale.toJavaLocale()
        }
    }

    if (style.color.isSet) {
        color = style.color.toArgb()
    }

    when (style.letterSpacing.type) {
        TextUnitType.Em -> { letterSpacing = style.letterSpacing.value }
        TextUnitType.Sp -> {} // Sp will be handled by applying a span
        TextUnitType.Inherit -> {} // Do nothing
    }

    if (style.fontFeatureSettings != null && style.fontFeatureSettings != "") {
        fontFeatureSettings = style.fontFeatureSettings
    }

    if (style.textGeometricTransform != null &&
        style.textGeometricTransform != TextGeometricTransform.None
    ) {
        textScaleX *= style.textGeometricTransform.scaleX
        textSkewX += style.textGeometricTransform.skewX
    }

    if (style.shadow != null && style.shadow != Shadow.None) {
        setShadowLayer(
            style.shadow.blurRadius,
            style.shadow.offset.x,
            style.shadow.offset.y,
            style.shadow.color.toArgb()
        )
    }

    if (style.textDecoration != null && style.textDecoration != TextDecoration.None) {
        if (TextDecoration.Underline in style.textDecoration) {
            isUnderlineText = true
        }
        if (TextDecoration.LineThrough in style.textDecoration) {
            isStrikeThruText = true
        }
    }

    // When FontFamily is a custom font(FontListFontFamily), it needs to be applied on Paint to
    // compute empty paragraph height. Meanwhile, we also need a FontSpan for
    // FontStyle/FontWeight span to work correctly.
    // letterSpacing with unit Sp needs to be handled by span.
    // baselineShift and bgColor is reset in the Android Layout constructor,
    // therefore we cannot apply them on paint, have to use spans.
    return SpanStyle(
        fontFamily = if (style.fontFamily != null && style.fontFamily is FontListFontFamily) {
            style.fontFamily
        } else {
            null
        },
        letterSpacing = if (style.letterSpacing.type == TextUnitType.Sp &&
            style.letterSpacing.value != 0f) {
            style.letterSpacing
        } else {
            TextUnit.Inherit
        },
        background = if (style.background == Color.Transparent) {
            Color.Unset // No need to add transparent background for default text style.
        } else {
            style.background
        },
        baselineShift = if (style.baselineShift == BaselineShift.None) {
            null
        } else {
            style.baselineShift
        }
    )
}

/**
 * Returns true if this [SpanStyle] contains any font style attributes set.
 */
private fun SpanStyle.hasFontAttributes(): Boolean {
    return fontFamily != null || fontStyle != null || fontWeight != null
}

private fun createTypeface(style: SpanStyle, typefaceAdapter: TypefaceAdapter): Typeface {
    return typefaceAdapter.create(
        fontFamily = style.fontFamily,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All
    )
}
