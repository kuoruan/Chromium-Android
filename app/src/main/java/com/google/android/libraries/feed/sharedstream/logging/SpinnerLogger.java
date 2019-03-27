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

package com.google.android.libraries.feed.sharedstream.logging;

import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.host.logging.SpinnerType;

/** Logs events for displaying spinner in the Stream. */
public class SpinnerLogger {

  private static final String TAG = "SpinnerLogger";
  private final BasicLoggingApi basicLoggingApi;
  private final Clock clock;

  private long spinnerStartTime = -1;
  @SpinnerType private int spinnerType;

  public SpinnerLogger(BasicLoggingApi basicLoggingApi, Clock clock) {
    this.basicLoggingApi = basicLoggingApi;
    this.clock = clock;
  }

  public void spinnerStarted(@SpinnerType int spinnerType) {
    if (isSpinnerActive()) {
      Logger.wtf(
          TAG,
          "spinnerStarted should not be called if another spinner is currently being tracked.");
      return;
    }

    spinnerStartTime = clock.currentTimeMillis();
    this.spinnerType = spinnerType;
  }

  public void spinnerFinished() {
    if (!isSpinnerActive()) {
      Logger.wtf(TAG, "spinnerFinished should only be called after spinnerStarted.");
      return;
    }

    long spinnerDuration = clock.currentTimeMillis() - spinnerStartTime;
    basicLoggingApi.onSpinnerShown((int) spinnerDuration, spinnerType);
    spinnerStartTime = -1;
  }

  public boolean isSpinnerActive() {
    return spinnerStartTime != -1;
  }
}
