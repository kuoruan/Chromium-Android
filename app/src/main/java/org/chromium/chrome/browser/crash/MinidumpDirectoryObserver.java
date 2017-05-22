// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.content.Context;
import android.os.AsyncTask;
import android.os.FileObserver;

import org.chromium.base.ContextUtils;
import org.chromium.components.minidump_uploader.CrashFileManager;

import java.io.File;

/**
 * The FileObserver that monitors the minidump directory for new crash records.
 */
public class MinidumpDirectoryObserver extends FileObserver {
    /**
     * A utility class to help manage the contents of the crash minidump local storage directory.
     */
    final CrashFileManager mFileManager;

    public MinidumpDirectoryObserver() {
        this(new CrashFileManager(ContextUtils.getApplicationContext().getCacheDir()));
    }

    private MinidumpDirectoryObserver(CrashFileManager fileManager) {
        // The file observer detects MOVED_TO for child processes.
        super(fileManager.getCrashDirectory().toString(), FileObserver.MOVED_TO);
        mFileManager = fileManager;
    }

    /**
     * When a minidump is detected, extract and append a logcat to it, then upload it to the crash
     * server.
     */
    @Override
    public void onEvent(int event, String path) {
        // This is executed on a thread dedicated to FileObserver.
        // Note: It's possible for |path| to be null: http://crbug.com/711404
        if (path != null && CrashFileManager.isMinidumpMIMEFirstTry(path)) {
            // Note that the logcat extraction might fail. This is ok; in that case, the minidump
            // will be found and uploaded upon the next browser launch.
            Context context = ContextUtils.getApplicationContext();
            File minidump = mFileManager.getCrashFile(path);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new LogcatExtractionRunnable(context, minidump));
        }
    }
}
