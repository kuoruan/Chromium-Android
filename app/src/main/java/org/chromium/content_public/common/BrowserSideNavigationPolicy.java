// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.common;

import org.chromium.base.annotations.JNINamespace;

/**
 * This is a utility class to wrap browser_side_navigation_policy.cc.
 */
@JNINamespace("content")
public final class BrowserSideNavigationPolicy {
    public static boolean isBrowserSideNavigationEnabled() {
        return nativeIsBrowserSideNavigationEnabled();
    }

    private static native boolean nativeIsBrowserSideNavigationEnabled();

    // Do not instantiate this class.
    private BrowserSideNavigationPolicy() {}
}
