// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.chromium.base.BuildInfo;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPreferences;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrl;
import org.chromium.chrome.browser.signin.SigninManager;

import java.util.HashMap;
import java.util.Map;

/**
 * The main settings screen, shown when the user first opens Settings.
 */
public class MainPreferences extends PreferenceFragment
        implements SigninManager.SignInStateObserver, TemplateUrlService.LoadListener {
    public static final String PREF_SIGN_IN = "sign_in";
    public static final String PREF_AUTOFILL_SETTINGS = "autofill_settings";
    public static final String PREF_SEARCH_ENGINE = "search_engine";
    public static final String PREF_SAVED_PASSWORDS = "saved_passwords";
    public static final String PREF_HOMEPAGE = "homepage";
    public static final String PREF_DATA_REDUCTION = "data_reduction";
    public static final String PREF_NOTIFICATIONS = "notifications";

    private final ManagedPreferenceDelegate mManagedPreferenceDelegate;
    private final Map<String, Preference> mAllPreferences = new HashMap<>();
    private SignInPreference mSignInPreference;

    public MainPreferences() {
        setHasOptionsMenu(true);
        mManagedPreferenceDelegate = createManagedPreferenceDelegate();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createPreferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPreference.onPreferenceFragmentDestroyed();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (SigninManager.get(getActivity()).isSigninSupported()) {
            SigninManager.get(getActivity()).addSignInStateObserver(this);
            mSignInPreference.registerForUpdates();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (SigninManager.get(getActivity()).isSigninSupported()) {
            SigninManager.get(getActivity()).removeSignInStateObserver(this);
            mSignInPreference.unregisterForUpdates();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    private void createPreferences() {
        PreferenceUtils.addPreferencesFromResource(this, R.xml.main_preferences);

        cachePreferences();
        setManagedPreferenceDelegateForPreference(PREF_SEARCH_ENGINE);
        setManagedPreferenceDelegateForPreference(PREF_AUTOFILL_SETTINGS);
        setManagedPreferenceDelegateForPreference(PREF_SAVED_PASSWORDS);
        setManagedPreferenceDelegateForPreference(PREF_DATA_REDUCTION);

        if (BuildInfo.isAtLeastO()) {
            // If we are on Android O+ the Notifications preference should lead to the Android
            // Settings notifications page, not to Chrome's notifications settings page.
            Preference notifications = findPreference(PREF_NOTIFICATIONS);
            notifications.setOnPreferenceClickListener(preference -> {
                // TODO(crbug.com/707804): Use Android O constants.
                Intent intent = new Intent();
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("android.provider.extra.APP_PACKAGE", BuildInfo.getPackageName());
                startActivity(intent);
                // We handle the click so the default action (opening NotificationsPreference)
                // isn't triggered.
                return true;
            });
        } else if (!ChromeFeatureList.isEnabled(
                           ChromeFeatureList.CONTENT_SUGGESTIONS_NOTIFICATIONS)) {
            // The Notifications Preferences page currently only contains the Content Suggestions
            // Notifications setting and a link to per-website notification settings. The latter can
            // be access through Site Settings, so if the Content Suggestions Notifications feature
            // isn't enabled we don't show the Notifications Preferences page.

            // This checks whether the Content Suggestions Notifications *feature* is enabled on the
            // user's device, not whether the user has Content Suggestions Notifications themselves
            // enabled (which is what the user can toggle on the Notifications Preferences page).
            getPreferenceScreen().removePreference(findPreference(PREF_NOTIFICATIONS));
        }

        if (!TemplateUrlService.getInstance().isLoaded()) {
            TemplateUrlService.getInstance().registerLoadListener(this);
            TemplateUrlService.getInstance().load();
        }
    }

    /**
     * Stores all preferences in memory so that, if they needed to be added/removed from the
     * PreferenceScreen, there would be no need to reload them from 'main_preferences.xml'.
     */
    private void cachePreferences() {
        int preferenceCount = getPreferenceScreen().getPreferenceCount();
        for (int index = 0; index < preferenceCount; index++) {
            Preference preference = getPreferenceScreen().getPreference(index);
            mAllPreferences.put(preference.getKey(), preference);
        }
        mSignInPreference = (SignInPreference) mAllPreferences.get(PREF_SIGN_IN);
    }

    private void setManagedPreferenceDelegateForPreference(String key) {
        ChromeBasePreference chromeBasePreference = (ChromeBasePreference) mAllPreferences.get(key);
        chromeBasePreference.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
    }

    private void updatePreferences() {
        if (SigninManager.get(getActivity()).isSigninSupported()) {
            addPreferenceIfAbsent(PREF_SIGN_IN);
        } else {
            removePreferenceIfPresent(PREF_SIGN_IN);
        }

        updateSearchEnginePreference();

        ChromeBasePreference passwordsPref =
                (ChromeBasePreference) findPreference(PREF_SAVED_PASSWORDS);
        setOnOffSummary(
                passwordsPref, PrefServiceBridge.getInstance().isRememberPasswordsEnabled());

        if (HomepageManager.shouldShowHomepageSetting()) {
            Preference homepagePref = addPreferenceIfAbsent(PREF_HOMEPAGE);
            setOnOffSummary(homepagePref,
                    HomepageManager.getInstance(getActivity()).getPrefHomepageEnabled());
        } else {
            removePreferenceIfPresent(PREF_HOMEPAGE);
        }

        ChromeBasePreference dataReduction =
                (ChromeBasePreference) findPreference(PREF_DATA_REDUCTION);
        dataReduction.setSummary(DataReductionPreferences.generateSummary(getResources()));
    }

    private Preference addPreferenceIfAbsent(String key) {
        Preference preference = getPreferenceScreen().findPreference(key);
        if (preference == null) getPreferenceScreen().addPreference(mAllPreferences.get(key));
        return mAllPreferences.get(key);
    }

    private void removePreferenceIfPresent(String key) {
        Preference preference = getPreferenceScreen().findPreference(key);
        if (preference != null) getPreferenceScreen().removePreference(preference);
    }

    private void updateSearchEnginePreference() {
        if (!TemplateUrlService.getInstance().isLoaded()) {
            ChromeBasePreference searchEnginePref =
                    (ChromeBasePreference) findPreference(PREF_SEARCH_ENGINE);
            searchEnginePref.setEnabled(false);
            return;
        }

        String defaultSearchEngineName = null;
        TemplateUrl dseTemplateUrl =
                TemplateUrlService.getInstance().getDefaultSearchEngineTemplateUrl();
        if (dseTemplateUrl != null) defaultSearchEngineName = dseTemplateUrl.getShortName();

        Preference searchEnginePreference = findPreference(PREF_SEARCH_ENGINE);
        searchEnginePreference.setEnabled(true);
        searchEnginePreference.setSummary(defaultSearchEngineName);
    }

    private void setOnOffSummary(Preference pref, boolean isOn) {
        pref.setSummary(getResources().getString(isOn ? R.string.text_on : R.string.text_off));
    }

    // SigninManager.SignInStateObserver implementation.
    @Override
    public void onSignedIn() {
        // After signing in or out of a managed account, preferences may change or become enabled
        // or disabled.
        new Handler().post(() -> updatePreferences());
    }

    @Override
    public void onSignedOut() {
        updatePreferences();
    }

    // TemplateUrlService.LoadListener implementation.
    @Override
    public void onTemplateUrlServiceLoaded() {
        TemplateUrlService.getInstance().unregisterLoadListener(this);
        updateSearchEnginePreference();
    }

    @VisibleForTesting
    ManagedPreferenceDelegate getManagedPreferenceDelegateForTest() {
        return mManagedPreferenceDelegate;
    }

    private ManagedPreferenceDelegate createManagedPreferenceDelegate() {
        return new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                if (PREF_AUTOFILL_SETTINGS.equals(preference.getKey())) {
                    return PersonalDataManager.isAutofillManaged();
                }
                if (PREF_SAVED_PASSWORDS.equals(preference.getKey())) {
                    return PrefServiceBridge.getInstance().isRememberPasswordsManaged();
                }
                if (PREF_DATA_REDUCTION.equals(preference.getKey())) {
                    return DataReductionProxySettings.getInstance().isDataReductionProxyManaged();
                }
                if (PREF_SEARCH_ENGINE.equals(preference.getKey())) {
                    return TemplateUrlService.getInstance().isDefaultSearchManaged();
                }
                return false;
            }

            @Override
            public boolean isPreferenceClickDisabledByPolicy(Preference preference) {
                if (PREF_AUTOFILL_SETTINGS.equals(preference.getKey())) {
                    return PersonalDataManager.isAutofillManaged()
                            && !PersonalDataManager.isAutofillEnabled();
                }
                if (PREF_SAVED_PASSWORDS.equals(preference.getKey())) {
                    PrefServiceBridge prefs = PrefServiceBridge.getInstance();
                    return prefs.isRememberPasswordsManaged()
                            && !prefs.isRememberPasswordsEnabled();
                }
                if (PREF_DATA_REDUCTION.equals(preference.getKey())) {
                    DataReductionProxySettings settings = DataReductionProxySettings.getInstance();
                    return settings.isDataReductionProxyManaged()
                            && !settings.isDataReductionProxyEnabled();
                }
                if (PREF_SEARCH_ENGINE.equals(preference.getKey())) {
                    return TemplateUrlService.getInstance().isDefaultSearchManaged();
                }
                return super.isPreferenceClickDisabledByPolicy(preference);
            }
        };
    }
}
