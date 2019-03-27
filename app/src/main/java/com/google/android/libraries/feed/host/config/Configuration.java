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

package com.google.android.libraries.feed.host.config;

import android.support.annotation.StringDef;
import java.util.HashMap;

/**
 * Contains an immutable collection of {@link ConfigKey} {@link String}, {@link Object} pairs.
 *
 * <p>Note: this class should not be mocked. Use the {@link Builder} instead.
 */
// TODO: This can't be final because we mock it
public class Configuration {
  /** A unique string identifier for a config value */
  @StringDef({
    // Configuration to have Stream abort restores if user is past configured fold count.
    ConfigKey.ABANDON_RESTORE_BELOW_FOLD,
    // Configuration on threshold of cards which abandons a restore if scroll is past that content
    // count.
    ConfigKey.ABANDON_RESTORE_BELOW_FOLD_THRESHOLD,
    ConfigKey.FEED_SERVER_ENDPOINT,
    ConfigKey.FEED_SERVER_METHOD,
    ConfigKey.FEED_SERVER_RESPONSE_LENGTH_PREFIXED,
    ConfigKey.SESSION_LIFETIME_MS,
    ConfigKey.MOCK_SERVER_DELAY_MS,
    ConfigKey.INITIAL_NON_CACHED_PAGE_SIZE,
    ConfigKey.NON_CACHED_PAGE_SIZE,
    ConfigKey.NON_CACHED_MIN_PAGE_SIZE,
    ConfigKey.DEFAULT_ACTION_TTL_SECONDS,
    ConfigKey.TRIGGER_IMMEDIATE_PAGINATION,
    // Boolean which causes synthetic tokens to be consumed when they are found. Only valid during
    // initialization, during restore we always consume synthetic tokens.
    ConfigKey.CONSUME_SYNTHETIC_TOKENS,
    // Turn on the Timeout Scheduler
    ConfigKey.USE_TIMEOUT_SCHEDULER,
    // Time in ms where the stories are consider current
    ConfigKey.TIMEOUT_STORIES_ARE_CURRENT,
    // Time in ms where the stores are current, but we start a refresh
    ConfigKey.TIMEOUT_STORIES_CURRENT_WITH_REFRESH,
    // Time in ms for the length of the timeout
    ConfigKey.TIMEOUT_TIMEOUT_MS,
    ConfigKey.MINIMUM_VALID_ACTION_RATIO,
    // Time in ms that content should be rendered in order to be considered an immediate open
    ConfigKey.LOGGING_IMMEDIATE_CONTENT_THRESHOLD_MS,
    // Percentage of the view that should be on screen to log a view
    ConfigKey.VIEW_LOG_THRESHOLD,
    // Use direct storage
    ConfigKey.USE_DIRECT_STORAGE,
    // Time in ms to wait for image loading before showing a fade in animation.
    ConfigKey.FADE_IMAGE_THRESHOLD_MS,
    // Only update HEAD and the session making a page request
    ConfigKey.LIMIT_PAGE_UPDATES,
    // This is a host specific flag currently not used in the Jardin libraries.
    ConfigKey.USE_SQLITE_CONTENT,
    // A content directly on the device file system where the files/directories are created
    // for the file based persistent storage.
    // TODO: The persistent content storage doesn't currently use this
    ConfigKey.CONTENT_DIR,
    // Boolean which if true, will ask the server for the feed ui response.
    ConfigKey.FEED_UI_ENABLED,
    // Boolean which if true, will ask the server for a different pagination strategy.
    ConfigKey.USE_SECONDARY_PAGE_REQUEST,
  })
  public @interface ConfigKey {
    String ABANDON_RESTORE_BELOW_FOLD = "abandon_restore_below_fold";
    String ABANDON_RESTORE_BELOW_FOLD_THRESHOLD = "abandon_restore_below_fold_threshold";
    String FEED_SERVER_ENDPOINT = "feed_server_endpoint";
    String FEED_SERVER_METHOD = "feed_server_method";
    String FEED_SERVER_RESPONSE_LENGTH_PREFIXED = "feed_server_response_length_prefixed";
    String SESSION_LIFETIME_MS = "session_lifetime_ms";
    String MOCK_SERVER_DELAY_MS = "mock_server_delay_ms";
    String INITIAL_NON_CACHED_PAGE_SIZE = "initial_non_cached_page_size";
    String NON_CACHED_PAGE_SIZE = "non_cached_page_size";
    String NON_CACHED_MIN_PAGE_SIZE = "non_cached_min_page_size";
    String DEFAULT_ACTION_TTL_SECONDS = "default_action_ttl_seconds";
    String TRIGGER_IMMEDIATE_PAGINATION = "trigger_immediate_pagination_bool";
    String CONSUME_SYNTHETIC_TOKENS = "consume_synthetic_tokens_bool";
    String USE_TIMEOUT_SCHEDULER = "use_timeout_scheduler";
    String TIMEOUT_STORIES_ARE_CURRENT = "timeout_stories_are_current";
    String TIMEOUT_STORIES_CURRENT_WITH_REFRESH = "timeout_stories_current_with_refresh";
    String TIMEOUT_TIMEOUT_MS = "timeout_timeout_ms";
    String MINIMUM_VALID_ACTION_RATIO = "minimum_valid_action_ratio";
    String LOGGING_IMMEDIATE_CONTENT_THRESHOLD_MS = "logging_immediate_content_threshold_ms";
    String VIEW_LOG_THRESHOLD = "view_log_threshold";
    String USE_DIRECT_STORAGE = "use_direct_storage";
    String USE_SQLITE_CONTENT = "use_sqlite_content";
    String CONTENT_DIR = "content_directory";
    String FADE_IMAGE_THRESHOLD_MS = "fade_image_threshold_ms";
    String LIMIT_PAGE_UPDATES = "limit_page_updates";
    String FEED_UI_ENABLED = "feed_ui_enabled";
    String USE_SECONDARY_PAGE_REQUEST = "use_secondary_page_request";
  }

  private final HashMap<String, Object> values;

  private Configuration(HashMap<String, Object> values) {
    this.values = values;
  }

  /**
   * Returns the value if it exists, or {@code defaultValue} otherwise.
   *
   * @throws ClassCastException if the value can't be cast to {@code T}.
   */
  public <T> T getValueOrDefault(String key, T defaultValue) {
    if (values.containsKey(key)) {
      // The caller assumes the responsibility of ensuring this cast succeeds
      @SuppressWarnings("unchecked")
      T castedValue = (T) values.get(key);
      return castedValue;
    } else {
      return defaultValue;
    }
  }

  /** Returns true if a value exists for the {@code key}. */
  public boolean hasValue(String key) {
    return values.containsKey(key);
  }

  /** Builder class used to create {@link Configuration} objects. */
  public static final class Builder {
    private final HashMap<String, Object> values = new HashMap<>();

    public Builder put(@ConfigKey String key, Object value) {
      values.put(key, value);
      return this;
    }

    public Configuration build() {
      return new Configuration(values);
    }
  }
}
