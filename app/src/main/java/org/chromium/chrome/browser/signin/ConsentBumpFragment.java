// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.SyncAndServicesPreferences;

/**
 * This fragment implements the consent bump screen. This is a variation of the sign-in screen that
 * provides an easy option to enable all features gated by unified consent. This screen is shown to
 * users who are already signed in and have Sync enabled, so there's no need to sign in users.
 */
public class ConsentBumpFragment extends SigninFragmentBase {
    /**
     * Creates arguments for instantiating this fragment.
     * @param accountName The name of the account to show in the consent bump
     * @return The bundle to pass to {@link Fragment#setArguments} or {@link Fragment#instantiate}
     */
    public static Bundle createArguments(String accountName) {
        return createArgumentsForConsentBumpFlow(accountName);
    }

    // Every fragment must have a public default constructor.
    public ConsentBumpFragment() {}

    @Override
    protected Bundle getSigninArguments() {
        return getArguments();
    }

    @Override
    protected void onSigninRefused() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        // Get the id of the view that contains this fragment and replace the fragment.
        @IdRes int containerId = ((ViewGroup) getView().getParent()).getId();
        transaction.replace(containerId, new ConsentBumpMoreOptionsFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    protected void onSigninAccepted(String accountName, boolean isDefaultAccount,
            boolean settingsClicked, Runnable callback) {
        UnifiedConsentServiceBridge.setUnifiedConsentGiven(true);
        if (settingsClicked) {
            PreferencesLauncher.launchSettingsPage(
                    getActivity(), SyncAndServicesPreferences.class.getName());
        }
        getActivity().finish();
        callback.run();
    }

    @Override
    @StringRes
    protected int getTitleTextId() {
        return R.string.consent_bump_title;
    }

    @Override
    @StringRes
    protected int getNegativeButtonTextId() {
        return R.string.consent_bump_more_options;
    }
}
