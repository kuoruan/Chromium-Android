// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.content;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Browser;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;
import android.view.Window;

import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ActivityTabProvider.HintlessActivityTabObserver;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.ServiceTabLauncher;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.customtabs.CustomTabDelegateFactory;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider.LaunchSourceType;
import org.chromium.chrome.browser.customtabs.CustomTabNavigationEventObserver;
import org.chromium.chrome.browser.customtabs.CustomTabObserver;
import org.chromium.chrome.browser.customtabs.CustomTabTabPersistencePolicy;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.customtabs.FirstMeaningfulPaintObserver;
import org.chromium.chrome.browser.customtabs.PageLoadMetricsObserver;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.lifecycle.InflationObserver;
import org.chromium.chrome.browser.lifecycle.NativeInitObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.tabmodel.AsyncTabParams;
import org.chromium.chrome.browser.tabmodel.AsyncTabParamsManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.browser.tabmodel.TabReparentingParams;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.PageTransition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Inject;

import dagger.Lazy;

/**
 * Directly works with the {@link Tab}, {@link WebContents} and related objects in the context of
 * a Custom Tab activity. This includes creating or retrieving existing instances of Tab and
 * WebContents, initializing them, and loading urls in them. Loads the url received in the Intent
 * as soon as possible, and also executes further requests to load urls (see {@link #loadUrlInTab}).
 */
@ActivityScope
public class CustomTabActivityTabController implements InflationObserver, NativeInitObserver {
    // For CustomTabs.WebContentsStateOnLaunch, see histograms.xml. Append only.
    @IntDef({WebContentsState.NO_WEBCONTENTS, WebContentsState.PRERENDERED_WEBCONTENTS,
            WebContentsState.SPARE_WEBCONTENTS, WebContentsState.TRANSFERRED_WEBCONTENTS})
    @Retention(RetentionPolicy.SOURCE)
    private @interface WebContentsState {
        int NO_WEBCONTENTS = 0;

        int PRERENDERED_WEBCONTENTS = 1;
        int SPARE_WEBCONTENTS = 2;
        int TRANSFERRED_WEBCONTENTS = 3;
        int NUM_ENTRIES = 4;
    }

    private final Lazy<CustomTabDelegateFactory> mCustomTabDelegateFactory;
    private final ChromeActivity mActivity;
    private final CustomTabsConnection mConnection;
    private final CustomTabIntentDataProvider mIntentDataProvider;
    private final Lazy<TabContentManager> mTabContentManager;
    private final TabObserverRegistrar mTabObserverRegistrar;
    private final Lazy<CompositorViewHolder> mCompositorViewHolder;
    private final WarmupManager mWarmupManager;
    private final CustomTabTabPersistencePolicy mTabPersistencePolicy;
    private final CustomTabActivityTabFactory mTabFactory;
    private final Lazy<CustomTabObserver> mCustomTabObserver;
    private final WebContentsFactory mWebContentsFactory;

    @Nullable
    private final CustomTabsSessionToken mSession;
    private final Intent mIntent;

    private final ObserverList<Observer> mObservers = new ObserverList<>();

    private boolean mHasCreatedTabEarly;

    private CustomTabNavigationEventObserver mTabNavigationEventObserver;

    @Nullable
    private String mSpeculatedUrl;
    private boolean mUsingHiddenTab;

    // This boolean is used to do a hack in navigation history for hidden tab loads with
    // unmatching fragments.
    private boolean mIsFirstLoad;

    // Currently managed tab, see comment to getTab().
    // Should be updated only via setTab method in order to notify observers.
    @Nullable
    private Tab mTab;

