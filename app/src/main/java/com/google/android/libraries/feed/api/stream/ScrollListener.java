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

package com.google.android.libraries.feed.api.stream;

import android.support.annotation.IntDef;

/** Interface users can implement to be told about changes to scrolling in the Stream. */
public interface ScrollListener {

  /**
   * Constant used to denote that a scroll was performed but scroll delta could not be calculated.
   * This normally maps to a programmatic scroll.
   */
  int UNKNOWN_SCROLL_DELTA = Integer.MIN_VALUE;

  void onScrollStateChanged(@ScrollState int state);

  /**
   * Called when a scroll happens and provides the amount of pixels scrolled. {@link
   * #UNKNOWN_SCROLL_DELTA} will be specified if scroll delta would not be determined. An example of
   * this would be a scroll initiated programmatically.
   */
  void onScrolled(int dx, int dy);

  /** Possible scroll states. */
  @IntDef({ScrollState.IDLE, ScrollState.DRAGGING, ScrollState.SETTLING})
  @interface ScrollState {
    /** Stream is not scrolling */
    int IDLE = 0;

    /** Stream is currently scrolling through external means such as user input. */
    int DRAGGING = 1;

    /** Stream is animating to a final position. */
    int SETTLING = 2;
  }
}
