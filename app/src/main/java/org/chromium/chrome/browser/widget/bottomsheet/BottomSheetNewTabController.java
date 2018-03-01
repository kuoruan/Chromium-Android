// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.BottomToolbarPhone;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that handles showing and hiding the bottom sheet new tab UI.
 */
public class BottomSheetNewTabController extends EmptyBottomSheetObserver {
    /** Observe events related to the bottom sheet new tab UI. **/
    public interface Observer {
        /** Called when the bottom sheet NTP UI is shown. */
        void onNewTabShown();

        /** Called when the bottom sheet NTP UI is hidden. */
        void onNewTabHidden();
    }

    private final BottomSheet mBottomSheet;
    private final BottomToolbarPhone mToolbar;
    private final ChromeActivity mActivity;
    private final List<Observer> mObservers = new ArrayList<>();

    private LayoutManagerChrome mLayoutManager;
    private OverviewModeObserver mOverviewModeObserver;
    private TabModelSelector mTabModelSelector;

    private boolean mIsShowingNewTabUi;
    private boolean mIsShowingNormalToolbar;
    private boolean mHideOverviewOnClose;
    private boolean mSelectIncognitoModelOnClose;

    /**
     * Creates a new {@link BottomSheetNewTabController}.
     * @param bottomSheet The {@link BottomSheet} that will be opened as part of the new tab UI.
     * @param toolbar The {@link BottomToolbarPhone} that this controller will set state on as part
     *                of the new tab UI.
     * @param activity The {@link ChromeActivity} containing the {@link BottomSheet}.
     */
    public BottomSheetNewTabController(
            BottomSheet bottomSheet, BottomToolbarPhone toolbar, ChromeActivity activity) {
        mBottomSheet = bottomSheet;
        mBottomSheet.addObserver(this);
        mToolbar = toolbar;
        mActivity = activity;
    }

    /**
     * @param observer An {@link Observer} to be notified of events related to the new tab UI.
     */
    public void addObserver(Observer observer) {
        mObservers.add(observer);
    }

    /**
     * @param observer The {@link Observer} to remove.
     */
    public void removeObserver(Observer observer) {
        mObservers.remove(observer);
    }

