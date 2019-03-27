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

/** The BasicLoggingApi is used by the Feed to log actions performed on the Feed. */
public interface BasicLoggingApi {
  /**
   * Called when a section of content (generally a card) comes into view. Will not get called by
   * Stream if content has already been logged. If the stream is recreated this will get logged
   * again.
   *
   * <p>Content viewed will get logged as soon as content is 2/3 (will be configurable in {@link
   * com.google.android.libraries.feed.host.config.Configuration.ConfigKey#VIEW_LOG_THRESHOLD})
   * visible in view-port. This means logging will not be tied to scroll state events (scroll
   * start/stop) but rather raw scrolling events.
   */
  void onContentViewed(ContentLoggingData data);

  /**
   * Called when content has been dismissed by a user. This could be done via a swipe or through the
   * context menu.
   */
  void onContentDismissed(ContentLoggingData data);

  /**
   * Called when content has been swiped by a user.
   *
   * @param data data for content on which swipe was performed.
   */
  void onContentSwiped(ContentLoggingData data);

  /** Called when content is clicked/tapped. */
  void onContentClicked(ContentLoggingData data);

  /**
   * Called when a client action has been performed on a piece of content.
   *
   * @param data data for content on which action was performed.
   * @param actionType describes the type of action which is being performed by user.
   */
  void onClientAction(ContentLoggingData data, @ActionType int actionType);

  /** Called when the context menu for content has been opened. */
  void onContentContextMenuOpened(ContentLoggingData data);

  /**
   * Called when the more button appears at the bottom of the screen. This is a view on the more
   * button which is created from continuation features (paging). A view from the more button due to
   * having zero cards will not trigger this event.
   *
   * @param position index of the more button in the Stream.
   */
  void onMoreButtonViewed(int position);

  /**
   * Called when the more button appears at the bottom of the screen and is clicked. This is a click
   * on the more button which is created from continuation features (paging). A click from the more
   * button due to having zero cards will not trigger this event.
   *
   * @param position index of the more button in the Stream.
   */
  void onMoreButtonClicked(int position);

  /**
   * Called when Stream is shown and content was shown to the user. Content could have been cached
   * or a network fetch may have been needed.
   *
   * @param timeToPopulateMs time in milliseconds, since {@link
   *     com.google.android.libraries.feed.api.stream.Stream#onShow()}, it took to show content to
   *     the user. This does not include time to render but time to populate data in the UI.
   * @param contentCount Count of content shown to user. This will generally be the number of cards.
   */
  void onOpenedWithContent(int timeToPopulateMs, int contentCount);

  /**
   * Called when Stream was shown and no content was immediately available to be shown. Content may
   * have been shown after a network fetch.
   */
  void onOpenedWithNoImmediateContent();

  /**
   * Called when Stream was shown and no content could be shown to the user at all. This means that
   * there was no cached content and a network request to fetch new content was not allowed, could
   * not complete, or failed.
   */
  void onOpenedWithNoContent();

  /**
   * Called when a loading spinner is shown in the Stream
   *
   * @param timeShownMs time in milliseconds that the spinner was shown.
   * @param spinnerType type of spinner that was shown.
   */
  void onSpinnerShown(int timeShownMs, @SpinnerType int spinnerType);
}
