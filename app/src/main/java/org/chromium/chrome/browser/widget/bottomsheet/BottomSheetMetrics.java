// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.support.annotation.IntDef;

import org.chromium.base.metrics.RecordHistogram;
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
            RecordHistogram.recordMediumTimesHistogram("Android.ChromeHome.TimeToFirstOpen",
                    mLastOpenTime - mCreationTime, TimeUnit.MILLISECONDS);
        } else {
            RecordHistogram.recordMediumTimesHistogram(
                    "Android.ChromeHome.TimeBetweenCloseAndNextOpen",
                    mLastOpenTime - mLastCloseTime, TimeUnit.MILLISECONDS);
        }

        recordSheetOpenReason(reason);
    }

    @Override
    public void onSheetClosed(@StateChangeReason int reason) {
        mIsSheetOpen = false;
        recordSheetCloseReason(reason);

        mLastCloseTime = System.currentTimeMillis();
        RecordHistogram.recordMediumTimesHistogram("Android.ChromeHome.DurationOpen",
                mLastCloseTime - mLastOpenTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onSheetStateChanged(int newState) {
        if (newState == BottomSheet.SHEET_STATE_HALF) {
            RecordUserAction.record("Android.ChromeHome.HalfState");
        } else if (newState == BottomSheet.SHEET_STATE_FULL) {
            RecordUserAction.record("Android.ChromeHome.FullState");
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
            RecordUserAction.record("Android.ChromeHome.ShowSuggestions");
        } else if (contentType == BottomSheetContentController.TYPE_DOWNLOADS) {
            RecordUserAction.record("Android.ChromeHome.ShowDownloads");
        } else if (contentType == BottomSheetContentController.TYPE_BOOKMARKS) {
            RecordUserAction.record("Android.ChromeHome.ShowBookmarks");
        } else if (contentType == BottomSheetContentController.TYPE_HISTORY) {
            RecordUserAction.record("Android.ChromeHome.ShowHistory");
        } else if (contentType == BottomSheetContentController.TYPE_INCOGNITO_HOME) {
            RecordUserAction.record("Android.ChromeHome.ShowIncognitoHome");
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
                RecordUserAction.record("Android.ChromeHome.OpenedBySwipe");
                break;
            case StateChangeReason.OMNIBOX_FOCUS:
                metricsReason = OPENED_BY_OMNIBOX_FOCUS;
                RecordUserAction.record("Android.ChromeHome.OpenedByOmnibox");
                break;
            case StateChangeReason.NEW_TAB:
                metricsReason = OPENED_BY_NEW_TAB_CREATION;
                RecordUserAction.record("Android.ChromeHome.OpenedByNTP");
                break;
            case StateChangeReason.EXPAND_BUTTON:
                metricsReason = OPENED_BY_EXPAND_BUTTON;
                RecordUserAction.record("Android.ChromeHome.OpenedByExpandButton");
                break;
            case StateChangeReason.STARTUP:
                metricsReason = OPENED_BY_STARTUP;
                RecordUserAction.record("Android.ChromeHome.OpenedByStartup");
                break;
            case StateChangeReason.NONE:
                // Intentionally empty.
                break;
            default:
                assert false;
        }

        RecordHistogram.recordEnumeratedHistogram(
                "Android.ChromeHome.OpenReason", metricsReason, OPENED_BY_BOUNDARY);
    }

    /**
     * Records the reason the sheet was closed.
     * @param reason The {@link StateChangeReason} that cause the bottom sheet to close.
     */
    private void recordSheetCloseReason(@StateChangeReason int reason) {
        switch (reason) {
            case StateChangeReason.SWIPE:
                RecordUserAction.record("Android.ChromeHome.ClosedBySwipe");
                break;
            case StateChangeReason.BACK_PRESS:
                RecordUserAction.record("Android.ChromeHome.ClosedByBackPress");
                break;
            case StateChangeReason.TAP_SCRIM:
                RecordUserAction.record("Android.ChromeHome.ClosedByTapScrim");
                break;
            case StateChangeReason.NAVIGATION:
                RecordUserAction.record("Android.ChromeHome.ClosedByNavigation");
                break;
            case StateChangeReason.NONE:
                RecordUserAction.record("Android.ChromeHome.Closed");
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
