// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Enumerated values to use with histograms. We use this instead of a proper java enum for
 * efficiency.
 */
public class ExploreSitesEnums {
    /** Enum for use with UMA for identifying where a catalog update request started. */
    @IntDef({CatalogUpdateRequestSource.NEW_TAB_PAGE, CatalogUpdateRequestSource.EXPLORE_SITES_PAGE,
            CatalogUpdateRequestSource.BACKGROUND, CatalogUpdateRequestSource.COUNT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CatalogUpdateRequestSource {
        int NEW_TAB_PAGE = 0; // Catalog update request came from NTP
        int EXPLORE_SITES_PAGE = 1; // Catalog update request came from ESP
        int BACKGROUND = 2; // Catalog update request came from background update.
        int COUNT = 3; // This must always be one higher than the last value.
    }
}
