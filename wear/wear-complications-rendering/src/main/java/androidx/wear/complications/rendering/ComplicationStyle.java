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

package androidx.wear.complications.rendering;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.wearable.complications.ComplicationData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Defines attributes to customize appearance of rendered {@link
 * android.support.wearable.complications.ComplicationData}.
 */
public class ComplicationStyle {

    /** Default primary color. */
    private static final int PRIMARY_COLOR_DEFAULT = Color.WHITE;

    /** Default secondary color. */
    private static final int SECONDARY_COLOR_DEFAULT = Color.LTGRAY;

    /** Default background color. */
    private static final int BACKGROUND_COLOR_DEFAULT = Color.BLACK;

    /** Default background color. */
    private static final int HIGHLIGHT_COLOR_DEFAULT = Color.LTGRAY;

    /** Default border color. */
    private static final int BORDER_COLOR_DEFAULT = Color.WHITE;

    /** Default text size. */
    private static final int TEXT_SIZE_DEFAULT = Integer.MAX_VALUE;

    /** Default typeface. */
    private static final Typeface TYPEFACE_DEFAULT =
            Typeface.create("sans-serif-condensed", Typeface.NORMAL);

    /** Default dash width. */
    private static final int DASH_WIDTH_DEFAULT = 3;

    /** Default dash gap. */
    private static final int DASH_GAP_DEFAULT = 3;

    /** Default border width. */
    private static final int BORDER_WIDTH_DEFAULT = 1;

    /** Default ring width. */
    private static final int RING_WIDTH_DEFAULT = 2;

    /** Default border radius. */
    public static final int BORDER_RADIUS_DEFAULT = Integer.MAX_VALUE;

    private int mBackgroundColor = BACKGROUND_COLOR_DEFAULT;
    private Drawable mBackgroundDrawable = null;
    private int mTextColor = PRIMARY_COLOR_DEFAULT;
    private int mTitleColor = SECONDARY_COLOR_DEFAULT;
    private Typeface mTextTypeface = TYPEFACE_DEFAULT;
    private Typeface mTitleTypeface = TYPEFACE_DEFAULT;
    private int mTextSize = TEXT_SIZE_DEFAULT;
    private int mTitleSize = TEXT_SIZE_DEFAULT;
    private ColorFilter mImageColorFilter = null;
    private int mIconColor = PRIMARY_COLOR_DEFAULT;
    private int mBorderColor = BORDER_COLOR_DEFAULT;
    private int mBorderStyle = ComplicationDrawable.BORDER_STYLE_SOLID;
    private int mBorderDashWidth = DASH_WIDTH_DEFAULT;
    private int mBorderDashGap = DASH_GAP_DEFAULT;
    private int mBorderRadius = BORDER_RADIUS_DEFAULT;
    private int mBorderWidth = BORDER_WIDTH_DEFAULT;
    private int mRangedValueRingWidth = RING_WIDTH_DEFAULT;
    private int mRangedValuePrimaryColor = PRIMARY_COLOR_DEFAULT;
    private int mRangedValueSecondaryColor = SECONDARY_COLOR_DEFAULT;
    private int mHighlightColor = HIGHLIGHT_COLOR_DEFAULT;

    public ComplicationStyle() {
    }

    public ComplicationStyle(@NonNull ComplicationStyle style) {
        mBackgroundColor = style.getBackgroundColor();
        mBackgroundDrawable = style.getBackgroundDrawable();
        mTextColor = style.getTextColor();
        mTitleColor = style.getTitleColor();
        mTextTypeface = style.getTextTypeface();
        mTitleTypeface = style.getTitleTypeface();
        mTextSize = style.getTextSize();
        mTitleSize = style.getTitleSize();
        mImageColorFilter = style.getImageColorFilter();
        mIconColor = style.getIconColor();
        mBorderColor = style.getBorderColor();
        mBorderStyle = style.getBorderStyle();
        mBorderDashWidth = style.getBorderDashWidth();
        mBorderDashGap = style.getBorderDashGap();
        mBorderRadius = style.getBorderRadius();
        mBorderWidth = style.getBorderWidth();
        mRangedValueRingWidth = style.getRangedValueRingWidth();
        mRangedValuePrimaryColor = style.getRangedValuePrimaryColor();
        mRangedValueSecondaryColor = style.getRangedValueSecondaryColor();
        mHighlightColor = style.getHighlightColor();
    }

