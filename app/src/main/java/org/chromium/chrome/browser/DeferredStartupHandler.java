// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.base.PowerMonitor;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.bookmarkswidget.BookmarkWidgetProvider;
import org.chromium.chrome.browser.crash.LogcatExtractionRunnable;
import org.chromium.chrome.browser.crash.MinidumpUploadService;
import org.chromium.chrome.browser.init.ProcessInitializationHandler;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.media.MediaCaptureNotificationService;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.physicalweb.PhysicalWeb;
import org.chromium.chrome.browser.precache.PrecacheLauncher;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.webapps.ChromeWebApkHost;
import org.chromium.chrome.browser.webapps.WebApkVersionManager;
import org.chromium.chrome.browser.webapps.WebappRegistry;
import org.chromium.components.minidump_uploader.CrashFileManager;
import org.chromium.content.browser.ChildProcessLauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Handler for application level tasks to be completed on deferred startup.
 */
public class DeferredStartupHandler {
    private static final String TAG = "DeferredStartup";
    /** Prevents race conditions when deleting snapshot database. */
    private static final Object SNAPSHOT_DATABASE_LOCK = new Object();
    private static final String SNAPSHOT_DATABASE_REMOVED = "snapshot_database_removed";
    private static final String SNAPSHOT_DATABASE_NAME = "snapshots.db";

    private static class Holder {
        private static final DeferredStartupHandler INSTANCE = new DeferredStartupHandler();
    }

    private boolean mDeferredStartupInitializedForApp;
    private boolean mDeferredStartupCompletedForApp;
    private long mDeferredStartupDuration;
    private long mMaxTaskDuration;
    private final Context mAppContext;

    private final Queue<Runnable> mDeferredTasks;

    /**
     * This class is an application specific object that handles the deferred startup.
     * @return The singleton instance of {@link DeferredStartupHandler}.
     */
    public static DeferredStartupHandler getInstance() {
        return Holder.INSTANCE;
    }

    private DeferredStartupHandler() {
        mAppContext = ContextUtils.getApplicationContext();
        mDeferredTasks = new LinkedList<>();
    }

