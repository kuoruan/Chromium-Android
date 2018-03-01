// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import org.chromium.base.VisibleForTesting;

/**
 * This collection of static methods provides reauthentication primitives for passwords
 * settings UI.
 */
public final class ReauthenticationManager {
    // Useful for retrieving the fragment in tests.
    @VisibleForTesting
    public static final String FRAGMENT_TAG = "reauthentication-manager-fragment";

    // Defines how long a successful reauthentication remains valid.
    private static final int VALID_REAUTHENTICATION_TIME_INTERVAL_MILLIS = 60000;

    // Used for verifying if the last successful reauthentication is still valid. The value of 0
    // means there was no successful reauthentication yet.
    private static long sLastReauthTimeMillis;

    /**
     * Stores the timestamp of last reauthentication of the user.
     */
    public static void setLastReauthTimeMillis(long value) {
        sLastReauthTimeMillis = value;
    }

    /**
     * Checks whether reauthentication is available on the device at all.
     * @return The result of the check.
     */
    public static boolean isReauthenticationApiAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * Initiates the reauthentication prompt with a given description.
     *
     * @param descriptionId   The resource ID of the string to be displayed to explain the reason
     *                        for the reauthentication.
     * @param containerViewId The ID of the container, fragments of which will get replaced with the
     *                        reauthentication prompt. It may be equal to View.NO_ID in tests.
     * @param fragmentManager For putting the lock screen on the transaction stack.
     */
    public static void displayReauthenticationFragment(
            int descriptionId, int containerViewId, FragmentManager fragmentManager) {
        Fragment passwordReauthentication = new PasswordReauthenticationFragment();
        Bundle args = new Bundle();
        args.putInt(PasswordReauthenticationFragment.DESCRIPTION_ID, descriptionId);
        passwordReauthentication.setArguments(args);

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (containerViewId == View.NO_ID) {
            fragmentTransaction.add(passwordReauthentication, FRAGMENT_TAG);
        } else {
            fragmentTransaction.replace(containerViewId, passwordReauthentication, FRAGMENT_TAG);
        }
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    /** Checks whether authentication is recent enought to be valid. */
    public static boolean authenticationStillValid() {
        return sLastReauthTimeMillis != 0
                && (System.currentTimeMillis() - sLastReauthTimeMillis)
                < VALID_REAUTHENTICATION_TIME_INTERVAL_MILLIS;
    }
}
