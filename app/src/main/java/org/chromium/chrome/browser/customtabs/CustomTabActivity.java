// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Browser;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsSessionToken;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RemoteViews;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabTaskDescriptionHelper;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.ExternalAppId;
import org.chromium.chrome.browser.KeyboardShortcuts;
import org.chromium.chrome.browser.LaunchIntentDispatcher;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.autofill_assistant.AutofillAssistantFacade;
import org.chromium.chrome.browser.browserservices.BrowserSessionContentHandler;
import org.chromium.chrome.browser.browserservices.BrowserSessionContentUtils;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModule;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabController;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabFactory;
import org.chromium.chrome.browser.customtabs.dependency_injection.CustomTabActivityComponent;
import org.chromium.chrome.browser.customtabs.dependency_injection.CustomTabActivityModule;
import org.chromium.chrome.browser.customtabs.dynamicmodule.DynamicModuleCoordinator;
import org.chromium.chrome.browser.dependency_injection.ChromeActivityCommonsModule;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.firstrun.FirstRunSignInProcessor;
import org.chromium.chrome.browser.gsa.GSAState;
import org.chromium.chrome.browser.incognito.IncognitoTabHost;
import org.chromium.chrome.browser.incognito.IncognitoTabHostRegistry;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.page_info.PageInfoController;
import org.chromium.chrome.browser.rappor.RapporServiceBridge;
import org.chromium.chrome.browser.share.ShareMenuActionHandler;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.browser.toolbar.ToolbarManager;
import org.chromium.chrome.browser.toolbar.top.ToolbarControlContainer;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.webapps.WebappCustomTabTimeSpentLogger;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.WebContents;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The activity for custom tabs. It will be launched on top of a client's task.
 */
public class CustomTabActivity extends ChromeActivity<CustomTabActivityComponent> {
    private static final String TAG = "CustomTabActivity";
    private static final String LAST_URL_PREF = "pref_last_custom_tab_url";

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

