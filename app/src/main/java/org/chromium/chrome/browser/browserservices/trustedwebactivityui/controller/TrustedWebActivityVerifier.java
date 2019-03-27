// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsService;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.browserservices.Origin;
import org.chromium.chrome.browser.browserservices.OriginVerifier;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.lifecycle.NativeInitObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Inject;

import dagger.Lazy;

/**
 * Checks whether the currently seen web page belongs to a verified origin and updates the
 * {@link TrustedWebActivityModel} accordingly.
 */
@ActivityScope
public class TrustedWebActivityVerifier implements NativeInitObserver {
    /** The Digital Asset Link relationship used for Trusted Web Activities. */
    private final static int RELATIONSHIP = CustomTabsService.RELATION_HANDLE_ALL_URLS;

    private final Lazy<ClientAppDataRecorder> mClientAppDataRecorder;
    private final CustomTabsConnection mCustomTabsConnection;
    private final CustomTabIntentDataProvider mIntentDataProvider;
    private final ActivityTabProvider mActivityTabProvider;
    private final TabObserverRegistrar mTabObserverRegistrar;
    private final String mClientPackageName;
    private final OriginVerifier mOriginVerifier;

    @Nullable private VerificationState mState;

    private final ObserverList<Runnable> mObservers = new ObserverList<>();

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VERIFICATION_PENDING, VERIFICATION_SUCCESS, VERIFICATION_FAILURE})
    public @interface VerificationStatus {}

    public static final int VERIFICATION_PENDING = 0;
    public static final int VERIFICATION_SUCCESS = 1;
    public static final int VERIFICATION_FAILURE = 2;

    /** Represents the verification state of currently viewed web page. */
    public static class VerificationState {
        public final Origin origin;
        @VerificationStatus
        public final int status;

        public VerificationState(Origin origin, @VerificationStatus int status) {
            this.origin = origin;
            this.status = status;
        }
    }

    /** A {@link TabObserver} that checks whether we are on a verified Origin on page navigation. */
    private final TabObserver mVerifyOnPageLoadObserver = new EmptyTabObserver() {
        @Override
        public void onDidFinishNavigation(Tab tab, String url, boolean isInMainFrame,
                boolean isErrorPage, boolean hasCommitted, boolean isSameDocument,
                boolean isFragmentNavigation, Integer pageTransition, int errorCode,
                int httpStatusCode) {
            if (!hasCommitted || !isInMainFrame) return;
            if (!ChromeFeatureList.isEnabled(ChromeFeatureList.TRUSTED_WEB_ACTIVITY)) {
                assert false : "Shouldn't observe navigation when TWAs are disabled";
                return;
            }

            // This doesn't perform a network request or attempt new verification - it checks to
            // see if a verification already exists for the given inputs.
            handleVerificationResult(isPageOnVerifiedOrigin(url), new Origin(url));
        }
    };

    @Inject
    public TrustedWebActivityVerifier(Lazy<ClientAppDataRecorder> clientAppDataRecorder,
            CustomTabIntentDataProvider intentDataProvider,
            CustomTabsConnection customTabsConnection,
            ActivityLifecycleDispatcher lifecycleDispatcher,
            TabObserverRegistrar tabObserverRegistrar,
            ActivityTabProvider activityTabProvider) {
        mClientAppDataRecorder = clientAppDataRecorder;
        mCustomTabsConnection = customTabsConnection;
        mIntentDataProvider = intentDataProvider;
        mActivityTabProvider = activityTabProvider;
        mTabObserverRegistrar =  tabObserverRegistrar;
        mClientPackageName = customTabsConnection.getClientPackageNameForSession(
                intentDataProvider.getSession());
        assert mClientPackageName != null;

        mOriginVerifier = new OriginVerifier(mClientPackageName, RELATIONSHIP);

        tabObserverRegistrar.registerTabObserver(mVerifyOnPageLoadObserver);
        lifecycleDispatcher.register(this);
    }

    /**
     * @return package name of the client app hosting this Trusted Web Activity.
     */
    public String getClientPackageName() {
        return mClientPackageName;
    }

    /**
     * @return the {@link VerificationState} of the origin we are currently in.
     * Since resolving the origin requires native, returns null before native is loaded.
     */
    @Nullable
    public VerificationState getState() {
        return mState;
    }

    public void addVerificationObserver(Runnable observer) {
        mObservers.addObserver(observer);
    }

    public void removeVerificationObserver(Runnable observer) {
        mObservers.removeObserver(observer);
    }

    @Override
    public void onFinishNativeInitialization() {
        attemptVerificationForInitialUrl(
                mIntentDataProvider.getUrlToLoad(), mActivityTabProvider.getActivityTab());

        // This doesn't belong here, but doesn't deserve a separate class. Do extract it if more
        // PostMessage-related code appears.
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.TRUSTED_WEB_ACTIVITY_POST_MESSAGE)) {
            mCustomTabsConnection.resetPostMessageHandlerForSession(
                    mIntentDataProvider.getSession(), null);
        }
    }

    /** Returns whether the given |url| is on an Origin that the package has been verified for. */
    public boolean isPageOnVerifiedOrigin(String url) {
        return mOriginVerifier.wasPreviouslyVerified(new Origin(url));
    }

    /**
     * Perform verification for the URL that the CustomTabActivity starts on.
     */
    private void attemptVerificationForInitialUrl(String url, Tab tab) {
        Origin origin = new Origin(url);
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.TRUSTED_WEB_ACTIVITY)) {
            mTabObserverRegistrar.unregisterTabObserver(mVerifyOnPageLoadObserver);
            updateState(origin, VERIFICATION_FAILURE);
            return;
        }

        updateState(origin, VERIFICATION_PENDING);
        mOriginVerifier.start((packageName2, origin2, verified, online) -> {
            if (!origin.equals(new Origin(tab.getUrl()))) return;

            handleVerificationResult(verified, origin);
        }, origin);
    }

    private void handleVerificationResult(boolean verified, Origin origin) {
        if (verified) {
            registerClientAppData(origin);
        }
        updateState(origin, verified ? VERIFICATION_SUCCESS : VERIFICATION_FAILURE);
    }

    private void updateState(Origin origin, @VerificationStatus int status) {
        mState = new VerificationState(origin, status);
        for (Runnable observer : mObservers) {
            observer.run();
        }
    }

    /**
     * Register that we have Chrome data relevant to the Client app.
     *
     * We do this here, when the Trusted Web Activity UI is shown instead of in OriginVerifier when
     * verification completes because when an origin is being verified, we don't know whether it is
     * for the purposes of Trusted Web Activities or for Post Message (where this behaviour is not
     * required).
     *
     * Additionally we do it on every page navigation because an app can be verified for more than
     * one Origin, eg:
     * 1) App verifies with https://www.myfirsttwa.com/.
     * 2) App verifies with https://www.mysecondtwa.com/.
     * 3) App launches a TWA to https://www.myfirsttwa.com/.
     * 4) App navigates to https://www.mysecondtwa.com/.
     *
     * At step 2, we don't know why the app is verifying with that origin (it could be for TWAs or
     * for PostMessage). Only at step 4 do we know that Chrome should associate the browsing data
     * for that origin with that app.
     */
    private void registerClientAppData(Origin origin) {
        mClientAppDataRecorder.get().register(mClientPackageName, origin);
    }
}
