// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import org.chromium.base.annotations.UsedByReflection;
import org.chromium.base.process_launcher.ChildProcessServiceImpl;
import org.chromium.content.app.ContentChildProcessServiceDelegate;

/**
 * This class exposes ChildProcessServiceImpl so that WebApkSandboxedProcessService can access it
 * through reflection.
 */
@UsedByReflection("WebApkSandboxedProcessService")
public class WebApkChildProcessServiceImpl {
    private final ChildProcessServiceImpl mChildProcessServiceImpl;

    public WebApkChildProcessServiceImpl() {
        mChildProcessServiceImpl =
                new ChildProcessServiceImpl(new ContentChildProcessServiceDelegate());
    }

    @UsedByReflection("WebApkSandboxedProcessService")
    public void create(Context context, Context hostContext) {
        mChildProcessServiceImpl.create(context, hostContext);
    }

    @UsedByReflection("WebApkSandboxedProcessService")
    public void destroy() {
        mChildProcessServiceImpl.destroy();
    }

    @UsedByReflection("WebApkSandboxedProcessService")
    public IBinder bind(Intent intent, int authorizedCallerUid) {
        return mChildProcessServiceImpl.bind(intent, authorizedCallerUid);
    }
}