// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.PopupWindow.OnDismissListener;

import org.chromium.base.Callback;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.toolbar.BottomToolbarPhone;
import org.chromium.chrome.browser.widget.ViewHighlighter;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;
import org.chromium.chrome.browser.widget.textbubble.TextBubble;
import org.chromium.chrome.browser.widget.textbubble.ViewAnchoredTextBubble;
import org.chromium.components.feature_engagement.EventConstants;
import org.chromium.components.feature_engagement.FeatureConstants;
import org.chromium.components.feature_engagement.Tracker;

/** A controller used to display various in-product help bubbles related to Chrome Home. */
public class ChromeHomeIphBubbleController {
    /**
     * The default duration the in-product help bubble should be visible before dismissing
     * automatically.
     */
    private static final int HELP_BUBBLE_TIMEOUT_DURATION_MS = 10000;

    /**
     * The name of the fieldtrial parameter used to determine the timeout duration for the
     * in-product help bubble.
     */
    private static final String HELP_BUBBLE_TIMEOUT_PARAM_NAME = "x_iph-timeout-duration-ms";

    private TextBubble mHelpBubble;
    private LayoutManagerChrome mLayoutManager;
    private BottomToolbarPhone mToolbar;
    private View mControlContainer;
    private ChromeFullscreenManager mFullscreenManager;
    private BottomSheet mBottomSheet;
    private Context mContext;

