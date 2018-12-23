// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import com.google.android.libraries.feed.host.scheduler.SchedulerApi;

/** A {@link FeedScheduler} implementation for use in testing. */
class TestFeedScheduler implements FeedScheduler {
    @Override
    public void destroy() {}

    @Override
    public void onForegrounded() {}

    @Override
    public void onFixedTimer(Runnable onCompletion) {}

    @Override
    public void onSuggestionConsumed() {}

    @Override
    public int shouldSessionRequestData(SessionManagerState sessionManagerState) {
        return SchedulerApi.RequestBehavior.NO_REQUEST_WITH_CONTENT;
    }

    @Override
    public void onReceiveNewContent(long contentCreationDateTimeMs) {}

    @Override
    public void onRequestError(int networkResponseCode) {}
}
