// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.BottomSheet;
import org.chromium.chrome.browser.widget.BottomSheetObserver;

/**
 * Phone specific toolbar that exists at the bottom of the screen.
 */
public class BottomToolbarPhone extends ToolbarPhone implements BottomSheetObserver {
    /** A handle to the bottom sheet. */
    private BottomSheet mBottomSheet;

    /**
     * Whether the end toolbar buttons should be hidden regardless of whether the URL bar is
     * focused.
     */
    private boolean mShouldHideEndToolbarButtons;

    /**
     * This tracks the height fraction of the bottom bar to determine if it is moving up or down.
     */
    private float mLastHeightFraction;

    /**
     * Constructs a BottomToolbarPhone object.
     * @param context The Context in which this View object is created.
     * @param attrs The AttributeSet that was specified with this View.
     */
    public BottomToolbarPhone(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int getProgressBarTopMargin() {
        // In the case where the toolbar is at the bottom of the screen, the progress bar should
        // be at the top of the screen.
        return 0;
    }

    @Override
    protected void triggerUrlFocusAnimation(final boolean hasFocus) {
        super.triggerUrlFocusAnimation(hasFocus);

        if (mBottomSheet == null || !hasFocus) return;

        mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_FULL, true);
    }

    @Override
    public void setBottomSheet(BottomSheet sheet) {
        assert mBottomSheet == null;

        mBottomSheet = sheet;
        getLocationBar().setBottomSheet(mBottomSheet);
        mBottomSheet.addObserver(this);
    }

    @Override
    public boolean shouldIgnoreSwipeGesture() {
        // Only detect swipes if the bottom sheet in the peeking state and not animating.
        return mBottomSheet.getSheetState() != BottomSheet.SHEET_STATE_PEEK
                || mBottomSheet.isRunningSettleAnimation() || super.shouldIgnoreSwipeGesture();
    }

    @Override
    protected void addProgressBarToHierarchy() {
        if (mProgressBar == null) return;

        ViewGroup coordinator = (ViewGroup) getRootView().findViewById(R.id.coordinator);
        coordinator.addView(mProgressBar);
        mProgressBar.setProgressBarContainer(coordinator);
    }

    @Override
    protected boolean shouldHideEndToolbarButtons() {
        return mShouldHideEndToolbarButtons;
    }

    @Override
    public void onSheetOpened() {}

    @Override
    public void onSheetClosed() {}

    @Override
    public void onLoadUrl(String url) {}

    @Override
    public void onTransitionPeekToHalf(float transitionFraction) {
        // TODO(twellington): animate end toolbar button appearance/disappearance.
        if (transitionFraction >= 0.5 && !mShouldHideEndToolbarButtons) {
            mShouldHideEndToolbarButtons = true;
            updateUrlExpansionAnimation();
        } else if (transitionFraction < 0.5 && mShouldHideEndToolbarButtons) {
            mShouldHideEndToolbarButtons = false;
            updateUrlExpansionAnimation();
        }

        boolean buttonsClickable = transitionFraction == 0.f;
        mToggleTabStackButton.setClickable(buttonsClickable);
        mMenuButton.setClickable(buttonsClickable);
    }

    @Override
    public void onSheetOffsetChanged(float heightFraction) {
        boolean isMovingDown = heightFraction < mLastHeightFraction;
        mLastHeightFraction = heightFraction;

        // The only time the omnibox should have focus is when the sheet is fully expanded. Any
        // movement of the sheet should unfocus it.
        if (isMovingDown && getLocationBar().isUrlBarFocused()) {
            getLocationBar().setUrlBarFocus(false);
        }
    }
}
