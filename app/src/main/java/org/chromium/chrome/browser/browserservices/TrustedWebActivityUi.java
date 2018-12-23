// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices;

import android.support.customtabs.CustomTabsService;

import org.chromium.chrome.browser.fullscreen.BrowserStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;

/**
 * Class to handle the state and logic for CustomTabActivity to do with Trusted Web Activities.
 *
 * Lifecycle: There should be a 1-1 relationship between this class and
 * {@link org.chromium.chrome.browser.customtabs.CustomTabActivity}.
 * Thread safety: All methods on this class should be called on the UI thread.
 */
public class TrustedWebActivityUi {
    // TODO(peconn): Convert this class to use dependency injection, when you do clean up
    // CustomTabActivityComponent and TrustedWebActivityDisclosure.
    /** The Digital Asset Link relationship used for Trusted Web Activities. */
    private final static int RELATIONSHIP = CustomTabsService.RELATION_HANDLE_ALL_URLS;

    private final TrustedWebActivityUiDelegate mDelegate;
    private final TrustedWebActivityDisclosure mDisclosure;
    private final TrustedWebActivityOpenTimeRecorder mOpenTimeRecorder =
            new TrustedWebActivityOpenTimeRecorder();
    private final ClientAppDataRecorder mClientAppDataRecorder;

    private boolean mInTrustedWebActivity = true;

    /**
     * A delegate for embedders to implement to inject information into this class. The reason for
     * using this instead of passing these dependencies into the constructor is because they may not
     * be available at construction.
     */
    public interface TrustedWebActivityUiDelegate {
        /**
         * Provides a {@link BrowserStateBrowserControlsVisibilityDelegate} that is used to
         * temporarily force showing the browser controls when we leave a trusted origin.
         */
        BrowserStateBrowserControlsVisibilityDelegate
            getBrowserStateBrowserControlsVisibilityDelegate();

        /**
         * Provides the package name of the client for verification.
         */
        String getClientPackageName();

        /**
         * Gives the SnackbarManager to use to display the disclosure.
         */
        SnackbarManager getSnackbarManager();
    }

    /**
     * A {@link BrowserControlsVisibilityDelegate} that disallows showing the Browser Controls when
     * we are in a Trusted Web Activity.
     */
    private final BrowserControlsVisibilityDelegate mInTwaVisibilityDelegate =
            new BrowserControlsVisibilityDelegate() {
                @Override
                public boolean canShowBrowserControls() {
                    return !mInTrustedWebActivity;
                }

                @Override
                public boolean canAutoHideBrowserControls() {
                    return true;
                }
            };

    /** A {@link TabObserver} that checks whether we are on a verified Origin on page navigation. */
    private final TabObserver mVerifyOnPageLoadObserver = new EmptyTabObserver() {
        @Override
        public void onDidFinishNavigation(Tab tab, String url, boolean isInMainFrame,
                boolean isErrorPage, boolean hasCommitted, boolean isSameDocument,
                boolean isFragmentNavigation, Integer pageTransition, int errorCode,
                int httpStatusCode) {
            if (!hasCommitted || !isInMainFrame) return;

            String packageName = mDelegate.getClientPackageName();
            assert packageName != null;

            // This doesn't perform a network request or attempt new verification - it checks to
            // see if a verification already exists for the given inputs.
            Origin origin = new Origin(url);
            boolean verified =
                    OriginVerifier.isValidOrigin(packageName, origin, RELATIONSHIP);
            if (verified) registerClientAppData(packageName, origin);
            setTrustedWebActivityMode(verified, tab);
        }
    };


    /** Creates a TrustedWebActivityUi, providing a delegate from the embedder. */
    public TrustedWebActivityUi(TrustedWebActivityUiDelegate delegate,
            TrustedWebActivityDisclosure disclosure, ClientAppDataRecorder clientAppDataRecorder) {
        mDelegate = delegate;
        mClientAppDataRecorder = clientAppDataRecorder;
        mDisclosure = disclosure;
    }

    /**
     * Gets a {@link BrowserControlsVisibilityDelegate} that will hide/show the Custom Tab toolbar
     * on verification/leaving the verified origin.
     */
    public BrowserControlsVisibilityDelegate getBrowserControlsVisibilityDelegate() {
        return mInTwaVisibilityDelegate;
    }

    /**
     * Gets a {@link TabObserver} that watches for navigations and sets whether we are in a Trusted
     * Web Activity accordingly.
     */
    public TabObserver getTabObserver() {
        return mVerifyOnPageLoadObserver;
    }

    /**
     * Shows the disclosure Snackbar if needed on the first Tab. Subsequent navigations will update
     * the disclosure state automatically.
     */
    public void initialShowSnackbarIfNeeded() {
        assert mDelegate.getSnackbarManager() != null;
        assert mDelegate.getClientPackageName() != null;

        // If we have left Trusted Web Activity mode (through onDidFinishNavigation), we don't need
        // to show the Snackbar.
        if (!mInTrustedWebActivity) return;

        mDisclosure.showSnackbarIfNeeded(mDelegate.getSnackbarManager(),
                mDelegate.getClientPackageName());
    }

    /**
     * Perform verification for the URL that the CustomTabActivity starts on.
     */
    public void attemptVerificationForInitialUrl(String url, Tab tab) {
        assert mDelegate.getClientPackageName() != null;

        String packageName = mDelegate.getClientPackageName();
        Origin origin = new Origin(url);

        new OriginVerifier((packageName2, origin2, verified, online) -> {
            if (!origin.equals(new Origin(tab.getUrl()))) return;

            BrowserServicesMetrics.recordTwaOpened();
            if (verified) registerClientAppData(packageName, origin);
            setTrustedWebActivityMode(verified, tab);
        }, packageName, RELATIONSHIP).start(origin);
    }

    /** Notify (for metrics purposes) that the TWA has been resumed. */
    public void onResume() {
        // TODO(peconn): Move this over to LifecycleObserver or something similar once available.
        mOpenTimeRecorder.onResume();
    }

    /** Notify (for metrics purposes) that the TWA has been paused. */
    public void onPause() {
        // TODO(peconn): Move this over to LifecycleObserver or something similar once available.
        mOpenTimeRecorder.onPause();
    }

    /**
     * Updates the UI appropriately for whether or not Trusted Web Activity mode is enabled.
     */
    private void setTrustedWebActivityMode(boolean enabled, Tab tab) {
        if (mInTrustedWebActivity == enabled) return;

        mInTrustedWebActivity = enabled;

        if (enabled) {
            mDisclosure.showSnackbarIfNeeded(mDelegate.getSnackbarManager(),
                    mDelegate.getClientPackageName());

        } else {
            // Force showing the controls for a bit when leaving Trusted Web Activity mode.
            mDelegate.getBrowserStateBrowserControlsVisibilityDelegate().showControlsTransient();
            mDisclosure.dismissSnackbarIfNeeded(mDelegate.getSnackbarManager());
        }

        // Reflect the browser controls update in the Tab.
        tab.updateFullscreenEnabledState();
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
    private void registerClientAppData(String packageName, Origin origin) {
        mClientAppDataRecorder.register(packageName, origin);
    }
}
