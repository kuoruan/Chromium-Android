// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * A preference that displays the current sync account and status (enabled, error, needs passphrase,
 * etc).
 */
public class SyncPreference extends Preference {
    public SyncPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateSyncSummaryAndIcon();
    }

    /**
     * Updates the summary and icon for this preference to reflect the current state of syncing.
     */
    public void updateSyncSummaryAndIcon() {
        setSummary(SyncPreferenceUtils.getSyncStatusSummary(getContext()));

        if (SyncPreferenceUtils.showSyncErrorIcon(getContext())) {
            setIcon(ApiCompatibilityUtils.getDrawable(
                    getContext().getResources(), R.drawable.sync_error));
        } else {
            // Sets preference icon and tints it to blue.
            Drawable icon = ApiCompatibilityUtils.getDrawable(
                    getContext().getResources(), R.drawable.permission_background_sync);
            icon.setColorFilter(ApiCompatibilityUtils.getColor(getContext().getResources(),
                                        R.color.default_icon_color_blue),
                    PorterDuff.Mode.SRC_IN);
            setIcon(icon);
        }
    }
}
