// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dependency_injection;

import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModule;
import org.chromium.chrome.browser.contextual_suggestions.EnabledStateMonitor;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Component representing the Singletons in the main process of the application.
 */
@Component(modules = {ChromeAppModule.class})
@Singleton
public interface ChromeAppComponent {
    ChromeActivityComponent createChromeActivityComponent(ChromeActivityCommonsModule module,
            ContextualSuggestionsModule contextualSuggestionsModule);

    CustomTabActivityComponent createCustomTabActivityComponent(ChromeActivityCommonsModule module,
            ContextualSuggestionsModule contextualSuggestionsModule);

    // Temporary getters for DI migration process. All of these getters
    // should eventually be replaced with constructor injection.
    EnabledStateMonitor getContextualSuggestionsEnabledStateMonitor();
}
