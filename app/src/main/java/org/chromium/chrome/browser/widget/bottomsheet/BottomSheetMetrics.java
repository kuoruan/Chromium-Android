// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.support.annotation.IntDef;

import org.chromium.base.metrics.CachedMetrics;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;
import org.chromium.components.feature_engagement.EventConstants;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * Records user actions and histograms related to the {@link BottomSheet}.
 */
public class BottomSheetMetrics extends EmptyBottomSheetObserver {
    /**
     * The different ways that the bottom sheet can be opened. This is used to back a UMA
     * histogram and should therefore be treated as append-only.
     */
    @IntDef({OPENED_BY_SWIPE, OPENED_BY_OMNIBOX_FOCUS, OPENED_BY_NEW_TAB_CREATION,
            OPENED_BY_EXPAND_BUTTON, OPENED_BY_STARTUP})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SheetOpenReason {}
    private static final int OPENED_BY_SWIPE = 0;
    private static final int OPENED_BY_OMNIBOX_FOCUS = 1;
    private static final int OPENED_BY_NEW_TAB_CREATION = 2;
    private static final int OPENED_BY_EXPAND_BUTTON = 3;
    private static final int OPENED_BY_STARTUP = 4;
    private static final int OPENED_BY_BOUNDARY = 5;

    private static final CachedMetrics.TimesHistogramSample TIMES_FIRST_OPEN =
            new CachedMetrics.TimesHistogramSample(
                    "Android.ChromeHome.TimeToFirstOpen", TimeUnit.MILLISECONDS);

    private static final CachedMetrics.TimesHistogramSample TIMES_BETWEEN_CLOSE_AND_NEXT_OPEN =
            new CachedMetrics.TimesHistogramSample(
                    "Android.ChromeHome.TimeBetweenCloseAndNextOpen", TimeUnit.MILLISECONDS);

    private static final CachedMetrics.TimesHistogramSample TIMES_DURATION_OPEN =
            new CachedMetrics.TimesHistogramSample(
                    "Android.ChromeHome.DurationOpen", TimeUnit.MILLISECONDS);

    private static final CachedMetrics.EnumeratedHistogramSample ENUMERATED_OPEN_REASON =
            new CachedMetrics.EnumeratedHistogramSample(
                    "Android.ChromeHome.OpenReason", OPENED_BY_BOUNDARY);

    private static final CachedMetrics.ActionEvent ACTION_HALF_STATE =
            new CachedMetrics.ActionEvent("Android.ChromeHome.HalfState");
    private static final CachedMetrics.ActionEvent ACTION_FULL_STATE =
            new CachedMetrics.ActionEvent("Android.ChromeHome.FullState");

    private static final CachedMetrics.ActionEvent ACTION_SHOW_SUGGESTIONS =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ShowSuggestions");
    private static final CachedMetrics.ActionEvent ACTION_SHOW_DOWNLOADS =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ShowDownloads");
    private static final CachedMetrics.ActionEvent ACTION_SHOW_BOOKMARKS =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ShowBookmarks");
    private static final CachedMetrics.ActionEvent ACTION_SHOW_HISTORY =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ShowHistory");
    private static final CachedMetrics.ActionEvent ACTION_SHOW_INCOGNITO_HOME =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ShowIncognitoHome");

    private static final CachedMetrics.ActionEvent ACTION_OPENED_BY_SWIPE =
            new CachedMetrics.ActionEvent("Android.ChromeHome.OpenedBySwipe");
    private static final CachedMetrics.ActionEvent ACTION_OPENED_BY_OMNIBOX_FOCUS =
            new CachedMetrics.ActionEvent("Android.ChromeHome.OpenedByOmnibox");
    private static final CachedMetrics.ActionEvent ACTION_OPENED_BY_NEW_TAB_CREATION =
            new CachedMetrics.ActionEvent("Android.ChromeHome.OpenedByNTP");
    private static final CachedMetrics.ActionEvent ACTION_OPENED_BY_EXPAND_BUTTON =
            new CachedMetrics.ActionEvent("Android.ChromeHome.OpenedByExpandButton");
    private static final CachedMetrics.ActionEvent ACTION_OPENED_BY_STARTUP =
            new CachedMetrics.ActionEvent("Android.ChromeHome.OpenedByStartup");

    private static final CachedMetrics.ActionEvent ACTION_CLOSED_BY_SWIPE =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ClosedBySwipe");
    private static final CachedMetrics.ActionEvent ACTION_CLOSED_BY_BACK_PRESS =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ClosedByBackPress");
    private static final CachedMetrics.ActionEvent ACTION_CLOSED_BY_TAP_SCRIM =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ClosedByTapScrim");
    private static final CachedMetrics.ActionEvent ACTION_CLOSED_BY_NAVIGATION =
            new CachedMetrics.ActionEvent("Android.ChromeHome.ClosedByNavigation");
    private static final CachedMetrics.ActionEvent ACTION_CLOSED =
            new CachedMetrics.ActionEvent("Android.ChromeHome.Closed");

    /** Whether the sheet is currently open. */
    private boolean mIsSheetOpen;

    /** The last {@link BottomSheetContent} that was displayed. */
    private BottomSheetContent mLastContent;

    /** When this class was created. Used as a proxy for when the app was started. */
    private long mCreationTime;

    /** The last time the sheet was opened. */
    private long mLastOpenTime;

    /** The last time the sheet was closed. */
    private long mLastCloseTime;

    public BottomSheetMetrics() {
        mCreationTime = System.currentTimeMillis();
    }

