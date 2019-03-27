// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import org.chromium.base.metrics.CachedMetrics.EnumeratedHistogramSample;
import org.chromium.chrome.browser.autofill_assistant.metrics.DropOutReason;

/**
 * Records user actions and histograms related to Autofill Assistant.
 *
 * The |DropOutReason| enum is auto generated from
 * components/autofill_assistant/browser/metrics.h.
 */
/* package */ class AutofillAssistantMetrics {
    private static final EnumeratedHistogramSample ENUMERATED_DROP_OUT_REASON =
            new EnumeratedHistogramSample(
                    "Android.AutofillAssistant.DropOutReason", DropOutReason.NUM_ENTRIES);

    /**
     * Records the reason for a drop out.
     */
    /* package */ static void recordDropOut(@DropOutReason int reason) {
        ENUMERATED_DROP_OUT_REASON.record(reason);
    }
}
