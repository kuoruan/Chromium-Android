// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import static org.chromium.webapk.lib.common.WebApkConstants.WEBAPK_PACKAGE_PREFIX;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.metrics.WebApkUma;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.webapk.lib.common.WebApkConstants;

import java.util.concurrent.TimeUnit;

/**
 * An Activity is designed for WebAPKs (native Android apps) and displays a webapp in a nearly
 * UI-less Chrome.
 */
public class WebApkActivity extends WebappActivity {
    /** Manages whether to check update for the WebAPK, and starts update check if needed. */
    private WebApkUpdateManager mUpdateManager;

    /** Indicates whether launching renderer in WebAPK process is enabled. */
    private boolean mCanLaunchRendererInWebApkProcess;

    private final ChildProcessCreationParams mDefaultParams =
            ChildProcessCreationParams.getDefault();

    /** The start time that the activity becomes focused. */
    private long mStartTime;

    private static final String TAG = "cr_WebApkActivity";

    /**
     * Tries extracting the WebAPK short name from the passed in intent. Returns null if the intent
     * does not launch a WebApkActivity. This method is slow. It makes several PackageManager calls.
     */
    public static String slowExtractNameFromIntentIfTargetIsWebApk(Intent intent) {
        // Check for intents targetted at WebApkActivity and WebApkActivity0-9.
        if (!intent.getComponent().getClassName().startsWith(WebApkActivity.class.getName())) {
            return null;
        }

        WebApkInfo info = WebApkInfo.create(intent);
        return (info != null) ? info.shortName() : null;
    }

    @Override
    protected boolean isVerified() {
        return true;
    }

    @Override
    protected WebappInfo createWebappInfo(Intent intent) {
        return (intent == null) ? WebApkInfo.createEmpty() : WebApkInfo.create(intent);
    }

    @Override
    protected void initializeUI(Bundle savedInstance) {
        super.initializeUI(savedInstance);
        getActivityTab().setWebappManifestScope(mWebappInfo.scopeUri().toString());
    }

    @Override
    public boolean shouldPreferLightweightFre(Intent intent) {
        // We cannot use getWebApkPackageName() because {@link WebappActivity#preInflationStartup()}
        // may not have been called yet.
        String webApkPackageName =
                IntentUtils.safeGetStringExtra(intent, WebApkConstants.EXTRA_WEBAPK_PACKAGE_NAME);

        // Use the lightweight FRE for unbound WebAPKs.
        return webApkPackageName != null && !webApkPackageName.startsWith(WEBAPK_PACKAGE_PREFIX);
    }

    @Override
    public void finishNativeInitialization() {
        super.finishNativeInitialization();
        if (!isInitialized()) return;
        mCanLaunchRendererInWebApkProcess = ChromeWebApkHost.canLaunchRendererInWebApkProcess();
    }

    @Override
    public String getNativeClientPackageName() {
        return getWebappInfo().apkPackageName();
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();

        // When launching Chrome renderer in WebAPK process is enabled, WebAPK hosts Chrome's
        // renderer processes by declaring the Chrome's renderer service in its AndroidManifest.xml
        // and sets {@link ChildProcessCreationParams} for WebAPK's renderer process so the
        // {@link ChildProcessLauncher} knows which application's renderer service to connect to.
        initializeChildProcessCreationParams(mCanLaunchRendererInWebApkProcess);
    }

    @Override
    public void onResume() {
        super.onResume();
        mStartTime = SystemClock.elapsedRealtime();
    }

    @Override
    protected void recordIntentToCreationTime(long timeMs) {
        super.recordIntentToCreationTime(timeMs);

        RecordHistogram.recordTimesHistogram(
                "MobileStartup.IntentToCreationTime.WebApk", timeMs, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onDeferredStartupWithStorage(WebappDataStorage storage) {
        super.onDeferredStartupWithStorage(storage);

        WebApkInfo info = (WebApkInfo) mWebappInfo;
        WebApkUma.recordShellApkVersion(info.shellApkVersion(), info.apkPackageName());

        mUpdateManager = new WebApkUpdateManager(storage);
        mUpdateManager.updateIfNeeded(getActivityTab(), info);
    }

    @Override
    protected void onUpdatedLastUsedTime(
            WebappDataStorage storage, boolean previouslyLaunched, long previousUsageTimestamp) {
        if (previouslyLaunched) {
            WebApkUma.recordLaunchInterval(storage.getLastUsedTime() - previousUsageTimestamp);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        initializeChildProcessCreationParams(false);
    }

    @Override
    public void onPauseWithNative() {
        WebApkUma.recordWebApkSessionDuration(SystemClock.elapsedRealtime() - mStartTime);
        super.onPauseWithNative();
    }

    /**
     * Initializes {@link ChildProcessCreationParams} as a WebAPK's renderer process if
     * {@link isForWebApk}} is true; as Chrome's child process otherwise.
     * @param isForWebApk: Whether the {@link ChildProcessCreationParams} is initialized as a
     *                     WebAPK renderer process.
     */
    private void initializeChildProcessCreationParams(boolean isForWebApk) {
        // TODO(hanxi): crbug.com/664530. WebAPKs shouldn't use a global ChildProcessCreationParams.
        ChildProcessCreationParams params = mDefaultParams;
        if (isForWebApk) {
            boolean isExternalService = false;
            boolean bindToCaller = false;
            boolean ignoreVisibilityForImportance = false;
            params = new ChildProcessCreationParams(getWebappInfo().apkPackageName(),
                    isExternalService, LibraryProcessType.PROCESS_CHILD, bindToCaller,
                    ignoreVisibilityForImportance);
        }
        ChildProcessCreationParams.registerDefault(params);
    }

    @Override
    protected void onDestroyInternal() {
        if (mUpdateManager != null) {
            mUpdateManager.destroy();
        }
        super.onDestroyInternal();
    }
}
