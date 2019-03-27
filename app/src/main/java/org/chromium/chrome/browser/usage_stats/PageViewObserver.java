// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.usage_stats;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;

import org.chromium.base.Log;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.Tab.TabHidingType;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Class that observes url and tab changes in order to track when browsing stops and starts for each
 * visited fully-qualified domain name (FQDN).
 */
@SuppressLint("NewApi")
public class PageViewObserver {
    private static final String TAG = "PageViewObserver";

    private final Activity mActivity;
    private final TabModelSelectorTabModelObserver mTabModelObserver;
    private final TabModelSelector mTabModelSelector;
    private final TabObserver mTabObserver;
    private final EventTracker mEventTracker;
    private final TokenTracker mTokenTracker;

    private Tab mCurrentTab;
    private String mLastFqdn;

    public PageViewObserver(Activity activity, TabModelSelector tabModelSelector,
            EventTracker eventTracker, TokenTracker tokenTracker) {
        mActivity = activity;
        mTabModelSelector = tabModelSelector;
        mEventTracker = eventTracker;
        mTokenTracker = tokenTracker;
        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab, @TabSelectionType int type) {
                updateUrl(tab.getUrl());
            }

            @Override
            public void onHidden(Tab tab, @TabHidingType int type) {
                updateUrl(null);
            }

            @Override
            public void onUpdateUrl(Tab tab, String url) {
                assert tab == mCurrentTab;

                updateUrl(url);
            }
        };

        mTabModelObserver = new TabModelSelectorTabModelObserver(tabModelSelector) {
            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                assert tab != null;
                if (tab == mCurrentTab) return;

                switchObserverToTab(tab);
                updateUrl(tab.getUrl());
            }

            @Override
            public void willCloseTab(Tab tab, boolean animate) {
                assert tab != null;
                if (tab != mCurrentTab) return;

                updateUrl(null);
                switchObserverToTab(null);
            }

            @Override
            public void tabRemoved(Tab tab) {
                assert tab != null;
                if (tab != mCurrentTab) return;

                updateUrl(null);
                switchObserverToTab(null);
            }
        };

        switchObserverToTab(tabModelSelector.getCurrentTab());
    }

    private void updateUrl(String newUrl) {
        String newFqdn = newUrl == null ? "" : Uri.parse(newUrl).getHost();
        if (mLastFqdn != null && mLastFqdn.equals(newFqdn)) return;

        if (mLastFqdn != null) {
            mEventTracker.addWebsiteEvent(new WebsiteEvent(
                    System.currentTimeMillis(), mLastFqdn, WebsiteEvent.EventType.STOP));
            reportToPlatformIfDomainIsTracked("reportUsageStop", mLastFqdn);
            mLastFqdn = null;
        }

        if (!URLUtil.isHttpUrl(newUrl) && !URLUtil.isHttpsUrl(newUrl)) return;

        mLastFqdn = newFqdn;
        mEventTracker.addWebsiteEvent(new WebsiteEvent(
                System.currentTimeMillis(), mLastFqdn, WebsiteEvent.EventType.START));
        reportToPlatformIfDomainIsTracked("reportUsageStart", mLastFqdn);
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

    private void reportToPlatformIfDomainIsTracked(String reportMethodName, String fqdn) {
        String token = mTokenTracker.getTokenForFqdn(fqdn);
        if (token == null) return;

        try {
            UsageStatsManager instance =
                    (UsageStatsManager) mActivity.getSystemService(Context.USAGE_STATS_SERVICE);
            Method reportMethod = UsageStatsManager.class.getDeclaredMethod(
                    reportMethodName, Activity.class, String.class);

            reportMethod.invoke(instance, mActivity, token);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            Log.e(TAG, "Failed to report to platform API", e);
        }
    }
}
