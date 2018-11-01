// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsBridge.ContextualSuggestionsResult;
import org.chromium.chrome.browser.ntp.snippets.EmptySuggestionsSource;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content_public.browser.WebContents;

/**
 * Provides content for contextual suggestions.
 */
class ContextualSuggestionsSource extends EmptySuggestionsSource {
    private ContextualSuggestionsBridge mBridge;

    /**
     * Creates a ContextualSuggestionsSource for getting contextual suggestions for the current
     * user.
     *
     * @param profile Profile of the user.
     */
    ContextualSuggestionsSource(Profile profile) {
        init(profile);
    }

    /**
     * Initializes the ContextualSuggestionsSource. Intended to encapsulate creating connections
     * to native code, so that this can be easily stubbed out during tests.
     */
    protected void init(Profile profile) {
        mBridge = new ContextualSuggestionsBridge(profile);
    }

    @Override
    public void destroy() {
        mBridge.destroy();
    }

    @Override
    public void fetchSuggestionImage(SnippetArticle suggestion, Callback<Bitmap> callback) {
        mBridge.fetchSuggestionImage(suggestion, callback);
    }

    @Override
    public void fetchContextualSuggestionImage(
            SnippetArticle suggestion, Callback<Bitmap> callback) {
        mBridge.fetchSuggestionImage(suggestion, callback);
    }

    @Override
    public void fetchSuggestionFavicon(SnippetArticle suggestion, int minimumSizePx,
            int desiredSizePx, Callback<Bitmap> callback) {
        mBridge.fetchSuggestionFavicon(suggestion, callback);
    }

    /**
     * Fetches suggestions for a given URL.
     * @param url URL for which to fetch suggestions.
     * @param callback Callback used to return suggestions for a given URL.
     */
    void fetchSuggestions(String url, Callback<ContextualSuggestionsResult> callback) {
        mBridge.fetchSuggestions(url, callback);
    }

    /**
     * Reports an event happening in the context of the current URL.
     *
     * @param webContents Web contents with the document for which event is reported.
     * @param eventId The Id of the reported event as a {@link ContextualSuggestionsEvent} integer.
     */
    void reportEvent(WebContents webContents, @ContextualSuggestionsEvent int eventId) {
        mBridge.reportEvent(webContents, eventId);
    }

    /** Requests the backend to clear state. */
    void clearState() {
        mBridge.clearState();
    }

    // Some methods from SuggestionsSource are not applicable to contextual suggestions.
    // TODO(twellington): The NTP classes used to display suggestion cards rely
    // on the SuggestionsSource implementation. Refactor to limit reliance to the
    // subset of methods actually used to render cards.
}
