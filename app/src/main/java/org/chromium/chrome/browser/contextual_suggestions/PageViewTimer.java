// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.webkit.URLUtil;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.Tab.TabHidingType;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.WebContents;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/** Class allowing to measure and report a page view time in UMA. */
public class PageViewTimer {
    @VisibleForTesting
    public static final long SHORT_BUCKET_THRESHOLD_MS = 4 * 1000;
    @VisibleForTesting
    public static final long MEDIUM_BUCKET_THRESHOLD_MS = 180 * 1000;

    /**
     * Note: Because this is used for UMA reporting, these values shouldn't be
     * changed, reused or reordered. Additions should go on the end, and any
     * updates should also be reflected under enums.xml.
     */
    @VisibleForTesting
    @IntDef({DurationBucket.SHORT_CLICK, DurationBucket.MEDIUM_CLICK, DurationBucket.LONG_CLICK})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DurationBucket {
        int SHORT_CLICK = 0; /** Click <= 4 secconds */
        int MEDIUM_CLICK = 1; /** 4 seconds < Click <= 180 seconds */
        int LONG_CLICK = 2; /** 180 seconds < Click */
    }
    private static final int DURATION_BUCKET_COUNT = 3;

    /** Track the navigation source to report the page view time under. */
    @IntDef({NavigationSource.OTHER, NavigationSource.CONTEXTUAL_SUGGESTIONS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NavigationSource {
        int OTHER = 0;
        int CONTEXTUAL_SUGGESTIONS = 1;
    }

    private final TabModelSelectorTabModelObserver mTabModelObserver;
    private final TabObserver mTabObserver;
    private final OverviewModeBehavior mOverviewModeBehavior;

    /** Observer for the tab switcher, can be null. */
    private OverviewModeObserver mOverviewModeObserver;
    /** Currently observed tab. */
    private Tab mCurrentTab;
    /** Last URL loaded in the observed tab. */
    private String mLastUrl;
    /** Start time for the page that is observed. */
    private long mStartTimeMs;
    /** Whether the page is showing anything. */
    private boolean mPageDidPaint;
    /** The source of the navigation. */
    @NavigationSource
    private int mNavigationSource;
    /** Track when the timer is paused, which happens if the tab is hidden. */
    private boolean mIsPaused;
    /** When the timer is paused, track when the pause began. */
    private long mPauseStartTimeMs;
    /** Keep a cumulative duration of page not being visible. */
    private long mPauseDuration;

    public PageViewTimer(TabModelSelector tabModelSelector) {
        this(tabModelSelector, null);
    }

    public PageViewTimer(
            TabModelSelector tabModelSelector, OverviewModeBehavior overviewModeBehavior) {
        mOverviewModeBehavior = overviewModeBehavior;
        if (mOverviewModeBehavior != null) {
            mOverviewModeObserver = new EmptyOverviewModeObserver() {
                @Override
                public void onOverviewModeStartedShowing(boolean showToolbar) {
                    pauseMeasuring();
                }

                @Override
                public void onOverviewModeFinishedHiding() {
                    resumeMeasuring();
                }
            };
            mOverviewModeBehavior.addOverviewModeObserver(mOverviewModeObserver);
        }

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab, @TabSelectionType int type) {
                resumeMeasuring();
            }

            @Override
            public void onHidden(Tab tab, @TabHidingType int type) {
                pauseMeasuring();
            }

            @Override
            public void onUpdateUrl(Tab tab, String url) {
                assert tab == mCurrentTab;

                // In the current implementation, when the user refreshes a page or navigates to a
                // fragment on the page, it is still part of the same page view.
                if (UrlUtilities.urlsMatchIgnoringFragments(url, mLastUrl)) return;

                maybeReportViewTime();
                maybeStartMeasuring(url, !tab.isLoading(), tab.getWebContents());
            }

            @Override
            public void didFirstVisuallyNonEmptyPaint(Tab tab) {
                assert tab == mCurrentTab;
                mPageDidPaint = true;
            }

            @Override
            public void onPageLoadFinished(Tab tab, String url) {
                assert tab == mCurrentTab;
                mPageDidPaint = true;
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                assert tab == mCurrentTab;
                mPageDidPaint = true;
            }
        };

        mTabModelObserver = new TabModelSelectorTabModelObserver(tabModelSelector) {
            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                assert tab != null;
                if (tab == mCurrentTab) return;

                // If the tab switcher is entered, then the same tab is selected again, resume
                // instead of reporting/resetting.
                if (UrlUtilities.urlsMatchIgnoringFragments(tab.getUrl(), mLastUrl)) return;

                maybeReportViewTime();
                switchObserverToTab(tab);
                maybeStartMeasuring(tab.getUrl(), !tab.isLoading(), tab.getWebContents());
            }

            @Override
            public void willCloseTab(Tab tab, boolean animate) {
                assert tab != null;
                if (tab != mCurrentTab) return;

                maybeReportViewTime();
                switchObserverToTab(null);
            }

            @Override
            public void tabRemoved(Tab tab) {
                assert tab != null;
                if (tab != mCurrentTab) return;

                maybeReportViewTime();
                switchObserverToTab(null);
            }
        };
    }

    /** Destroys the PageViewTimer. */
    public void destroy() {
        maybeReportViewTime();
        switchObserverToTab(null);
        mTabModelObserver.destroy();

        // Remove the observer if it's been set.
        if (mOverviewModeBehavior != null) {
            mOverviewModeBehavior.removeOverviewModeObserver(mOverviewModeObserver);
        }
    }

    private void maybeReportViewTime() {
        if (mLastUrl != null && mStartTimeMs != 0 && mPageDidPaint) {
            long durationMs = SystemClock.uptimeMillis() - mStartTimeMs - mPauseDuration;
            reportDurationRaw(durationMs);
            reportDurationBucket(calculateDurationBucket(durationMs));
        }

        // Reporting triggers every time the user would see something new, therefore we clean up
        // reporting state every time.
        mLastUrl = null;
        mStartTimeMs = 0;
        mPageDidPaint = false;
        mIsPaused = false;
        mNavigationSource = NavigationSource.OTHER;
        mPauseDuration = 0;
        mPauseStartTimeMs = 0;
    }

    private void reportDurationRaw(long durationMs) {
        RecordHistogram.recordLongTimesHistogram100(
                "ContextualSuggestions.PageViewTime", durationMs, TimeUnit.MILLISECONDS);
        if (mNavigationSource == NavigationSource.CONTEXTUAL_SUGGESTIONS) {
            RecordHistogram.recordLongTimesHistogram100(
                    "ContextualSuggestions.PageViewTime.ContextualSuggestions", durationMs,
                    TimeUnit.MILLISECONDS);
            return;
        }

        RecordHistogram.recordLongTimesHistogram100(
                "ContextualSuggestions.PageViewTime.Other", durationMs, TimeUnit.MILLISECONDS);
    }

    private void reportDurationBucket(@DurationBucket int durationBucket) {
        if (mNavigationSource == NavigationSource.CONTEXTUAL_SUGGESTIONS) {
            RecordHistogram.recordEnumeratedHistogram(
                    "ContextualSuggestions.PageViewClickLength.ContextualSuggestions",
                    durationBucket, DURATION_BUCKET_COUNT);
            return;
        }

        RecordHistogram.recordEnumeratedHistogram("ContextualSuggestions.PageViewClickLength.Other",
                durationBucket, DURATION_BUCKET_COUNT);
    }

    private void switchObserverToTab(Tab tab) {
        if (mCurrentTab != tab && mCurrentTab != null) {
            mCurrentTab.removeObserver(mTabObserver);
        }

        mCurrentTab = tab;
        if (mCurrentTab != null) {
            mCurrentTab.addObserver(mTabObserver);
        }
    }

    private void maybeStartMeasuring(String url, boolean isLoaded, WebContents webContents) {
        if (!URLUtil.isHttpUrl(url) && !URLUtil.isHttpsUrl(url)) return;

        mLastUrl = url;
        mStartTimeMs = SystemClock.uptimeMillis();
        mPageDidPaint = isLoaded;
        mNavigationSource = getNavigationSource(webContents);
    }

    private void pauseMeasuring() {
        if (mIsPaused) return;

        mIsPaused = true;
        mPauseStartTimeMs = SystemClock.uptimeMillis();
    }

    private void resumeMeasuring() {
        if (!mIsPaused) return;

        mIsPaused = false;
        mPauseDuration += SystemClock.uptimeMillis() - mPauseStartTimeMs;
    }

    @VisibleForTesting
    public @NavigationSource int getNavigationSource(WebContents webContents) {
        NavigationController navigationController = webContents.getNavigationController();
        NavigationEntry navigationEntry = navigationController.getEntryAtIndex(
                navigationController.getLastCommittedEntryIndex());
        if (navigationEntry == null) {
            return NavigationSource.OTHER;
        }

        String referrer = navigationEntry.getReferrerUrl();
        // TODO(fgorski): Share this with other declarations of the referrer.
        if ("https://goto.google.com/explore-on-content-viewer".equals(referrer)) {
            return NavigationSource.CONTEXTUAL_SUGGESTIONS;
        }

        return NavigationSource.OTHER;
    }

    @VisibleForTesting
    public @DurationBucket int calculateDurationBucket(long durationMs) {
        if (durationMs <= SHORT_BUCKET_THRESHOLD_MS) return DurationBucket.SHORT_CLICK;
        if (durationMs <= MEDIUM_BUCKET_THRESHOLD_MS) return DurationBucket.MEDIUM_CLICK;

        return DurationBucket.LONG_CLICK;
    }
}
