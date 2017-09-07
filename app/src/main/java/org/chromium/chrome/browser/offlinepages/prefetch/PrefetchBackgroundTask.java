// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.prefetch;

import android.content.Context;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.background_task_scheduler.NativeBackgroundTask;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.background_task_scheduler.BackgroundTask.TaskFinishedCallback;
import org.chromium.components.background_task_scheduler.BackgroundTaskScheduler;
import org.chromium.components.background_task_scheduler.BackgroundTaskSchedulerFactory;
import org.chromium.components.background_task_scheduler.TaskIds;
import org.chromium.components.background_task_scheduler.TaskInfo;
import org.chromium.components.background_task_scheduler.TaskParameters;

import java.util.concurrent.TimeUnit;

/**
 * Handles servicing background offlining requests.
 *
 * Can schedule or cancel tasks, and handles the actual initialization that
 * happens when a task fires.
 */
@JNINamespace("offline_pages::prefetch")
public class PrefetchBackgroundTask extends NativeBackgroundTask {
    public static final long DEFAULT_START_DELAY_SECONDS = 15 * 60;

    private static final String TAG = "OPPrefetchBGTask";

    private static BackgroundTaskScheduler sSchedulerInstance;

    private long mNativeTask = 0;
    private TaskFinishedCallback mTaskFinishedCallback = null;
    private Profile mProfile = null;

    public PrefetchBackgroundTask() {}

    static BackgroundTaskScheduler getScheduler() {
        if (sSchedulerInstance != null) {
            return sSchedulerInstance;
        }
        return BackgroundTaskSchedulerFactory.getScheduler();
    }

    protected Profile getProfile() {
        if (mProfile == null) mProfile = Profile.getLastUsedProfile();
        return mProfile;
    }

    /**
     * Schedules the default 'NWake' task for the prefetching service.
     *
     * This task will only be scheduled on a good network type.
     * TODO(dewittj): Handle skipping work if the battery percentage is too low.
     */
    @CalledByNative
    public static void scheduleTask(int additionalDelaySeconds) {
        TaskInfo taskInfo =
                TaskInfo.createOneOffTask(TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID,
                                PrefetchBackgroundTask.class,
                                // Minimum time to wait
                                TimeUnit.SECONDS.toMillis(
                                        DEFAULT_START_DELAY_SECONDS + additionalDelaySeconds),
                                // Maximum time to wait.  After this interval the event will fire
                                // regardless of whether the conditions are right.
                                TimeUnit.DAYS.toMillis(7))
                        .setRequiredNetworkType(TaskInfo.NETWORK_TYPE_UNMETERED)
                        .setIsPersisted(true)
                        .setUpdateCurrent(true)
                        .build();
        getScheduler().schedule(ContextUtils.getApplicationContext(), taskInfo);
    }

    /**
     * Cancels the default 'NWake' task for the prefetching service.
     */
    @CalledByNative
    public static void cancelTask() {
        getScheduler().cancel(
                ContextUtils.getApplicationContext(), TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID);
    }

    @Override
    public int onStartTaskBeforeNativeLoaded(
            Context context, TaskParameters taskParameters, TaskFinishedCallback callback) {
        // TODO(dewittj): Ensure that the conditions are right to do work.  If the maximum time to
        // wait is reached, it is possible the task will fire even if network conditions are
        // incorrect.  We want:
        // * Unmetered WiFi connection
        // * >50% battery
        // * Preferences enabled.
        return NativeBackgroundTask.LOAD_NATIVE;
    }

    @Override
    protected void onStartTaskWithNative(
            Context context, TaskParameters taskParameters, TaskFinishedCallback callback) {
        assert taskParameters.getTaskId() == TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID;
        if (mNativeTask != 0) return;

        mTaskFinishedCallback = callback;
        nativeStartPrefetchTask(getProfile());
    }

    @Override
    protected boolean onStopTaskBeforeNativeLoaded(Context context, TaskParameters taskParameters) {
        // TODO(dewittj): Implement this properly.
        return true;
    }

    @Override
    protected boolean onStopTaskWithNative(Context context, TaskParameters taskParameters) {
        assert taskParameters.getTaskId() == TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID;
        assert mNativeTask != 0;

        return nativeOnStopTask(mNativeTask);
    }

    @Override
    public void reschedule(Context context) {
        // TODO(dewittj): Set the backoff time appropriately.
        scheduleTask(0);
    }

    /**
     * Called during construction of the native task.
     *
     * PrefetchBackgroundTask#onStartTask constructs the native task.
     */
    @VisibleForTesting
    @CalledByNative
    void setNativeTask(long nativeTask) {
        mNativeTask = nativeTask;
    }

    /**
     * Invoked by the native task when it is destroyed.
     */
    @VisibleForTesting
    @CalledByNative
    void doneProcessing(boolean needsReschedule) {
        assert mTaskFinishedCallback != null;
        mTaskFinishedCallback.taskFinished(needsReschedule);
        setNativeTask(0);
    }

    @VisibleForTesting
    static void setSchedulerForTesting(BackgroundTaskScheduler scheduler) {
        sSchedulerInstance = scheduler;
    }

    @VisibleForTesting
    void setTaskReschedulingForTesting(boolean reschedule, boolean backoff) {
        if (mNativeTask == 0) return;
        nativeSetTaskReschedulingForTesting(mNativeTask, reschedule, backoff);
    }

    @VisibleForTesting
    void signalTaskFinishedForTesting() {
        if (mNativeTask == 0) return;
        nativeSignalTaskFinishedForTesting(mNativeTask);
    }

    @VisibleForTesting
    native boolean nativeStartPrefetchTask(Profile profile);
    @VisibleForTesting
    native boolean nativeOnStopTask(long nativePrefetchBackgroundTask);
    native void nativeSetTaskReschedulingForTesting(
            long nativePrefetchBackgroundTask, boolean reschedule, boolean backoff);
    native void nativeSignalTaskFinishedForTesting(long nativePrefetchBackgroundTask);
}
