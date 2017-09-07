// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.os.SystemClock;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.PageTransition;

import javax.annotation.Nullable;

/**
 * Records stats related to a page visit, such as the time spent on the website, or if the user
 * comes back to the starting point. Use through {@link #record(Tab, Callback)}.
 */
public class NavigationRecorder extends EmptyTabObserver {
    private final Callback<VisitData> mVisitEndCallback;
    private final int mNavigationStackIndex;
    private final WebContentsObserver mWebContentsObserver;

    private long mStartTimeMs;

    /**
     * Sets up visit recording for the provided tab.
     * @param tab Tab where the visit should be recorded
     * @param visitEndCallback The callback where the visit data is sent when it completes.
     */
    public static void record(Tab tab, Callback<VisitData> visitEndCallback) {
        tab.addObserver(new NavigationRecorder(tab, visitEndCallback));
    }

    /** Private because users should not hold to references to the instance. */
    private NavigationRecorder(final Tab tab, Callback<VisitData> visitEndCallback) {
        assert tab.getWebContents() != null;

        mVisitEndCallback = visitEndCallback;

        // onLoadUrl below covers many exit conditions to stop recording but not all,
        // such as navigating back. We therefore stop recording if a navigation stack change
        // indicates we are back to our starting point.
        final NavigationController navController = tab.getWebContents().getNavigationController();
        mNavigationStackIndex = navController.getLastCommittedEntryIndex();
        mWebContentsObserver = new WebContentsObserver() {
            @Override
            public void navigationEntryCommitted() {
                if (mNavigationStackIndex != navController.getLastCommittedEntryIndex()) return;
                endRecording(tab, tab.getUrl());
            }
        };
        tab.getWebContents().addObserver(mWebContentsObserver);

        if (!tab.isHidden()) mStartTimeMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void onShown(Tab tab) {
        if (mStartTimeMs == 0) mStartTimeMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void onHidden(Tab tab) {
        endRecording(tab, null);
    }

    @Override
    public void onDestroyed(Tab tab) {
        endRecording(null, null);
    }

    @Override
    public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
        // End recording if a new URL gets loaded e.g. after entering a new query in
        // the omnibox. This doesn't cover the navigate-back case so we also need to observe
        // changes to WebContent's navigation entries.
        int transitionTypeMask = PageTransition.FROM_ADDRESS_BAR | PageTransition.HOME_PAGE
                | PageTransition.CHAIN_START | PageTransition.CHAIN_END;

        if ((params.getTransitionType() & transitionTypeMask) != 0) endRecording(tab, null);
    }

    private void endRecording(@Nullable Tab removeObserverFromTab, @Nullable String endUrl) {
        if (removeObserverFromTab != null) {
            removeObserverFromTab.removeObserver(this);
            if (removeObserverFromTab.getWebContents() != null) {
                removeObserverFromTab.getWebContents().removeObserver(mWebContentsObserver);
            }
        }

        long visitTimeMs = SystemClock.elapsedRealtime() - mStartTimeMs;
        mVisitEndCallback.onResult(new VisitData(visitTimeMs, endUrl));
    }

    /** Plain holder for the data of a recorded visit. */
    public static class VisitData {
        public final long duration;
        public final String endUrl;

        public VisitData(long duration, String endUrl) {
            this.duration = duration;
            this.endUrl = endUrl;
        }
    }
}
