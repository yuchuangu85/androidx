/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.slice.widget;

import static androidx.slice.core.SliceHints.ICON_IMAGE;
import static androidx.slice.core.SliceHints.UNKNOWN_IMAGE;
import static androidx.slice.widget.SliceView.MODE_LARGE;
import static androidx.slice.widget.SliceView.MODE_SMALL;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.slice.view.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds style information shared between child views of a slice
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresApi(19)
public class SliceStyle {
    private int mTintColor = -1;
    private int mTitleColor;
    private int mSubtitleColor;
    private int mHeaderTitleSize;
    private int mHeaderSubtitleSize;
    private int mVerticalHeaderTextPadding;
    private int mTitleSize;
    private int mSubtitleSize;
    private int mVerticalTextPadding;
    private int mGridTitleSize;
    private int mGridSubtitleSize;
    private int mVerticalGridTextPadding;
    private int mGridTopPadding;
    private int mGridBottomPadding;

    private int mRowMaxHeight;
    private int mRowTextWithRangeHeight;
    private int mRowSingleTextWithRangeHeight;
    private int mRowMinHeight;
    private int mRowRangeHeight;
    private int mRowSelectionHeight;
    private int mRowTextWithSelectionHeight;
    private int mRowSingleTextWithSelectionHeight;
    private int mRowInlineRangeHeight;

    private int mGridBigPicMinHeight;
    private int mGridBigPicMaxHeight;
    private int mGridAllImagesHeight;
    private int mGridImageTextHeight;
    private int mGridMaxHeight;
    private int mGridMinHeight;

    private int mListMinScrollHeight;
    private int mListLargeHeight;

    private boolean mExpandToAvailableHeight;
    private boolean mHideHeaderRow;

    private RowStyle mRowStyle;

