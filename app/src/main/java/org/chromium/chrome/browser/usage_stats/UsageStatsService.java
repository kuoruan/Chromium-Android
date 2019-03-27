// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.usage_stats;

import android.app.Activity;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.List;

/**
 * Public interface for all usage stats related functionality. All calls to instances of
 * UsageStatsService must be made on the UI thread.
 */
public class UsageStatsService {
    private static UsageStatsService sInstance;

    private EventTracker mEventTracker;
    private SuspensionTracker mSuspensionTracker;
    private TokenTracker mTokenTracker;
    private UsageStatsBridge mBridge;
    private boolean mOptInState;

    /** Get the global instance of UsageStatsService */
    public static UsageStatsService getInstance() {
        if (sInstance == null) {
            sInstance = new UsageStatsService();
        }

        return sInstance;
    }

    @VisibleForTesting
    UsageStatsService() {
        mEventTracker = new EventTracker();
        mSuspensionTracker = new SuspensionTracker();
        mTokenTracker = new TokenTracker();
        Profile profile = Profile.getLastUsedProfile().getOriginalProfile();
        mBridge = new UsageStatsBridge(profile);
        // TODO(pnoland): listen for preference changes so we can notify DW.
    }

    /**
     * Create a {@link PageViewObserver} for the given tab model selector and activity.
     * @param tabModelSelector The tab model selector that should be used to get the current tab
     *         model.
     * @param activity The activity in which page view events are occuring.
     */
    public PageViewObserver createPageViewObserver(
            TabModelSelector tabModelSelector, Activity activity) {
        ThreadUtils.assertOnUiThread();
        return new PageViewObserver(activity, tabModelSelector, mEventTracker, mTokenTracker);
    }

    /** @return Whether the user has authorized DW to access usage stats data. */
    public boolean getOptInState() {
        ThreadUtils.assertOnUiThread();
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();
        return prefServiceBridge.getBoolean(Pref.USAGE_STATS_ENABLED);
    }

    /** Sets the user's opt in state. */
    public void setOptInState(boolean state) {
        ThreadUtils.assertOnUiThread();
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();
        prefServiceBridge.setBoolean(Pref.USAGE_STATS_ENABLED, state);
    }

    /** Query for all events that occurred in the half-open range [start, end) */
    public List<WebsiteEvent> queryWebsiteEvents(long start, long end) {
        ThreadUtils.assertOnUiThread();
        return mEventTracker.queryWebsiteEvents(start, end);
    }

    /** Get all tokens that are currently being tracked. */
    public List<String> getAllTrackedTokens() {
        ThreadUtils.assertOnUiThread();
        return mTokenTracker.getAllTrackedTokens();
    }

    /**
     * Start tracking a full-qualified domain name(FQDN), returning the token used to identify it.
     * If the FQDN is already tracked, this will return the existing token.
     */
    public String startTrackingWebsite(String fqdn) {
        ThreadUtils.assertOnUiThread();
        return mTokenTracker.startTrackingWebsite(fqdn);
    }

    /**
     * Stops tracking the site associated with the given token.
     * If the token was not associated with a site, this does nothing.
     */
    public void stopTrackingToken(String token) {
        ThreadUtils.assertOnUiThread();
        mTokenTracker.stopTrackingToken(token);
    }

    /**
     * Suspend or unsuspend every site in FQDNs, depending on the truthiness of <c>suspended</c>.
     */
    public void setWebsitesSuspended(List<String> fqdns, boolean suspended) {
        ThreadUtils.assertOnUiThread();
        mSuspensionTracker.setWebsitesSuspended(fqdns, suspended);
    }

    /** @return all the sites that are currently suspended. */
    public List<String> getAllSuspendedWebsites() {
        ThreadUtils.assertOnUiThread();
        return mSuspensionTracker.getAllSuspendedWebsites();
    }

    /** @return whether the given site is suspended. */
    public boolean isWebsiteSuspended(String fqdn) {
        ThreadUtils.assertOnUiThread();
        return mSuspensionTracker.isWebsiteSuspended(fqdn);
    }
}