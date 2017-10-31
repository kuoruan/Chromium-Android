// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.prefetch;

import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.components.background_task_scheduler.BackgroundTaskSchedulerFactory;
import org.chromium.components.background_task_scheduler.TaskIds;
import org.chromium.components.background_task_scheduler.TaskInfo;

import java.util.concurrent.TimeUnit;

/**
 * Handles scheduling background task for offline pages prefetching.
 */
@JNINamespace("offline_pages::prefetch")
public class PrefetchBackgroundTaskScheduler {
    public static final long DEFAULT_START_DELAY_SECONDS = 15 * 60;

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
        BackgroundTaskSchedulerFactory.getScheduler().schedule(
                ContextUtils.getApplicationContext(), taskInfo);
    }

    /**
     * Cancels the default 'NWake' task for the prefetching service.
     */
    @CalledByNative
    public static void cancelTask() {
        BackgroundTaskSchedulerFactory.getScheduler().cancel(
                ContextUtils.getApplicationContext(), TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID);
    }
}
