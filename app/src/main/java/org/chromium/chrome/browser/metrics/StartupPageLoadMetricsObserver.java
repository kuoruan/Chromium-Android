// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.content_public.browser.WebContents;

/**
 * Page load metrics observer for the first navigation after the application start.
 */
public class StartupPageLoadMetricsObserver implements PageLoadMetrics.Observer {
    private final static long NO_NAVIGATION_ID = -1;

    private long mNavigationId = NO_NAVIGATION_ID;
    private boolean mShouldRecordHistograms;

    @Override
    public void onNewNavigation(WebContents webContents, long navigationId) {
        if (mNavigationId != NO_NAVIGATION_ID) return;

        mNavigationId = navigationId;
        mShouldRecordHistograms = UmaUtils.isRunningApplicationStart();
    }

    @Override
    public void onNetworkQualityEstimate(WebContents webContents, long navigationId,
            int effectiveConnectionType, long httpRttMs, long transportRttMs) {}

    @Override
    public void onFirstContentfulPaint(WebContents webContents, long navigationId,
            long navigationStartTick, long firstContentfulPaintMs) {
        if (navigationId != mNavigationId || !mShouldRecordHistograms) return;

        UmaUtils.recordFirstContentfulPaint(navigationStartTick / 1000 + firstContentfulPaintMs);
    }

    @Override
    public void onLoadEventStart(WebContents webContents, long navigationId,
            long navigationStartTick, long loadEventStartMs) {}

    @Override
    public void onLoadedMainResource(WebContents webContents, long navigationId, long dnsStartMs,
            long dnsEndMs, long connectStartMs, long connectEndMs, long requestStartMs,
            long sendStartMs, long sendEndMs) {}
}
