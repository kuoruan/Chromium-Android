// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.base.Log;

/**
 * Implements the UMA logging for Ranker that's used for Contextual Search Tap Suppression.
 */
public class ContextualSearchRankerLoggerImpl implements ContextualSearchRankerLogger {
    private static final String TAG = "ContextualSearch";

    @Override
    public void log(Feature feature, Object value) {
        // TODO(donnd): log to an actual persistent proto ASAP!
        Log.v(TAG, "log %s with value %s", feature.toString(), value);
    }

    @Override
    public void logOutcome(Object value) {
        log(Feature.OUTCOME_WAS_PANEL_OPENED, value);
    }

    @Override
    public void writeLogAndReset() {
        Log.v(TAG, "Reset!\n");
    }
}
