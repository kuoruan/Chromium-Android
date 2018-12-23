// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.preferences.PrefChangeRegistrar;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.ProfileSyncService.SyncStateChangedListener;

/**
 * Implementation of {@link EnabledStateMonitor}.
 */
public class EnabledStateMonitorImpl implements EnabledStateMonitor, SyncStateChangedListener,
        SignInStateObserver, TemplateUrlServiceObserver,
        PrefChangeRegistrar.PrefObserver {
    private final ObserverList<Observer> mObservers = new ObserverList<>();

    /** Whether contextual suggestions are enabled. */
    private boolean mEnabled;

    /** Whether the user settings for contextual suggestions are enabled. */
    private boolean mSettingsEnabled;

    public EnabledStateMonitorImpl() {
        // Assert that we don't need to check for the search engine promo to avoid needing to check
        // every time the default search engine is updated.
        assert !LocaleManager.getInstance().needToCheckForSearchEnginePromo();

        new PrefChangeRegistrar().addObserver(Pref.CONTEXTUAL_SUGGESTIONS_ENABLED, this);
        ProfileSyncService.get().addSyncStateChangedListener(this);
        SigninManager.get().addSignInStateObserver(this);
        TemplateUrlService.getInstance().addObserver(this);
        updateEnabledState();
        ContextualSuggestionsEnabledStateUtils.recordPreferenceEnabled(
                PrefServiceBridge.getInstance().getBoolean(Pref.CONTEXTUAL_SUGGESTIONS_ENABLED));
    }

    @Override
    public void addObserver(Observer observer) {
        mObservers.addObserver(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        mObservers.removeObserver(observer);
    }

    @Override
    public boolean getSettingsEnabled() {
        return ContextualSuggestionsEnabledStateUtils.getSettingsEnabled();
    }

    @Override
    public boolean getEnabledState() {
        return ContextualSuggestionsEnabledStateUtils.getEnabledState();
    }

    @Override
    public void syncStateChanged() {
        updateEnabledState();
    }

    @Override
    public void onSignedIn() {
        updateEnabledState();
    }

    @Override
    public void onSignedOut() {
        updateEnabledState();
    }

    @Override
    public void onTemplateURLServiceChanged() {
        updateEnabledState();
    }

    @Override
    public void onPreferenceChange() {
        updateEnabledState();
    }

    /**
     * Updates whether contextual suggestions are enabled. Notifies the observer if the
     * enabled state has changed.
     */
    private void updateEnabledState() {
        boolean previousSettingsState = mSettingsEnabled;
        boolean previousState = mEnabled;

        mSettingsEnabled = getSettingsEnabled();
        mEnabled = getEnabledState();

        if (mSettingsEnabled != previousSettingsState) {
            for (Observer observer : mObservers) {
                observer.onSettingsStateChanged(mSettingsEnabled);
            }
        }

        if (mEnabled != previousState) {
            for (Observer observer : mObservers) {
                observer.onEnabledStateChanged(mEnabled);
            }
            ContextualSuggestionsEnabledStateUtils.recordEnabled(mEnabled);
        }
    }
}
