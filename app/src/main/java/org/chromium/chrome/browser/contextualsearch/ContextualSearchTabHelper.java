// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.app.Activity;
import android.view.ContextMenu;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.net.NetworkChangeNotifier;

/**
 * Manages the activation and gesture listeners for ContextualSearch on a given tab.
 */
public class ContextualSearchTabHelper
        extends EmptyTabObserver implements NetworkChangeNotifier.ConnectionTypeObserver {
    /** The Tab that this helper tracks. */
    private final Tab mTab;

    /**
     * Notification handler for Contextual Search events.
     */
    private TemplateUrlServiceObserver mTemplateUrlObserver;

    /**
     * The current ContentViewCore for the Tab which this helper is monitoring.
     */
    private ContentViewCore mBaseContentViewCore;

    /**
     * The GestureListener used for handling events from the current ContentViewCore.
     */
    private GestureStateListener mGestureStateListener;

    /**
     * Manages incoming calls to Smart Select when available, for the current mBaseContentViewCore.
     */
    private SelectionClientManager mSelectionClientManager;

    private long mNativeHelper;

    /**
     * Creates a contextual search tab helper for the given tab.
     * @param tab The tab whose contextual search actions will be handled by this helper.
     */
    public static void createForTab(Tab tab) {
        new ContextualSearchTabHelper(tab);
    }

    /**
     * Constructs a Tab helper that can enable and disable Contextual Search based on Tab activity.
     * @param tab The {@link Tab} to track with this helper.
     */
    private ContextualSearchTabHelper(Tab tab) {
        mTab = tab;
        tab.addObserver(this);
        // Connect to a network, unless under test.
        if (NetworkChangeNotifier.isInitialized()) {
            NetworkChangeNotifier.addConnectionTypeObserver(this);
        }
    }

    // ============================================================================================
    // EmptyTabObserver overrides.
    // ============================================================================================

    @Override
    public void onPageLoadStarted(Tab tab, String url) {
        updateHooksForNewContentViewCore(tab);
        ContextualSearchManager manager = getContextualSearchManager();
        if (manager != null) manager.onBasePageLoadStarted();
    }

    @Override
    public void onContentChanged(Tab tab) {
        // Native initialization happens after a page loads or content is changed to ensure profile
        // is initialized.
        if (mNativeHelper == 0) {
            mNativeHelper = nativeInit(tab.getProfile());
        }
        if (mTemplateUrlObserver == null) {
            mTemplateUrlObserver =
                    new TemplateUrlServiceObserver() {
                        @Override
                        public void onTemplateURLServiceChanged() {
                            updateContextualSearchHooks(mBaseContentViewCore);
                        }
                    };
            TemplateUrlService.getInstance().addObserver(mTemplateUrlObserver);
        }
        updateHooksForNewContentViewCore(tab);
    }

    @Override
    public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
        updateHooksForNewContentViewCore(tab);
    }

    @Override
    public void onDestroyed(Tab tab) {
        if (mNativeHelper != 0) {
            nativeDestroy(mNativeHelper);
            mNativeHelper = 0;
        }
        if (mTemplateUrlObserver != null) {
            TemplateUrlService.getInstance().removeObserver(mTemplateUrlObserver);
        }
        if (NetworkChangeNotifier.isInitialized()) {
            NetworkChangeNotifier.removeConnectionTypeObserver(this);
        }
        removeContextualSearchHooks(mBaseContentViewCore);
        mBaseContentViewCore = null;
        mSelectionClientManager = null;
        mGestureStateListener = null;
    }

    @Override
    public void onToggleFullscreenMode(Tab tab, boolean enable) {
        ContextualSearchManager manager = getContextualSearchManager();
        if (manager != null) {
            manager.hideContextualSearch(StateChangeReason.UNKNOWN);
        }
    }

    @Override
    public void onReparentingFinished(Tab tab) {
        updateHooksForNewContentViewCore(tab);
    }

    @Override
    public void onContextMenuShown(Tab tab, ContextMenu menu) {
        ContextualSearchManager manager = getContextualSearchManager();
        if (manager != null) {
            manager.onContextMenuShown();
        }
    }

    // ============================================================================================
    // NetworkChangeNotifier.ConnectionTypeObserver overrides.
    // ============================================================================================

    @Override
    public void onConnectionTypeChanged(int connectionType) {
        updateContextualSearchHooks(mBaseContentViewCore);
    }

    // ============================================================================================
    // Private helpers.
    // ============================================================================================

    /**
     * Should be called whenever the Tab's ContentViewCore may have changed. Removes hooks from the
     * existing ContentViewCore, if necessary, and then adds hooks for the new ContentViewCore.
     * @param tab The current tab.
     */
    private void updateHooksForNewContentViewCore(Tab tab) {
        ContentViewCore currentContentViewCore = tab.getActiveContentViewCore();
        if (currentContentViewCore != mBaseContentViewCore) {
            removeContextualSearchHooks(mBaseContentViewCore);
            mBaseContentViewCore = currentContentViewCore;
            if (mBaseContentViewCore != null) {
                mSelectionClientManager = new SelectionClientManager(mBaseContentViewCore);
            } else {
                mSelectionClientManager = null;
            }
            updateContextualSearchHooks(mBaseContentViewCore);
        }
    }

    /**
     * Updates the Contextual Search hooks, adding or removing them depending on whether it is
     * currently active.  If the current tab's {@link ContentViewCore} may have changed, call
     * {@link #updateHooksForNewContentViewCore(Tab)} instead.
     * @param cvc The content view core to attach the gesture state listener to.
     */
    private void updateContextualSearchHooks(ContentViewCore cvc) {
        if (cvc == null) return;

        if (isContextualSearchActive(cvc)) {
            addContextualSearchHooks(cvc);
        } else {
            removeContextualSearchHooks(cvc);
        }
    }

    /**
     * Adds Contextual Search hooks for its client and listener to the given content view core.
     * @param cvc The content view core to attach the gesture state listener to.
     */
    private void addContextualSearchHooks(ContentViewCore cvc) {
        ContextualSearchManager contextualSearchManager = getContextualSearchManager();
        if (mGestureStateListener == null && contextualSearchManager != null) {
            mGestureStateListener = contextualSearchManager.getGestureStateListener();
            cvc.addGestureStateListener(mGestureStateListener);

            // If we needed to add our listener, we also need to add our selection client.
            cvc.setSelectionClient(mSelectionClientManager.addContextualSearchSelectionClient(
                    contextualSearchManager.getContextualSearchSelectionClient()));
            contextualSearchManager.suppressContextualSearchForSmartSelection(
                    mSelectionClientManager.isSmartSelectionEnabledInChrome());
        }
    }

    /**
     * Removes Contextual Search hooks for its client and listener from the given content view core.
     * @param cvc The content view core to detach the gesture state listener from.
     */
    private void removeContextualSearchHooks(ContentViewCore cvc) {
        if (cvc == null) return;

        if (mGestureStateListener != null) {
            cvc.removeGestureStateListener(mGestureStateListener);
            mGestureStateListener = null;

            // If we needed to remove our listener, we also need to remove our selection client.
            cvc.setSelectionClient(mSelectionClientManager.removeContextualSearchSelectionClient());
        }
    }

    /**
     * @return whether Contextual Search is enabled and active in this tab.
     */
    private boolean isContextualSearchActive(ContentViewCore cvc) {
        ContextualSearchManager manager = getContextualSearchManager();
        if (manager == null) return false;

        return !cvc.getWebContents().isIncognito()
                && FirstRunStatus.getFirstRunFlowComplete()
                && !PrefServiceBridge.getInstance().isContextualSearchDisabled()
                && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()
                && !LocaleManager.getInstance().needToCheckForSearchEnginePromo()
                // Svelte and Accessibility devices are incompatible with the first-run flow and
                // Talkback has poor interaction with tap to search (see http://crbug.com/399708 and
                // http://crbug.com/396934).
                && !manager.isRunningInCompatibilityMode()
                && !(mTab.isShowingErrorPage() || mTab.isShowingInterstitialPage())
                && isDeviceOnline(manager);
    }

    /**
     * @return Whether the device is online, or we have disabled online-detection.
     */
    private boolean isDeviceOnline(ContextualSearchManager manager) {
        if (ContextualSearchFieldTrial.isOnlineDetectionDisabled()) return true;

        return manager.isDeviceOnline();
    }

    /**
     * @return the Contextual Search manager.
     */
    private ContextualSearchManager getContextualSearchManager() {
        Activity activity = mTab.getWindowAndroid().getActivity().get();
        if (activity instanceof ChromeActivity) {
            return ((ChromeActivity) activity).getContextualSearchManager();
        }
        return null;
    }

    // ============================================================================================
    // Native support.
    // ============================================================================================

    @CalledByNative
    private void onContextualSearchPrefChanged() {
        updateContextualSearchHooks(mBaseContentViewCore);

        ContextualSearchManager manager = getContextualSearchManager();
        if (manager != null) {
            boolean isEnabled = !PrefServiceBridge.getInstance().isContextualSearchDisabled()
                    && !PrefServiceBridge.getInstance().isContextualSearchUninitialized();
            manager.onContextualSearchPrefChanged(isEnabled);
        }
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeContextualSearchTabHelper);
}