    /**
     * Add the idle handler which will run deferred startup tasks in sequence when idle. This can
     * be called multiple times by different activities to schedule their own deferred startup
     * tasks.
     */
    public void queueDeferredTasksOnIdleHandler() {
        mMaxTaskDuration = 0;
        mDeferredStartupDuration = 0;
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Runnable currentTask = mDeferredTasks.poll();
                if (currentTask == null) {
                    if (mDeferredStartupInitializedForApp && !mDeferredStartupCompletedForApp) {
                        mDeferredStartupCompletedForApp = true;
                        recordDeferredStartupStats();
                    }
                    return false;
                }

                long startTime = SystemClock.uptimeMillis();
                currentTask.run();
                long timeTaken = SystemClock.uptimeMillis() - startTime;

                mMaxTaskDuration = Math.max(mMaxTaskDuration, timeTaken);
                mDeferredStartupDuration += timeTaken;
                return true;
            }
        });
    }

    private void recordDeferredStartupStats() {
        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUpDuration", mDeferredStartupDuration,
                TimeUnit.MILLISECONDS);
        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUpMaxTaskDuration", mMaxTaskDuration,
                TimeUnit.MILLISECONDS);
        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUpCompleteTime",
                SystemClock.uptimeMillis() - UmaUtils.getForegroundStartTime(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a single deferred task to the queue. The caller is responsible for calling
     * queueDeferredTasksOnIdleHandler after adding tasks.
     *
     * @param deferredTask The tasks to be run.
     */
    public void addDeferredTask(Runnable deferredTask) {
        ThreadUtils.assertOnUiThread();
        mDeferredTasks.add(deferredTask);
    }

    /**
     * Handle application level deferred startup tasks that can be lazily done after all
     * the necessary initialization has been completed. Any calls requiring network access should
     * probably go here.
     *
     * Keep these tasks short and break up long tasks into multiple smaller tasks, as they run on
     * the UI thread and are blocking. Remember to follow RAIL guidelines, as much as possible, and
     * that most devices are quite slow, so leave enough buffer.
     */
    @UiThread
    public void initDeferredStartupForApp() {
        if (mDeferredStartupInitializedForApp) return;
        mDeferredStartupInitializedForApp = true;
        ThreadUtils.assertOnUiThread();

        RecordHistogram.recordLongTimesHistogram("UMA.Debug.EnableCrashUpload.DeferredStartUptime2",
                SystemClock.uptimeMillis() - UmaUtils.getForegroundStartTime(),
                TimeUnit.MILLISECONDS);

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Punt all tasks that may block on disk off onto a background thread.
                initAsyncDiskTask();

                DefaultBrowserInfo.initBrowserFetcher();

                AfterStartupTaskUtils.setStartupComplete();

                PartnerBrowserCustomizations.setOnInitializeAsyncFinished(new Runnable() {
                    @Override
                    public void run() {
                        String homepageUrl = HomepageManager.getHomepageUri(mAppContext);
                        LaunchMetrics.recordHomePageLaunchMetrics(
                                HomepageManager.isHomepageEnabled(mAppContext),
                                NewTabPage.isNTPUrl(homepageUrl), homepageUrl);
                    }
                });

                PartnerBookmarksShim.kickOffReading(mAppContext);

                PowerMonitor.create();

                ShareHelper.clearSharedImages();

                OfflinePageUtils.clearSharedOfflineFiles(mAppContext);
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Clear any media notifications that existed when Chrome was last killed.
                MediaCaptureNotificationService.clearMediaNotifications(mAppContext);

                startModerateBindingManagementIfNeeded();

                recordKeyboardLocaleUma();
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Start or stop Physical Web
                PhysicalWeb.onChromeStart();
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                LocaleManager.getInstance().recordStartupMetrics();
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Starts syncing with GSA.
                AppHooks.get().createGsaHelper().startSync();
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Record the saved restore state in a histogram
                ChromeBackupAgent.recordRestoreHistogram();
            }
        });

        ProcessInitializationHandler.getInstance().initializeDeferredStartupTasks();
    }

    private void initAsyncDiskTask() {
        new AsyncTask<Void, Void, Void>() {
            /**
             * The threshold after which it's no longer appropriate to try to attach logcat output
             * to a minidump file.
             * Note: This threshold of 12 hours was chosen fairly imprecisely, based on the
             * following intuition: On the one hand, Chrome can only access its own logcat output,
             * so the most recent lines should be relevant when available. On a typical device,
             * multiple hours of logcat output are available. On the other hand, it's important to
             * provide an escape hatch in case the logcat extraction code itself crashes, as
             * described in the doesCrashMinidumpNeedLogcat() documentation. Since this is a fairly
             * small and relatively frequently-executed piece of code, crashes are expected to be
             * unlikely; so it's okay for the escape hatch to be hard to use -- it's intended as an
             * extreme last resort.
             */
            private static final long LOGCAT_RELEVANCE_THRESHOLD_IN_HOURS = 12;

            private long mAsyncTaskStartTime;

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    TraceEvent.begin("ChromeBrowserInitializer.onDeferredStartup.doInBackground");
                    mAsyncTaskStartTime = SystemClock.uptimeMillis();

                    initCrashReporting();

                    // Initialize the WebappRegistry if it's not already initialized. Must be in
                    // async task due to shared preferences disk access on N.
                    WebappRegistry.getInstance();

                    // Force a widget refresh in order to wake up any possible zombie widgets.
                    // This is needed to ensure the right behavior when the process is suddenly
                    // killed.
                    BookmarkWidgetProvider.refreshAllWidgets(mAppContext);

                    // Initialize whether or not precaching is enabled.
                    PrecacheLauncher.updatePrecachingEnabled(mAppContext);

                    if (ChromeWebApkHost.isEnabled()) {
                        WebApkVersionManager.updateWebApksIfNeeded();
                    }

                    removeSnapshotDatabase();

                    // Warm up all web app shared prefs. This must be run after the WebappRegistry
                    // instance is initialized.
                    WebappRegistry.warmUpSharedPrefs();

                    return null;
                } finally {
                    TraceEvent.end("ChromeBrowserInitializer.onDeferredStartup.doInBackground");
                }
            }

            @Override
            protected void onPostExecute(Void params) {
                // Must be run on the UI thread after the WebappRegistry has been completely warmed.
                WebappRegistry.getInstance().unregisterOldWebapps(System.currentTimeMillis());

                RecordHistogram.recordLongTimesHistogram(
                        "UMA.Debug.EnableCrashUpload.DeferredStartUpAsyncTaskDuration",
                        SystemClock.uptimeMillis() - mAsyncTaskStartTime, TimeUnit.MILLISECONDS);
            }

            /**
             * Initializes the crash reporting system. More specifically, enables the crash
             * reporting system if it is user-permitted, and initiates uploading of any pending
             * crash reports. Also updates some UMA metrics and performs cleanup in the local crash
             * minidump storage directory.
             */
            private void initCrashReporting() {
                // Perform cleanup prior to checking whether crash reporting is enabled, so that
                // users who disable crash reporting are still able to eventually recover disk space
                // dedicated to storing pending crash reports.
                CrashFileManager crashFileManager = new CrashFileManager(mAppContext.getCacheDir());
                crashFileManager.cleanOutAllNonFreshMinidumpFiles();

                // Likewise, there might be pending metrics from previous runs when crash reporting
                // was enabled.
                MinidumpUploadService.storeBreakpadUploadStatsInUma(
                        ChromePreferenceManager.getInstance());

                // Now check whether crash reporting is enabled. If it is, broadcast the appropriate
                // permission.
                boolean crashReportingDisabled = CommandLine.getInstance().hasSwitch(
                        ChromeSwitches.DISABLE_CRASH_DUMP_UPLOAD);
                if (crashReportingDisabled) return;
                PrivacyPreferencesManager.getInstance().enablePotentialCrashUploading();

                RecordHistogram.recordLongTimesHistogram("UMA.Debug.EnableCrashUpload.Uptime3",
                        mAsyncTaskStartTime - UmaUtils.getForegroundStartTime(),
                        TimeUnit.MILLISECONDS);

                // Finally, uploading any pending crash reports.
                File[] minidumps = crashFileManager.getAllMinidumpFiles(
                        MinidumpUploadService.MAX_TRIES_ALLOWED);
                int numMinidumpsSansLogcat = 0;
                for (File minidump : minidumps) {
                    if (CrashFileManager.isMinidumpMIMEFirstTry(minidump.getName())) {
                        ++numMinidumpsSansLogcat;
                    }
                }
                // TODO(isherman): These two histograms are intended to be temporary, and can
                // probably be removed around the M60 timeframe: http://crbug.com/699785
                RecordHistogram.recordSparseSlowlyHistogram(
                        "Stability.Android.PendingMinidumpsOnStartup", minidumps.length);
                RecordHistogram.recordSparseSlowlyHistogram(
                        "Stability.Android.PendingMinidumpsOnStartup.SansLogcat",
                        numMinidumpsSansLogcat);
                if (minidumps.length == 0) return;

                Log.i(TAG, "Attempting to upload %d accumulated crash dumps.", minidumps.length);
                File mostRecentMinidump = minidumps[0];
                if (doesCrashMinidumpNeedLogcat(mostRecentMinidump)) {
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(
                            new LogcatExtractionRunnable(mAppContext, mostRecentMinidump));

                    // The JobScheduler will schedule uploads for all of the available minidumps
                    // once the logcat is attached. But if the JobScheduler API is not being used,
                    // then the logcat extraction process will only initiate an upload for the first
                    // minidump; it's required to manually initiate uploads for all of the remaining
                    // minidumps.
                    if (!MinidumpUploadService.shouldUseJobSchedulerForUploads()) {
                        List<File> remainingMinidumps =
                                Arrays.asList(minidumps).subList(1, minidumps.length);
                        for (File minidump : remainingMinidumps) {
                            MinidumpUploadService.tryUploadCrashDump(mAppContext, minidump);
                        }
                    }
                } else if (MinidumpUploadService.shouldUseJobSchedulerForUploads()) {
                    MinidumpUploadService.scheduleUploadJob(mAppContext);
                } else {
                    MinidumpUploadService.tryUploadAllCrashDumps(mAppContext);
                }
            }

            /**
             * Returns whether or not it's appropriate to try to extract recent logcat output and
             * include that logcat output alongside the given {@param minidump} in a crash report.
             * Logcat output should only be extracted if (a) it hasn't already been extracted for
             * this minidump file, and (b) the minidump is fairly fresh. The freshness check is
             * important for two reasons: (1) First of all, it helps avoid including irrelevant
             * logcat output for a crash report. (2) Secondly, it provides an escape hatch that can
             * help circumvent a possible infinite crash loop, if the code responsible for
             * extracting and appending the logcat content is itself crashing. That is, the user can
             * wait 12 hours prior to relaunching Chrome, at which point this potential crash loop
             * would be circumvented.
             * @return Whether to try to include logcat output in the crash report corresponding to
             *     the given minidump.
             */
            private boolean doesCrashMinidumpNeedLogcat(File minidump) {
                if (!CrashFileManager.isMinidumpMIMEFirstTry(minidump.getName())) return false;

                long ageInMillis = new Date().getTime() - minidump.lastModified();
                long ageInHours = TimeUnit.HOURS.convert(ageInMillis, TimeUnit.MILLISECONDS);
                return ageInHours < LOGCAT_RELEVANCE_THRESHOLD_IN_HOURS;
            }
        }
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void startModerateBindingManagementIfNeeded() {
        // Moderate binding doesn't apply to low end devices.
        if (SysUtils.isLowEndDevice()) return;

        boolean moderateBindingTillBackgrounded =
                FieldTrialList.findFullName("ModerateBindingOnBackgroundTabCreation")
                        .equals("Enabled");
        ChildProcessLauncher.startModerateBindingManagement(
                mAppContext, moderateBindingTillBackgrounded);
    }

    /**
     * Deletes the snapshot database which is no longer used because the feature has been removed
     * in Chrome M41.
     */
    @WorkerThread
    private void removeSnapshotDatabase() {
        synchronized (SNAPSHOT_DATABASE_LOCK) {
            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            if (!prefs.getBoolean(SNAPSHOT_DATABASE_REMOVED, false)) {
                mAppContext.deleteDatabase(SNAPSHOT_DATABASE_NAME);
                prefs.edit().putBoolean(SNAPSHOT_DATABASE_REMOVED, true).apply();
            }
        }
    }

    @SuppressWarnings("deprecation")  // InputMethodSubtype.getLocale() deprecated in API 24
    private void recordKeyboardLocaleUma() {
        InputMethodManager imm =
                (InputMethodManager) mAppContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> ims = imm.getEnabledInputMethodList();
        ArrayList<String> uniqueLanguages = new ArrayList<>();
        for (InputMethodInfo method : ims) {
            List<InputMethodSubtype> submethods =
                    imm.getEnabledInputMethodSubtypeList(method, true);
            for (InputMethodSubtype submethod : submethods) {
                if (submethod.getMode().equals("keyboard")) {
                    String language = submethod.getLocale().split("_")[0];
                    if (!uniqueLanguages.contains(language)) {
                        uniqueLanguages.add(language);
                    }
                }
            }
        }
        RecordHistogram.recordCountHistogram("InputMethod.ActiveCount", uniqueLanguages.size());

        InputMethodSubtype currentSubtype = imm.getCurrentInputMethodSubtype();
        Locale systemLocale = Locale.getDefault();
        if (currentSubtype != null && currentSubtype.getLocale() != null && systemLocale != null) {
            String keyboardLanguage = currentSubtype.getLocale().split("_")[0];
            boolean match = systemLocale.getLanguage().equalsIgnoreCase(keyboardLanguage);
            RecordHistogram.recordBooleanHistogram("InputMethod.MatchesSystemLanguage", match);
        }
    }

    /**
     * @return Whether deferred startup has been completed.
     */
    @VisibleForTesting
    public boolean isDeferredStartupCompleteForApp() {
        return mDeferredStartupCompletedForApp;
    }
}
