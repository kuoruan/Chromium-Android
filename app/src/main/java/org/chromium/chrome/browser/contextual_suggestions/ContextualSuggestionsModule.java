// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import static org.chromium.chrome.browser.dependency_injection.ChromeCommonQualifiers.LAST_USED_PROFILE;

import org.chromium.chrome.browser.profiles.Profile;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

/**
 * Module for dependencies related to contextual suggestions.
 */
@Module
public class ContextualSuggestionsModule {
    public interface Factory { ContextualSuggestionsModule create(); }

    @Provides
    ContextualSuggestionsSource provideContextualSuggestionsSource(
            @Named(LAST_USED_PROFILE) Profile profile) {
        return new ContextualSuggestionsSourceImpl(profile);
    }
}
