// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.searchwidget;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.WindowDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.init.AsyncInitializationActivity;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.omnibox.AutocompleteController;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabIdManager;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.ActivityWindowAndroid;

/** Queries the user's default search engine and shows autocomplete suggestions. */
public class SearchActivity extends AsyncInitializationActivity
        implements SnackbarManageable, SearchActivityLocationBarLayout.Delegate,
                   View.OnLayoutChangeListener {

    /** Main content view. */
    private ViewGroup mContentView;

    /** Whether the native library has been loaded. */
    private boolean mIsNativeReady;

    /** Input submitted before before the native library was loaded. */
    private String mQueuedUrl;

    /** The View that represents the search box. */
    private SearchActivityLocationBarLayout mSearchBox;

    private SnackbarManager mSnackbarManager;
    private SearchBoxDataProvider mSearchBoxDataProvider;
    private Tab mTab;

    @Override
    public void backKeyPressed() {
        cancelSearch();
    }

    @Override
    protected boolean shouldDelayBrowserStartup() {
        return true;
    }

    @Override
    protected ActivityWindowAndroid createWindowAndroid() {
        return new ActivityWindowAndroid(this);
    }

    @Override
    protected void setContentView() {
        mSnackbarManager = new SnackbarManager(this, null);
        mSearchBoxDataProvider = new SearchBoxDataProvider();

        mContentView = createContentView();

        // Build the search box.
        mSearchBox = (SearchActivityLocationBarLayout) mContentView.findViewById(
                R.id.search_location_bar);
        mSearchBox.setDelegate(this);
        mSearchBox.setToolbarDataProvider(mSearchBoxDataProvider);
        mSearchBox.initializeControls(new WindowDelegate(getWindow()), getWindowAndroid());

        setContentView(mContentView);
    }

    @Override
    public void finishNativeInitialization() {
        super.finishNativeInitialization();
        mIsNativeReady = true;

        mTab = new Tab(TabIdManager.getInstance().generateValidId(Tab.INVALID_TAB_ID),
                Tab.INVALID_TAB_ID, false, this, getWindowAndroid(),
                TabLaunchType.FROM_EXTERNAL_APP, null, null);
        mTab.initialize(WebContentsFactory.createWebContents(false, false), null,
                new TabDelegateFactory(), false, false);
        mTab.loadUrl(new LoadUrlParams("about:blank"));

        mSearchBoxDataProvider.onNativeLibraryReady(mTab);
        mSearchBox.onNativeLibraryReady();

        if (mQueuedUrl != null) loadUrl(mQueuedUrl);

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                onDeferredStartup();
            }
        });
    }

    @Override
    public void onDeferredStartup() {
        super.onDeferredStartup();

        AutocompleteController.nativePrefetchZeroSuggestResults();
        CustomTabsConnection.getInstance(getApplication()).warmup(0);
        mSearchBox.onDeferredStartup(isVoiceSearchIntent());
    }

    @Override
    protected View getViewToBeDrawnBeforeInitializingNative() {
        return mSearchBox;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        beginQuery();
    }

    @Override
    public SnackbarManager getSnackbarManager() {
        return mSnackbarManager;
    }

    private boolean isVoiceSearchIntent() {
        return IntentUtils.safeGetBooleanExtra(
                getIntent(), SearchWidgetProvider.EXTRA_START_VOICE_SEARCH, false);
    }

    private void beginQuery() {
        mSearchBox.beginQuery(isVoiceSearchIntent());
    }

    @Override
    protected void onDestroy() {
        if (mTab != null && mTab.isInitialized()) mTab.destroy();
        super.onDestroy();
    }

    @Override
    public boolean shouldStartGpuProcess() {
        return true;
    }

    @Override
    public void loadUrl(String url) {
        // Wait until native has loaded.
        if (!mIsNativeReady) {
            mQueuedUrl = url;
            return;
        }

        // Don't do anything if the input was empty. This is done after the native check to prevent
        // resending a queued query after the user deleted it.
        if (TextUtils.isEmpty(url)) return;

        // Fix up the URL and send it to the full browser.
        String fixedUrl = UrlFormatter.fixupUrl(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(fixedUrl));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.setPackage(getPackageName());
        IntentHandler.addTrustedIntentExtras(intent);
        IntentUtils.safeStartActivity(this, intent,
                ActivityOptionsCompat
                        .makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
                        .toBundle());
        finish();
    }

    private ViewGroup createContentView() {
        assert mContentView == null;

        ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.search_activity, null, false);
        contentView.addOnLayoutChangeListener(this);
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelSearch();
            }
        });
        return contentView;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        mContentView.removeOnLayoutChangeListener(this);
        beginLoadingLibrary();
    }

    private void beginLoadingLibrary() {
        beginQuery();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSearchBox.showCachedZeroSuggestResultsIfAvailable();
            }
        });
        ChromeBrowserInitializer.getInstance(getApplicationContext())
                .handlePreNativeStartup(SearchActivity.this);
    }

    private void cancelSearch() {
        finish();
        overridePendingTransition(0, R.anim.activity_close_exit);
    }
}
