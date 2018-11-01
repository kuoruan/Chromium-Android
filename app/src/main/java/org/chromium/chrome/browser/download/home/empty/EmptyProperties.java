// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.empty;

import android.support.annotation.IntDef;

import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.IntPropertyKey;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** The properties required to build a {@link EmptyView}. */
interface EmptyProperties {
    @IntDef({State.LOADING, State.EMPTY, State.GONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
        int LOADING = 0;
        int EMPTY = 1;
        int GONE = 2;
    }

    /** The current state of the empty view. */
    public static final IntPropertyKey STATE = new IntPropertyKey();

    /** The current text resource to use for the empty view. */
    public static final IntPropertyKey EMPTY_TEXT_RES_ID = new IntPropertyKey();

    /** The current icon resource to use for the empty view. */
    public static final IntPropertyKey EMPTY_ICON_RES_ID = new IntPropertyKey();

    public static final PropertyKey[] ALL_KEYS =
            new PropertyKey[] {STATE, EMPTY_TEXT_RES_ID, EMPTY_ICON_RES_ID};
}