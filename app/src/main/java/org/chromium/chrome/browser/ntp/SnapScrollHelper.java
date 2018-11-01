// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;

/**
 * This class handles snap scroll for the search box on a {@link NewTabPage}.
 */
public class SnapScrollHelper {
    private static final long SNAP_SCROLL_DELAY_MS = 30;

    private final NewTabPageManager mManager;
    private final NewTabPageLayout mNewTabPageLayout;
    private final View mView;
    private final Runnable mSnapScrollRunnable;
    private final int mToolbarHeight;
    private final int mSearchBoxTransitionLength;

    private boolean mPendingSnapScroll;
    private int mLastScrollY = -1;

    /**
     * @param manager The {@link NewTabPageManager} to get information about user interactions on
     *                the {@link NewTabPage}.
     * @param newTabPageLayout The {@link NewTabPageLayout} associated with the {@link NewTabPage}.
     * @param view The view on which this class needs to handle snap scroll.
     */
    public SnapScrollHelper(NewTabPageView.NewTabPageManager manager,
            NewTabPageLayout newTabPageLayout, View view) {
        mManager = manager;
        mNewTabPageLayout = newTabPageLayout;
        mView = view;
        mSnapScrollRunnable = new SnapScrollRunnable();

        Resources res = view.getResources();
        mToolbarHeight = res.getDimensionPixelSize(R.dimen.toolbar_height_no_shadow)
                + res.getDimensionPixelSize(R.dimen.toolbar_progress_bar_height);
        mSearchBoxTransitionLength =
                res.getDimensionPixelSize(R.dimen.ntp_search_box_transition_length);

        @SuppressLint("ClickableViewAccessibility")
        View.OnTouchListener onTouchListener = (v, event) -> {
            mView.removeCallbacks(mSnapScrollRunnable);

            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || event.getActionMasked() == MotionEvent.ACTION_UP) {
                mPendingSnapScroll = true;
                mView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
            } else {
                mPendingSnapScroll = false;
            }
            return false;
        };
        mView.setOnTouchListener(onTouchListener);
    }

    /** Update scroll offset and perform snap scroll if necessary. */
    public void handleScroll() {
        int scrollY = mNewTabPageLayout.getScrollDelegate().getVerticalScrollOffset();
        if (mLastScrollY == scrollY) return;

        mLastScrollY = scrollY;
        if (mPendingSnapScroll) {
            mView.removeCallbacks(mSnapScrollRunnable);
            mView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
        }
        mNewTabPageLayout.updateSearchBoxOnScroll();
    }

    /**
     * @param scrollPosition The scroll position that the snap scroll calculation is based on.
     * @return The modified scroll position that accounts for snap scroll.
     */
    @VisibleForTesting
    public int calculateSnapPosition(int scrollPosition) {
        if (mManager.isLocationBarShownInNTP()) {
            // Snap scroll to prevent only part of the toolbar from showing.
            scrollPosition = calculateSnapPositionForRegion(scrollPosition, 0, mToolbarHeight);

            // Snap scroll to prevent resting in the middle of the omnibox transition.
            View fakeBox = mNewTabPageLayout.getSearchBoxView();
            int fakeBoxUpperBound = fakeBox.getTop() + fakeBox.getPaddingTop();
            scrollPosition = calculateSnapPositionForRegion(scrollPosition,
                    fakeBoxUpperBound - mSearchBoxTransitionLength, fakeBoxUpperBound);
        }

        return scrollPosition;
    }

    /**
     * Calculates the position to scroll to in order to move out of a region where {@code mView}
     * should not stay at rest.
     * @param currentScroll the current scroll position.
     * @param regionStart the beginning of the region to scroll out of.
     * @param regionEnd the end of the region to scroll out of.
     * @param flipPoint the threshold used to decide which bound of the region to scroll to.
     * @return the position to scroll to.
     */
    private static int calculateSnapPositionForRegion(
            int currentScroll, int regionStart, int regionEnd, int flipPoint) {
        assert regionStart <= flipPoint;
        assert flipPoint <= regionEnd;

        if (currentScroll < regionStart || currentScroll > regionEnd) return currentScroll;

        if (currentScroll < flipPoint) {
            return regionStart;
        } else {
            return regionEnd;
        }
    }

    /**
     * If {@code mView} is currently scrolled to between regionStart and regionEnd, smooth scroll
     * out of the region to the nearest edge.
     */
    private static int calculateSnapPositionForRegion(
            int currentScroll, int regionStart, int regionEnd) {
        return calculateSnapPositionForRegion(
                currentScroll, regionStart, regionEnd, (regionStart + regionEnd) / 2);
    }

    private class SnapScrollRunnable implements Runnable {
        @Override
        public void run() {
            assert mPendingSnapScroll;
            mPendingSnapScroll = false;

            mNewTabPageLayout.getScrollDelegate().snapScroll();
        }
    }
}
