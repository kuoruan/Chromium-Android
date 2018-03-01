// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.util.Log;

import org.chromium.base.BuildInfo;
import org.chromium.base.ContextUtils;
import org.chromium.base.StrictModeContext;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.MainDex;
import org.chromium.chrome.browser.ChromeVersionInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Creates a crash report and uploads it to crash server if there is a Java exception.
 *
 * This class is written in pure Java, so it can handle exception happens before native is loaded.
 */
@MainDex
public class PureJavaExceptionReporter {
    // report fields, please keep the name sync with MIME blocks in breakpad_linux.cc
    public static final String CHANNEL = "channel";
    public static final String VERSION = "ver";
    public static final String PRODUCT = "prod";
    public static final String ANDROID_BUILD_ID = "android_build_id";
    public static final String ANDROID_BUILD_FP = "android_build_fp";
    public static final String DEVICE = "device";
    public static final String GMS_CORE_VERSION = "gms_core_version";
    public static final String INSTALLER_PACKAGE_NAME = "installer_package_name";
    public static final String ABI_NAME = "abi_name";
    public static final String PACKAGE = "package";
    public static final String MODEL = "model";
    public static final String BRAND = "brand";
    public static final String EXCEPTION_INFO = "exception_info";
    public static final String PROCESS_TYPE = "ptype";
    public static final String EARLY_JAVA_EXCEPTION = "early_java_exception";

    private static final String CRASH_DUMP_DIR = "Crash Reports";
    private static final String FILE_PREFIX = "chromium-browser-minidump-";
    private static final String FILE_SUFFIX = ".dmp";
    private static final String RN = "\r\n";
    private static final String FORM_DATA_MESSAGE = "Content-Disposition: form-data; name=\"";

    protected File mMinidumpFile = null;
    private FileOutputStream mMinidumpFileStream = null;
    private final String mLocalId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private final String mBoundary = "------------" + UUID.randomUUID() + RN;

    /**
     * Report and upload the device info and stack trace as if it was a crash. Runs synchronously
     * and results in I/O on the main thread.
     *
     * @param javaException The exception to report.
     */
    public static void reportJavaException(Throwable javaException) {
        PureJavaExceptionReporter reporter = new PureJavaExceptionReporter();
        reporter.createAndUploadReport(javaException);
    }

    /**
     * Detect if the current process is isolated.
     *
     * @return whether the process is isolated, or null if cannot determine.
     */
    public static Boolean detectIsIsolatedProcess() {
        try {
            Method isIsolatedMethod = android.os.Process.class.getMethod("isIsolated");
            Object retVal = isIsolatedMethod.invoke(null);
            if (retVal == null || !(retVal instanceof Boolean)) {
                return null;
            }
            return (Boolean) retVal;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    @VisibleForTesting
    void createAndUploadReport(Throwable javaException) {
        // It is OK to do IO in main thread when we know there is a crash happens.
        try (StrictModeContext unused = StrictModeContext.allowDiskWrites()) {
            createReport(javaException);
            flushToFile();
            uploadReport();
        }
    }

    @VisibleForTesting
    File getMinidumpFile() {
        return mMinidumpFile;
    }

    private void addPairedString(String messageType, String messageData) {
        addString(mBoundary);
        addString(FORM_DATA_MESSAGE + messageType + "\"");
        addString(RN + RN + messageData + RN);
    }

    private void addString(String s) {
        try {
            mMinidumpFileStream.write(s.getBytes());
        } catch (IOException e) {
            // Nothing we can do here.
        }
    }

    private void createReport(Throwable javaException) {
        try {
            String minidumpFileName = FILE_PREFIX + mLocalId + FILE_SUFFIX;
            mMinidumpFile = new File(
                    new File(ContextUtils.getApplicationContext().getCacheDir(), CRASH_DUMP_DIR),
                    minidumpFileName);
            mMinidumpFileStream = new FileOutputStream(mMinidumpFile);
        } catch (FileNotFoundException e) {
            mMinidumpFile = null;
            mMinidumpFileStream = null;
            return;
        }
        String processName = detectCurrentProcessName();
        if (processName == null || !processName.contains(":")) {
            processName = "browser";
        }

        String[] allInfo = BuildInfo.getAll();
        addPairedString(PRODUCT, "Chrome_Android");
        addPairedString(PROCESS_TYPE, processName);
        addPairedString(DEVICE, allInfo[BuildInfo.DEVICE_INDEX]);
        addPairedString(VERSION, ChromeVersionInfo.getProductVersion());
        addPairedString(CHANNEL, getChannel());
        addPairedString(ANDROID_BUILD_ID, allInfo[BuildInfo.ANDROID_BUILD_ID_INDEX]);
        addPairedString(MODEL, allInfo[BuildInfo.MODEL_INDEX]);
        addPairedString(BRAND, allInfo[BuildInfo.BRAND_INDEX]);
        addPairedString(ANDROID_BUILD_FP, allInfo[BuildInfo.ANDROID_BUILD_FP_INDEX]);
        addPairedString(GMS_CORE_VERSION, allInfo[BuildInfo.GMS_CORE_VERSION_INDEX]);
        addPairedString(INSTALLER_PACKAGE_NAME, allInfo[BuildInfo.INSTALLER_PACKAGE_NAME_INDEX]);
        addPairedString(ABI_NAME, allInfo[BuildInfo.ABI_NAME_INDEX]);
        addPairedString(PACKAGE, BuildInfo.getPackageName());
        addPairedString(EXCEPTION_INFO, Log.getStackTraceString(javaException));
        addPairedString(EARLY_JAVA_EXCEPTION, "true");
        addString(mBoundary);
    }

    private void flushToFile() {
        if (mMinidumpFileStream != null) {
            try {
                mMinidumpFileStream.flush();
                mMinidumpFileStream.close();
            } catch (Throwable e) {
                mMinidumpFileStream = null;
                mMinidumpFile = null;
            }
        }
    }

    private static String getChannel() {
        if (ChromeVersionInfo.isCanaryBuild()) {
            return "canary";
        }
        if (ChromeVersionInfo.isDevBuild()) {
            return "dev";
        }
        if (ChromeVersionInfo.isBetaBuild()) {
            return "beta";
        }
        if (ChromeVersionInfo.isStableBuild()) {
            return "stable";
        }
        return "";
    }

    private static String detectCurrentProcessName() {
        try {
            int pid = android.os.Process.myPid();

            ActivityManager manager =
                    (ActivityManager) ContextUtils.getApplicationContext().getSystemService(
                            Context.ACTIVITY_SERVICE);
            for (RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
                if (processInfo.pid == pid) {
                    return processInfo.processName;
                }
            }
            return null;
        } catch (SecurityException e) {
            return null;
        }
    }

    @VisibleForTesting
    public void uploadReport() {
        if (mMinidumpFile == null) return;
        LogcatExtractionRunnable logcatExtractionRunnable =
                new LogcatExtractionRunnable(mMinidumpFile);
        logcatExtractionRunnable.uploadMinidumpWithLogcat(true);
    }
}
