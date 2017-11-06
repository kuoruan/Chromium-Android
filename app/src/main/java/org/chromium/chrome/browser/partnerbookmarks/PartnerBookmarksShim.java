// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.partnerbookmarks;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeVersionInfo;

import java.util.concurrent.TimeUnit;

/**
 * The Java counterpart for the C++ partner bookmarks shim.
 * Responsible for:
 * - checking if we need to fetch the partner bookmarks,
 * - kicking off the async fetching of the partner bookmarks,
 * - pushing the partner bookmarks to the C++ side,
 * - reporting that all partner bookmarks were read to the C++ side.
 */
public class PartnerBookmarksShim {
    private static final String TAG = "PartnerBookmarksShim";

    private static boolean sIsReadingAttempted;
    private static final long BAN_DURATION_MS = TimeUnit.DAYS.toMillis(7);

    /**
     * Checks if we need to fetch the Partner bookmarks and kicks the reading off. If reading was
     * attempted before, it won't do anything.
     */
    public static void kickOffReading(Context context) {
        if (sIsReadingAttempted) return;
        sIsReadingAttempted = true;

        PartnerBookmarksReader reader = new PartnerBookmarksReader(context);

        boolean skip = shouldSkipReading();
        RecordHistogram.recordBooleanHistogram("PartnerBookmark.Skipped", skip);
        if (skip) {
            Log.i(TAG, "Skip reading partner bookmarks since recent result was empty.");
        }
        boolean systemOrPreStable =
                (context.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 1
                || !ChromeVersionInfo.isStableBuild();
        if (skip || !systemOrPreStable) {
            reader.onBookmarksRead();
            return;
        }
        reader.readBookmarks();
    }

    private static boolean shouldSkipReading() {
        SharedPreferences pref = ContextUtils.getAppSharedPreferences();
        long last = pref.getLong(PartnerBookmarksReader.LAST_EMPTY_READ_PREFS_NAME, 0);
        long elapsed = System.currentTimeMillis() - last;
        // Without checking elapsed >= 0, we might get stuck at an "always skip mode" if
        // |LAST_EMPTY_READ_PREFS_NAME| is a bogus future time.
        return 0 <= elapsed && elapsed < BAN_DURATION_MS;
    }
}
