// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;

import java.util.List;

/** A provider that notifies its observers when the number of tabs changes. */
public class TabCountProvider {
    /** An observer that is notified of changes to the number of open tabs. */
    public interface TabCountObserver {
        /**
         * @param tabCount Number of open tabs in the selected tab model.
         * @param isIncognito Whether the selected tab model is incognito.
         */
        void onTabCountChanged(int tabCount, boolean isIncognito);
    }

    /** List of {@link TabCountObserver}s. These are used to broadcast events to listeners. */
    private final ObserverList<TabCountObserver> mTabCountObservers;

    /** The {@link TabModelSelector} this class will observe. */
    private TabModelSelector mTabModelSelector;

    /** The {@link TabModelSelectorObserver} that observes when the tab count may have changed. */
    private TabModelSelectorObserver mTabModelSelectorObserver;

    /**
     *  The {@link TabModelSelectorTabModelObserver} that observes when the tab count may have
     *  changed.
     */
    private TabModelSelectorTabModelObserver mTabModelSelectorTabModelObserver;

    private int mTabCount;

    private boolean mIsIncognito;

    TabCountProvider() {
        mTabCountObservers = new ObserverList<TabCountObserver>();
    }

    /**
     * @param observer The observer to add.
     */
    public void addObserver(TabCountObserver observer) {
        mTabCountObservers.addObserver(observer);
    }

    /**
     * Adds an observer and triggers the {@link TabCountObserver#onTabCountChanged(int, boolean)}
     * for the added observer.
     * @param observer The observer to add.
     */
    public void addObserverAndTrigger(TabCountObserver observer) {
        addObserver(observer);

        if (mTabModelSelector != null) {
            observer.onTabCountChanged(mTabModelSelector.getCurrentModel().getCount(),
                    mTabModelSelector.isIncognitoSelected());
        }
    }

    /**
     * @param observer The observer to remove.
     */
    public void removeObserver(TabCountObserver observer) {
        mTabCountObservers.removeObserver(observer);
    }

    /**
     * @param tabModelSelector The {@link TabModelSelectorObserver} that observes when the tab count
     *                         may have changed.
     */
    void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                updateTabCount();
            }

            @Override
            public void onTabStateInitialized() {
                updateTabCount();
            }
        };
        mTabModelSelector.addObserver(mTabModelSelectorObserver);

        mTabModelSelectorTabModelObserver =
                new TabModelSelectorTabModelObserver(mTabModelSelector) {
                    @Override
                    public void didAddTab(Tab tab, @TabLaunchType int type) {
                        updateTabCount();
                    }

                    @Override
                    public void tabClosureUndone(Tab tab) {
                        updateTabCount();
                    }

                    @Override
                    public void didCloseTab(int tabId, boolean incognito) {
                        updateTabCount();
                    }

                    @Override
                    public void tabPendingClosure(Tab tab) {
                        updateTabCount();
                    }

                    @Override
                    public void allTabsPendingClosure(List<Tab> tabs) {
                        updateTabCount();
                    }

                    @Override
                    public void tabRemoved(Tab tab) {
                        updateTabCount();
                    }
                };

        updateTabCount();
    }

    /**
     * Clean up any state when the TabCountProvider is destroyed.
     */
    void destroy() {
        if (mTabModelSelector != null) {
            mTabModelSelector.removeObserver(mTabModelSelectorObserver);
            mTabModelSelector = null;
        }
        if (mTabModelSelectorTabModelObserver != null) {
            mTabModelSelectorTabModelObserver.destroy();
            mTabModelSelectorTabModelObserver = null;
        }
        mTabCountObservers.clear();
    }

    private void updateTabCount() {
        final int tabCount = mTabModelSelector.getCurrentModel().getCount();
        final boolean isIncognito = mTabModelSelector.isIncognitoSelected();

        if (mTabCount == tabCount && mIsIncognito == isIncognito) return;

        mTabCount = tabCount;
        mIsIncognito = isIncognito;

        for (TabCountObserver observer : mTabCountObservers) {
            observer.onTabCountChanged(tabCount, isIncognito);
        }
    }
}