    /**
     * @param tabModelSelector A TabModelSelector for getting the current tab and activity.
     */
    public void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;
    }

    /**
     * @param layoutManager The {@link LayoutManagerChrome} used to show and hide overview mode.
     */
    public void setLayoutManagerChrome(LayoutManagerChrome layoutManager) {
        assert mLayoutManager == null;

        mLayoutManager = layoutManager;

        mOverviewModeObserver = new EmptyOverviewModeObserver() {
            @Override
            public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {
                if (!mIsShowingNewTabUi
                        || mBottomSheet.getTargetSheetState() == BottomSheet.SHEET_STATE_PEEK) {
                    return;
                }

                // Close the bottom sheet to hide the new tab UI.
                mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
            }
        };
        mLayoutManager.addOverviewModeObserver(mOverviewModeObserver);
    }

    /**
     * Shows the new tab UI.
     * @param isIncognito Whether to display the incognito new tab UI.
     */
    public void displayNewTabUi(boolean isIncognito) {
        displayNewTabUi(isIncognito, R.id.action_home);
    }

    /**
     * Shows the new tab UI with the specified content.
     * @param isIncognito Whether to display the incognito new tab UI.
     * @param actionId The action id of the bottom sheet content to be displayed.
     */
    public void displayNewTabUi(boolean isIncognito, int actionId) {
        mIsShowingNewTabUi = true;
        mHideOverviewOnClose = !mLayoutManager.overviewVisible();
        mSelectIncognitoModelOnClose = mTabModelSelector.isIncognitoSelected()
                && mTabModelSelector.getModel(true).getCount() > 0;

        if (mActivity.getFullscreenManager() != null
                && mActivity.getFullscreenManager().getPersistentFullscreenMode()) {
            mActivity.getFullscreenManager().setPersistentFullscreenMode(false);
        }

        // Show the tab switcher if needed. The overview should be shown before the sheet is opened
        // to ensure the toolbar ends up in the correct state.
        if (!mLayoutManager.overviewVisible()) mLayoutManager.showOverview(true);

        // Transition from the tab switcher toolbar back to the normal toolbar.
        mToolbar.showNormalToolbar();
        mIsShowingNormalToolbar = true;

        // Tell the model that a new tab may be added soon.
        mTabModelSelector.getModel(isIncognito).setIsPendingTabAdd(true);

        // Select the correct model, immediately ending animations so that the previous sheet
        // content is not in use while calling #setIsPendingTabAdd() on previous model.
        if (mTabModelSelector.isIncognitoSelected() != isIncognito) {
            mTabModelSelector.selectModel(isIncognito);
            mBottomSheet.endTransitionAnimations();
            mTabModelSelector.getModel(!isIncognito).setIsPendingTabAdd(false);
        }

        // Select the correct sheet content, immediately ending animations so that the sheet content
        // is not in transition while the sheet is opening.
        mActivity.getBottomSheetContentController().selectItem(actionId);
        mBottomSheet.endTransitionAnimations();

        // Open the sheet if it isn't already open to full height.
        int sheetState = BottomSheet.SHEET_STATE_FULL;
        if (mBottomSheet.getSheetState() != sheetState) {
            mBottomSheet.setSheetState(sheetState, true, StateChangeReason.NEW_TAB);
        }

        for (Observer observer : mObservers) observer.onNewTabShown();
    }

    /**
     * @return Whether the the new tab UI is showing.
     */
    public boolean isShowingNewTabUi() {
        return mIsShowingNewTabUi;
    }

    @Override
    public void onSheetReleased() {
        if (!mIsShowingNewTabUi) return;

        // Start transitioning back to the tab switcher toolbar when the sheet is released to help
        // smooth out animations.
        if (mBottomSheet.getTargetSheetState() == BottomSheet.SHEET_STATE_PEEK) {
            showTabSwitcherToolbarIfNecessary();
        }
    }

    @Override
    public void onSheetOffsetChanged(float heightFraction) {
        if (!mIsShowingNewTabUi) return;

        // Start transitioning to the tab switcher toolbar when the sheet is close to the bottom
        // of the screen.
        if (heightFraction < 0.2f
                && mBottomSheet.getTargetSheetState() == BottomSheet.SHEET_STATE_PEEK) {
            showTabSwitcherToolbarIfNecessary();
        }
    }

    @Override
    public void onSheetClosed(@StateChangeReason int reason) {
        if (!mIsShowingNewTabUi) return;

        mIsShowingNewTabUi = false;

        if (mLayoutManager.overviewVisible()
                && mTabModelSelector.isIncognitoSelected() != mSelectIncognitoModelOnClose
                && (!mSelectIncognitoModelOnClose
                           || mTabModelSelector.getModel(true).getCount() > 0)) {
            mTabModelSelector.selectModel(mSelectIncognitoModelOnClose);
            // End transitions immediately to ensure previous tab model is no longer in use and
            // can be destroyed if necessary.
            mBottomSheet.endTransitionAnimations();
        }

        mHideOverviewOnClose = mHideOverviewOnClose
                && mTabModelSelector.getCurrentModel().getCount() > 0
                && mLayoutManager.overviewVisible();

        mTabModelSelector.getModel(false).setIsPendingTabAdd(false);
        mTabModelSelector.getModel(true).setIsPendingTabAdd(false);

        // Hide the overview after setting pendingTabAdd to false so that the StackLayout animation
        // knows which tab index is being selected and animates the tab stacks correctly.
        if (mHideOverviewOnClose) {
            // TODO(twellington): Ideally we would start hiding the overview sooner. Modifications
            // are needed for the StackLayout to know which tab will be selected before the sheet is
            // closed so that it can animate properly.
            mLayoutManager.hideOverview(true);
        } else {
            showTabSwitcherToolbarIfNecessary();
        }

        mHideOverviewOnClose = false;

        for (Observer observer : mObservers) observer.onNewTabHidden();
    }

    private void showTabSwitcherToolbarIfNecessary() {
        if (mLayoutManager.overviewVisible() && !mHideOverviewOnClose && mIsShowingNormalToolbar) {
            mIsShowingNormalToolbar = false;
            mToolbar.showTabSwitcherToolbar();
        }
    }
}
