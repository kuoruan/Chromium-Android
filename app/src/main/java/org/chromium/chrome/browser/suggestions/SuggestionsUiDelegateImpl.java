// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.support.annotation.Nullable;

import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SuggestionsUiDelegate} implementation.
 */
public class SuggestionsUiDelegateImpl implements SuggestionsUiDelegate {
    private final List<DestructionObserver> mDestructionObservers = new ArrayList<>();
    private final SuggestionsSource mSuggestionsSource;
    private final SuggestionsMetricsReporter mSuggestionsMetricsReporter;
    private final SuggestionsNavigationDelegate mSuggestionsNavigationDelegate;

    private final Profile mProfile;

    private final NativePageHost mHost;

    private FaviconHelper mFaviconHelper;
    private LargeIconBridge mLargeIconBridge;

    private boolean mIsDestroyed;

    public SuggestionsUiDelegateImpl(SuggestionsSource suggestionsSource,
            SuggestionsMetricsReporter metricsReporter,
            SuggestionsNavigationDelegate navigationDelegate, Profile profile,
            NativePageHost host) {
        mSuggestionsSource = suggestionsSource;
        mSuggestionsMetricsReporter = metricsReporter;
        mSuggestionsNavigationDelegate = navigationDelegate;

        mProfile = profile;
        mHost = host;
    }

    @Override
    public void getLocalFaviconImageForURL(
            String url, int size, FaviconImageCallback faviconCallback) {
        if (mIsDestroyed) return;
        getFaviconHelper().getLocalFaviconImageForURL(mProfile, url, size, faviconCallback);
    }

    @Override
    public void getLargeIconForUrl(String url, int size, LargeIconCallback callback) {
        if (mIsDestroyed) return;
        getLargeIconBridge().getLargeIconForUrl(url, size, callback);
    }

    @Override
    public void ensureIconIsAvailable(String pageUrl, String iconUrl, boolean isLargeIcon,
            boolean isTemporary, IconAvailabilityCallback callback) {
        if (mIsDestroyed) return;
        if (mHost.getActiveTab() != null) {
            getFaviconHelper().ensureIconIsAvailable(mProfile,
                    mHost.getActiveTab().getWebContents(), pageUrl, iconUrl, isLargeIcon,
                    isTemporary, callback);
        }
    }

    @Override
    public SuggestionsSource getSuggestionsSource() {
        return mSuggestionsSource;
    }

    @Nullable
    @Override
    public SuggestionsMetricsReporter getMetricsReporter() {
        return mSuggestionsMetricsReporter;
    }

    @Nullable
    @Override
    public SuggestionsNavigationDelegate getNavigationDelegate() {
        return mSuggestionsNavigationDelegate;
    }

    @Override
    public void addDestructionObserver(DestructionObserver destructionObserver) {
        mDestructionObservers.add(destructionObserver);
    }

    @Override
    public boolean isVisible() {
        return mHost.isVisible();
    }

    /** Invalidates the delegate and calls the registered destruction observers. */
    public void onDestroy() {
        assert !mIsDestroyed;

        for (DestructionObserver observer : mDestructionObservers) observer.onDestroy();

        if (mFaviconHelper != null) {
            mFaviconHelper.destroy();
            mFaviconHelper = null;
        }
        if (mLargeIconBridge != null) {
            mLargeIconBridge.destroy();
            mLargeIconBridge = null;
        }
        mIsDestroyed = true;
    }

    /**
     * Utility method to lazily create the {@link FaviconHelper}, and avoid unnecessary native
     * calls in tests.
     */
    private FaviconHelper getFaviconHelper() {
        assert !mIsDestroyed;
        if (mFaviconHelper == null) mFaviconHelper = new FaviconHelper();
        return mFaviconHelper;
    }

    /**
     * Utility method to lazily create the {@link LargeIconBridge}, and avoid unnecessary native
     * calls in tests.
     */
    private LargeIconBridge getLargeIconBridge() {
        assert !mIsDestroyed;
        if (mLargeIconBridge == null) mLargeIconBridge = new LargeIconBridge(mProfile);
        return mLargeIconBridge;
    }
}