    /** Returns the background color to be used. */
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    /** Returns the background drawable to be used, or null if there's no background drawable. */
    @Nullable
    public Drawable getBackgroundDrawable() {
        return mBackgroundDrawable;
    }

    /** Returns the text color. Text color should be used for short and long text. */
    public int getTextColor() {
        return mTextColor;
    }

    /** Returns the title color. Title color should be used for short and long title. */
    public int getTitleColor() {
        return mTitleColor;
    }

    /**
     * Returns the color filter to be used when rendering small and large images, or null if there's
     * no color filter.
     */
    @Nullable
    public ColorFilter getImageColorFilter() {
        return mImageColorFilter;
    }

    /** Returns the color for tinting icons. */
    public int getIconColor() {
        return mIconColor;
    }

    /** Returns the typeface to be used for short and long text. */
    @Nullable
    public Typeface getTextTypeface() {
        return mTextTypeface;
    }

    /** Returns the typeface to be used for short and long title. */
    @Nullable
    public Typeface getTitleTypeface() {
        return mTitleTypeface;
    }

    /** Returns the text size to be used for short and long text. */
    public int getTextSize() {
        return mTextSize;
    }

    /** Returns the text size to be used for short and long title. */
    public int getTitleSize() {
        return mTitleSize;
    }

    /** Returns the border color. */
    public int getBorderColor() {
        return mBorderColor;
    }

    @ComplicationDrawable.BorderStyle
    public int getBorderStyle() {
        return mBorderStyle;
    }

    /**
     * Returns the dash width to be used when drawing borders of type {@link
     * ComplicationDrawable#BORDER_STYLE_DASHED}.
     */
    public int getBorderDashWidth() {
        return mBorderDashWidth;
    }

    /**
     * Returns the dash gap to be used when drawing borders of type {@link
     * ComplicationDrawable#BORDER_STYLE_DASHED}.
     */
    public int getBorderDashGap() {
        return mBorderDashGap;
    }

    /**
     * Returns the border radius. If {@link ComplicationStyle#BORDER_RADIUS_DEFAULT} is returned,
     * border radius should be reduced to half of the minimum of width or height during the
     * rendering.
     */
    public int getBorderRadius() {
        return mBorderRadius;
    }

    /** Returns the border width. */
    public int getBorderWidth() {
        return mBorderWidth;
    }

    /** Returns the ring width to be used when rendering ranged value indicator. */
    public int getRangedValueRingWidth() {
        return mRangedValueRingWidth;
    }

    /** Returns the color to be used when rendering first part of ranged value indicator. */
    public int getRangedValuePrimaryColor() {
        return mRangedValuePrimaryColor;
    }

    /** Returns the color to be used when rendering second part of ranged value indicator. */
    public int getRangedValueSecondaryColor() {
        return mRangedValueSecondaryColor;
    }

    /** Returns the highlight color to be used when the complication is highlighted. */
    public int getHighlightColor() {
        return mHighlightColor;
    }

    /**
     * Sets the background color.
     *
     * @param backgroundColor The color to set
     */
    public void setBackgroundColor(int backgroundColor) {
        this.mBackgroundColor = backgroundColor;
    }

    /**
     * Sets the {@link Drawable} to render in the background.
     *
     * @param backgroundDrawable The {@link Drawable} to render in the background
     */
    public void setBackgroundDrawable(@Nullable Drawable backgroundDrawable) {
        this.mBackgroundDrawable = backgroundDrawable;
    }

    /**
     * Sets the color to render the text with. Text color is used for rendering short text
     * and long text fields.
     *
     * @param textColor The color to render the text with
     */
    public void setTextColor(int textColor) {
        this.mTextColor = textColor;
    }

    /**
     * Sets the color to render the title with.  Title color is used for rendering short
     * title and long title fields.
     *
     * @param titleColor The color to render the title with
     */
    public void setTitleColor(int titleColor) {
        this.mTitleColor = titleColor;
    }

    /**
     * Sets the color filter used in active mode when rendering large images and small images
     * with style {@link ComplicationData#IMAGE_STYLE_PHOTO}.
     *
     * @param colorFilter The {@link ColorFilter} to use
     */
    public void setImageColorFilter(@Nullable ColorFilter colorFilter) {
        this.mImageColorFilter = colorFilter;
    }

    /**
     * Sets the color for tinting the icon with.
     *
     * @param iconColor The color to render the icon with
     */
    public void setIconColor(int iconColor) {
        this.mIconColor = iconColor;
    }

