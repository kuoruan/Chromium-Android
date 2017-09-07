// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.webapk.lib.client.WebApkValidator;

/**
 * Contains functionality needed for Chrome to host WebAPKs.
 */
public class ChromeWebApkHost {
    private static final String TAG = "ChromeWebApkHost";

    public static void init() {
        WebApkValidator.init(
                ChromeWebApkHostSignature.EXPECTED_SIGNATURE, ChromeWebApkHostSignature.PUBLIC_KEY);
    }

    /* Returns whether launching renderer in WebAPK process is enabled by Chrome. */
    public static boolean canLaunchRendererInWebApkProcess() {
        return LibraryLoader.isInitialized() && nativeCanLaunchRendererInWebApkProcess();
    }


    private static native boolean nativeCanLaunchRendererInWebApkProcess();
}
