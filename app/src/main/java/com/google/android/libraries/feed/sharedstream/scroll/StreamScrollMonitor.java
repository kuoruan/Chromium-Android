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

package com.google.android.libraries.feed.sharedstream.scroll;

import static com.google.android.libraries.feed.api.stream.ScrollListener.UNKNOWN_SCROLL_DELTA;

import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import com.google.android.libraries.feed.api.stream.ContentChangedListener;
import com.google.android.libraries.feed.api.stream.ScrollListener;
import com.google.android.libraries.feed.api.stream.ScrollListener.ScrollState;
import com.google.android.libraries.feed.common.concurrent.MainThreadRunner;
import com.google.android.libraries.feed.common.logging.Logger;
import java.util.HashSet;
import java.util.Set;

/** Class which monitors scrolls and notifies listeners on changes. */
public class StreamScrollMonitor extends RecyclerView.OnScrollListener {

  private static final String TAG = "StreamScrollMonitor";

  private final RecyclerView recyclerView;
  private final MainThreadRunner mainThreadRunner;
  private final Set<ScrollListener> scrollListeners;
  private final ContentChangedListener contentChangedListener;

  // Nullness checker doesn't like adding listeners in constructor.  This is OK as RecyclerView will
  // not call listener when it is added.
  @SuppressWarnings("initialization")
  public StreamScrollMonitor(
      RecyclerView recyclerView,
      ContentChangedListener childChangeListener,
      MainThreadRunner mainThreadRunner) {
    this.contentChangedListener = childChangeListener;
    this.mainThreadRunner = mainThreadRunner;

    scrollListeners = new HashSet<>();

    this.recyclerView = recyclerView;
    recyclerView.addOnScrollListener(this);
  }

  public void addScrollListener(ScrollListener listener) {
    scrollListeners.add(listener);
  }

  public void removeScrollListener(ScrollListener listener) {
    scrollListeners.remove(listener);
  }

  /**
   * Should be called if a programmatic scroll of the RecyclerView is done. Will notify host with
   * appropriate deltas.
   */
  public void onProgrammaticScroll() {
    mainThreadRunner.execute(
        TAG + " onProgrammaticScroll",
        () -> {
          // Post scroll as this allows users of scroll to retrieve new heights/widths of change.
          onScrolled(recyclerView, UNKNOWN_SCROLL_DELTA, UNKNOWN_SCROLL_DELTA);
        });
  }

  @Override
  public void onScrollStateChanged(RecyclerView recyclerView, int state) {
    if (state == RecyclerView.SCROLL_STATE_IDLE) {
      contentChangedListener.onContentChanged();
    }

    int scrollState = convertRecyclerViewScrollStateToListenerState(state);
    for (ScrollListener scrollListener : scrollListeners) {
      scrollListener.onScrollStateChanged(scrollState);
    }
  }

  @Override
  public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
    for (ScrollListener scrollListener : scrollListeners) {
      scrollListener.onScrolled(dx, dy);
    }
  }

  @ScrollState
  @VisibleForTesting
  static int convertRecyclerViewScrollStateToListenerState(int state) {
    switch (state) {
      case RecyclerView.SCROLL_STATE_DRAGGING:
        return ScrollState.DRAGGING;
      case RecyclerView.SCROLL_STATE_SETTLING:
        return ScrollState.SETTLING;
      case RecyclerView.SCROLL_STATE_IDLE:
        return ScrollState.IDLE;
      default:
        Logger.wtf(TAG, "Invalid recycler view scroll state: %d", state);
        return ScrollState.IDLE;
    }
  }
}