    /**
     * Sets {@link Typeface} to use when rendering short text and long text fields.
     *
     * @param textTypeface The {@link Typeface} to render the text with
     */
    public void setTextTypeface(@NonNull Typeface textTypeface) {
        this.mTextTypeface = textTypeface;
    }

    /**
     * Sets the {@link Typeface} to render the title for short and long text with.
     *
     * @param titleTypeface The {@link Typeface} to render the title with
     */
    public void setTitleTypeface(@NonNull Typeface titleTypeface) {
        this.mTitleTypeface = titleTypeface;
    }

    /**
     * Sets the size of the text to use when rendering short text and long text fields.
     *
     * @param textSize The size of the text=
     */
    public void setTextSize(int textSize) {
        this.mTextSize = textSize;
    }

    /**
     * Sets the size of the title text to use when rendering short text and long text fields.
     *
     * @param titleSize The size of the title text=
     */
    public void setTitleSize(int titleSize) {
        this.mTitleSize = titleSize;
    }

    /**
     * Sets the color to render the complication border with.
     *
     * @param borderColor The color to render the complication border with
     */
    public void setBorderColor(int borderColor) {
        this.mBorderColor = borderColor;
    }

    /**
     * Sets the style to render the complication border with.
     *
     * @param borderStyle The style to render the complication border with
     */
    public void setBorderStyle(@ComplicationDrawable.BorderStyle int borderStyle) {
        switch (borderStyle) {
            case ComplicationDrawable.BORDER_STYLE_SOLID:
                this.mBorderStyle = ComplicationDrawable.BORDER_STYLE_SOLID;
                break;
            case ComplicationDrawable.BORDER_STYLE_DASHED:
                this.mBorderStyle = ComplicationDrawable.BORDER_STYLE_DASHED;
                break;
            default:
                this.mBorderStyle = ComplicationDrawable.BORDER_STYLE_NONE;
        }
    }

    /**
     * Sets dash widths to render the complication border with when drawing borders with style
     * {@link ComplicationDrawable#BORDER_STYLE_DASHED}.
     *
     * @param borderDashWidth The dash widths to render the complication border with
     */
    public void setBorderDashWidth(int borderDashWidth) {
        this.mBorderDashWidth = borderDashWidth;
    }

    /**
     * Sets the dash gap render the complication border with when drawing borders with style
     * {@link ComplicationDrawable#BORDER_STYLE_DASHED}.
     *
     * @param borderDashGap The dash gap render the complication border with
     */
    public void setBorderDashGap(int borderDashGap) {
        this.mBorderDashGap = borderDashGap;
    }

    /**
     * Sets the border radius to be applied to the corners of the bounds of the complication in
     * active mode. Border radius will be limited to the half of width or height, depending
     * on which one is smaller.
     *
     * @param borderRadius The radius to render the complication border with
     */
    public void setBorderRadius(int borderRadius) {
        this.mBorderRadius = borderRadius;
    }

    /**
     * Sets the width to render the complication border with.
     *
     * @param borderWidth The width to render the complication border with
     */
    public void setBorderWidth(int borderWidth) {
        this.mBorderWidth = borderWidth;
    }

    /**
     * Sets the stroke width used when rendering the ranged value indicator.
     *
     * @param rangedValueRingWidth The width to render the ranged value ring with
     */
    public void setRangedValueRingWidth(int rangedValueRingWidth) {
        this.mRangedValueRingWidth = rangedValueRingWidth;
    }

    /**
     * Sets the main color to render the ranged value text with.
     *
     * @param rangedValuePrimaryColor The main color to render the ranged value text with
     */
    public void setRangedValuePrimaryColor(int rangedValuePrimaryColor) {
        this.mRangedValuePrimaryColor = rangedValuePrimaryColor;
    }

    /**
     * Sets the secondary color to render the ranged value text with.
     *
     * @param rangedValueSecondaryColor The secondary color to render the ranged value text with
     */
    public void setRangedValueSecondaryColor(int rangedValueSecondaryColor) {
        this.mRangedValueSecondaryColor = rangedValueSecondaryColor;
    }

    /**
     * Sets the background color to use when the complication is highlighted.
     *
     * @param highlightColor The background color to use when the complication is highlighted
     */
    public void setHighlightColor(int highlightColor) {
        this.mHighlightColor = highlightColor;
    }
}