    /**
     * Create a new ChromeHomeIphBubbleController.
     * @param context The {@link Context} used to retrieve resources.
     * @param toolbar The {@link BottomToolbarPhone} used to determine if the expand button is
     *                showing.
     * @param controlContainer The {@link View} used to position bubbles.
     * @param bottomSheet The {@link BottomSheet} for the activity.
     */
    public ChromeHomeIphBubbleController(Context context, BottomToolbarPhone toolbar,
            View controlContainer, BottomSheet bottomSheet) {
        mContext = context;
        mToolbar = toolbar;
        mControlContainer = controlContainer;
        mBottomSheet = bottomSheet;

        mBottomSheet.addObserver(new EmptyBottomSheetObserver() {
            @Override
            public void onSheetOpened(@StateChangeReason int reason) {
                dismissHelpBubble();

                Tracker tracker = TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile());
                tracker.notifyEvent(EventConstants.BOTTOM_SHEET_EXPANDED);

                if (reason == StateChangeReason.SWIPE) {
                    tracker.notifyEvent(EventConstants.BOTTOM_SHEET_EXPANDED_FROM_SWIPE);
                } else if (reason == StateChangeReason.EXPAND_BUTTON) {
                    tracker.notifyEvent(EventConstants.BOTTOM_SHEET_EXPANDED_FROM_BUTTON);
                } else if (reason == StateChangeReason.OMNIBOX_FOCUS) {
                    tracker.notifyEvent(EventConstants.BOTTOM_SHEET_EXPANDED_FROM_OMNIBOX_FOCUS);
                }
            }

            @Override
            public void onSheetClosed(@StateChangeReason int reason) {
                showColdStartHelpBubble();
            }
        });
    }

    /**
     * @param layoutManager The {@link LayoutManagerChrome} used to show and hide overview mode.
     */
    public void setLayoutManagerChrome(LayoutManagerChrome layoutManager) {
        mLayoutManager = layoutManager;
    }

    /**
     * @param fullscreenManager Chrome's fullscreen manager.
     */
    public void setFullscreenManager(ChromeFullscreenManager fullscreenManager) {
        mFullscreenManager = fullscreenManager;
    }

    /**
     * Show the in-product help bubble for the {@link BottomSheet} if it has not already been shown.
     * This method must be called after the toolbar has had at least one layout pass.
     */
    public void showColdStartHelpBubble() {
        // If FRE is not complete, the FRE screen is likely covering ChromeTabbedActivity so the
        // help bubble should not be shown.
        if (!FirstRunStatus.getFirstRunFlowComplete()) return;

        Tracker tracker = TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile());
        tracker.addOnInitializedCallback(new Callback<Boolean>() {
            @Override
            public void onResult(Boolean success) {
                // Skip showing if the tracker failed to initialize.
                if (!success) return;

                maybeShowHelpBubble(false, false);
            }
        });
    }

    /**
     * Show the in-product help bubble for the {@link BottomSheet} if conditions are right. This
     * method must be called after the toolbar has had at least one layout pass and
     * ChromeFeatureList has been initialized.
     * @param fromMenu Whether the help bubble is being displayed in response to a click on the
     *                 IPH menu header.
     * @param fromPullToRefresh Whether the help bubble is being displayed due to a pull to refresh.
     */
    public void maybeShowHelpBubble(boolean fromMenu, boolean fromPullToRefresh) {
        // Skip showing if the bottom sheet is already open, the UI has not been initialized
        // (indicated by mLayoutManager == null), or the tab switcher is showing.
        if (mBottomSheet.isSheetOpen() || mLayoutManager == null
                || mLayoutManager.overviewVisible()) {
            return;
        }

        // Determine which IPH feature to use for triggering the help UI.
        Tracker tracker = TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile());
        boolean showRefreshIph = fromPullToRefresh
                && tracker.shouldTriggerHelpUI(
                           FeatureConstants.CHROME_HOME_PULL_TO_REFRESH_FEATURE);
        boolean showColdStartIph = !fromMenu && !fromPullToRefresh
                && tracker.shouldTriggerHelpUI(FeatureConstants.CHROME_HOME_EXPAND_FEATURE);
        if (!fromMenu && !showRefreshIph && !showColdStartIph) return;

        // Determine which strings to use.
        boolean showAtTopOfScreen = showRefreshIph
                && ChromeFeatureList.isEnabled(
                           ChromeFeatureList.CHROME_HOME_PULL_TO_REFRESH_IPH_AT_TOP);
        boolean showExpandButtonHelpBubble = !showRefreshIph && mToolbar.isUsingExpandButton();
        View anchorView = showExpandButtonHelpBubble
                ? mControlContainer.findViewById(R.id.expand_sheet_button)
                : mControlContainer;
        int stringId = 0;
        if (showRefreshIph) {
            stringId = showAtTopOfScreen
                    ? R.string.bottom_sheet_pull_to_refresh_help_bubble_accessibility_message
                    : R.string.bottom_sheet_pull_to_refresh_help_bubble_message;
        } else if (showExpandButtonHelpBubble) {
            stringId = R.string.bottom_sheet_accessibility_expand_button_help_bubble_message;
        } else {
            stringId = R.string.bottom_sheet_help_bubble_message;
        }
        int accessibilityStringId = showRefreshIph
                ? R.string.bottom_sheet_pull_to_refresh_help_bubble_accessibility_message
                : stringId;

        // Register an overview mode observer so the bubble can be dismissed if overview mode
        // is shown.
        EmptyOverviewModeObserver overviewModeObserver = new EmptyOverviewModeObserver() {
            @Override
            public void onOverviewModeStartedShowing(boolean showToolbar) {
                dismissHelpBubble();
            }
        };
        mLayoutManager.addOverviewModeObserver(overviewModeObserver);

        // Force the browser controls to stay visible while the help bubble is showing.
        int persistentControlsToken =
                mFullscreenManager.getBrowserVisibilityDelegate().showControlsPersistent();

        // Create the help bubble and setup dismissal behavior.
        View topAnchorView = (View) mBottomSheet.getParent();
        OnLayoutChangeListener topAnchorLayoutChangeListener = new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom) {
                    dismissHelpBubble();
                }
            }
        };

        if (showAtTopOfScreen) {
            mHelpBubble =
                    new TextBubble(mContext, topAnchorView, stringId, accessibilityStringId, false);
            mHelpBubble.setAnchorRect(getTopAnchorRect(topAnchorView));
            topAnchorView.addOnLayoutChangeListener(topAnchorLayoutChangeListener);
        } else {
            mHelpBubble = new ViewAnchoredTextBubble(
                    mContext, anchorView, stringId, accessibilityStringId);
            int inset = mContext.getResources().getDimensionPixelSize(
                    R.dimen.bottom_sheet_help_bubble_inset);
            ((ViewAnchoredTextBubble) mHelpBubble).setInsetPx(0, inset, 0, inset);
        }

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_PERSISTENT_IPH)) {
            int dismissTimeout = ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                    ChromeFeatureList.CHROME_HOME_PERSISTENT_IPH, HELP_BUBBLE_TIMEOUT_PARAM_NAME,
                    HELP_BUBBLE_TIMEOUT_DURATION_MS);
            mHelpBubble.setAutoDismissTimeout(dismissTimeout);
        } else {
            mHelpBubble.setDismissOnTouchInteraction(true);
        }

        mHelpBubble.addOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                mFullscreenManager.getBrowserVisibilityDelegate().hideControlsPersistent(
                        persistentControlsToken);
                mLayoutManager.removeOverviewModeObserver(overviewModeObserver);

                if (fromMenu) {
                    tracker.dismissed(FeatureConstants.CHROME_HOME_MENU_HEADER_FEATURE);
                } else if (fromPullToRefresh) {
                    tracker.dismissed(FeatureConstants.CHROME_HOME_PULL_TO_REFRESH_FEATURE);
                } else {
                    tracker.dismissed(FeatureConstants.CHROME_HOME_EXPAND_FEATURE);
                }

                ViewHighlighter.turnOffHighlight(anchorView);

                if (showAtTopOfScreen) {
                    topAnchorView.removeOnLayoutChangeListener(topAnchorLayoutChangeListener);
                }

                mHelpBubble = null;
            }
        });

        // Highlight the expand button if necessary.
        if (showExpandButtonHelpBubble) {
            ViewHighlighter.turnOnHighlight(anchorView, true);
        }

        // Show the bubble.
        mHelpBubble.show();
    }

    /**
     * @return The bottom sheet's help bubble if it exists.
     */
    @VisibleForTesting
    public @Nullable TextBubble getHelpBubbleForTests() {
        return mHelpBubble;
    }

    /** Dismiss the help bubble if it is not null. */
    private void dismissHelpBubble() {
        if (mHelpBubble != null) mHelpBubble.dismiss();
    }

    /**
     * @param topAnchorView The view used display the IPH bubble when it is shown at the top of the
     *                      screen.
     * @return A {@link Rect} used to anchor the IPH bubble.
     */
    private Rect getTopAnchorRect(View topAnchorView) {
        int[] locationInWindow = new int[2];
        topAnchorView.getLocationInWindow(locationInWindow);
        int centerPoint = locationInWindow[0] + topAnchorView.getWidth() / 2;
        return new Rect(centerPoint, locationInWindow[1], centerPoint, locationInWindow[1]);
    }
}
