// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.download.ui.ThumbnailProvider;
import org.chromium.chrome.browser.download.ui.ThumbnailProviderImpl;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Provides an injection mechanisms for dependencies of the suggestions package.
 *
 * This class is intended to handle creating the instances of the various classes that interact with
 * native code, so that they can be easily swapped out during tests.
 */
public class SuggestionsDependencyFactory {
    private static SuggestionsDependencyFactory sInstance;

    public static SuggestionsDependencyFactory getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) sInstance = new SuggestionsDependencyFactory();
        return sInstance;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(SuggestionsDependencyFactory testInstance) {
        if (sInstance != null && testInstance != null) {
            throw new IllegalStateException("A real instance already exists.");
        }
        sInstance = testInstance;
    }

    public SuggestionsSource createSuggestionSource(Profile profile) {
        return new SnippetsBridge(profile);
    }

    public SuggestionsEventReporter createEventReporter() {
        return new SuggestionsEventReporterBridge();
    }

    public MostVisitedSites createMostVisitedSites(Profile profile) {
        return new MostVisitedSitesBridge(profile);
    }

    public LargeIconBridge createLargeIconBridge(Profile profile) {
        return new LargeIconBridge(profile);
    }

    public ThumbnailProvider createThumbnailProvider() {
        return new ThumbnailProviderImpl();
    }

    public FaviconHelper createFaviconHelper() {
        return new FaviconHelper();
    }
}
