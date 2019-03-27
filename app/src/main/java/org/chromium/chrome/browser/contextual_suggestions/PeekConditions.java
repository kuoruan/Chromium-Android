// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

/** Encapsulates server-provided conditions for "peeking" the contextual suggestions UI. */
public class PeekConditions {
    private final float mConfidence;
    private final float mPageScrollPercentage;
    private final float mMinimumSecondsOnPage;
    private final float mMaximumNumberOfPeeks;

    public PeekConditions() {
        this(0, 0, 0, 0);
    }

    /**
     * Constructs a new PeekConditions.
     * @param confidence The confidence of the results that were returned.
     * @param pageScrollPercentage The percentage of the page that the user scrolls required
     *                             for an auto peek to occur.
     * @param minimumSecondsOnPage The minimum time (seconds) the user spends on the page
     *                             required for auto peek.
     * @param maximumNumberOfPeeks The maximum number of auto peeks that we can show for this
     *                             page.
     */
    public PeekConditions(float confidence, float pageScrollPercentage, float minimumSecondsOnPage,
            float maximumNumberOfPeeks) {
        mConfidence = confidence;
        mPageScrollPercentage = pageScrollPercentage;
        mMinimumSecondsOnPage = minimumSecondsOnPage;
        mMaximumNumberOfPeeks = maximumNumberOfPeeks;
    }

    /** @return The confidence of the results that were returned.*/
    public float getConfidence() {
        return mConfidence;
    }

    /**
     * @return The percentage of the page that the user scrolls required for an auto peek to occur.
     */
    public float getPageScrollPercentage() {
        return mPageScrollPercentage;
    }

    /** @return The minimum time (seconds) the user spends on the page required for auto peek. */
    public float getMinimumSecondsOnPage() {
        return mMinimumSecondsOnPage;
    }

    /** @return The maximum number of auto peeks that we can show for this page. */
    public float getMaximumNumberOfPeeks() {
        return mMaximumNumberOfPeeks;
    }
}