    @Override
    public void onSheetOpened(int reason) {
        mIsSheetOpen = true;

        boolean isFirstOpen = mLastOpenTime == 0;
        mLastOpenTime = System.currentTimeMillis();

        if (isFirstOpen) {
            TIMES_FIRST_OPEN.record(mLastOpenTime - mCreationTime);
        } else {
            TIMES_BETWEEN_CLOSE_AND_NEXT_OPEN.record(mLastOpenTime - mLastCloseTime);
        }

        recordSheetOpenReason(reason);
    }

    @Override
    public void onSheetClosed(@StateChangeReason int reason) {
        mIsSheetOpen = false;

        recordSheetCloseReason(reason);

        mLastCloseTime = System.currentTimeMillis();
        TIMES_DURATION_OPEN.record(mLastCloseTime - mLastOpenTime);
    }

    @Override
    public void onSheetStateChanged(int newState) {
        if (newState == BottomSheet.SHEET_STATE_HALF) {
            ACTION_HALF_STATE.record();
        } else if (newState == BottomSheet.SHEET_STATE_FULL) {
            ACTION_FULL_STATE.record();
        }
    }

    @Override
    public void onSheetContentChanged(BottomSheetContent newContent) {
        // Return early if the sheet content is being set during initialization (previous content
        // is null) or while the sheet is closed (sheet content being reset), so that we only
        // record actions when the user explicitly takes an action.
        if (mLastContent == null || !mIsSheetOpen || newContent == null) {
            mLastContent = newContent;
            return;
        }

        int contentType = newContent.getType();

        if (contentType == BottomSheetContentController.TYPE_SUGGESTIONS) {
            ACTION_SHOW_SUGGESTIONS.record();
        } else if (contentType == BottomSheetContentController.TYPE_DOWNLOADS) {
            ACTION_SHOW_DOWNLOADS.record();
        } else if (contentType == BottomSheetContentController.TYPE_BOOKMARKS) {
            ACTION_SHOW_BOOKMARKS.record();
        } else if (contentType == BottomSheetContentController.TYPE_HISTORY) {
            ACTION_SHOW_HISTORY.record();
        } else if (contentType == BottomSheetContentController.TYPE_INCOGNITO_HOME) {
            ACTION_SHOW_INCOGNITO_HOME.record();
        }

        if (contentType == BottomSheetContentController.TYPE_DOWNLOADS
                || contentType == BottomSheetContentController.TYPE_BOOKMARKS
                || contentType == BottomSheetContentController.TYPE_HISTORY) {
            TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile())
                    .notifyEvent(EventConstants.CHROME_HOME_NON_HOME_CONTENT_SHOWN);
        }

        mLastContent = newContent;
    }

    /**
     * Records the reason the sheet was opened.
     * @param reason The {@link StateChangeReason} that caused the bottom sheet to open.
     */
    public void recordSheetOpenReason(@StateChangeReason int reason) {
        @SheetOpenReason
        int metricsReason = OPENED_BY_SWIPE;
        switch (reason) {
            case StateChangeReason.SWIPE:
                metricsReason = OPENED_BY_SWIPE;
                ACTION_OPENED_BY_SWIPE.record();
                break;
            case StateChangeReason.OMNIBOX_FOCUS:
                metricsReason = OPENED_BY_OMNIBOX_FOCUS;
                ACTION_OPENED_BY_OMNIBOX_FOCUS.record();
                break;
            case StateChangeReason.NEW_TAB:
                metricsReason = OPENED_BY_NEW_TAB_CREATION;
                ACTION_OPENED_BY_NEW_TAB_CREATION.record();
                break;
            case StateChangeReason.EXPAND_BUTTON:
                metricsReason = OPENED_BY_EXPAND_BUTTON;
                ACTION_OPENED_BY_EXPAND_BUTTON.record();
                break;
            case StateChangeReason.STARTUP:
                metricsReason = OPENED_BY_STARTUP;
                ACTION_OPENED_BY_STARTUP.record();
                break;
            case StateChangeReason.NONE:
                // Intentionally empty.
                break;
            default:
                assert false;
        }

        ENUMERATED_OPEN_REASON.record(metricsReason);
    }

    /**
     * Records the reason the sheet was closed.
     * @param reason The {@link StateChangeReason} that cause the bottom sheet to close.
     */
    private void recordSheetCloseReason(@StateChangeReason int reason) {
        switch (reason) {
            case StateChangeReason.SWIPE:
                ACTION_CLOSED_BY_SWIPE.record();
                break;
            case StateChangeReason.BACK_PRESS:
                ACTION_CLOSED_BY_BACK_PRESS.record();
                break;
            case StateChangeReason.TAP_SCRIM:
                ACTION_CLOSED_BY_TAP_SCRIM.record();
                break;
            case StateChangeReason.NAVIGATION:
                ACTION_CLOSED_BY_NAVIGATION.record();
                break;
            case StateChangeReason.NONE:
                ACTION_CLOSED.record();
                break;
            default:
                assert false;
        }
    }

    /**
     * Records that a user navigation instructed the NativePageFactory to create a native page for
     * the NTP. This may occur if the user has NTP URLs in a tab's navigation history.
     */
    public void recordNativeNewTabPageShown() {
        RecordUserAction.record("Android.ChromeHome.NativeNTPShown");
    }

    /**
     * Records that the user tapped the app menu item that triggers the in-product help bubble.
     */
    public void recordInProductHelpMenuItemClicked() {
        RecordUserAction.record("Android.ChromeHome.IPHMenuItemClicked");
    }
}
