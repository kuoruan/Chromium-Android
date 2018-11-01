// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.filter;

import android.view.View;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.BooleanPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.IntPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.ObjectPropertyKey;

/** The properties needed to render the download home filter view. */
public interface FilterProperties {
    /** The {@link View} to show in the content area. */
    public static final ObjectPropertyKey<View> CONTENT_VIEW = new ObjectPropertyKey<>();

    /** Which {@code TabType} should be selected. */
    public static final IntPropertyKey SELECTED_TAB = new IntPropertyKey();

    /** The callback listener for {@code TabType} selection changes. */
    public static final ObjectPropertyKey<Callback</* @TabType */ Integer>> CHANGE_LISTENER =
            new ObjectPropertyKey<>();

    /** Whether or not to show the tabs or just show the content. */
    public static final BooleanPropertyKey SHOW_TABS = new BooleanPropertyKey();

    public static final PropertyKey[] ALL_KEYS =
            new PropertyKey[] {CONTENT_VIEW, SELECTED_TAB, CHANGE_LISTENER, SHOW_TABS};
}