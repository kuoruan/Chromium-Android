// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import android.os.Bundle;
import android.os.RemoteException;

/**
 * A wrapper around a {@link IActivityDelegate}.
 *
 * No {@link RemoteException} should ever be thrown as all of this code runs in the same process.
 */
public class ActivityDelegate {
    private final IActivityDelegate mActivityDelegate;

    public ActivityDelegate(IActivityDelegate activityDelegate) {
        mActivityDelegate = activityDelegate;
    }

    public void onCreate(Bundle savedInstanceState) {
        try {
            mActivityDelegate.onCreate(savedInstanceState);
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onPostCreate(Bundle savedInstanceState) {
        try {
            mActivityDelegate.onPostCreate(savedInstanceState);
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onStart() {
        try {
            mActivityDelegate.onStart();
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onStop() {
        try {
            mActivityDelegate.onStop();
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        try {
            mActivityDelegate.onWindowFocusChanged(hasFocus);
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        try {
            mActivityDelegate.onSaveInstanceState(outState);
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            mActivityDelegate.onRestoreInstanceState(savedInstanceState);
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onResume() {
        try {
            mActivityDelegate.onResume();
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onPause() {
        try {
            mActivityDelegate.onPause();
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onDestroy() {
        try {
            mActivityDelegate.onDestroy();
        } catch (RemoteException e) {
            assert false;
        }
    }

    public boolean onBackPressed() {
        try {
            return mActivityDelegate.onBackPressed();
        } catch (RemoteException e) {
            assert false;
        }
        return false;
    }

    public void onBackPressedAsync(Runnable notHandledRunnable) {
        try {
            mActivityDelegate.onBackPressedAsync(ObjectWrapper.wrap(notHandledRunnable));
        } catch (RemoteException e) {
            assert false;
        }
    }

    public void onNavigationEvent(int navigationEvent, Bundle extras) {
        try {
            mActivityDelegate.onNavigationEvent(navigationEvent, extras);
        } catch (RemoteException e) {
            assert false;
        }
    }
}
