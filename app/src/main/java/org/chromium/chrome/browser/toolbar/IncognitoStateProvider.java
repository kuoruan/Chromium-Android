// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v7.content.res.AppCompatResources;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ObserverList;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.toolbar.ThemeColorProvider.ThemeColorObserver;

/** A provider that notifies its observers when incognito mode is entered or exited. */
public class IncognitoStateProvider implements ThemeColorProvider {
    /**
     * An interface to be notified when incognito mode is entered or left.
     */
    public interface IncognitoStateObserver { void onIncognitoStateChanged(boolean isIncognito); }

    /** List of {@link IncognitoStateObserver}s. These are used to broadcast events to listeners. */
    private final ObserverList<ThemeColorObserver> mThemeColorObservers;

    /** List of {@link IncognitoStateObserver}s. These are used to broadcast events to listeners. */
    private final ObserverList<IncognitoStateObserver> mIncognitoStateObservers;

    /** Tint to be used in incognito mode. */
    private final ColorStateList mIncognitoTint;

    /** Tint to be used in normal mode. */
    private final ColorStateList mNormalTint;

    /** Primary color for normal mode. */
    private final int mNormalPrimaryColor;

    /** Primary color for incognito mode. */
    private final int mIncognitoPrimaryColor;

    /** A {@link TabModelSelectorObserver} used to know when incognito mode is entered or exited. */
    private final TabModelSelectorObserver mTabModelSelectorObserver;

    /** A {@link TabModelSelector} used to know when incognito mode is entered or exited. */
    private TabModelSelector mTabModelSelector;

    public IncognitoStateProvider(Context context) {
        mThemeColorObservers = new ObserverList<ThemeColorObserver>();
        mIncognitoStateObservers = new ObserverList<IncognitoStateObserver>();

        mIncognitoTint = AppCompatResources.getColorStateList(context, R.color.light_mode_tint);
        mNormalTint = AppCompatResources.getColorStateList(context, R.color.dark_mode_tint);
        mNormalPrimaryColor = ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.modern_primary_color);
        mIncognitoPrimaryColor = ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.incognito_modern_primary_color);

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                incognitoStateChanged(newModel.isIncognito());
            }
        };
    }

    /**
     * @param observer Add an observer that will have events broadcast to.
     */
    public void addObserver(IncognitoStateObserver observer) {
        mIncognitoStateObservers.addObserver(observer);
    }

    /**
     * @param observer Remove the observer.
     */
    public void removeObserver(IncognitoStateObserver observer) {
        mIncognitoStateObservers.removeObserver(observer);
    }

    @Override
    public void addObserver(ThemeColorObserver observer) {
        mThemeColorObservers.addObserver(observer);
    }

    @Override
    public void removeObserver(ThemeColorObserver observer) {
        mThemeColorObservers.removeObserver(observer);
    }

    void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;
        mTabModelSelector.addObserver(mTabModelSelectorObserver);
        incognitoStateChanged(mTabModelSelector.isIncognitoSelected());
    }

    void destroy() {
        if (mTabModelSelector != null) {
            mTabModelSelector.removeObserver(mTabModelSelectorObserver);
            mTabModelSelector = null;
        }
        mThemeColorObservers.clear();
        mIncognitoStateObservers.clear();
    }

    private void incognitoStateChanged(boolean isIncognito) {
        for (IncognitoStateObserver observer : mIncognitoStateObservers) {
            observer.onIncognitoStateChanged(isIncognito);
        }
        final ColorStateList tint = isIncognito ? mIncognitoTint : mNormalTint;
        final int primaryColor = isIncognito ? mIncognitoPrimaryColor : mNormalPrimaryColor;
        for (ThemeColorObserver observer : mThemeColorObservers) {
            observer.onThemeColorChanged(tint, primaryColor);
        }
    }
}
