// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.touchless;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ViewGroup;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.SingleTabActivity;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.tab.TabState;
import org.chromium.chrome.browser.tab.TabUma.TabCreationState;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;

import java.util.concurrent.TimeUnit;

/**
 * An Activity used to display WebContents on devices that don't support touch.
 */
public class NoTouchActivity extends SingleTabActivity {
    private static final String BUNDLE_TAB_ID = "tabId";

    // Time at which an intent was received and handled.
    private long mIntentHandlingTimeMs;

    /**
     * Internal class which performs the intent handling operations delegated by IntentHandler.
     */
    private class InternalIntentDelegate implements IntentHandler.IntentHandlerDelegate {
        /**
         * Processes a url view intent.
         *
         * @param url The url from the intent.
         */
        @Override
        public void processUrlViewIntent(String url, String referer, String headers,
                @TabOpenType int tabOpenType, String externalAppId, int tabIdToBringToFront,
                boolean hasUserGesture, Intent intent) {
            // TODO(mthiesse): ChromeTabbedActivity records a user Action here, we should do the
            // same.
            assert getActivityTab() != null;

            switch (tabOpenType) {
                case TabOpenType.REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB:
                    if (getActivityTab().getUrl().contentEquals(url)) break;
                    // fall through
                case TabOpenType.BRING_TAB_TO_FRONT: // fall through
                case TabOpenType.REUSE_APP_ID_MATCHING_TAB_ELSE_NEW_TAB: // fall through
                case TabOpenType.OPEN_NEW_TAB: // fall through
                case TabOpenType.CLOBBER_CURRENT_TAB:
                    // TODO(mthiesse): For now, let's just clobber current tab always. Are the other
                    // tab open types meaningful when we only have a single tab?

                    // When we get a view intent, create a new tab to reset history state so that
                    // back returns you to the sender.
                    if (tabOpenType != TabOpenType.BRING_TAB_TO_FRONT) createAndShowTab();
                    Tab currentTab = getActivityTab();
                    TabRedirectHandler.from(currentTab).updateIntent(intent);
                    int transitionType = PageTransition.LINK | PageTransition.FROM_API;
                    LoadUrlParams loadUrlParams = new LoadUrlParams(url);
                    loadUrlParams.setIntentReceivedTimestamp(mIntentHandlingTimeMs);
                    loadUrlParams.setHasUserGesture(hasUserGesture);
                    loadUrlParams.setTransitionType(
                            IntentHandler.getTransitionTypeFromIntent(intent, transitionType));
                    if (referer != null) {
                        loadUrlParams.setReferrer(new Referrer(
                                referer, IntentHandler.getReferrerPolicyFromIntent(intent)));
                    }
                    currentTab.loadUrl(loadUrlParams);
                    break;
                case TabOpenType.OPEN_NEW_INCOGNITO_TAB:
                    // Incognito is unsupported for this Activity.
                    assert false;
                    break;
                default:
                    assert false : "Unknown TabOpenType: " + tabOpenType;
                    break;
            }
        }

        @Override
        public void processWebSearchIntent(String query) {
            assert false;
        }
    }

    @Override
    public void finishNativeInitialization() {
        initializeCompositorContent(new LayoutManager(getCompositorViewHolder()), null /* urlBar */,
                (ViewGroup) findViewById(android.R.id.content), null /* controlContainer */);

        getFullscreenManager().setTab(getActivityTab());
        super.finishNativeInitialization();
    }

    @Override
    public void initializeState() {
        super.initializeState();

        // By this point if we were going to restore a URL from savedInstanceState we would already
        // have done so.
        if (getActivityTab().getUrl().isEmpty()) {
            boolean intentWithEffect = false;
            Intent intent = getIntent();
            mIntentHandlingTimeMs = SystemClock.uptimeMillis();
            if (intent != null) {
                if (!mIntentHandler.shouldIgnoreIntent(intent)) {
                    intentWithEffect = mIntentHandler.onNewIntent(intent);
                }
            }
            if (!intentWithEffect) getTabCreator(false).launchNTP();
        }
        resetSavedInstanceState();
    }

    @Override
    public void onNewIntent(Intent intent) {
        mIntentHandlingTimeMs = SystemClock.uptimeMillis();
        super.onNewIntent(intent);
    }

    @Override
    protected IntentHandlerDelegate createIntentHandlerDelegate() {
        return new InternalIntentDelegate();
    }

    @Override
    protected void initializeToolbar() {}

    @Override
    protected ChromeFullscreenManager createFullscreenManager() {
        return new ChromeFullscreenManager(this, ChromeFullscreenManager.ControlsPosition.NONE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveTabState(outState);
    }

    private void saveTabState(Bundle outState) {
        Tab tab = getActivityTab();
        if (tab == null || tab.getUrl() == null || tab.getUrl().isEmpty()) return;
        long time = SystemClock.elapsedRealtime();
        outState.putInt(BUNDLE_TAB_ID, tab.getId());
        TabState.saveState(outState, tab.getState());
        RecordHistogram.recordTimesHistogram("Android.StrictMode.NoTouchActivitySaveState",
                SystemClock.elapsedRealtime() - time, TimeUnit.MILLISECONDS);
    }

    @Override
    protected Tab restoreTab(Bundle savedInstanceState) {
        int tabId = getSavedInstanceState().getInt(BUNDLE_TAB_ID, Tab.INVALID_TAB_ID);

        if (tabId == Tab.INVALID_TAB_ID) return null;

        TabState tabState = TabState.restoreTabState(savedInstanceState);
        assert tabState != null;

        return new Tab(tabId, Tab.INVALID_TAB_ID, false, getWindowAndroid(),
                TabLaunchType.FROM_RESTORE, TabCreationState.FROZEN_ON_RESTORE, tabState);
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        getFullscreenManager().exitPersistentFullscreenMode();
    }
}
