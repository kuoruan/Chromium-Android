// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.os.Build;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.share.ShareActivity;

/**
 * A simple activity that allows Chrome to start the physical web sharing service.
 */
public class PhysicalWebShareActivity extends ShareActivity {
    @Override
    protected void handleShareAction(ChromeActivity triggeringActivity) {
        // TODO(iankc): implement this.
    }

    public static boolean featureIsAvailable() {
        return PhysicalWeb.sharingIsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }
}
