// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Environment;
import android.os.StatFs;
import android.util.Pair;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CollectionUtil;
import org.chromium.base.annotations.JNINamespace;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Grabs feedback about the current system. */
@JNINamespace("chrome::android")
public class SystemInfoFeedbackSource implements AsyncFeedbackSource {
    private StorageTask mStorageTask;

    private static class StorageTask extends AsyncTask<Void, Void, StatFs> {
        private Runnable mCallback;

        public StorageTask(Runnable callback) {
            mCallback = callback;
        }

        Long getAvailableSpaceMB() {
            if (getStatus() != Status.FINISHED) return null;

            try {
                StatFs statFs = get();
                if (statFs == null) return null;
                long blockSize = ApiCompatibilityUtils.getBlockSize(statFs);
                return ApiCompatibilityUtils.getAvailableBlocks(statFs) * blockSize / 1024 / 1024;
            } catch (ExecutionException | InterruptedException e) {
                return null;
            }
        }

        Long getTotalSpaceMB() {
            if (getStatus() != Status.FINISHED) return null;

            try {
                StatFs statFs = get();
                if (statFs == null) return null;
                long blockSize = ApiCompatibilityUtils.getBlockSize(statFs);
                return ApiCompatibilityUtils.getBlockCount(statFs) * blockSize / 1024 / 1024;
            } catch (ExecutionException | InterruptedException e) {
                return null;
            }
        }

        // AsyncTask implementation.
        @Override
        protected StatFs doInBackground(Void... params) {
            File directory = Environment.getDataDirectory();

            if (!directory.exists()) return null;

            return new StatFs(directory.getPath());
        }

        @Override
        protected void onPostExecute(StatFs result) {
            super.onPostExecute(result);
            mCallback.run();
        }
    }

    SystemInfoFeedbackSource() {}

    // AsyncFeedbackSource implementation.
    @Override
    public boolean isReady() {
        return mStorageTask != null && mStorageTask.getStatus() == Status.FINISHED;
    }

    @Override
    public void start(Runnable callback) {
        if (mStorageTask != null) return;
        mStorageTask = new StorageTask(callback);
        mStorageTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public Map<String, String> getFeedback() {
        Map<String, String> feedback = CollectionUtil.newHashMap(
                Pair.create("CPU Architecture", nativeGetCpuArchitecture()),
                Pair.create(
                        "Available Memory (MB)", Integer.toString(nativeGetAvailableMemoryMB())),
                Pair.create("Total Memory (MB)", Integer.toString(nativeGetTotalMemoryMB())),
                Pair.create("GPU Vendor", nativeGetGpuVendor()),
                Pair.create("GPU Model", nativeGetGpuModel()));

        if (isReady()) {
            Long availSpace = mStorageTask.getAvailableSpaceMB();
            Long totalSpace = mStorageTask.getTotalSpaceMB();

            if (availSpace != null) feedback.put("Available Storage (MB)", availSpace.toString());
            if (totalSpace != null) feedback.put("Total Storage (MB)", totalSpace.toString());
        }

        return feedback;
    }

    private static native String nativeGetCpuArchitecture();
    private static native String nativeGetGpuVendor();
    private static native String nativeGetGpuModel();
    private static native int nativeGetAvailableMemoryMB();
    private static native int nativeGetTotalMemoryMB();
}