    @Inject
    public CustomTabActivityTabController(ChromeActivity activity,
            Lazy<CustomTabDelegateFactory> customTabDelegateFactory,
            CustomTabsConnection connection, CustomTabIntentDataProvider intentDataProvider,
            Lazy<TabContentManager> tabContentManager, ActivityTabProvider tabProvider,
            TabObserverRegistrar tabObserverRegistrar,
            Lazy<CompositorViewHolder> compositorViewHolder,
            ActivityLifecycleDispatcher lifecycleDispatcher, WarmupManager warmupManager,
            CustomTabTabPersistencePolicy persistencePolicy, CustomTabActivityTabFactory tabFactory,
            Lazy<CustomTabObserver> customTabObserver, WebContentsFactory webContentsFactory) {
        mCustomTabDelegateFactory = customTabDelegateFactory;
        mActivity = activity;
        mConnection = connection;
        mIntentDataProvider = intentDataProvider;
        mTabContentManager = tabContentManager;
        mTabObserverRegistrar = tabObserverRegistrar;
        mCompositorViewHolder = compositorViewHolder;
        mWarmupManager = warmupManager;
        mTabPersistencePolicy = persistencePolicy;
        mTabFactory = tabFactory;
        mCustomTabObserver = customTabObserver;
        mWebContentsFactory = webContentsFactory;

        mSession = mIntentDataProvider.getSession();
        mIntent = mIntentDataProvider.getIntent();
        mSpeculatedUrl = mConnection.getSpeculatedUrl(mSession);

        tabProvider.addObserverAndTrigger(new HintlessActivityTabObserver() {
            @Override
            public void onActivityTabChanged(Tab tab) {
                setTab(tab);
            }
        });

        lifecycleDispatcher.register(this);
    }

    /** Adds an {@link Observer} */
    public void addObserver(Observer observer) {
        mObservers.addObserver(observer);
    }

