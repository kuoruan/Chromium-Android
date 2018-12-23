// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import com.google.android.libraries.feed.host.scheduler.SchedulerApi;

/**
 * An extension of the {@link SchedulerApi} with additional methods needed for scheduling logic
 * in Chromium.
 */
public interface FeedScheduler extends SchedulerApi {
    /** Cleans up native resources, should be called when no longer needed. */
    void destroy();

    /** To be called whenever the browser is foregrounded. */
    void onForegrounded();

    /** To be called when a background scheduling task wakes up the browser. */
    void onFixedTimer(Runnable onCompletion);

    /** To be called when an article is consumed, influencing future scheduling. */
    void onSuggestionConsumed();
}
