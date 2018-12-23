// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link android.content.BroadcastReceiver} that detects when a Trusted Web Activity client app
 * has been uninstalled or has had its data cleared. When this happens we clear Chrome's data
 * corresponding to that app.
 *
 * Trusted Web Activities are registered to an origin (eg https://www.example.com), however because
 * cookies can be scoped more loosely, at TLD+1 level (eg *.example.com) [1], we need to clear data
 * at that level. This unfortunately can lead to too much data getting cleared - for example if the
 * https://peconn.github.io TWA is cleared, you'll loose cookies for https://beverloo.github.io too.
 *
 * We find this acceptable for two reasons:
 * - The alternative is *not* clearing some related data - eg a TWA linked to
 *   https://peconn.github.io sets a cookie with Domain=github.io. The TWA is uninstalled and
 *   reinstalled and it can access the cookie it stored before.
 * - We ask the user before clearing the data and while doing so display the scope of data we're
 *   going to wipe.
 *
 * [1] https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#Scope_of_cookies
 */
public class ClearDataBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ClearDataBroadRec";
    private static final Set<String> BROADCASTS = new HashSet<>(Arrays.asList(
            Intent.ACTION_PACKAGE_DATA_CLEARED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED
    ));

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        // Since we only care about ACTION_PACKAGE_DATA_CLEARED and and ACTION_PACKAGE_FULLY_REMOVED
        // which are protected Intents, we can assume that anything that gets past here will be a
        // legitimate Intent sent by the system.
        if (!BROADCASTS.contains(intent.getAction())) return;

        int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
        if (uid == -1) return;

        // TODO(peconn): Add UMA to record time taken.
        ClientAppDataRegister register = new ClientAppDataRegister();

        if (!register.chromeHoldsDataForPackage(uid)) return;

        String appName = register.getAppNameForRegisteredUid(uid);
        Set<String> origins = register.getOriginsForRegisteredUid(uid);

        for (String origin : origins) {
            String action = Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())
                    ? "been uninstalled" : "had its data cleared";
            Log.d(TAG, "%s has just %s, it was linked to %s.", appName, action, origin);
        }
    }
}
