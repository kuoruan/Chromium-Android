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

import org.chromium.chrome.R;

/** Show the lock screen confirmation and lock the screen. */
public class PasswordReauthenticationFragment extends Fragment {
    protected static final int CONFIRM_DEVICE_CREDENTIAL_REQUEST_CODE = 2;

    private boolean mPreventLockDevice;

    private FragmentManager mFragmentManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFragmentManager = getFragmentManager();
        if (!mPreventLockDevice) {
            lockDevice();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIRM_DEVICE_CREDENTIAL_REQUEST_CODE) {
            if (resultCode == getActivity().RESULT_OK) {
                SavePasswordsPreferences.setLastReauthTimeMillis(System.currentTimeMillis());
                mFragmentManager.popBackStack();
            }
        }
    }

    /**
     * Prevent calling the {@link #lockDevice} method in {@link #onCreate}.
     */
    public void preventLockingForTesting(boolean preventLockDevice) {
        mPreventLockDevice = preventLockDevice;
    }

    /**
     * Should only be called on Lollipop or above devices.
     */
    @TargetApi(VERSION_CODES.LOLLIPOP)
    private void lockDevice() {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        KeyguardManager keyguardManager =
                (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                null /* title */, getString(R.string.lockscreen_description) /* description */);
        if (intent != null) {
            startActivityForResult(intent, CONFIRM_DEVICE_CREDENTIAL_REQUEST_CODE);
            return;
        }
        mFragmentManager.popBackStackImmediate();
    }
}
