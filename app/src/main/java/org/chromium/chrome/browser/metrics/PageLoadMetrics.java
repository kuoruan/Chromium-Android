// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.content_public.browser.WebContents;

/**
 * Receives the page load metrics updates from AndroidPageLoadMetricsObserver, and notifies the
 * observers.
 *
 * Threading: everything here must happen on the UI thread.
 */
public class PageLoadMetrics {
    public static final String FIRST_CONTENTFUL_PAINT = "firstContentfulPaint";
    public static final String NAVIGATION_START = "navigationStart";
    public static final String LOAD_EVENT_START = "loadEventStart";
    public static final String DOMAIN_LOOKUP_START = "domainLookupStart";
    public static final String DOMAIN_LOOKUP_END = "domainLookupEnd";
    public static final String CONNECT_START = "connectStart";
    public static final String CONNECT_END = "connectEnd";
    public static final String REQUEST_START = "requestStart";
    public static final String RESPONSE_START = "responseStart";
    public static final String RESPONSE_END = "responseEnd";
    public static final String EFFECTIVE_CONNECTION_TYPE = "effectiveConnectionType";
    public static final String HTTP_RTT = "httpRtt";
    public static final String TRANSPORT_RTT = "transportRtt";

    /** Observer for page load metrics. */
    public interface Observer {
        /**
         * Called when Network Quality Estimate is available, once per page load, when the
         * load is started. This is guaranteed to be called before any other metric event
         * below. If Chromium has just been started, this will likely be determined from
         * the current connection type rather than actual network measurements and so
         * probably similar to what the ConnectivityManager reports.
         *
         * @param webContents the WebContents this metrics is related to.
         * @param effectiveConnectionType the effective connection type, see
         *     net::EffectiveConnectionType.
         * @param httpRttMs an estimate of HTTP RTT, in milliseconds. Will be zero if unknown.
         * @param transportRttMs an estimate of transport RTT, in milliseconds. Will be zero
         *     if unknown.
         */
        public void onNetworkQualityEstimate(WebContents webContents, int effectiveConnectionType,
                long httpRttMs, long transportRttMs);

        /**
         * Called when the first contentful paint page load metric is available.
         *
         * @param webContents the WebContents this metrics is related to.
         * @param navigationStartTick Absolute navigation start time, as TimeTicks.
         * @param firstContentfulPaintMs Time to first contentful paint from navigation start.
         */
        public void onFirstContentfulPaint(
                WebContents webContents, long navigationStartTick, long firstContentfulPaintMs);

        /**
         * Called when the load event start metric is available.
         *
         * @param webContents the WebContents this metrics is related to.
         * @param navigationStartTick Absolute navigation start time, as TimeTicks.
         * @param loadEventStartMs Time to load event start from navigation start.
         */
        public void onLoadEventStart(
                WebContents webContents, long navigationStartTick, long loadEventStartMs);

        /**
         * Called when the main resource is loaded.
         *
         * @param webContents the WebContents this metrics is related to.
         *
         * Remaining parameters are timing information in milliseconds from a common
         * arbitrary point (such as, but not guaranteed to be, system start).
         */
        public void onLoadedMainResource(WebContents webContents, long dnsStartMs, long dnsEndMs,
                long connectStartMs, long connectEndMs, long requestStartMs, long sendStartMs,
                long sendEndMs);
    }

    private static ObserverList<Observer> sObservers;

    /** Adds an observer. */
    public static boolean addObserver(Observer observer) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) sObservers = new ObserverList<>();
        return sObservers.addObserver(observer);
    }

    /** Removes an observer. */
    public static boolean removeObserver(Observer observer) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return false;
        return sObservers.removeObserver(observer);
    }

    @CalledByNative
    static void onNetworkQualityEstimate(WebContents webContents, int effectiveConnectionType,
            long httpRttMs, long transportRttMs) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return;
        for (Observer observer : sObservers) {
            observer.onNetworkQualityEstimate(
                    webContents, effectiveConnectionType, httpRttMs, transportRttMs);
        }
    }

    @CalledByNative
    static void onFirstContentfulPaint(
            WebContents webContents, long navigationStartTick, long firstContentfulPaintMs) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return;
        for (Observer observer : sObservers) {
            observer.onFirstContentfulPaint(
                    webContents, navigationStartTick, firstContentfulPaintMs);
        }
    }

    @CalledByNative
    static void onLoadEventStart(
            WebContents webContents, long navigationStartTick, long loadEventStartMs) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return;
        for (Observer observer : sObservers) {
            observer.onLoadEventStart(webContents, navigationStartTick, loadEventStartMs);
        }
    }

    @CalledByNative
    static void onLoadedMainResource(WebContents webContents, long dnsStartMs, long dnsEndMs,
            long connectStartMs, long connectEndMs, long requestStartMs, long sendStartMs,
            long sendEndMs) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return;
        for (Observer observer : sObservers) {
            observer.onLoadedMainResource(webContents, dnsStartMs, dnsEndMs, connectStartMs,
                    connectEndMs, requestStartMs, sendStartMs, sendEndMs);
        }
    }

    private PageLoadMetrics() {}
}
