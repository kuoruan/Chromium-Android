// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import com.google.android.libraries.feed.common.functional.Consumer;

import java.util.List;

/** A {@link FeedScheduler} implementation for use in testing. */
class TestFeedOfflineIndicator implements FeedOfflineIndicator {
    @Override
    public void destroy() {}

    @Override
    public Long getOfflineIdIfPageIsOfflined(String url) {
        return null;
    }

    @Override
    public void getOfflineStatus(
            List<String> urlsToRetrieve, Consumer<List<String>> urlListConsumer) {}

    @Override
    public void addOfflineStatusListener(OfflineStatusListener offlineStatusListener) {}

    @Override
    public void removeOfflineStatusListener(OfflineStatusListener offlineStatusListener) {}
}
