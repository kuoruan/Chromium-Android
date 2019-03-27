// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tabmodel.SingleTabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * Base class for task-focused activities that need to display a single tab.
 *
 * Example applications that might use this Activity would be webapps and streaming media
 * activities - anything where maintaining multiple tabs is unnecessary.
 */
public abstract class SingleTabActivity extends ChromeActivity {
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected TabModelSelector createTabModelSelector() {
        return new SingleTabModelSelector(this, false, false) {
            @Override
            public Tab openNewTab(LoadUrlParams loadUrlParams, @TabLaunchType int type, Tab parent,
                    boolean incognito) {
                getTabCreator(incognito).createNewTab(loadUrlParams, type, parent);
                return null;
            }
        };
    }

    @Override
    protected Pair<TabDelegate, TabDelegate> createTabCreators() {
        return Pair.create(createTabDelegate(false), createTabDelegate(true));
    }

    /** Creates TabDelegates for opening new Tabs. */
    protected TabDelegate createTabDelegate(boolean incognito) {
        return new TabDelegate(incognito);
    }

    @Override
    public void initializeState() {
        super.initializeState();

        createAndShowTab();
    }

    protected void createAndShowTab() {
        Tab tab = createTab();
        getTabModelSelector().setTab(tab);
        tab.show(TabSelectionType.FROM_NEW);
    }

    @Override
    public SingleTabModelSelector getTabModelSelector() {
        return (SingleTabModelSelector) super.getTabModelSelector();
    }

    /**
     * Creates the {@link Tab} used by the {@link SingleTabActivity}.
     * If the {@code savedInstanceState} exists, then the user did not intentionally close the app
     * by swiping it away in the recent tasks list.  In that case, we try to restore the tab from
     * disk.
     */
    protected Tab createTab() {
        Tab tab = null;
        boolean unfreeze = false;

        if (getSavedInstanceState() != null) {
            tab = restoreTab(getSavedInstanceState());
            if (tab != null) unfreeze = true;
        }

        if (tab == null) {
            tab = new Tab(Tab.INVALID_TAB_ID, Tab.INVALID_TAB_ID, false, getWindowAndroid(),
                    TabLaunchType.FROM_CHROME_UI, null, null);
        }

        tab.initialize(null, getTabContentManager(), createTabDelegateFactory(), false, unfreeze);
        return tab;
    }

    /**
     * @return {@link TabDelegateFactory} to be used while creating the associated {@link Tab}.
     */
    protected TabDelegateFactory createTabDelegateFactory() {
        return new TabDelegateFactory();
    }

    protected abstract Tab restoreTab(Bundle savedInstanceState);

    private boolean supportsAppSwitcher() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                || KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_APP_SWITCH);
    }

    @Override
    protected boolean handleBackPressed() {
        Tab tab = getActivityTab();
        if (tab == null) return false;

        if (exitFullscreenIfShowing()) return true;

        if (tab.canGoBack()) {
            tab.goBack();
            return true;
        }

        if (!supportsAppSwitcher()) {
            // If the device has no way to get to the task switcher, we don't want the default back
            // button behavior of finishing the Activity. If the device is low on memory LMK will
            // kill us, and if not, we'll start up faster when returned to.
            moveTaskToBack(true);
            return true;
        }
        return false;
    }

    @Override
    public void onUpdateStateChanged() {}
}
