// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * An interface for logging to UMA via Ranker.
 */
public interface ContextualSearchRankerLogger {
    enum Feature {
        UNKNOWN,
        // Outcome labels:
        OUTCOME_WAS_PANEL_OPENED,
        OUTCOME_WAS_QUICK_ACTION_CLICKED,
        OUTCOME_WAS_QUICK_ANSWER_SEEN,
        // Features:
        DURATION_AFTER_SCROLL_MS,
        DURATION_BEFORE_SCROLL_MS,
        SCREEN_TOP_DPS,
        WAS_SCREEN_BOTTOM,
        PREVIOUS_WEEK_IMPRESSIONS_COUNT,
        PREVIOUS_WEEK_CTR_PERCENT,
        PREVIOUS_28DAY_IMPRESSIONS_COUNT,
        PREVIOUS_28DAY_CTR_PERCENT,
        SELECTION_LENGTH,
        SELECTION_FIRST_CHAR,
        SELECTION_WAS_ALL_CAPS
    }

    /**
     * Logs a particular key/value pair.
     * @param feature The feature to log.
     * @param value The value to log, which is associated with the given key.
     */
    void log(Feature feature, Object value);

    /**
     * Logs the final outcome value that indicates the ML label.
     * @param value The outcome label value.
     */
    void logOutcome(Object value);

    /**
     * Writes all the accumulated log entries and resets the logger so that future log calls
     * accumulate into a new record.
     */
    void writeLogAndReset();
}
