// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices;

import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

/**
 * Records how long Trusted Web Activities are used for.
 *
 * Lifecycle: There should be a 1-1 relationship between this class and
 * {@link TrustedWebActivityUi} (and transitively {@link CustomTabActivity}).
 * Thread safety: All methods on this class should be called on the UI thread.
 */
class TrustedWebActivityOpenTimeRecorder {
    private long mOnResumeTimestampMs;

    /** Notify that the TWA has been resumed. */
    public void onResume() {
        mOnResumeTimestampMs = SystemClock.elapsedRealtime();
    }

    /** Notify that the TWA has been paused. */
    public void onPause() {
        assert mOnResumeTimestampMs != 0;
        BrowserServicesMetrics.recordTwaOpenTime(
                SystemClock.elapsedRealtime() - mOnResumeTimestampMs, TimeUnit.MILLISECONDS);
        mOnResumeTimestampMs = 0;
    }
}