    /** Removes an {@link Observer} */
    public void removeObserver(Observer observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Returns tab currently managed by the Custom Tab activity.
     *
     * The difference from {@link ActivityTabProvider#getActivityTab()} is that we may have acquired
     * a hidden tab (see {@link CustomTabsConnection#takeHiddenTab}), which is not yet added to a
     * {@link TabModel}. In that case this method returns the hidden tab, and ActivityTabProvider
     * returns null.
     *
     * During reparenting (i.e. after a call to {@link #detachAndStartReparenting}), both this
     * method and ActivityTabProvider return null.
     */
    @Nullable
    public Tab getTab() {
        return mTab;
    }

    /** @return whether allocating a child connection is needed during native initialization. */
    public boolean shouldAllocateChildConnection() {
        return !mHasCreatedTabEarly && !hasSpeculated() && !mWarmupManager.hasSpareWebContents();
    }

    /**
     *  @return whether tab was created before finishing native init, and that tab has finished
     *  loading.
     */
    public boolean earlyCreatedTabIsReady() {
        return mHasCreatedTabEarly && mTab != null && !mTab.isLoading();
    }

    /**
     * Loads the current tab with the given load params while taking client
     * referrer and extra headers into account.
     */
    public void loadUrlInTab(final LoadUrlParams params, long timeStamp) {
        if (mTab == null) {
            assert false;
            return;
        }
        String originalUrl = mIntentDataProvider.getUrlToLoad();

        boolean isFirstLoad = mIsFirstLoad;
        mIsFirstLoad = false;

        // The following block is a hack that deals with urls preloaded with
        // the wrong fragment. Does an extra pageload and replaces history.
        if (hasSpeculated() && isFirstLoad
                && UrlUtilities.urlsFragmentsDiffer(mSpeculatedUrl, originalUrl)) {
            params.setShouldReplaceCurrentEntry(true);
        }

        mCustomTabObserver.get().trackNextPageLoadFromTimestamp(mTab, timeStamp);

        // Manually generating metrics in case the hidden tab has completely finished loading.
        String url = params.getUrl();
        if (mUsingHiddenTab && !mTab.isLoading() && !mTab.isShowingErrorPage()) {
            mCustomTabObserver.get().onPageLoadStarted(mTab, url);
            mCustomTabObserver.get().onPageLoadFinished(mTab, url);
            mTabNavigationEventObserver.onPageLoadStarted(mTab, url);
            mTabNavigationEventObserver.onPageLoadFinished(mTab, url);
        }

        // No actual load to do if tab already has the exact correct url.
        if (TextUtils.equals(mSpeculatedUrl, url) && mUsingHiddenTab && isFirstLoad) {
            return;
        }

        IntentHandler.addReferrerAndHeaders(params, mIntent);
        if (params.getReferrer() == null) {
            params.setReferrer(mConnection.getReferrerForSession(mSession));
        }

        // See ChromeTabCreator#getTransitionType(). If the sender of the intent was a WebAPK, mark
        // the intent as a standard link navigation. Pass the user gesture along since one must have
        // been active to open a new tab and reach here. Otherwise, mark the navigation chain as
        // starting from an external intent. See crbug.com/792990.
        int defaultTransition = PageTransition.LINK | PageTransition.FROM_API;
        if (mIntentDataProvider.isOpenedByWebApk()) {
            params.setHasUserGesture(true);
            defaultTransition = PageTransition.LINK;
        }
        params.setTransitionType(
                IntentHandler.getTransitionTypeFromIntent(mIntent, defaultTransition));
        mTab.loadUrl(params);
    }

    /**
     * Detaches the tab and starts reparenting into the browser using given {@param intent} and
     * {@param startActivityOptions}.
     */
    public void detachAndStartReparenting(Intent intent, Bundle startActivityOptions,
            Runnable finishCallback) {
        if (mTab == null) {
            assert false;
            return;
        }
        Tab tab = mTab;
        setTab(null);
        tab.detachAndStartReparenting(intent, startActivityOptions, finishCallback);
    }

    /** Closes the tab and deletes related metadata. */
    public void closeAndForgetTab() {
        mTabFactory.getTabModelSelector().closeAllTabs(true);
        mTabPersistencePolicy.deleteMetadataStateFileAsync();
    }

    /** Save the current state of the tab. */
    public void saveState() {
        mTabFactory.getTabModelSelector().saveState();
    }

    @Override
    public void onPreInflationStartup() {
        // This must be requested before adding content.
        mActivity.supportRequestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);

        if (mActivity.getSavedInstanceState() == null && mConnection.hasWarmUpBeenFinished()) {
            mTabFactory.initializeTabModels();
            Tab tab = getHiddenTab();
            if (tab == null) tab = createTab(null);
            setTab(tab);
            mIsFirstLoad = true;
            loadUrlInTab(new LoadUrlParams(mIntentDataProvider.getUrlToLoad()),
                    IntentHandler.getTimestampFromIntent(mIntent));
            mHasCreatedTabEarly = true;
        }
    }

    @Override
    public void onPostInflationStartup() {}

    @Override
    public void onFinishNativeInitialization() {
        // If extra headers have been passed, cancel any current speculation, as
        // speculation doesn't support extra headers.
        if (IntentHandler.getExtraHeadersFromIntent(mIntent) != null) {
            mConnection.cancelSpeculation(mSession);
        }

        TabModelSelectorImpl tabModelSelector = mTabFactory.getTabModelSelector();

        TabModel tabModel = tabModelSelector.getModel(mIntentDataProvider.isIncognito());
        tabModel.addObserver(mTabObserverRegistrar);

        boolean successfulStateRestore = false;

        // Attempt to restore the previous tab state if applicable.
        if (mActivity.getSavedInstanceState() != null) {
            assert mTab == null;
            tabModelSelector.loadState(true);
            tabModelSelector.restoreTabs(true);
            setTab(tabModelSelector.getCurrentTab());
            successfulStateRestore = mTab != null;
            if (successfulStateRestore) initializeTab(mTab);
        }

        // If no tab was restored, create a new tab.
        if (!successfulStateRestore) {
            if (mTab != null) {
                // When the tab is created early, we don't have the TabContentManager connected,
                // since compositor related controllers were not initialized at that point.
                mTab.attachTabContentManager(mTabContentManager.get());
            } else {
                setTab(createTab(mTabContentManager.get()));
            }
            tabModel.addTab(mTab, 0, mTab.getLaunchType());
        }

        // This cannot be done before because we want to do the reparenting only
        // when we have compositor related controllers.
        if (mUsingHiddenTab) {
            TabReparentingParams params =
                    (TabReparentingParams) AsyncTabParamsManager.remove(mTab.getId());
            mTab.attachAndFinishReparenting(mActivity, mCustomTabDelegateFactory.get(),
                    (params == null ? null : params.getFinalizeCallback()));
        }

        if (!mHasCreatedTabEarly && !successfulStateRestore && !mTab.isLoading()) {
            loadUrlInTab(new LoadUrlParams(mIntentDataProvider.getUrlToLoad()),
                    IntentHandler.getTimestampFromIntent(mIntent));
        }

        // Put Sync in the correct state by calling tab state initialized. crbug.com/581811.
        tabModelSelector.markTabStateInitialized();

        // Notify ServiceTabLauncher if this is an asynchronous tab launch.
        if (mIntent.hasExtra(ServiceTabLauncher.LAUNCH_REQUEST_ID_EXTRA)) {
            ServiceTabLauncher.onWebContentsForRequestAvailable(
                    mIntent.getIntExtra(ServiceTabLauncher.LAUNCH_REQUEST_ID_EXTRA, 0),
                    mTab.getWebContents());
        }
    }

    /** Encapsulates CustomTabsConnection#takeHiddenTab() with additional initialization logic. */
    @Nullable
    private Tab getHiddenTab() {
        String url = mIntentDataProvider.getUrlToLoad();
        String referrerUrl = mConnection.getReferrer(mSession, mIntent);
        Tab tab = mConnection.takeHiddenTab(mSession, url, referrerUrl);
        mUsingHiddenTab = tab != null;
        if (!mUsingHiddenTab) return null;
        RecordHistogram.recordEnumeratedHistogram("CustomTabs.WebContentsStateOnLaunch",
                WebContentsState.PRERENDERED_WEBCONTENTS, WebContentsState.NUM_ENTRIES);
        tab.setAppAssociatedWith(mConnection.getClientPackageNameForSession(mSession));
        if (mIntentDataProvider.shouldEnableEmbeddedMediaExperience()) {
            tab.enableEmbeddedMediaExperience(true);
        }
        initializeTab(tab);
        return tab;
    }

    private Tab createTab(@Nullable TabContentManager tabContentManager) {
        WebContents webContents = takeWebContents();
        Tab tab = mTabFactory.createTab();
        int launchSource = mIntent.getIntExtra(
                CustomTabIntentDataProvider.EXTRA_BROWSER_LAUNCH_SOURCE, LaunchSourceType.OTHER);
        if (launchSource == LaunchSourceType.WEBAPK) {
            String webapkPackageName = mIntent.getStringExtra(Browser.EXTRA_APPLICATION_ID);
            tab.setAppAssociatedWith(webapkPackageName);
        } else {
            tab.setAppAssociatedWith(mConnection.getClientPackageNameForSession(mSession));
        }

        tab.initialize(webContents, tabContentManager, mCustomTabDelegateFactory.get(),
                false /*initiallyHidden*/, false /*unfreeze*/);

        if (mIntentDataProvider.shouldEnableEmbeddedMediaExperience()) {
            tab.enableEmbeddedMediaExperience(true);
        }

        initializeTab(tab);
        return tab;
    }

    private void setTab(@Nullable Tab tab) {
        if (tab == mTab) {
            return;
        }
        mTab = tab;
        for (Observer observer : mObservers) {
            observer.onTabChanged();
        }
    }

    private WebContents takeWebContents() {
        int webContentsStateOnLaunch;

        WebContents webContents = takeAsyncWebContents();
        if (webContents != null) {
            webContentsStateOnLaunch = WebContentsState.TRANSFERRED_WEBCONTENTS;
            webContents.resumeLoadingCreatedWebContents();
        } else {
            webContents = mWarmupManager.takeSpareWebContents(mIntentDataProvider.isIncognito(),
                    false /*initiallyHidden*/);
            if (webContents != null) {
                webContentsStateOnLaunch = WebContentsState.SPARE_WEBCONTENTS;
            } else {
                webContents = mWebContentsFactory.createWebContentsWithWarmRenderer(
                        mIntentDataProvider.isIncognito(), false);
                webContentsStateOnLaunch = WebContentsState.NO_WEBCONTENTS;
            }
        }

        RecordHistogram.recordEnumeratedHistogram("CustomTabs.WebContentsStateOnLaunch",
                webContentsStateOnLaunch, WebContentsState.NUM_ENTRIES);

        return webContents;
    }

    @Nullable
    private WebContents takeAsyncWebContents() {
        int assignedTabId = IntentUtils.safeGetIntExtra(
                mIntent, IntentHandler.EXTRA_TAB_ID, Tab.INVALID_TAB_ID);
        AsyncTabParams asyncParams = AsyncTabParamsManager.remove(assignedTabId);
        if (asyncParams == null) return null;
        return asyncParams.getWebContents();
    }

    private void initializeTab(Tab tab) {
        TabRedirectHandler.from(tab).updateIntent(mIntent);
        tab.getView().requestFocus();

        mTabNavigationEventObserver = new CustomTabNavigationEventObserver(mSession, mConnection);

        mTabObserverRegistrar.registerTabObserver(mCustomTabObserver.get());
        mTabObserverRegistrar.registerTabObserver(mTabNavigationEventObserver);
        mTabObserverRegistrar.registerPageLoadMetricsObserver(
                new PageLoadMetricsObserver(mConnection, mSession, tab));
        mTabObserverRegistrar.registerPageLoadMetricsObserver(
                new FirstMeaningfulPaintObserver(mCustomTabObserver.get(), tab));

        // Immediately add the observer to PageLoadMetrics to catch early events that may
        // be generated in the middle of tab initialization.
        mTabObserverRegistrar.addObserversForTab(tab);
        prepareTabBackground(tab);
    }

    /** Sets the initial background color for the Tab, shown before the page content is ready. */
    private void prepareTabBackground(final Tab tab) {
        if (!IntentHandler.notSecureIsIntentChromeOrFirstParty(mIntent)) return;

        int backgroundColor = mIntentDataProvider.getInitialBackgroundColor();
        if (backgroundColor == Color.TRANSPARENT) return;

        // Set the background color.
        tab.getView().setBackgroundColor(backgroundColor);

        // Unset the background when the page has rendered.
        EmptyTabObserver mediaObserver = new EmptyTabObserver() {
            @Override
            public void didFirstVisuallyNonEmptyPaint(final Tab tab) {
                tab.removeObserver(this);

                // Blink has rendered the page by this point, but we need to wait for the compositor
                // frame swap to avoid flash of white content.
                mCompositorViewHolder.get().getCompositorView().surfaceRedrawNeededAsync(() -> {
                    if (!tab.isInitialized() || mActivity.isActivityDestroyed()) return;
                    tab.getView().setBackgroundResource(0);
                });
            }
        };

        tab.addObserver(mediaObserver);
    }

    private boolean hasSpeculated() {
        return !TextUtils.isEmpty(mSpeculatedUrl);
    }

    public interface Observer {
        /**
         * Fired when the tab managed by this class has changed. Use {@link #getTab()} to retrieve
         * the new tab.
         */
        void onTabChanged();
    }
}
