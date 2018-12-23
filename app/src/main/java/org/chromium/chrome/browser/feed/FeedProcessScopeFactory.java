// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import android.support.annotation.Nullable;

import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.scope.FeedProcessScope;
import com.google.android.libraries.feed.feedapplifecyclelistener.FeedAppLifecycleListener;
import com.google.android.libraries.feed.host.config.ApplicationInfo;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.DebugBehavior;
import com.google.android.libraries.feed.host.network.NetworkClient;
import com.google.android.libraries.feed.hostimpl.logging.LoggingApiImpl;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.preferences.PrefChangeRegistrar;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.concurrent.Executors;

/** Holds singleton {@link FeedProcessScope} and some of the scope's host implementations. */
public class FeedProcessScopeFactory {
    private static boolean sIsDisableForPolicy =
            !PrefServiceBridge.getInstance().getBoolean(Pref.NTP_ARTICLES_SECTION_ENABLED);
    private static PrefChangeRegistrar sPrefChangeRegistrar;
    private static FeedAppLifecycle sFeedAppLifecycle;
    private static FeedProcessScope sFeedProcessScope;
    private static FeedScheduler sFeedScheduler;
    private static FeedOfflineIndicator sFeedOfflineIndicator;
    private static NetworkClient sTestNetworkClient;
    private static FeedLoggingBridge sFeedLoggingBridge;

    /** @return The shared {@link FeedProcessScope} instance. Null if the Feed is disabled. */
    public static @Nullable FeedProcessScope getFeedProcessScope() {
        if (sFeedProcessScope == null) {
            initialize();
        }
        return sFeedProcessScope;
    }

    /** @return The {@link FeedScheduler} that was given to the {@link FeedProcessScope}. Null if
     * the Feed is disabled. */
    public static @Nullable FeedScheduler getFeedScheduler() {
        if (sFeedScheduler == null) {
            initialize();
        }
        return sFeedScheduler;
    }

    /** @return The {@link FeedOfflineIndicator} that was given to the {@link FeedProcessScope}.
     * Null if the Feed is disabled. */
    public static @Nullable FeedOfflineIndicator getFeedOfflineIndicator() {
        if (sFeedOfflineIndicator == null) {
            initialize();
        }
        return sFeedOfflineIndicator;
    }

    /*
     * @return The global instance of {@link FeedAppLifecycle} for the process.
     *         Null if the Feed is disabled.
     */
    public static @Nullable FeedAppLifecycle getFeedAppLifecycle() {
        if (sFeedAppLifecycle == null) {
            initialize();
        }
        return sFeedAppLifecycle;
    }

    /** @return The {@link FeedLoggingBridge} that was given to the {@link FeedStreamScope}. Null if
     * the Feed is disabled. */
    public static @Nullable FeedLoggingBridge getFeedLoggingBridge() {
        if (sFeedLoggingBridge == null) {
            initialize();
        }
        return sFeedLoggingBridge;
    }

    /**
     * @return Whether the dependencies provided by this class are allowed to be created. The feed
     *         process is disabled if supervised user or enterprise policy has once been added
     *         within the current session.
     */
    public static boolean isFeedProcessEnabled() {
        return !sIsDisableForPolicy
                && PrefServiceBridge.getInstance().getBoolean(Pref.NTP_ARTICLES_SECTION_ENABLED);
    }

    private static void initialize() {
        assert sFeedProcessScope == null && sFeedScheduler == null && sFeedOfflineIndicator == null
                && sFeedAppLifecycle == null && sFeedLoggingBridge == null;
        if (!isFeedProcessEnabled()) return;

        sPrefChangeRegistrar = new PrefChangeRegistrar();
        sPrefChangeRegistrar.addObserver(Pref.NTP_ARTICLES_SECTION_ENABLED,
                FeedProcessScopeFactory::articlesEnabledPrefChange);

        Profile profile = Profile.getLastUsedProfile().getOriginalProfile();
        Configuration configHostApi = FeedConfiguration.createConfiguration();

        FeedSchedulerBridge schedulerBridge = new FeedSchedulerBridge(profile);
        sFeedScheduler = schedulerBridge;
        FeedAppLifecycleListener lifecycleListener =
                new FeedAppLifecycleListener(new ThreadUtils());
        // TODO(gangwu): Getting real build info if possible.
        ApplicationInfo applicationInfo =
                new ApplicationInfo.Builder(ContextUtils.getApplicationContext())
                        .setAppType(ApplicationInfo.AppType.CHROME)
                        .build();
        FeedContentStorage contentStorage = new FeedContentStorage(profile);
        FeedJournalStorage journalStorage = new FeedJournalStorage(profile);
        NetworkClient networkClient = sTestNetworkClient == null ?
            new FeedNetworkBridge(profile) : sTestNetworkClient;
        sFeedLoggingBridge = new FeedLoggingBridge(profile);
        sFeedProcessScope = new FeedProcessScope
                                    .Builder(configHostApi, Executors.newSingleThreadExecutor(),
                                            new LoggingApiImpl(), networkClient, schedulerBridge,
                                            lifecycleListener, DebugBehavior.SILENT,
                                            ContextUtils.getApplicationContext(), applicationInfo)
                                    .setContentStorage(contentStorage)
                                    .setJournalStorage(journalStorage)
                                    .build();
        schedulerBridge.initializeFeedDependencies(
                sFeedProcessScope.getRequestManager(), sFeedProcessScope.getSessionManager());

        sFeedOfflineIndicator =
                new FeedOfflineBridge(profile, sFeedProcessScope.getKnownContentApi());

        sFeedAppLifecycle = new FeedAppLifecycle(sFeedProcessScope.getAppLifecycleListener(),
                new FeedLifecycleBridge(profile), sFeedScheduler);
    }

