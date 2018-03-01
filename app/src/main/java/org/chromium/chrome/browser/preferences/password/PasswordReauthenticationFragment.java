// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;

/** Show the lock screen confirmation and lock the screen. */
public class PasswordReauthenticationFragment extends Fragment {
    // The key for the description argument, which is used to retrieve an explanation of the
    // reauthentication prompt to the user.
    public static final String DESCRIPTION_ID = "description";

    protected static final int CONFIRM_DEVICE_CREDENTIAL_REQUEST_CODE = 2;

    private static boolean sPreventLockDevice = false;

    private FragmentManager mFragmentManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFragmentManager = getFragmentManager();
        if (!sPreventLockDevice) {
            lockDevice();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIRM_DEVICE_CREDENTIAL_REQUEST_CODE) {
            if (resultCode == getActivity().RESULT_OK) {
                ReauthenticationManager.setLastReauthTimeMillis(System.currentTimeMillis());
                mFragmentManager.popBackStack();
            }
        }
    }

    /**
     * Prevent calling the {@link #lockDevice} method in {@link #onCreate}.
     */
    public static void preventLockingForTesting() {
        sPreventLockDevice = true;
    }

    /**
     * Should only be called on Lollipop or above devices.
     */
    @TargetApi(VERSION_CODES.LOLLIPOP)
    private void lockDevice() {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        KeyguardManager keyguardManager =
                (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        final int resourceId = getArguments().getInt(DESCRIPTION_ID, 0);
        // Forgetting to set the DESCRIPTION_ID is an error on the callsite.
        assert resourceId != 0;
        // Set title to null to use the system default title which is adapted to the particular type
        // of device lock which the user set up.
        Intent intent =
                keyguardManager.createConfirmDeviceCredentialIntent(null, getString(resourceId));
        if (intent != null) {
            startActivityForResult(intent, CONFIRM_DEVICE_CREDENTIAL_REQUEST_CODE);
            return;
        }
        mFragmentManager.popBackStackImmediate();
    }
}
