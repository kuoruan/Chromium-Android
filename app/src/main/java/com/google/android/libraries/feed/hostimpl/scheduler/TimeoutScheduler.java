// Copyright 2018 The Feed Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.libraries.feed.hostimpl.scheduler;

import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.common.concurrent.DirectHostSupported;
import com.google.android.libraries.feed.common.feedobservable.FeedObservable;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.logging.StringFormattingUtils;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.feedapplifecyclelistener.FeedLifecycleListener;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.Configuration.ConfigKey;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concrete impl of {@link SchedulerApi}. The implementation will timeout session creation when a
 * request is running too long and will show existing content.
 */
public final class TimeoutScheduler implements SchedulerApi, DirectHostSupported {
  private static final String TAG = "TimeoutScheduler";

  private static final long TIMEOUT_STORIES_ARE_CURRENT = TimeUnit.HOURS.toMillis(1);
  private static final long TIMEOUT_STORIES_CURRENT_WITH_REFRESH = TimeUnit.HOURS.toMillis(12);

  private final Clock clock;
  private final boolean useTimeScheduler;
  private final long storiesAreCurrentMs;
  private final long currentStoresWithRefreshMs;

  @VisibleForTesting final AtomicBoolean clearAllTriggered = new AtomicBoolean(false);

  public TimeoutScheduler(
      Clock clock,
      Configuration configuration,
      FeedObservable<FeedLifecycleListener> lifecycleListenerObservable) {
    this.clock = clock;
    useTimeScheduler = configuration.getValueOrDefault(ConfigKey.USE_TIMEOUT_SCHEDULER, false);
    storiesAreCurrentMs =
        configuration.getValueOrDefault(
            ConfigKey.TIMEOUT_STORIES_ARE_CURRENT, TIMEOUT_STORIES_ARE_CURRENT);
    currentStoresWithRefreshMs =
        configuration.getValueOrDefault(
            ConfigKey.TIMEOUT_STORIES_CURRENT_WITH_REFRESH, TIMEOUT_STORIES_CURRENT_WITH_REFRESH);

    lifecycleListenerObservable.registerObserver(
        (event) -> {
          switch (event) {
            case FeedLifecycleListener.LifecycleEvent.CLEAR_ALL:
              clearAllTriggered.set(true);
              break;
            default:
              // Do nothing
          }
        });
  }

  @Override
  @RequestBehavior
  public int shouldSessionRequestData(SessionManagerState sessionManagerState) {
    if (!useTimeScheduler) {
      Logger.i(TAG, "TimeoutScheduler is Disabled: NO_REQUEST_WITH_CONTENT");
      return RequestBehavior.NO_REQUEST_WITH_WAIT;
    }
    Logger.i(
        TAG,
        "shouldSessionRequestData, content %s, contentCreationMs %s, request %s",
        sessionManagerState.hasContent,
        StringFormattingUtils.formatLogDate(sessionManagerState.contentCreationDateTimeMs),
        sessionManagerState.hasOutstandingRequest);
    if (clearAllTriggered.get()) {
      clearAllTriggered.set(false);
      return RequestBehavior.NO_REQUEST_WITH_TIMEOUT;
    }
    if (sessionManagerState.hasOutstandingRequest) {
      if (sessionManagerState.hasContent) {
        Logger.i(TAG, "Outstanding Request: NO_REQUEST_WITH_TIMEOUT");
        return RequestBehavior.NO_REQUEST_WITH_TIMEOUT;
      } else {
        Logger.i(TAG, "Outstanding Request, no content: NO_REQUEST_WITH_WAIT");
        return RequestBehavior.NO_REQUEST_WITH_WAIT;
      }
    }
    if (!sessionManagerState.hasContent) {
      Logger.i(TAG, "No Content: REQUEST_WITH_WAIT");
      return RequestBehavior.REQUEST_WITH_WAIT; // #1
    }
    long deltaTime = clock.currentTimeMillis() - sessionManagerState.contentCreationDateTimeMs;
    Logger.i(TAG, "Delta: [%s]", deltaTime);
    if (deltaTime < storiesAreCurrentMs) {
      Logger.i(TAG, "Stories are current: NO_REQUEST_WITH_CONTENT");
      return RequestBehavior.NO_REQUEST_WITH_CONTENT; // #2
    }
    if (deltaTime < currentStoresWithRefreshMs) {
      Logger.i(TAG, "Stories are current, with refresh: REQUEST_WITH_CONTENT");
      return RequestBehavior.REQUEST_WITH_CONTENT; // #3
    }
    // Stories are considered old
    Logger.i(TAG, "Old Stories found: REQUEST_WITH_TIMEOUT");
    return RequestBehavior.REQUEST_WITH_TIMEOUT; // #4
  }

  @Override
  public void onReceiveNewContent(long contentCreationDateTimeMs) {
    Logger.i(
        TAG,
        "onReceiveNewContent %s",
        StringFormattingUtils.formatLogDate(contentCreationDateTimeMs));
  }

  @Override
  public void onRequestError(int networkResponseCode) {
    // Do nothing
  }
}
