// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import org.chromium.content_public.browser.WebContents;

/**
 * Perform navigation for share target with POST request.
 */
public class WebApkPostShareTargetNavigator {
    public static boolean navigateIfPostShareTarget(
            WebApkInfo webApkInfo, WebContents webContents) {
        WebApkShareTargetUtil.PostData postData = WebApkShareTargetUtil.computePostData(
                webApkInfo.webApkPackageName(), webApkInfo.shareData());
        if (postData == null) {
            return false;
        }
        nativeLoadViewForShareTargetPost(postData.isMultipartEncoding, postData.names,
                postData.values, postData.filenames, postData.types, webApkInfo.uri().toString(),
                webContents);
        return true;
    }

    private static native void nativeLoadViewForShareTargetPost(boolean isMultipartEncoding,
            String[] names, byte[][] values, String[] filenames, String[] types, String startUrl,
            WebContents webContents);
}