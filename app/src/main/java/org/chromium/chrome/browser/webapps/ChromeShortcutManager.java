// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.AppHooks;

import java.util.List;

/**
 * This class handles the adding of shortcuts to the Android Home Screen.
 */
public class ChromeShortcutManager {
    // There is no public string defining this intent so if Home changes the value, we
    // have to update this string.
    private static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

    private static ChromeShortcutManager sInstance;

    /* Returns the singleton instance of ChromeShortcutManager, creating it if needed. */
    public static ChromeShortcutManager getInstance() {
        if (sInstance == null) {
            sInstance = AppHooks.get().createChromeShortcutManager();
        }
        return sInstance;
    }

    /**
     * Creates an intent that will add a shortcut to the home screen.
     * @param title          Title of the shortcut.
     * @param icon           Image that represents the shortcut.
     * @param shortcutIntent Intent to fire when the shortcut is activated.
     * @return Intent for the shortcut.
     */
    public static Intent createAddToHomeIntent(String title, Bitmap icon, Intent shortcutIntent) {
        Intent i = new Intent(INSTALL_SHORTCUT);
        i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        i.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        i.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        return i;
    }

    /**
     * Add a shortcut to the home screen.
     * @param title          Title of the shortcut.
     * @param icon           Image that represents the shortcut.
     * @param shortcutIntent Intent to fire when the shortcut is activated.
     */
    public void addShortcutToHomeScreen(String title, Bitmap icon, Intent shortcutIntent) {
        Intent intent = createAddToHomeIntent(title, icon, shortcutIntent);
        ContextUtils.getApplicationContext().sendBroadcast(intent);
    }

    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("WrongConstant")
    public boolean canAddShortcutToHomescreen() {
        PackageManager pm = ContextUtils.getApplicationContext().getPackageManager();
        Intent i = new Intent(INSTALL_SHORTCUT);
        List<ResolveInfo> receivers =
                pm.queryBroadcastReceivers(i, PackageManager.GET_INTENT_FILTERS);
        return !receivers.isEmpty();
    }

    public boolean shouldShowToastWhenAddingShortcut() {
        return true;
    }
}