    /**
     * Creates a {@link FeedProcessScope} using the provided host implementations. Call {@link
     * #clearFeedProcessScopeForTesting()} to reset the FeedProcessScope after testing is complete.
     *
     * @param feedScheduler A {@link FeedScheduler} to use for testing.
     * @param networkClient A {@link NetworkClient} to use for testing.
     * @param feedOfflineIndicator A {@link FeedOfflineIndicator} to use for testing.
     */
    @VisibleForTesting
    static void createFeedProcessScopeForTesting(FeedScheduler feedScheduler,
            NetworkClient networkClient, FeedOfflineIndicator feedOfflineIndicator,
            FeedAppLifecycle feedAppLifecycle, FeedAppLifecycleListener lifecycleListener) {
        Configuration configHostApi = FeedConfiguration.createConfiguration();
        sFeedScheduler = feedScheduler;
        ApplicationInfo applicationInfo =
                new ApplicationInfo.Builder(ContextUtils.getApplicationContext()).build();

        sFeedProcessScope = new FeedProcessScope
                                    .Builder(configHostApi, Executors.newSingleThreadExecutor(),
                                            new LoggingApiImpl(), networkClient, sFeedScheduler,
                                            lifecycleListener, DebugBehavior.SILENT,
                                            ContextUtils.getApplicationContext(), applicationInfo)
                                    .build();
        sFeedOfflineIndicator = feedOfflineIndicator;
        sFeedAppLifecycle = feedAppLifecycle;
    }

    /** Use supplied NetworkClient instead of real one, for tests. */
    @VisibleForTesting
    public static void setTestNetworkClient(NetworkClient client) {
        if (client == null) {
            sTestNetworkClient = null;
        } else if (sFeedProcessScope == null) {
            sTestNetworkClient = client;
        } else {
            throw(new IllegalStateException(
                    "TestNetworkClient can not be set after FeedProcessScope has initialized."));
        }
    }

    /** Resets the FeedProcessScope after testing is complete. */
    @VisibleForTesting
    static void clearFeedProcessScopeForTesting() {
        destroy();
    }

    private static void articlesEnabledPrefChange() {
        // Should only be subscribed while it was enabled. A change should mean articles are now
        // disabled.
        assert !PrefServiceBridge.getInstance().getBoolean(Pref.NTP_ARTICLES_SECTION_ENABLED);
        sIsDisableForPolicy = true;
        destroy();
    }

    /** Clears out all static state. */
    private static void destroy() {
        if (sPrefChangeRegistrar != null) {
            sPrefChangeRegistrar.removeObserver(Pref.NTP_ARTICLES_SECTION_ENABLED);
            sPrefChangeRegistrar.destroy();
            sPrefChangeRegistrar = null;
        }
        if (sFeedProcessScope != null) {
            sFeedProcessScope.onDestroy();
            sFeedProcessScope = null;
        }
        if (sFeedScheduler != null) {
            sFeedScheduler.destroy();
            sFeedScheduler = null;
        }
        if (sFeedOfflineIndicator != null) {
            sFeedOfflineIndicator.destroy();
            sFeedOfflineIndicator = null;
        }
        if (sFeedAppLifecycle != null) {
            sFeedAppLifecycle.destroy();
            sFeedAppLifecycle = null;
        }
        if (sFeedLoggingBridge != null) {
            sFeedLoggingBridge.destroy();
            sFeedLoggingBridge = null;
        }
    }
}
