// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.res.ColorStateList;

/**
 * An interface that provides the current theme color.
 */
public interface ThemeColorProvider {
    /**
     * An interface to be notified about changes to the theme color.
     */
    public interface ThemeColorObserver {
        void onThemeColorChanged(ColorStateList tint, int primaryColor);
    }

    /**
     * @param observer Add an observer that will have events broadcast to.
     */
    void addObserver(ThemeColorObserver observer);

    /**
     * @param observer Remove the observer.
     */
    void removeObserver(ThemeColorObserver observer);
}
