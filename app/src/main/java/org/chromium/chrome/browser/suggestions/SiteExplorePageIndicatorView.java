// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.Context;
import android.database.DataSetObserver;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.ViewUtils;

/**
 * Page indicator view for a {@link ViewPager}. It has to be inserted as a child of the
 * {@link ViewPager}. After this, it will read the number of pages, display that many dots and
 * highlight the dot of the selected tab.
 */
public class SiteExplorePageIndicatorView
        extends LinearLayout implements ViewPager.OnPageChangeListener {
    private static final int SPACE_BETWEEN_INDICATORS_DP = 8;

    private ViewPager mViewPager;
    private DataSetObserver mDataSetObserver;
    private int mCurrentSelectedPosition;

    public SiteExplorePageIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDataSetObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                addPageIndicators();
            }
        };
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {}

    @Override
    public void onPageSelected(int newPosition) {
        // Switch the highlighted dot to the one corresponding to the newly selected page.
        View currentSelectedView = getChildAt(mCurrentSelectedPosition);
        View newSelectedView = getChildAt(newPosition);

        currentSelectedView.setSelected(false);
        newSelectedView.setSelected(true);

        mCurrentSelectedPosition = newPosition;
    }

    @Override
    public void onPageScrollStateChanged(int i) {}

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mViewPager = (ViewPager) getParent();

        mViewPager.addOnPageChangeListener(this);
        mViewPager.getAdapter().registerDataSetObserver(mDataSetObserver);

        ((ViewPager.LayoutParams) getLayoutParams()).isDecor = true;

        addPageIndicators();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mViewPager.removeOnPageChangeListener(this);
        mViewPager.getAdapter().unregisterDataSetObserver(mDataSetObserver);
        mViewPager = null;
    }

    private void addPageIndicators() {
        int numberOfPages = mViewPager.getAdapter().getCount();
        mCurrentSelectedPosition = mViewPager.getCurrentItem();

        removeAllViews();

        int indicatorSideMarginPx = ViewUtils.dpToPx(getContext(), SPACE_BETWEEN_INDICATORS_DP / 2);
        for (int i = 0; i < numberOfPages; i++) {
            ImageView singlePageIndicatorView = new ImageView(getContext());

            singlePageIndicatorView.setBackgroundResource(R.drawable.site_explore_page_indicator);
            singlePageIndicatorView.setAdjustViewBounds(true);

            // Set space on both sides of the indicator.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(indicatorSideMarginPx, 0, indicatorSideMarginPx, 0);
            singlePageIndicatorView.setLayoutParams(lp);

            if (i == mCurrentSelectedPosition) {
                singlePageIndicatorView.setSelected(true);
            }

            addView(singlePageIndicatorView);
        }
    }

}