    // For CustomTabs.ConnectionStatusOnReturn, see histograms.xml. Append only.
    @IntDef({ConnectionStatus.DISCONNECTED, ConnectionStatus.DISCONNECTED_KEEP_ALIVE,
            ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTED_KEEP_ALIVE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ConnectionStatus {
        int DISCONNECTED = 0;
        int DISCONNECTED_KEEP_ALIVE = 1;
        int CONNECTED = 2;
        int CONNECTED_KEEP_ALIVE = 3;
        int NUM_ENTRIES = 4;
    }

    private CustomTabIntentDataProvider mIntentDataProvider;
    private CustomTabsSessionToken mSession;
    private BrowserSessionContentHandler mBrowserSessionContentHandler;
    private CustomTabBottomBarDelegate mBottomBarDelegate;
    private CustomTabTopBarDelegate mTopBarDelegate;
    private CustomTabActivityTabController mTabController;
    private CustomTabActivityTabFactory mTabFactory;

    // This is to give the right package name while using the client's resources during an
    // overridePendingTransition call.
    // TODO(ianwen, yusufo): Figure out a solution to extract external resources without having to
    // change the package name.
    private boolean mShouldOverridePackage;

    private boolean mIsInitialResume = true;

    /** Adds and removes observers from tabs when needed. */
    private TabObserverRegistrar mTabObserverRegistrar;

    private boolean mIsClosing;
    private boolean mIsKeepAlive;

    private final CustomTabsConnection mConnection = CustomTabsConnection.getInstance();

    private WebappCustomTabTimeSpentLogger mWebappTimeSpentLogger;

    @Nullable
    private DynamicModuleCoordinator mDynamicModuleCoordinator;

    private ActivityTabTaskDescriptionHelper mTaskDescriptionHelper;

    /**
     * Return true when the activity has been launched in a separate task. The default behavior is
     * to reuse the same task and put the activity on top of the previous one (i.e hiding it). A
     * separate task creates a new entry in the Android recent screen.
     **/
    private boolean useSeparateTask() {
        final int separateTaskFlags =
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
        return (getIntent().getFlags() & separateTaskFlags) != 0;
    }

    private CustomTabActivityTabController.Observer mTabChangeObserver = () -> {
        resetPostMessageHandlersForCurrentSession();
        if (mTabController.getTab() == null) {
            finishAndClose(false);
        }
    };

    @Nullable
    private IncognitoTabHost mIncognitoTabHost;

    @Override
    protected Drawable getBackgroundDrawable() {
        int initialBackgroundColor = mIntentDataProvider.getInitialBackgroundColor();
        if (mIntentDataProvider.isTrustedIntent() && initialBackgroundColor != Color.TRANSPARENT) {
            return new ColorDrawable(initialBackgroundColor);
        } else {
            return super.getBackgroundDrawable();
        }
    }

    @Override
    public boolean isCustomTab() {
        return true;
    }

    @Override
    protected void recordIntentToCreationTime(long timeMs) {
        super.recordIntentToCreationTime(timeMs);

        RecordHistogram.recordTimesHistogram(
                "MobileStartup.IntentToCreationTime.CustomTabs", timeMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsClosing = false;
        mIsKeepAlive = mConnection.keepAliveForSession(
                mIntentDataProvider.getSession(), mIntentDataProvider.getKeepAliveServiceIntent());
    }

    @Override
    public void onStop() {
        super.onStop();
        mConnection.dontKeepAliveForSession(mIntentDataProvider.getSession());
        mIsKeepAlive = false;
    }

    @Override
    public void preInflationStartup() {
        // Parse the data from the Intent before calling super to allow the Intent to customize
        // the Activity parameters, including the background of the page.
        mIntentDataProvider = new CustomTabIntentDataProvider(getIntent(), this);

        super.preInflationStartup();
        mTabController.addObserver(mTabChangeObserver);
        // We might have missed an onTabChanged event.
        resetPostMessageHandlersForCurrentSession();

        mSession = mIntentDataProvider.getSession();

        if (mIntentDataProvider.isIncognito()) {
            initializeIncognito();
        }

        initalizePreviewsObserver();
    }

    private void initializeIncognito() {
        mIncognitoTabHost = new IncognitoCustomTabHost();
        IncognitoTabHostRegistry.getInstance().register(mIncognitoTabHost);

        if (!CommandLine.getInstance().hasSwitch(
                ChromeSwitches.ENABLE_INCOGNITO_SNAPSHOTS_IN_ANDROID_RECENTS)) {
            // Disable taking screenshots and seeing snapshots in recents
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Nullable
    private NavigationController getNavigationController() {
        WebContents webContents = getActivityTab().getWebContents();
        return webContents == null ? null : webContents.getNavigationController();
    }

    @Override
    public boolean shouldAllocateChildConnection() {
        return mTabController.shouldAllocateChildConnection();
    }

    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        getToolbarManager().setCloseButtonDrawable(mIntentDataProvider.getCloseButtonDrawable());
        getToolbarManager().setShowTitle(mIntentDataProvider.getTitleVisibilityState()
                == CustomTabsIntent.SHOW_PAGE_TITLE);
        if (mConnection.shouldHideDomainForSession(mSession)) {
            getToolbarManager().setUrlBarHidden(true);
        }
        int toolbarColor = mIntentDataProvider.getToolbarColor();
        getToolbarManager().onThemeColorChanged(toolbarColor, false);
        if (!mIntentDataProvider.isOpenedByChrome()) {
            getToolbarManager().setShouldUpdateToolbarPrimaryColor(false);
        }

        super.setStatusBarColor(toolbarColor,
                ColorUtils.isUsingDefaultToolbarColor(getResources(), false, toolbarColor));

        // Properly attach tab's infobar to the view hierarchy, as the main tab might have been
        // initialized prior to inflation.
        if (mTabController.getTab() != null) {
            ViewGroup bottomContainer = (ViewGroup) findViewById(R.id.bottom_container);
            InfoBarContainer.get(mTabController.getTab()).setParentView(bottomContainer);
        }

        // Setting task title and icon to be null will preserve the client app's title and icon.
        ApiCompatibilityUtils.setTaskDescription(this, null, null, toolbarColor);
        showCustomButtonsOnToolbar();
        mBottomBarDelegate = getComponent().resolveBottomBarDelegate();
        mBottomBarDelegate.showBottomBarIfNecessary();
        mTopBarDelegate = getComponent().resolveTobBarDelegate();
    }

    @Override
    protected TabModelSelector createTabModelSelector() {
        return mTabFactory.createTabModelSelector();
    }

    @Override
    protected Pair<ChromeTabCreator, ChromeTabCreator> createTabCreators() {
        return mTabFactory.createTabCreators();
    }

    @Override
    public void finishNativeInitialization() {
        if (!mIntentDataProvider.isInfoPage()) FirstRunSignInProcessor.start(this);

        // Try to initialize dynamic module early to enqueue navigation events
        // @see DynamicModuleNavigationEventObserver
        if (mIntentDataProvider.isDynamicModuleEnabled()) {
            mDynamicModuleCoordinator = getComponent().resolveDynamicModuleCoordinator();
        }

        LayoutManager layoutDriver = new LayoutManager(getCompositorViewHolder());
        initializeCompositorContent(layoutDriver, findViewById(R.id.url_bar),
                (ViewGroup) findViewById(android.R.id.content),
                (ToolbarControlContainer) findViewById(R.id.control_container));
        getToolbarManager().initializeWithNative(getTabModelSelector(),
                getFullscreenManager().getBrowserVisibilityDelegate(), getFindToolbarManager(),
                null, layoutDriver, null, null, null, new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        RecordUserAction.record("CustomTabs.CloseButtonClicked");
                        if (mIntentDataProvider.shouldEnableEmbeddedMediaExperience()) {
                            RecordUserAction.record("CustomTabs.CloseButtonClicked.DownloadsUI");
                        }
                        if (getComponent().resolveCloseButtonNavigator()
                                .navigateOnClose(getNavigationController())) {
                            RecordUserAction.record(
                                    "CustomTabs.CloseButtonClicked.GoToModuleManagedUrl");
                            return;
                        }
                        recordClientConnectionStatus();
                        finishAndClose(false);
                    }
                });

        mBrowserSessionContentHandler = new BrowserSessionContentHandler() {
            @Override
            public void loadUrlAndTrackFromTimestamp(LoadUrlParams params, long timestamp) {
                if (!TextUtils.isEmpty(params.getUrl())) {
                    params.setUrl(DataReductionProxySettings.getInstance()
                            .maybeRewriteWebliteUrl(params.getUrl()));
                }
                mTabController.loadUrlInTab(params, timestamp);
            }

            @Override
            public CustomTabsSessionToken getSession() {
                return mSession;
            }

            @Override
            public boolean shouldIgnoreIntent(Intent intent) {
                return mIntentHandler.shouldIgnoreIntent(intent);
            }

            @Override
            public boolean updateCustomButton(int id, Bitmap bitmap, String description) {
                CustomButtonParams params = mIntentDataProvider.getButtonParamsForId(id);
                if (params == null) {
                    Log.w(TAG, "Custom toolbar button with ID %d not found", id);
                    return false;
                }

                params.update(bitmap, description);
                if (params.showOnToolbar()) {
                    if (!CustomButtonParams.doesIconFitToolbar(CustomTabActivity.this, bitmap)) {
                        return false;
                    }
                    int index = mIntentDataProvider.getCustomToolbarButtonIndexForId(id);
                    assert index != -1;
                    getToolbarManager().updateCustomActionButton(
                            index, params.getIcon(CustomTabActivity.this), description);
                } else {
                    if (mBottomBarDelegate != null) {
                        mBottomBarDelegate.updateBottomBarButtons(params);
                    }
                }
                return true;
            }

            @Override
            public boolean updateRemoteViews(RemoteViews remoteViews, int[] clickableIDs,
                    PendingIntent pendingIntent) {
                if (mBottomBarDelegate == null) return false;
                return mBottomBarDelegate.updateRemoteViews(
                        remoteViews, clickableIDs, pendingIntent);
            }

            @Override
            @Nullable
            public String getCurrentUrl() {
                return getActivityTab() == null ? null : getActivityTab().getUrl();
            }

            @Override
            @Nullable
            public String getPendingUrl() {
                if (getActivityTab() == null) return null;
                if (getActivityTab().getWebContents() == null) return null;

                NavigationEntry entry = getActivityTab().getWebContents().getNavigationController()
                        .getPendingEntry();
                return entry != null ? entry.getUrl() : null;
            }

            @Override
            public void triggerSharingFlow() {
                ShareMenuActionHandler.getInstance().onShareMenuItemSelected(CustomTabActivity.this,
                        getActivityTab(), false /* shareDirectly */, false /* isIncognito */);
            }

            @Override
            public int getTaskId() {
                return CustomTabActivity.this.getTaskId();
            }
        };

        recordClientPackageName();
        mConnection.showSignInToastIfNecessary(mSession, getIntent());

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.AUTOFILL_ASSISTANT)
                && AutofillAssistantFacade.isConfigured(getInitialIntent().getExtras())) {
            AutofillAssistantFacade.start(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && useSeparateTask()) {
            mTaskDescriptionHelper = new ActivityTabTaskDescriptionHelper(this,
                    ApiCompatibilityUtils.getColor(getResources(), R.color.default_primary_color));
        }

        super.finishNativeInitialization();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Intent originalIntent = getIntent();
        super.onNewIntent(intent);
        // Currently we can't handle arbitrary updates of intent parameters, so make sure
        // getIntent() returns the same intent as before.
        setIntent(originalIntent);
    }

    @Override
    public void onNewIntentWithNative(Intent intent) {
        super.onNewIntentWithNative(intent);
        BrowserSessionContentUtils.setActiveContentHandler(mBrowserSessionContentHandler);
        if (!BrowserSessionContentUtils.handleBrowserServicesIntent(intent)) {
            int flagsToRemove = Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP;
            intent.setFlags(intent.getFlags() & ~flagsToRemove);
            startActivity(intent);
        }
    }

    private void resetPostMessageHandlersForCurrentSession() {
        Tab tab = mTabController.getTab();
        WebContents webContents = tab == null ? null : tab.getWebContents();
        mConnection.resetPostMessageHandlerForSession(
                mIntentDataProvider.getSession(), webContents);

        if (mDynamicModuleCoordinator != null) {
            mDynamicModuleCoordinator.resetPostMessageHandlersForCurrentSession(null);
        }
    }

    private void initalizePreviewsObserver() {
        mTabObserverRegistrar.registerTabObserver(new EmptyTabObserver() {
            /** Keeps track of the original color before the preview was shown. */
            private int mOriginalColor;

            /** True if a change to the toolbar color was made because of a preview. */
            private boolean mTriggeredPreviewChange;

            @Override
            public void onPageLoadFinished(Tab tab, String url) {
                // Update the color when the page load finishes.
                updateColor(tab);
            }

            @Override
            public void didReloadLoFiImages(Tab tab) {
                // Update the color when the LoFi preview is reloaded.
                updateColor(tab);
            }

            @Override
            public void onUrlUpdated(Tab tab) {
                // Update the color on every new URL.
                updateColor(tab);
            }

            /**
             * Updates the color of the Activity's status bar and the CCT Toolbar. When a preview is
             * shown, it should be reset to the default color. If the user later navigates away from
             * that preview to a non-preview page, reset the color back to the original. This does
             * not interfere with site-specific theme colors which are disabled when a preview is
             * being shown.
             */
            private void updateColor(Tab tab) {
                final ToolbarManager manager = getToolbarManager();
                if (manager == null) return;

                // Record the original toolbar color in case we need to revert back to it later
                // after a preview has been shown then the user navigates to another non-preview
                // page.
                if (mOriginalColor == 0) mOriginalColor = manager.getPrimaryColor();

                final boolean shouldUpdateOriginal = manager.getShouldUpdateToolbarPrimaryColor();
                manager.setShouldUpdateToolbarPrimaryColor(true);

                if (tab.isPreview()) {
                    final int defaultColor = ColorUtils.getDefaultThemeColor(getResources(), false);
                    manager.onThemeColorChanged(defaultColor, false);
                    setStatusBarColor(defaultColor, false);
                    mTriggeredPreviewChange = true;
                } else if (mOriginalColor != manager.getPrimaryColor() && mTriggeredPreviewChange) {
                    manager.onThemeColorChanged(mOriginalColor, false);
                    setStatusBarColor(mOriginalColor, false);

                    mTriggeredPreviewChange = false;
                    mOriginalColor = 0;
                }

                manager.setShouldUpdateToolbarPrimaryColor(shouldUpdateOriginal);
            }
        });
    }

    @Override
    public void initializeCompositor() {
        super.initializeCompositor();
        getTabModelSelector().onNativeLibraryReady(getTabContentManager());
        mBottomBarDelegate.addOverlayPanelManagerObserver();
    }

    private void recordClientPackageName() {
        String clientName = mConnection.getClientPackageNameForSession(mSession);
        if (TextUtils.isEmpty(clientName)) clientName = mIntentDataProvider.getClientPackageName();
        final String packageName = clientName;
        if (TextUtils.isEmpty(packageName) || packageName.contains(getPackageName())) return;
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RapporServiceBridge.sampleString(
                        "CustomTabs.ServiceClient.PackageName", packageName);
                if (GSAState.isGsaPackageName(packageName)) return;
                RapporServiceBridge.sampleString(
                        "CustomTabs.ServiceClient.PackageNameThirdParty", packageName);
            }
        });
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        BrowserSessionContentUtils.setActiveContentHandler(mBrowserSessionContentHandler);
        if (mTabController.earlyCreatedTabIsReady()) postDeferredStartupIfNeeded();
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();

        if (getSavedInstanceState() != null || !mIsInitialResume) {
            if (mIntentDataProvider.isOpenedByChrome()) {
                RecordUserAction.record("ChromeGeneratedCustomTab.StartedReopened");
            } else {
                RecordUserAction.record("CustomTabs.StartedReopened");
            }
        } else {
            SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
            String lastUrl = preferences.getString(LAST_URL_PREF, null);
            String urlToLoad = mIntentDataProvider.getUrlToLoad();
            if (lastUrl != null && lastUrl.equals(urlToLoad)) {
                RecordUserAction.record("CustomTabsMenuOpenSameUrl");
            } else {
                preferences.edit().putString(LAST_URL_PREF, urlToLoad).apply();
            }

            if (mIntentDataProvider.isOpenedByChrome()) {
                RecordUserAction.record("ChromeGeneratedCustomTab.StartedInitially");
            } else {
                @ExternalAppId
                int externalId = IntentHandler.determineExternalIntentSource(getIntent());
                RecordHistogram.recordEnumeratedHistogram(
                        "CustomTabs.ClientAppId", externalId, ExternalAppId.NUM_ENTRIES);

                RecordUserAction.record("CustomTabs.StartedInitially");
            }
        }
        mIsInitialResume = false;
        mWebappTimeSpentLogger = WebappCustomTabTimeSpentLogger.createInstanceAndStartTimer(
                getIntent().getIntExtra(CustomTabIntentDataProvider.EXTRA_BROWSER_LAUNCH_SOURCE,
                        CustomTabIntentDataProvider.LaunchSourceType.OTHER));
    }

    @Override
    public void onPauseWithNative() {
        super.onPauseWithNative();
        if (mWebappTimeSpentLogger != null) {
            mWebappTimeSpentLogger.onPause();
        }
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        BrowserSessionContentUtils.removeActiveContentHandler(mBrowserSessionContentHandler);
        if (mIsClosing) {
            mTabController.closeAndForgetTab();
        } else {
            mTabController.saveState();
        }
    }

    @Override
    protected void onDestroyInternal() {
        super.onDestroyInternal();

        if (mIncognitoTabHost != null) {
            IncognitoTabHostRegistry.getInstance().unregister(mIncognitoTabHost);
        }

        if (mTaskDescriptionHelper != null) mTaskDescriptionHelper.destroy();
    }

    @Override
    public void createContextualSearchTab(String searchUrl) {
        if (getActivityTab() == null) return;
        getActivityTab().loadUrl(new LoadUrlParams(searchUrl));
    }

    @Override
    public TabModelSelectorImpl getTabModelSelector() {
        return (TabModelSelectorImpl) super.getTabModelSelector();
    }

    @Override
    @Nullable
    public Tab getActivityTab() {
        return mTabController.getTab();
    }

    @Override
    protected AppMenuPropertiesDelegate createAppMenuPropertiesDelegate() {
        return new CustomTabAppMenuPropertiesDelegate(this,
                mIntentDataProvider.getUiType(),
                mIntentDataProvider.getMenuTitles(),
                mIntentDataProvider.isOpenedByChrome(),
                mIntentDataProvider.shouldShowShareMenuItem(),
                mIntentDataProvider.shouldShowStarButton(),
                mIntentDataProvider.shouldShowDownloadButton(),
                mIntentDataProvider.isIncognito());
    }

    @Override
    protected int getAppMenuLayoutId() {
        return R.menu.custom_tabs_menu;
    }

    @Override
    protected int getControlContainerLayoutId() {
        return R.layout.custom_tabs_control_container;
    }

    @Override
    protected int getToolbarLayoutId() {
        return R.layout.custom_tabs_toolbar;
    }

    @Override
    public int getControlContainerHeightResource() {
        return R.dimen.custom_tabs_control_container_height;
    }

    @Override
    public String getPackageName() {
        if (mShouldOverridePackage) return mIntentDataProvider.getClientPackageName();
        return super.getPackageName();
    }

    @Override
    public void finish() {
        // Prevent the menu window from leaking.
        if (getAppMenuHandler() != null) getAppMenuHandler().hideAppMenu();

        super.finish();
        if (mIntentDataProvider != null && mIntentDataProvider.shouldAnimateOnFinish()) {
            mShouldOverridePackage = true;
            overridePendingTransition(mIntentDataProvider.getAnimationEnterRes(),
                    mIntentDataProvider.getAnimationExitRes());
            mShouldOverridePackage = false;
        } else if (mIntentDataProvider != null && mIntentDataProvider.isOpenedByChrome()) {
            overridePendingTransition(R.anim.no_anim, R.anim.activity_close_exit);
        }
    }

    /**
     * Finishes the activity and removes the reference from the Android recents.
     *
     * @param reparenting true iff the activity finishes due to tab reparenting.
     */
    public final void finishAndClose(boolean reparenting) {
        if (mIsClosing) return;
        mIsClosing = true;

        if (!reparenting) {
            // Closing the activity destroys the renderer as well. Re-create a spare renderer some
            // time after, so that we have one ready for the next tab open. This does not increase
            // memory consumption, as the current renderer goes away. We create a renderer as a lot
            // of users open several Custom Tabs in a row. The delay is there to avoid jank in the
            // transition animation when closing the tab.
            ThreadUtils.postOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    WarmupManager.getInstance().createSpareWebContents();
                }
            }, 500);
        }

        handleFinishAndClose();
    }

    /**
     * Internal implementation that finishes the activity and removes the references from Android
     * recents.
     */
    protected void handleFinishAndClose() {
        if (useSeparateTask()) {
            ApiCompatibilityUtils.finishAndRemoveTask(this);
        } else {
            finish();
        }
    }

    @Override
    protected boolean handleBackPressed() {
        if (!LibraryLoader.getInstance().isInitialized()) return false;

        RecordUserAction.record("CustomTabs.SystemBack");
        if (getActivityTab() == null) return false;

        if (exitFullscreenIfShowing()) return true;

        if (mDynamicModuleCoordinator != null &&
                mDynamicModuleCoordinator.onBackPressedAsync(this::handleTabBackNavigation)) {
            return true;
        }

        handleTabBackNavigation();
        return true;
    }

    private void handleTabBackNavigation() {
        if (!getToolbarManager().back()) {
            if (getCurrentTabModel().getCount() > 1) {
                getCurrentTabModel().closeTab(getActivityTab(), false, false, false);
            } else {
                recordClientConnectionStatus();
                finishAndClose(false);
            }
        }
    }

    private void recordClientConnectionStatus() {
        String packageName =
                (getActivityTab() == null) ? null : getActivityTab().getAppAssociatedWith();
        if (packageName == null) return; // No associated package

        boolean isConnected =
                packageName.equals(mConnection.getClientPackageNameForSession(mSession));
        int status = -1;
        if (isConnected) {
            if (mIsKeepAlive) {
                status = ConnectionStatus.CONNECTED_KEEP_ALIVE;
            } else {
                status = ConnectionStatus.CONNECTED;
            }
        } else {
            if (mIsKeepAlive) {
                status = ConnectionStatus.DISCONNECTED_KEEP_ALIVE;
            } else {
                status = ConnectionStatus.DISCONNECTED;
            }
        }
        assert status >= 0;

        if (GSAState.isGsaPackageName(packageName)) {
            RecordHistogram.recordEnumeratedHistogram("CustomTabs.ConnectionStatusOnReturn.GSA",
                    status, ConnectionStatus.NUM_ENTRIES);
        } else {
            RecordHistogram.recordEnumeratedHistogram("CustomTabs.ConnectionStatusOnReturn.NonGSA",
                    status, ConnectionStatus.NUM_ENTRIES);
        }
    }

    /**
     * Configures the custom button on toolbar. Does nothing if invalid data is provided by clients.
     */
    private void showCustomButtonsOnToolbar() {
        final List<CustomButtonParams> paramList = mIntentDataProvider.getCustomButtonsOnToolbar();
        for (CustomButtonParams params : paramList) {
            getToolbarManager().addCustomActionButton(
                    params.getIcon(this), params.getDescription(), v -> {
                        if (getActivityTab() == null) return;
                        mIntentDataProvider.sendButtonPendingIntentWithUrlAndTitle(
                                ContextUtils.getApplicationContext(), params,
                                getActivityTab().getUrl(), getActivityTab().getTitle());
                        RecordUserAction.record("CustomTabsCustomActionButtonClick");
                        if (mIntentDataProvider.shouldEnableEmbeddedMediaExperience()
                                && TextUtils.equals(
                                           params.getDescription(), getString(R.string.share))) {
                            RecordUserAction.record(
                                    "CustomTabsCustomActionButtonClick.DownloadsUI.Share");
                        }
                    });
        }
    }

    @Override
    public boolean shouldShowAppMenu() {
        if (getActivityTab() == null || !getToolbarManager().isInitialized()) return false;

        return super.shouldShowAppMenu();
    }

    @Override
    protected void showAppMenuForKeyboardEvent() {
        if (!shouldShowAppMenu()) return;
        super.showAppMenuForKeyboardEvent();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuIndex = getAppMenuPropertiesDelegate().getIndexOfMenuItem(item);
        if (menuIndex >= 0) {
            mIntentDataProvider.clickMenuItemWithUrlAndTitle(
                    this, menuIndex, getActivityTab().getUrl(), getActivityTab().getTitle());
            RecordUserAction.record("CustomTabsMenuCustomMenuItem");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Boolean result = KeyboardShortcuts.dispatchKeyEvent(event, this,
                getToolbarManager().isInitialized());
        return result != null ? result : super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!getToolbarManager().isInitialized()) {
            return super.onKeyDown(keyCode, event);
        }
        return KeyboardShortcuts.onKeyDown(event, this, true, false)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onMenuOrKeyboardAction(int id, boolean fromMenu) {
        // Disable creating new tabs, bookmark, history, print, help, focus_url, etc.
        if (id == R.id.focus_url_bar || id == R.id.all_bookmarks_menu_id
                || id == R.id.help_id || id == R.id.recent_tabs_menu_id
                || id == R.id.new_incognito_tab_menu_id || id == R.id.new_tab_menu_id
                || id == R.id.open_history_menu_id) {
            return true;
        } else if (id == R.id.bookmark_this_page_id) {
            addOrEditBookmark(getActivityTab());
            RecordUserAction.record("MobileMenuAddToBookmarks");
            return true;
        } else if (id == R.id.open_in_browser_id) {
            if (openCurrentUrlInBrowser(false)) {
                RecordUserAction.record("CustomTabsMenuOpenInChrome");
                mConnection.notifyOpenInBrowser(mSession);
            }
            return true;
        } else if (id == R.id.info_menu_id) {
            if (getTabModelSelector().getCurrentTab() == null) return false;
            PageInfoController.show(this, getTabModelSelector().getCurrentTab(),
                    getToolbarManager().getContentPublisher(),
                    PageInfoController.OpenedFromSource.MENU);
            return true;
        }
        return super.onMenuOrKeyboardAction(id, fromMenu);
    }

    @Override
    protected void setStatusBarColor(Tab tab, int color) {
        // Intentionally do nothing as CustomTabActivity explicitly sets status bar color.  Except
        // for Custom Tabs opened by Chrome.
        if (mIntentDataProvider.isOpenedByChrome()) super.setStatusBarColor(tab, color);
    }

    /**
     * @return The {@link AppMenuPropertiesDelegate} associated with this activity. For test
     *         purposes only.
     */
    @VisibleForTesting
    @Override
    public CustomTabAppMenuPropertiesDelegate getAppMenuPropertiesDelegate() {
        return (CustomTabAppMenuPropertiesDelegate) super.getAppMenuPropertiesDelegate();
    }

    @Override
    public void onUpdateStateChanged() {}

    /**
     * @return The {@link CustomTabIntentDataProvider} for this {@link CustomTabActivity}. For test
     *         purposes only.
     */
    @VisibleForTesting
    public CustomTabIntentDataProvider getIntentDataProvider() {
        return mIntentDataProvider;
    }

    /**
     * Opens the URL currently being displayed in the Custom Tab in the regular browser.
     * @param forceReparenting Whether tab reparenting should be forced for testing.
     *
     * @return Whether or not the tab was sent over successfully.
     */
    boolean openCurrentUrlInBrowser(boolean forceReparenting) {
        Tab tab = getActivityTab();
        if (tab == null) return false;

        String url = tab.getUrl();
        if (DomDistillerUrlUtils.isDistilledPage(url)) {
            url = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
        }
        if (TextUtils.isEmpty(url)) url = mIntentDataProvider.getUrlToLoad();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LaunchIntentDispatcher.EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT, false);

        boolean willChromeHandleIntent =
                getIntentDataProvider().isOpenedByChrome() || getIntentDataProvider().isIncognito();

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            willChromeHandleIntent |= ExternalNavigationDelegateImpl
                    .willChromeHandleIntent(intent, true);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        Bundle startActivityOptions = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.abc_fade_in, R.anim.abc_fade_out).toBundle();
        if (willChromeHandleIntent || forceReparenting) {
            Runnable finalizeCallback = new Runnable() {
                @Override
                public void run() {
                    finishAndClose(true);
                }
            };

            mTabController.detachAndStartReparenting(intent, startActivityOptions,
                    finalizeCallback);
        } else {
            // Temporarily allowing disk access while fixing. TODO: http://crbug.com/581860
            StrictMode.allowThreadDiskWrites();
            try {
                if (mIntentDataProvider.isInfoPage()) {
                    IntentHandler.startChromeLauncherActivityForTrustedIntent(intent);
                } else {
                    startActivity(intent, startActivityOptions);
                }
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
        return true;
    }

    @Override
    protected void initializeToolbar() {
        super.initializeToolbar();
        if (mIntentDataProvider.isMediaViewer()) {
            getToolbarManager().setToolbarShadowVisibility(View.GONE);

            // The media viewer has no default menu items, so if there are also no custom items, we
            // should hide the menu button altogether.
            if (mIntentDataProvider.getMenuTitles().isEmpty()) {
                getToolbarManager().getToolbar().disableMenuButton();
            }
        }
    }

    /**
     * Show the web page with CustomTabActivity, without any navigation control.
     * Used in showing the terms of services page or help pages for Chrome.
     * @param context The current activity context.
     * @param url The url of the web page.
     */
    public static void showInfoPage(Context context, String url) {
        // TODO(xingliu): The title text will be the html document title, figure out if we want to
        // use Chrome strings here as EmbedContentViewActivity does.
        CustomTabsIntent customTabIntent = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setToolbarColor(ApiCompatibilityUtils.getColor(
                        context.getResources(),
                        R.color.dark_action_bar_color))
                .build();
        customTabIntent.intent.setData(Uri.parse(url));

        Intent intent = LaunchIntentDispatcher.createCustomTabActivityIntent(
                context, customTabIntent.intent);
        intent.setPackage(context.getPackageName());
        intent.putExtra(CustomTabIntentDataProvider.EXTRA_UI_TYPE,
                CustomTabIntentDataProvider.CustomTabsUiType.INFO_PAGE);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        IntentHandler.addTrustedIntentExtras(intent);

        context.startActivity(intent);
    }

    @Override
    protected boolean requiresFirstRunToBeCompleted(Intent intent) {
        // Custom Tabs can be used to open Chrome help pages before the ToS has been accepted.
        if (IntentHandler.notSecureIsIntentChromeOrFirstParty(intent)
                && IntentUtils.safeGetIntExtra(intent, CustomTabIntentDataProvider.EXTRA_UI_TYPE,
                           CustomTabIntentDataProvider.CustomTabsUiType.DEFAULT)
                        == CustomTabIntentDataProvider.CustomTabsUiType.INFO_PAGE) {
            return false;
        }

        return super.requiresFirstRunToBeCompleted(intent);
    }

    @Override
    public boolean canShowTrustedCdnPublisherUrl() {
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.SHOW_TRUSTED_PUBLISHER_URL)) {
            return false;
        }

        Tab tab = mTabController.getTab();
        if (tab != null && tab.isPreview()) {
            return false;
        }

        String publisherUrlPackage = mConnection.getTrustedCdnPublisherUrlPackage();
        return publisherUrlPackage != null
                && publisherUrlPackage.equals(mConnection.getClientPackageNameForSession(mSession));
    }

    private class IncognitoCustomTabHost implements IncognitoTabHost {

        public IncognitoCustomTabHost() {
            assert mIntentDataProvider.isIncognito();
        }

        @Override
        public boolean hasIncognitoTabs() {
            return !isFinishing();
        }

        @Override
        public void closeAllIncognitoTabs() {
            finishAndClose(false);
        }
    }

    @Override
    protected CustomTabActivityComponent createComponent(ChromeActivityCommonsModule commonsModule,
            ContextualSuggestionsModule contextualSuggestionsModule) {
        CustomTabActivityModule customTabsModule =
                new CustomTabActivityModule(mIntentDataProvider);
        CustomTabActivityComponent component = ChromeApplication.getComponent()
                .createCustomTabActivityComponent(commonsModule, contextualSuggestionsModule,
                        customTabsModule);

        mTabObserverRegistrar = component.resolveTabObserverRegistrar();
        mTabController = component.resolveTabController();
        mTabFactory = component.resolveTabFactory();

        if (mIntentDataProvider.isTrustedWebActivity()) {
            component.resolveTrustedWebActivityCoordinator();
        }

        return component;
    }
}
