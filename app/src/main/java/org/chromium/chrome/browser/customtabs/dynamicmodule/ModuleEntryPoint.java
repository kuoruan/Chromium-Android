// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import android.os.Bundle;
import android.os.RemoteException;

/**
 * A wrapper around a {@link IModuleEntryPoint}.
 *
 * No {@link RemoteException} should ever be thrown as all of this code runs in the same process.
 */
public class ModuleEntryPoint {
    private final IModuleEntryPoint mEntryPoint;

    public ModuleEntryPoint(IModuleEntryPoint entryPoint) {
        mEntryPoint = entryPoint;
    }

    public void init(ModuleHostImpl moduleHost) {
        try {
            mEntryPoint.init(moduleHost);
        } catch (RemoteException e) {
            assert false;
        }
    }

    public int getModuleVersion() {
        try {
            return mEntryPoint.getModuleVersion();
        } catch (RemoteException e) {
            assert false;
        }
        return -1;
    }

    public int getMinimumHostVersion() {
        try {
            return mEntryPoint.getMinimumHostVersion();
        } catch (RemoteException e) {
            assert false;
        }
        return -1;
    }

    public IActivityDelegate createActivityDelegate(IActivityHost.Stub activityHost) {
        try {
            return mEntryPoint.createActivityDelegate(activityHost);
        } catch (RemoteException e) {
            assert false;
        }
        return null;
    }

    public void onDestroy() {
        try {
            mEntryPoint.onDestroy();
        } catch (RemoteException e) {
            assert false;
        }
    }


    /**
     * Introduced in API version 6.
     */
    public void onBundleReceived(Bundle bundle) {
        if (getModuleVersion() < 6) return;
        try {
            mEntryPoint.onBundleReceived(ObjectWrapper.wrap(bundle));
        } catch (RemoteException e) {
            assert false;
        }
    }
}
