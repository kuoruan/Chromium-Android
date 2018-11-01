// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home;

/** Helper class to expose the status of the offline prefetch feature. */
public class PrefetchStatusProvider {
    /** @return Whether or not the offline prefetch feature is enabled. */
    public boolean enabled() {
        return true;
    }
}