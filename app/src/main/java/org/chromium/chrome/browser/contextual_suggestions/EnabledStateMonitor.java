// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

/**
 * A monitor that is responsible for detecting changes to conditions required for contextual
 * suggestions to be enabled. Alerts its {@link Observer}s when state changes.
 */
public interface EnabledStateMonitor {
    /** An observer to be notified of enabled state changes. **/
    interface Observer {
        void onEnabledStateChanged(boolean enabled);
        void onSettingsStateChanged(boolean enabled);
    }

    /** Add an {@link Observer} to be notified of changes to enabled state. */
    void addObserver(Observer observer);

    /** Remove an observer. */
    void removeObserver(Observer observer);

    /** @return Whether the settings state is currently enabled. */
    boolean getSettingsEnabled();

    /** @return Whether the state is currently enabled. */
    boolean getEnabledState();
}
