// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.send_tab_to_self;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.share.ShareActivity;
import org.chromium.chrome.browser.tab.Tab;

/**
 * A simple activity that allows Chrome to expose send tab to self as an
 * option in the share menu.
 */
public class SendTabToSelfShareActivity extends ShareActivity {
    @Override
    protected void handleShareAction(ChromeActivity triggeringActivity) {
        // TODO(tgupta): Hook up logic to the sync server here
    }

    public static boolean featureIsAvailable(Tab currentTab) {
      // TODO(tgupta): Add additional checks here to make sure the user
      // has additional devices that they are signed into and syncing on.
      return ChromeFeatureList.isEnabled(ChromeFeatureList.SEND_TAB_TO_SELF) &&
              !currentTab.isIncognito();
    }
}

