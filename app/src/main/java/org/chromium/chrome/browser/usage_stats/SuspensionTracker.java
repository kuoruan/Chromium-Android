// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.usage_stats;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class that tracks which sites are currently suspended.
 */
public class SuspensionTracker {
    private Set<String> mSuspendedWebsites;

    public SuspensionTracker() {
        mSuspendedWebsites = new HashSet<String>();
    }

    public void setWebsitesSuspended(List<String> fqdns, boolean suspended) {
        if (suspended) {
            mSuspendedWebsites.addAll(fqdns);
        } else {
            mSuspendedWebsites.removeAll(fqdns);
        }
    }

    public List<String> getAllSuspendedWebsites() {
        List<String> result = new ArrayList<>();
        result.addAll(mSuspendedWebsites);
        return result;
    }

    public boolean isWebsiteSuspended(String fqdn) {
        return mSuspendedWebsites.contains(fqdn);
    }
}