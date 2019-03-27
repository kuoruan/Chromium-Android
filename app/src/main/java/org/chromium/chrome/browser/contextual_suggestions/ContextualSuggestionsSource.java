// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsBridge.ContextualSuggestionsResult;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.content_public.browser.WebContents;

/**
 * Provides content for contextual suggestions.
 */
interface ContextualSuggestionsSource extends SuggestionsSource {
    /**
     * Fetches suggestions for a given URL.
     * @param url URL for which to fetch suggestions.
     * @param callback Callback used to return suggestions for a given URL.
     */
    void fetchSuggestions(String url, Callback<ContextualSuggestionsResult> callback);

    /**
     * Reports an event happening in the context of the current URL.
     *
     * @param webContents Web contents with the document for which event is reported.
     * @param eventId The Id of the reported event as a {@link ContextualSuggestionsEvent} integer.
     */
    void reportEvent(WebContents webContents, @ContextualSuggestionsEvent int eventId);

    /** Requests the backend to clear state. */
    void clearState();

    // Some methods from SuggestionsSource are not applicable to contextual suggestions.
    // TODO(twellington): The NTP classes used to display suggestion cards rely
    // on the SuggestionsSource implementation. Refactor to limit reliance to the
    // subset of methods actually used to render cards.
}
