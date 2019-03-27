// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.accounts.Account;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.chromium.base.BuildInfo;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsEnabledStateUtils;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchFieldTrial;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.invalidation.InvalidationController;
import org.chromium.chrome.browser.metrics.UmaSessionStats;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SignoutReason;
import org.chromium.chrome.browser.signin.UnifiedConsentServiceBridge;
import org.chromium.chrome.browser.sync.GoogleServiceAuthError;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.ui.PassphraseDialogFragment;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.components.signin.AccountManagerFacade;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;
import org.chromium.components.sync.ProtocolErrorClientAction;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Settings fragment to enable Sync and other services that communicate with Google.
 */
public class SyncAndServicesPreferences extends PreferenceFragment
        implements PassphraseDialogFragment.Listener, Preference.OnPreferenceChangeListener,
                   ProfileSyncService.SyncStateChangedListener {
    private static final String IS_FROM_SIGNIN_SCREEN =
            "SyncAndServicesPreferences.isFromSigninScreen";

    @VisibleForTesting
    public static final String FRAGMENT_ENTER_PASSPHRASE = "enter_password";

    private static final String PREF_SIGNIN = "sign_in";

    private static final String PREF_SYNC_CATEGORY = "sync_category";
    private static final String PREF_SYNC_ERROR_CARD = "sync_error_card";
    private static final String PREF_SYNC_REQUESTED = "sync_requested";

    private static final String PREF_SERVICES_CATEGORY = "services_category";
    private static final String PREF_SEARCH_SUGGESTIONS = "search_suggestions";
    private static final String PREF_NETWORK_PREDICTIONS = "network_predictions";
    private static final String PREF_NAVIGATION_ERROR = "navigation_error";
    private static final String PREF_SAFE_BROWSING = "safe_browsing";
    private static final String PREF_SAFE_BROWSING_SCOUT_REPORTING =
            "safe_browsing_scout_reporting";
    private static final String PREF_USAGE_AND_CRASH_REPORTING = "usage_and_crash_reports";
    private static final String PREF_URL_KEYED_ANONYMIZED_DATA = "url_keyed_anonymized_data";
    private static final String PREF_CONTEXTUAL_SEARCH = "contextual_search";
    private static final String PREF_CONTEXTUAL_SUGGESTIONS = "contextual_suggestions";

    @IntDef({SyncError.NO_ERROR, SyncError.ANDROID_SYNC_DISABLED, SyncError.AUTH_ERROR,
            SyncError.PASSPHRASE_REQUIRED, SyncError.CLIENT_OUT_OF_DATE, SyncError.OTHER_ERRORS})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SyncError {
        int NO_ERROR = -1;
        int ANDROID_SYNC_DISABLED = 0;
        int AUTH_ERROR = 1;
        int PASSPHRASE_REQUIRED = 2;
        int CLIENT_OUT_OF_DATE = 3;
        int OTHER_ERRORS = 128;
    }

    private final ProfileSyncService mProfileSyncService = ProfileSyncService.get();
    private final PrefServiceBridge mPrefServiceBridge = PrefServiceBridge.getInstance();
    private final PrivacyPreferencesManager mPrivacyPrefManager =
            PrivacyPreferencesManager.getInstance();
    private final ManagedPreferenceDelegate mManagedPreferenceDelegate =
            createManagedPreferenceDelegate();

    private boolean mIsFromSigninScreen;

    private SignInPreference mSigninPreference;

    private PreferenceCategory mSyncCategory;
    private Preference mSyncErrorCard;
    private ChromeSwitchPreference mSyncRequested;

    private ChromeSwitchPreference mSearchSuggestions;
    private ChromeSwitchPreference mNetworkPredictions;
    private ChromeSwitchPreference mNavigationError;
    private ChromeSwitchPreference mSafeBrowsing;
    private ChromeSwitchPreference mSafeBrowsingReporting;
    private ChromeSwitchPreference mUsageAndCrashReporting;
    private ChromeSwitchPreference mUrlKeyedAnonymizedData;
    private @Nullable Preference mContextualSearch;
    private @Nullable Preference mContextualSuggestions;

    private ProfileSyncService.SyncSetupInProgressHandle mSyncSetupInProgressHandle;

    private @SyncError int mCurrentSyncError = SyncError.NO_ERROR;

    /**
     * Creates an argument bundle for this fragment.
     * @param isFromSigninScreen Whether the screen is started from the sign-in screen.
     */
    public static Bundle createArguments(boolean isFromSigninScreen) {
        Bundle result = new Bundle();
        result.putBoolean(IS_FROM_SIGNIN_SCREEN, isFromSigninScreen);
        return result;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsFromSigninScreen =
                IntentUtils.safeGetBoolean(getArguments(), IS_FROM_SIGNIN_SCREEN, false);

        mPrivacyPrefManager.migrateNetworkPredictionPreferences();

        getActivity().setTitle(R.string.prefs_sync_and_services);
        setHasOptionsMenu(true);

        PreferenceUtils.addPreferencesFromResource(this, R.xml.sync_and_services_preferences);

        mSigninPreference = (SignInPreference) findPreference(PREF_SIGNIN);
        mSigninPreference.setPersonalizedPromoEnabled(false);

        mSyncCategory = (PreferenceCategory) findPreference(PREF_SYNC_CATEGORY);
        mSyncErrorCard = findPreference(PREF_SYNC_ERROR_CARD);
        mSyncErrorCard.setOnPreferenceClickListener(
                SyncPreferenceUtils.toOnClickListener(this, this::onSyncErrorCardClicked));
        mSyncRequested = (ChromeSwitchPreference) findPreference(PREF_SYNC_REQUESTED);
        mSyncRequested.setOnPreferenceChangeListener(this);

        mSearchSuggestions = (ChromeSwitchPreference) findPreference(PREF_SEARCH_SUGGESTIONS);
        mSearchSuggestions.setOnPreferenceChangeListener(this);
        mSearchSuggestions.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mNetworkPredictions = (ChromeSwitchPreference) findPreference(PREF_NETWORK_PREDICTIONS);
        mNetworkPredictions.setOnPreferenceChangeListener(this);
        mNetworkPredictions.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mNavigationError = (ChromeSwitchPreference) findPreference(PREF_NAVIGATION_ERROR);
        mNavigationError.setOnPreferenceChangeListener(this);
        mNavigationError.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mSafeBrowsing = (ChromeSwitchPreference) findPreference(PREF_SAFE_BROWSING);
        mSafeBrowsing.setOnPreferenceChangeListener(this);
        mSafeBrowsing.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mSafeBrowsingReporting =
                (ChromeSwitchPreference) findPreference(PREF_SAFE_BROWSING_SCOUT_REPORTING);
        mSafeBrowsingReporting.setOnPreferenceChangeListener(this);
        mSafeBrowsingReporting.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mUsageAndCrashReporting =
                (ChromeSwitchPreference) findPreference(PREF_USAGE_AND_CRASH_REPORTING);
        mUsageAndCrashReporting.setOnPreferenceChangeListener(this);
        mUsageAndCrashReporting.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mUrlKeyedAnonymizedData =
                (ChromeSwitchPreference) findPreference(PREF_URL_KEYED_ANONYMIZED_DATA);
        mUrlKeyedAnonymizedData.setOnPreferenceChangeListener(this);
        mUrlKeyedAnonymizedData.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        PreferenceCategory servicesCategory =
                (PreferenceCategory) findPreference(PREF_SERVICES_CATEGORY);
        mContextualSearch = findPreference(PREF_CONTEXTUAL_SEARCH);
        if (!ContextualSearchFieldTrial.isEnabled()) {
            removePreference(servicesCategory, mContextualSearch);
            mContextualSearch = null;
        }

        mContextualSuggestions = findPreference(PREF_CONTEXTUAL_SUGGESTIONS);
        if (!FeatureUtilities.areContextualSuggestionsEnabled(getActivity())
                || !ContextualSuggestionsEnabledStateUtils.shouldShowSettings()) {
            removePreference(servicesCategory, mContextualSuggestions);
            mContextualSuggestions = null;
        }

        // Prevent sync settings changes from taking effect until the user leaves this screen.
        mSyncSetupInProgressHandle = mProfileSyncService.getSetupInProgressHandle();

        updatePreferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSyncSetupInProgressHandle.close();

        if (mProfileSyncService.isSyncRequested()) {
            InvalidationController.get().ensureStartedAndUpdateRegisteredTypes();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        MenuItem help =
                menu.add(Menu.NONE, R.id.menu_id_targeted_help, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_id_targeted_help) {
            HelpAndFeedback.getInstance(getActivity())
                    .show(getActivity(), getString(R.string.help_context_sync_and_services),
                            Profile.getLastUsedProfile(), null);
            return true;
        }
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        mProfileSyncService.addSyncStateChangedListener(this);
        mSigninPreference.registerForUpdates();
    }

    @Override
    public void onStop() {
        super.onStop();

        mSigninPreference.unregisterForUpdates();
        mProfileSyncService.removeSyncStateChangedListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PREF_SYNC_REQUESTED.equals(key)) {
            assert canDisableSync();
            SyncPreferenceUtils.enableSync((boolean) newValue);
            ThreadUtils.postOnUiThread(this::updatePreferences);
        } else if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
            mPrefServiceBridge.setSearchSuggestEnabled((boolean) newValue);
        } else if (PREF_SAFE_BROWSING.equals(key)) {
            mPrefServiceBridge.setSafeBrowsingEnabled((boolean) newValue);
        } else if (PREF_SAFE_BROWSING_SCOUT_REPORTING.equals(key)) {
            mPrefServiceBridge.setSafeBrowsingExtendedReportingEnabled((boolean) newValue);
        } else if (PREF_NETWORK_PREDICTIONS.equals(key)) {
            mPrefServiceBridge.setNetworkPredictionEnabled((boolean) newValue);
            recordNetworkPredictionEnablingUMA((boolean) newValue);
        } else if (PREF_NAVIGATION_ERROR.equals(key)) {
            mPrefServiceBridge.setResolveNavigationErrorEnabled((boolean) newValue);
        } else if (PREF_USAGE_AND_CRASH_REPORTING.equals(key)) {
            UmaSessionStats.changeMetricsReportingConsent((boolean) newValue);
        } else if (PREF_URL_KEYED_ANONYMIZED_DATA.equals(key)) {
            UnifiedConsentServiceBridge.setUrlKeyedAnonymizedDataCollectionEnabled(
                    (boolean) newValue);
        }
        return true;
    }

    /**
     * ProfileSyncService.SyncStateChangedListener implementation, listens to sync state changes.
     *
     * If the user has just turned on sync, this listener is needed in order to enable
     * the encryption settings once the engine has initialized.
     */
    @Override
    public void syncStateChanged() {
        updatePreferences();
    }

    /** Returns whether Sync can be disabled. */
    private boolean canDisableSync() {
        return !Profile.getLastUsedProfile().isChild();
    }

    private void displayPassphraseDialog() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        PassphraseDialogFragment.newInstance(this).show(ft, FRAGMENT_ENTER_PASSPHRASE);
    }

    private void closeDialogIfOpen(String tag) {
        FragmentManager manager = getFragmentManager();
        if (manager == null) {
            // Do nothing if the manager doesn't exist yet; see http://crbug.com/480544.
            return;
        }
        DialogFragment df = (DialogFragment) manager.findFragmentByTag(tag);
        if (df != null) {
            df.dismiss();
        }
    }

    /** Returns whether the passphrase successfully decrypted the pending keys. */
    private boolean handleDecryption(String passphrase) {
        if (passphrase.isEmpty() || !mProfileSyncService.setDecryptionPassphrase(passphrase)) {
            return false;
        }
        updatePreferences();
        return true;
    }

    /** Callback for PassphraseDialogFragment.Listener */
    @Override
    public boolean onPassphraseEntered(String passphrase) {
        if (!mProfileSyncService.isEngineInitialized()
                || !mProfileSyncService.isPassphraseRequiredForDecryption()) {
            // If the engine was shut down since the dialog was opened, or the passphrase isn't
            // required anymore, do nothing.
            return false;
        }
        return handleDecryption(passphrase);
    }

    /** Callback for PassphraseDialogFragment.Listener */
    @Override
    public void onPassphraseCanceled() {}

    @SyncError
    private int getSyncError() {
        if (!AndroidSyncSettings.get().isMasterSyncEnabled()) {
            return SyncError.ANDROID_SYNC_DISABLED;
        }

        if (!AndroidSyncSettings.get().isChromeSyncEnabled()) {
            return SyncError.NO_ERROR;
        }

        if (mProfileSyncService.getAuthError()
                == GoogleServiceAuthError.State.INVALID_GAIA_CREDENTIALS) {
            return SyncError.AUTH_ERROR;
        }

        if (mProfileSyncService.getProtocolErrorClientAction()
                == ProtocolErrorClientAction.UPGRADE_CLIENT) {
            return SyncError.CLIENT_OUT_OF_DATE;
        }

        if (mProfileSyncService.getAuthError() != GoogleServiceAuthError.State.NONE
                || mProfileSyncService.hasUnrecoverableError()) {
            return SyncError.OTHER_ERRORS;
        }

        if (mProfileSyncService.isEngineInitialized()
                && mProfileSyncService.isPassphraseRequiredForDecryption()) {
            return SyncError.PASSPHRASE_REQUIRED;
        }

        return SyncError.NO_ERROR;
    }

    /**
     * Gets hint message to resolve sync error.
     * @param error The sync error.
     */
    private String getSyncErrorHint(@SyncError int error) {
        Resources res = getActivity().getResources();
        switch (error) {
            case SyncError.ANDROID_SYNC_DISABLED:
                return res.getString(R.string.hint_android_sync_disabled);
            case SyncError.AUTH_ERROR:
                return res.getString(R.string.hint_sync_auth_error);
            case SyncError.CLIENT_OUT_OF_DATE:
                return res.getString(
                        R.string.hint_client_out_of_date, BuildInfo.getInstance().hostPackageLabel);
            case SyncError.OTHER_ERRORS:
                return res.getString(R.string.hint_other_sync_errors);
            case SyncError.PASSPHRASE_REQUIRED:
                return res.getString(R.string.hint_passphrase_required);
            case SyncError.NO_ERROR:
            default:
                return null;
        }
    }

    private void onSyncErrorCardClicked() {
        if (mCurrentSyncError == SyncError.NO_ERROR) {
            return;
        }

        if (mCurrentSyncError == SyncError.ANDROID_SYNC_DISABLED) {
            IntentUtils.safeStartActivity(getActivity(), new Intent(Settings.ACTION_SYNC_SETTINGS));
            return;
        }

        if (mCurrentSyncError == SyncError.AUTH_ERROR) {
            AccountManagerFacade.get().updateCredentials(
                    ChromeSigninController.get().getSignedInUser(), getActivity(), null);
            return;
        }

        if (mCurrentSyncError == SyncError.CLIENT_OUT_OF_DATE) {
            // Opens the client in play store for update.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id="
                    + ContextUtils.getApplicationContext().getPackageName()));
            startActivity(intent);
            return;
        }

        if (mCurrentSyncError == SyncError.OTHER_ERRORS) {
            final Account account = ChromeSigninController.get().getSignedInUser();
            // TODO(https://crbug.com/873116): Pass the correct reason for the signout.
            SigninManager.get().signOut(SignoutReason.USER_CLICKED_SIGNOUT_SETTINGS,
                    () -> SigninManager.get().signIn(account, null, null));
            return;
        }

        if (mCurrentSyncError == SyncError.PASSPHRASE_REQUIRED) {
            displayPassphraseDialog();
            return;
        }
    }

    private void recordNetworkPredictionEnablingUMA(boolean enabled) {
        // Report user turning on and off NetworkPrediction.
        RecordHistogram.recordBooleanHistogram("PrefService.NetworkPredictionEnabled", enabled);
    }

    private static void removePreference(PreferenceGroup from, Preference preference) {
        boolean found = from.removePreference(preference);
        assert found : "Don't have such preference! Preference key: " + preference.getKey();
    }

    private void updatePreferences() {
        updateSyncPreferences();

        mSearchSuggestions.setChecked(mPrefServiceBridge.isSearchSuggestEnabled());
        mNetworkPredictions.setChecked(mPrefServiceBridge.getNetworkPredictionEnabled());
        mNavigationError.setChecked(mPrefServiceBridge.isResolveNavigationErrorEnabled());
        mSafeBrowsing.setChecked(mPrefServiceBridge.isSafeBrowsingEnabled());
        mSafeBrowsingReporting.setChecked(
                mPrefServiceBridge.isSafeBrowsingExtendedReportingEnabled());
        mUsageAndCrashReporting.setChecked(
                mPrivacyPrefManager.isUsageAndCrashReportingPermittedByUser());
        mUrlKeyedAnonymizedData.setChecked(
                UnifiedConsentServiceBridge.isUrlKeyedAnonymizedDataCollectionEnabled());

        if (mContextualSearch != null) {
            boolean isContextualSearchEnabled = !mPrefServiceBridge.isContextualSearchDisabled();
            mContextualSearch.setSummary(
                    isContextualSearchEnabled ? R.string.text_on : R.string.text_off);
        }

        if (mContextualSuggestions != null) {
            mContextualSuggestions.setSummary(
                    ContextualSuggestionsEnabledStateUtils.getEnabledState() ? R.string.text_on
                                                                             : R.string.text_off);
        }
    }

    private void updateSyncPreferences() {
        if (!mProfileSyncService.isEngineInitialized()
                || !mProfileSyncService.isPassphraseRequiredForDecryption()) {
            closeDialogIfOpen(FRAGMENT_ENTER_PASSPHRASE);
        }

        if (!ChromeSigninController.get().isSignedIn()) {
            getPreferenceScreen().removePreference(mSyncCategory);
            return;
        }
        getPreferenceScreen().addPreference(mSyncCategory);

        mCurrentSyncError = getSyncError();
        if (mCurrentSyncError == SyncError.NO_ERROR) {
            mSyncCategory.removePreference(mSyncErrorCard);
        } else {
            String summary = getSyncErrorHint(mCurrentSyncError);
            mSyncErrorCard.setSummary(summary);
            mSyncCategory.addPreference(mSyncErrorCard);
        }

        mSyncRequested.setChecked(AndroidSyncSettings.get().isChromeSyncEnabled());
        mSyncRequested.setEnabled(canDisableSync());
    }

    private ManagedPreferenceDelegate createManagedPreferenceDelegate() {
        return preference -> {
            String key = preference.getKey();
            if (PREF_NAVIGATION_ERROR.equals(key)) {
                return mPrefServiceBridge.isResolveNavigationErrorManaged();
            }
            if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
                return mPrefServiceBridge.isSearchSuggestManaged();
            }
            if (PREF_SAFE_BROWSING_SCOUT_REPORTING.equals(key)) {
                return mPrefServiceBridge.isSafeBrowsingExtendedReportingManaged();
            }
            if (PREF_SAFE_BROWSING.equals(key)) {
                return mPrefServiceBridge.isSafeBrowsingManaged();
            }
            if (PREF_NETWORK_PREDICTIONS.equals(key)) {
                return mPrefServiceBridge.isNetworkPredictionManaged();
            }
            if (PREF_USAGE_AND_CRASH_REPORTING.equals(key)) {
                return mPrefServiceBridge.isMetricsReportingManaged();
            }
            if (PREF_URL_KEYED_ANONYMIZED_DATA.equals(key)) {
                return UnifiedConsentServiceBridge.isUrlKeyedAnonymizedDataCollectionManaged();
            }
            return false;
        };
    }
}
