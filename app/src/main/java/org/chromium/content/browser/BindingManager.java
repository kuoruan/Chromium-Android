// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.support.v4.util.ArraySet;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.process_launcher.ChildProcessConnection;

import java.util.Set;

/**
 * Manages oom bindings used to bound child services.
 * This object must only be accessed from the launcher thread.
 */
class BindingManager implements ComponentCallbacks2 {
    private static final String TAG = "cr_BindingManager";

    // Low reduce ratio of moderate binding.
    private static final float MODERATE_BINDING_LOW_REDUCE_RATIO = 0.25f;
    // High reduce ratio of moderate binding.
    private static final float MODERATE_BINDING_HIGH_REDUCE_RATIO = 0.5f;

    // Delays used when clearing moderate binding pool when onSentToBackground happens.
    private static final long MODERATE_BINDING_POOL_CLEARER_DELAY_MILLIS = 10 * 1000;

    private final Set<ChildProcessConnection> mConnections = new ArraySet<ChildProcessConnection>();
    private final int mMaxSize;
    private final Iterable<ChildProcessConnection> mRanking;
    private final Runnable mDelayedClearer;

    @Override
    public void onTrimMemory(final int level) {
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "onTrimMemory: level=%d, size=%d", level, mConnections.size());
                if (mConnections.isEmpty()) {
                    return;
                }
                if (level <= TRIM_MEMORY_RUNNING_MODERATE) {
                    reduce(MODERATE_BINDING_LOW_REDUCE_RATIO);
                } else if (level <= TRIM_MEMORY_RUNNING_LOW) {
                    reduce(MODERATE_BINDING_HIGH_REDUCE_RATIO);
                } else if (level == TRIM_MEMORY_UI_HIDDEN) {
                    // This will be handled by |mDelayedClearer|.
                    return;
                } else {
                    removeAllConnections();
                }
            }
        });
    }

    @Override
    public void onLowMemory() {
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "onLowMemory: evict %d bindings", mConnections.size());
                removeAllConnections();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {}

    private void reduce(float reduceRatio) {
        int oldSize = mConnections.size();
        int newSize = (int) (oldSize * (1f - reduceRatio));
        Log.i(TAG, "Reduce connections from %d to %d", oldSize, newSize);
        removeOldConnections(oldSize - newSize);
        assert mConnections.size() == newSize;
    }

    private void addAndUseConnection(ChildProcessConnection connection) {
        // Note that the size of connections is currently fairly small (40).
        // If it became bigger we should consider using an alternate data structure.
        boolean alreadyInQueue = !mConnections.add(connection);
        if (!alreadyInQueue) connection.addModerateBinding();
        assert mConnections.size() <= mMaxSize;
    }

    private void removeConnection(ChildProcessConnection connection) {
        boolean alreadyInQueue = mConnections.remove(connection);
        if (alreadyInQueue) connection.removeModerateBinding();
        assert !mConnections.contains(connection);
    }

    private void removeAllConnections() {
        removeOldConnections(mConnections.size());
    }

    private void removeOldConnections(int numberOfConnections) {
        assert numberOfConnections <= mConnections.size();
        int numRemoved = 0;
        for (ChildProcessConnection connection : mRanking) {
            if (mConnections.contains(connection)) {
                connection.removeModerateBinding();
                mConnections.remove(connection);
                if (++numRemoved == numberOfConnections) break;
            }
        }
    }

    /**
     * Called when the embedding application is sent to background.
     * The embedder needs to ensure that:
     *  - every onBroughtToForeground() is followed by onSentToBackground()
     *  - pairs of consecutive onBroughtToForeground() / onSentToBackground() calls do not overlap
     */
    void onSentToBackground() {
        assert LauncherThread.runningOnLauncherThread();
        if (mConnections.isEmpty()) return;
        LauncherThread.postDelayed(mDelayedClearer, MODERATE_BINDING_POOL_CLEARER_DELAY_MILLIS);
    }

    /** Called when the embedding application is brought to foreground. */
    void onBroughtToForeground() {
        assert LauncherThread.runningOnLauncherThread();
        LauncherThread.removeCallbacks(mDelayedClearer);
    }

    // Whether this instance is used on testing.
    private final boolean mOnTesting;

    /**
     * The constructor is private to hide parameters exposed for testing from the regular consumer.
     * Use factory methods to create an instance.
     */
    BindingManager(Context context, int maxSize, Iterable<ChildProcessConnection> ranking,
            boolean onTesting) {
        assert LauncherThread.runningOnLauncherThread();
        Log.i(TAG, "Moderate binding enabled: maxSize=%d", maxSize);

        mOnTesting = onTesting;
        mMaxSize = maxSize;
        mRanking = ranking;
        assert mMaxSize > 0;

        mDelayedClearer = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Release moderate connections: %d", mConnections.size());
                if (!mOnTesting) {
                    RecordHistogram.recordCountHistogram(
                            "Android.ModerateBindingCount", mConnections.size());
                }
                removeAllConnections();
            }
        };

        // Note that it is safe to call Context.registerComponentCallbacks from a background
        // thread.
        context.registerComponentCallbacks(this);
    }

    public void increaseRecency(ChildProcessConnection connection) {
        assert LauncherThread.runningOnLauncherThread();
        addAndUseConnection(connection);
    }

    public void dropRecency(ChildProcessConnection connection) {
        assert LauncherThread.runningOnLauncherThread();
        removeConnection(connection);
    }
}