    public SliceStyle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SliceView,
                defStyleAttr, defStyleRes);
        try {
            int themeColor = a.getColor(R.styleable.SliceView_tintColor, -1);
            mTintColor = themeColor != -1 ? themeColor : mTintColor;
            mTitleColor = a.getColor(R.styleable.SliceView_titleColor, 0);
            mSubtitleColor = a.getColor(R.styleable.SliceView_subtitleColor, 0);

            mHeaderTitleSize = (int) a.getDimension(
                    R.styleable.SliceView_headerTitleSize, 0);
            mHeaderSubtitleSize = (int) a.getDimension(
                    R.styleable.SliceView_headerSubtitleSize, 0);
            mVerticalHeaderTextPadding = (int) a.getDimension(
                    R.styleable.SliceView_headerTextVerticalPadding, 0);

            mTitleSize = (int) a.getDimension(R.styleable.SliceView_titleSize, 0);
            mSubtitleSize = (int) a.getDimension(
                    R.styleable.SliceView_subtitleSize, 0);
            mVerticalTextPadding = (int) a.getDimension(
                    R.styleable.SliceView_textVerticalPadding, 0);

            mGridTitleSize = (int) a.getDimension(R.styleable.SliceView_gridTitleSize, 0);
            mGridSubtitleSize = (int) a.getDimension(
                    R.styleable.SliceView_gridSubtitleSize, 0);
            int defaultVerticalGridPadding = context.getResources().getDimensionPixelSize(
                    R.dimen.abc_slice_grid_text_inner_padding);
            mVerticalGridTextPadding = (int) a.getDimension(
                    R.styleable.SliceView_gridTextVerticalPadding, defaultVerticalGridPadding);
            mGridTopPadding = (int) a.getDimension(R.styleable.SliceView_gridTopPadding, 0);
            mGridBottomPadding = (int) a.getDimension(R.styleable.SliceView_gridBottomPadding, 0);

            int rowStyleRes = a.getResourceId(R.styleable.SliceView_rowStyle, 0);
            if (rowStyleRes != 0) {
                mRowStyle = new RowStyle(context, rowStyleRes);
            }

            int defaultRowMinHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.abc_slice_row_min_height);
            mRowMinHeight = (int) a.getDimension(
                    R.styleable.SliceView_rowMinHeight, defaultRowMinHeight);

            int defaultRowMaxHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.abc_slice_row_max_height);
            mRowMaxHeight = (int) a.getDimension(
                    R.styleable.SliceView_rowMaxHeight, defaultRowMaxHeight);

            int defaultRowRangeHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.abc_slice_row_range_height);
            mRowRangeHeight = (int) a.getDimension(
                    R.styleable.SliceView_rowRangeHeight, defaultRowRangeHeight);

            int defaultRowSingleTextWithRangeHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.abc_slice_row_range_single_text_height);
            mRowSingleTextWithRangeHeight = (int) a.getDimension(
                    R.styleable.SliceView_rowRangeSingleTextHeight,
                    defaultRowSingleTextWithRangeHeight);

            int defaultRowInlineRangeHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.abc_slice_row_range_inline_height);
            mRowInlineRangeHeight = (int) a.getDimension(
                    R.styleable.SliceView_rowInlineRangeHeight, defaultRowInlineRangeHeight);

            mExpandToAvailableHeight = a.getBoolean(
                    R.styleable.SliceView_expandToAvailableHeight, false);

            mHideHeaderRow = a.getBoolean(R.styleable.SliceView_hideHeaderRow, false);
        } finally {
            a.recycle();
        }

        // Note: The above colors and dimensions are styleable, but the below ones are not.

        final Resources r = context.getResources();

        mRowTextWithRangeHeight = r.getDimensionPixelSize(
                R.dimen.abc_slice_row_range_multi_text_height);
        mRowSelectionHeight = r.getDimensionPixelSize(R.dimen.abc_slice_row_selection_height);
        mRowTextWithSelectionHeight = r.getDimensionPixelSize(
                R.dimen.abc_slice_row_selection_multi_text_height);
        mRowSingleTextWithSelectionHeight = r.getDimensionPixelSize(
                R.dimen.abc_slice_row_selection_single_text_height);

        mGridBigPicMinHeight = r.getDimensionPixelSize(R.dimen.abc_slice_big_pic_min_height);
        mGridBigPicMaxHeight = r.getDimensionPixelSize(R.dimen.abc_slice_big_pic_max_height);
        mGridAllImagesHeight = r.getDimensionPixelSize(R.dimen.abc_slice_grid_image_only_height);
        mGridImageTextHeight = r.getDimensionPixelSize(R.dimen.abc_slice_grid_image_text_height);
        mGridMinHeight = r.getDimensionPixelSize(R.dimen.abc_slice_grid_min_height);
        mGridMaxHeight = r.getDimensionPixelSize(R.dimen.abc_slice_grid_max_height);

        mListMinScrollHeight = r.getDimensionPixelSize(R.dimen.abc_slice_row_min_height);
        mListLargeHeight = r.getDimensionPixelSize(R.dimen.abc_slice_large_height);
    }

    public int getRowMinHeight() {
        return mRowMinHeight;
    }

    public int getRowMaxHeight() {
        return mRowMaxHeight;
    }

    public int getRowInlineRangeHeight() {
        return mRowInlineRangeHeight;
    }

    public void setTintColor(int tint) {
        mTintColor = tint;
    }

    public int getTintColor() {
        return mTintColor;
    }

    public int getTitleColor() {
        return mTitleColor;
    }

    public int getSubtitleColor() {
        return mSubtitleColor;
    }

    public int getHeaderTitleSize() {
        return mHeaderTitleSize;
    }

    public int getHeaderSubtitleSize() {
        return mHeaderSubtitleSize;
    }

    public int getVerticalHeaderTextPadding() {
        return mVerticalHeaderTextPadding;
    }

    public int getTitleSize() {
        return mTitleSize;
    }

    public int getSubtitleSize() {
        return mSubtitleSize;
    }

    public int getVerticalTextPadding() {
        return mVerticalTextPadding;
    }

    public int getGridTitleSize() {
        return mGridTitleSize;
    }

    public int getGridSubtitleSize() {
        return mGridSubtitleSize;
    }

    public int getVerticalGridTextPadding() {
        return mVerticalGridTextPadding;
    }

    public int getGridTopPadding() {
        return mGridTopPadding;
    }

    public int getGridBottomPadding() {
        return mGridBottomPadding;
    }

    public RowStyle getRowStyle() {
        return mRowStyle;
    }

    public int getRowRangeHeight() {
        return mRowRangeHeight;
    }

    public int getRowSelectionHeight() {
        return mRowSelectionHeight;
    }

    public boolean getExpandToAvailableHeight() {
        return mExpandToAvailableHeight;
    }

    public boolean getHideHeaderRow() {
        return mHideHeaderRow;
    }

    public int getRowHeight(RowContent row, SliceViewPolicy policy) {
        int maxHeight = policy.getMaxSmallHeight() > 0 ? policy.getMaxSmallHeight() : mRowMaxHeight;

        if (row.getRange() == null && row.getSelection() == null
                && policy.getMode() != MODE_LARGE) {
            return maxHeight;
        }

        if (row.getRange() != null) {
            // If no StartItem, keep to use original layout.
            if (row.getStartItem() == null) {
                // Range element always has set height and then the height of the text
                // area on the row will vary depending on if 1 or 2 lines of text.
                int textAreaHeight = row.getLineCount() > 1 ? mRowTextWithRangeHeight
                        : mRowSingleTextWithRangeHeight;
                return textAreaHeight + mRowRangeHeight;
            } else {
                // If has StartItem then Range element is inline, the row height should be more to
                // fit thumb ripple.
                return mRowInlineRangeHeight;
            }
        }

        if (row.getSelection() != null) {
            // Selection element always has set height and then the height of the text
            // area on the row will vary depending on if 1 or 2 lines of text.
            int textAreaHeight = row.getLineCount() > 1 ? mRowTextWithSelectionHeight
                    : mRowSingleTextWithSelectionHeight;
            return textAreaHeight + mRowSelectionHeight;
        }

        return (row.getLineCount() > 1 || row.getIsHeader()) ? maxHeight : mRowMinHeight;
    }

    public int getGridHeight(GridContent grid, SliceViewPolicy policy) {
        boolean isSmall = policy.getMode() == MODE_SMALL;
        if (!grid.isValid()) {
            return 0;
        }
        int largestImageMode = grid.getLargestImageMode();
        int height;
        if (grid.isAllImages()) {
            height = grid.getGridContent().size() == 1
                    ? isSmall ? mGridBigPicMinHeight : mGridBigPicMaxHeight
                    : largestImageMode == ICON_IMAGE ? mGridMinHeight
                            : mGridAllImagesHeight;
        } else {
            boolean twoLines = grid.getMaxCellLineCount() > 1;
            boolean hasImage = grid.hasImage();
            boolean iconImagesOrNone = largestImageMode == ICON_IMAGE
                    || largestImageMode == UNKNOWN_IMAGE;
            height = (twoLines && !isSmall)
                    ? hasImage ? mGridMaxHeight : mGridMinHeight
                    : iconImagesOrNone ? mGridMinHeight : mGridImageTextHeight;
        }
        int topPadding = grid.isAllImages() && grid.getRowIndex() == 0
                ? mGridTopPadding : 0;
        int bottomPadding = grid.isAllImages() && grid.getIsLastIndex()
                ? mGridBottomPadding : 0;
        return height + topPadding + bottomPadding;
    }

    public int getListHeight(ListContent list, SliceViewPolicy policy) {
        if (policy.getMode() == MODE_SMALL) {
            return list.getHeader().getHeight(this, policy);
        }
        int maxHeight = policy.getMaxHeight();
        boolean scrollable = policy.isScrollable();

        int desiredHeight = getListItemsHeight(list.getRowItems(), policy);
        if (maxHeight > 0) {
            // Always ensure we're at least the height of our small version.
            int smallHeight = list.getHeader().getHeight(this, policy);
            maxHeight = Math.max(smallHeight, maxHeight);
        }
        int maxLargeHeight = maxHeight > 0
                ? maxHeight
                : mListLargeHeight;
        // Do we have enough content to reasonably scroll in our max?
        boolean bigEnoughToScroll = desiredHeight - maxLargeHeight >= mListMinScrollHeight;

        // Adjust for scrolling
        int height = bigEnoughToScroll && !getExpandToAvailableHeight() ? maxLargeHeight
                : maxHeight <= 0 ? desiredHeight
                : Math.min(maxLargeHeight, desiredHeight);
        if (!scrollable) {
            height = getListItemsHeight(getListItemsForNonScrollingList(list, height, policy),
                                        policy);
        }
        return height;
    }

    public int getListItemsHeight(List<SliceContent> listItems, SliceViewPolicy policy) {
        if (listItems == null) {
            return 0;
        }

        int height = 0;
        for (int i = 0; i < listItems.size(); i++) {
            SliceContent listItem = listItems.get(i);
            if (i == 0 && shouldSkipFirstListItem(listItems)) {
                continue;
            }
            height += listItem.getHeight(this, policy);
        }
        return height;
    }

    /**
     * Returns a list of items that can fit in the provided height. If this list
     * has a see more item this will be displayed in the list if appropriate.
     *
     * @param list the list from which to source the items.
     * @param availableHeight to use to determine the row items to return.
     * @param policy the policy info (scrolling, mode) to use when determining row items to return.
     *
     * @return the list of items that can be displayed in the provided height.
     */
    @NonNull
    public ArrayList<SliceContent> getListItemsForNonScrollingList(ListContent list,
                                                                    int availableHeight,
                                                                    SliceViewPolicy policy) {
        ArrayList<SliceContent> visibleItems = new ArrayList<>();
        if (list.getRowItems() == null || list.getRowItems().size() == 0) {
            return visibleItems;
        }
        final int minItemCountForSeeMore = list.getRowItems() != null ? 2 : 1;
        int visibleHeight = 0;
        int rowCount = list.getRowItems().size();
        for (int i = 0; i < rowCount; i++) {
            SliceContent listItem = list.getRowItems().get(i);
            if (i == 0 && shouldSkipFirstListItem(list.getRowItems())) {
                continue;
            }
            int itemHeight = listItem.getHeight(this, policy);
            if (availableHeight > 0 && visibleHeight + itemHeight > availableHeight) {
                break;
            } else {
                visibleHeight += itemHeight;
                visibleItems.add(listItem);
            }
        }


        // Only add see more if we're at least showing one item and it's not the header
        if (list.getSeeMoreItem() != null && visibleItems.size() >= minItemCountForSeeMore
                && visibleItems.size() != rowCount) {
            // Need to show see more
            int seeMoreHeight = list.getSeeMoreItem().getHeight(this, policy);
            visibleHeight += seeMoreHeight;

            // Free enough vertical space to fit the see more item.
            while (visibleHeight > availableHeight
                    && visibleItems.size() >= minItemCountForSeeMore) {
                int lastIndex = visibleItems.size() - 1;
                SliceContent lastItem = visibleItems.get(lastIndex);
                visibleHeight -= lastItem.getHeight(this, policy);
                visibleItems.remove(lastIndex);
            }

            if (visibleItems.size() >= minItemCountForSeeMore) {
                visibleItems.add(list.getSeeMoreItem());
            } else {
                // Not possible to free enough vertical space. We'll show only the header.
                visibleHeight -= seeMoreHeight;
            }
        }
        if (visibleItems.size() == 0) {
            // Didn't have enough space to show anything; should still show something
            visibleItems.add(list.getRowItems().get(0));
        }
        return visibleItems;
    }

    /**
     * Returns a list of items that should be displayed to the user.
     *
     * @param list the list from which to source the items.
     */
    @NonNull
    public List<SliceContent> getListItemsToDisplay(@NonNull ListContent list) {
        List<SliceContent> rowItems = list.getRowItems();
        if (rowItems.size() > 0 && shouldSkipFirstListItem(rowItems)) {
            return rowItems.subList(1, rowItems.size());
        }
        return rowItems;
    }

    /** Returns true if the first item of a list should be skipped. */
    private boolean shouldSkipFirstListItem(List<SliceContent> rowItems) {
        // Hide header row if requested, but only if there is at least one non-header row.
        return getHideHeaderRow() && rowItems.size() > 1 && rowItems.get(0) instanceof RowContent
                && ((RowContent) rowItems.get(0)).getIsHeader();
    }
}
