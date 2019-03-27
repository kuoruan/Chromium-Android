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

package com.google.android.libraries.feed.host.logging;

import android.support.annotation.IntDef;

/** IntDef representing the different types of spinners. */
@IntDef({
  SpinnerType.INITIAL_LOAD,
  SpinnerType.ZERO_STATE_REFRESH,
  SpinnerType.MORE_BUTTON,
  SpinnerType.SYNTHETIC_TOKEN,
  SpinnerType.INFINITE_FEED
})
public @interface SpinnerType {
  // Spinner shown on initial load of the Feed.
  int INITIAL_LOAD = 1;
  // Spinner shown when Feed is refreshed.
  int ZERO_STATE_REFRESH = 2;
  // Spinner shown when more button is clicked.
  int MORE_BUTTON = 3;
  // Spinner shown when a synthetic token is consumed.
  int SYNTHETIC_TOKEN = 4;
  // Spinner shown when a spinner is shown for loading the infinite feed.
  int INFINITE_FEED = 5;
}
