// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import org.chromium.base.annotations.JNINamespace;

/**
 * JNI bridge with content::NavigationHandleProxy.
 */
@JNINamespace("content")
public class NavigationHandleProxy {
    public static native void nativeSetRequestHeader(
            long nativeNavigationHandleProxy, String headerName, String headerValue);
    public static native void nativeRemoveRequestHeader(
            long nativeNavigationHandleProxy, String headerName);
}
