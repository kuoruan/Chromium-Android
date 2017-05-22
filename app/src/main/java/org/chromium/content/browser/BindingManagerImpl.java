// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.TargetApi;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.util.LruCache;
import android.util.SparseArray;

import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;

import java.util.Map;

/**
 * Manages oom bindings used to bound child services.
 */
class BindingManagerImpl implements BindingManager {
    private static final String TAG = "cr.BindingManager";

    // Low reduce ratio of moderate binding.
    private static final float MODERATE_BINDING_LOW_REDUCE_RATIO = 0.25f;
    // High reduce ratio of moderate binding.
    private static final float MODERATE_BINDING_HIGH_REDUCE_RATIO = 0.5f;

    // Delay of 1 second used when removing temporary strong binding of a process (only on
    // non-low-memory devices).
    private static final long DETACH_AS_ACTIVE_HIGH_END_DELAY_MILLIS = 1 * 1000;

    // Delays used when clearing moderate binding pool when onSentToBackground happens.
    private static final long MODERATE_BINDING_POOL_CLEARER_DELAY_MILLIS = 10 * 1000;

    // These fields allow to override the parameters for testing - see
    // createBindingManagerForTesting().
    private final boolean mIsLowMemoryDevice;

    private static class ModerateBindingPool
            extends LruCache<Integer, ManagedConnection> implements ComponentCallbacks2 {
        private final Object mDelayedClearerLock = new Object();
        private Runnable mDelayedClearer;
        private final Handler mHandler = new Handler(ThreadUtils.getUiThreadLooper());

        public ModerateBindingPool(int maxSize) {
            super(maxSize);
        }

        @Override
        public void onTrimMemory(int level) {
            Log.i(TAG, "onTrimMemory: level=%d, size=%d", level, size());
            if (size() > 0) {
                if (level <= TRIM_MEMORY_RUNNING_MODERATE) {
                    reduce(MODERATE_BINDING_LOW_REDUCE_RATIO);
                } else if (level <= TRIM_MEMORY_RUNNING_LOW) {
                    reduce(MODERATE_BINDING_HIGH_REDUCE_RATIO);
                } else if (level == TRIM_MEMORY_UI_HIDDEN) {
                    // This will be handled by |mDelayedClearer|.
                    return;
                } else {
                    evictAll();
                }
            }
        }

        @Override
        public void onLowMemory() {
            Log.i(TAG, "onLowMemory: evict %d bindings", size());
            evictAll();
        }

        @Override
        public void onConfigurationChanged(Configuration configuration) {}

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        private void reduce(float reduceRatio) {
            int oldSize = size();
            int newSize = (int) (oldSize * (1f - reduceRatio));
            Log.i(TAG, "Reduce connections from %d to %d", oldSize, newSize);
            if (newSize == 0) {
                evictAll();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                trimToSize(newSize);
            } else {
                // Entries will be removed from the front because snapshot() returns ones ordered
                // from least recently accessed to most recently accessed.
                int count = 0;
                for (Map.Entry<Integer, ManagedConnection> entry : snapshot().entrySet()) {
                    remove(entry.getKey());
                    ++count;
                    if (count == oldSize - newSize) break;
                }
            }
        }

        void addConnection(ManagedConnection managedConnection) {
            ChildProcessConnection connection = managedConnection.mConnection;
            if (connection != null && connection.isInSandbox()) {
                managedConnection.addModerateBinding();
                if (connection.isModerateBindingBound()) {
                    put(connection.getServiceNumber(), managedConnection);
                } else {
                    remove(connection.getServiceNumber());
                }
            }
        }

        void removeConnection(ManagedConnection managedConnection) {
            ChildProcessConnection connection = managedConnection.mConnection;
            if (connection != null && connection.isInSandbox()) {
                remove(connection.getServiceNumber());
            }
        }

        @Override
        protected void entryRemoved(boolean evicted, Integer key, ManagedConnection oldValue,
                ManagedConnection newValue) {
            if (oldValue != newValue) {
                oldValue.removeModerateBinding();
            }
        }

        void onSentToBackground(final boolean onTesting) {
            if (size() == 0) return;
            synchronized (mDelayedClearerLock) {
                mDelayedClearer = new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mDelayedClearerLock) {
                            if (mDelayedClearer == null) return;
                            mDelayedClearer = null;
                        }
                        Log.i(TAG, "Release moderate connections: %d", size());
                        if (!onTesting) {
                            RecordHistogram.recordCountHistogram(
                                    "Android.ModerateBindingCount", size());
                        }
                        evictAll();
                    }
                };
                mHandler.postDelayed(mDelayedClearer, MODERATE_BINDING_POOL_CLEARER_DELAY_MILLIS);
            }
        }

        void onBroughtToForeground() {
            synchronized (mDelayedClearerLock) {
                if (mDelayedClearer == null) return;
                mHandler.removeCallbacks(mDelayedClearer);
                mDelayedClearer = null;
            }
        }
    }

    private final Object mModerateBindingPoolLock = new Object();
    private ModerateBindingPool mModerateBindingPool;

    /**
     * Wraps ChildProcessConnection keeping track of additional information needed to manage the
     * bindings of the connection. The reference to ChildProcessConnection is cleared when the
     * connection goes away, but ManagedConnection itself is kept (until overwritten by a new entry
     * for the same pid).
     */
    private class ManagedConnection {
        // Set in constructor, cleared in clearConnection() (on a separate thread).
        // Need to keep a local reference to avoid it being cleared while using it.
        private ChildProcessConnection mConnection;

        // True iff there is a strong binding kept on the service because it is working in
        // foreground.
        private boolean mInForeground;

        // True iff there is a strong binding kept on the service because it was bound for the
        // application background period.
        private boolean mBoundForBackgroundPeriod;

        // When mConnection is cleared, oom binding status is stashed here.
        private boolean mWasOomProtected;

        /**
         * Removes the initial service binding.
         * @return true if the binding was removed.
         */
        private boolean removeInitialBinding() {
            ChildProcessConnection connection = mConnection;
            if (connection == null || !connection.isInitialBindingBound()) return false;

            connection.removeInitialBinding();
            return true;
        }

        /** Adds a strong service binding. */
        private void addStrongBinding() {
            ChildProcessConnection connection = mConnection;
            if (connection == null) return;

            connection.addStrongBinding();
            ModerateBindingPool moderateBindingPool;
            synchronized (mModerateBindingPoolLock) {
                moderateBindingPool = mModerateBindingPool;
            }
            if (moderateBindingPool != null) moderateBindingPool.removeConnection(this);
        }

        /** Removes a strong service binding. */
        private void removeStrongBinding(final boolean keepAsModerate) {
            final ChildProcessConnection connection = mConnection;
            // We have to fail gracefully if the strong binding is not present, as on low-end the
            // binding could have been removed by dropOomBindings() when a new service was started.
            if (connection == null || !connection.isStrongBindingBound()) return;

            // This runnable performs the actual unbinding. It will be executed synchronously when
            // on low-end devices and posted with a delay otherwise.
            Runnable doUnbind = new Runnable() {
                @Override
                public void run() {
                    if (connection.isStrongBindingBound()) {
                        connection.removeStrongBinding();
                        if (keepAsModerate) {
                            addConnectionToModerateBindingPool(connection);
                        }
                    }
                }
            };

            if (mIsLowMemoryDevice) {
                doUnbind.run();
            } else {
                ThreadUtils.postOnUiThreadDelayed(doUnbind, DETACH_AS_ACTIVE_HIGH_END_DELAY_MILLIS);
            }
        }

        /**
         * Adds connection to the moderate binding pool. No-op if the connection has a strong
         * binding.
         * @param connection The ChildProcessConnection to add to the moderate binding pool.
         */
        private void addConnectionToModerateBindingPool(ChildProcessConnection connection) {
            ModerateBindingPool moderateBindingPool;
            synchronized (mModerateBindingPoolLock) {
                moderateBindingPool = mModerateBindingPool;
            }
            if (moderateBindingPool != null && !connection.isStrongBindingBound()) {
                moderateBindingPool.addConnection(ManagedConnection.this);
            }
        }

        /** Removes the moderate service binding. */
        private void removeModerateBinding() {
            ChildProcessConnection connection = mConnection;
            if (connection == null || !connection.isModerateBindingBound()) return;

            connection.removeModerateBinding();
        }

        /** Adds the moderate service binding. */
        private void addModerateBinding() {
            ChildProcessConnection connection = mConnection;
            if (connection == null) return;

            connection.addModerateBinding();
        }

        /**
         * Drops the service bindings. This is used on low-end to drop bindings of the current
         * service when a new one is used in foreground.
         */
        private void dropBindings() {
            assert mIsLowMemoryDevice;
            ChildProcessConnection connection = mConnection;
            if (connection == null) return;

            connection.dropOomBindings();
        }

        ManagedConnection(ChildProcessConnection connection) {
            mConnection = connection;
        }

        /**
         * Sets the visibility of the service, adding or removing the strong binding as needed.
         */
        void setInForeground(boolean nextInForeground) {
            if (!mInForeground && nextInForeground) {
                addStrongBinding();
            } else if (mInForeground && !nextInForeground) {
                removeStrongBinding(true);
            }

            mInForeground = nextInForeground;
        }

        /**
         * Called when it is safe to rely on setInForeground() for binding management.
         */
        void determinedVisibility() {
            if (!removeInitialBinding()) return;

            if (mModerateBindingTillBackgrounded) {
                addConnectionToModerateBindingPool(mConnection);
            }
        }

        /**
         * Sets or removes additional binding when the service is main service during the embedder
         * background period.
         */
        void setBoundForBackgroundPeriod(boolean nextBound) {
            if (!mBoundForBackgroundPeriod && nextBound) {
                addStrongBinding();
            } else if (mBoundForBackgroundPeriod && !nextBound) {
                removeStrongBinding(false);
            }

            mBoundForBackgroundPeriod = nextBound;
        }

        boolean isOomProtected() {
            // When a process crashes, we can be queried about its oom status before or after the
            // connection is cleared. For the latter case, the oom status is stashed in
            // mWasOomProtected.
            ChildProcessConnection connection = mConnection;
            return connection != null
                    ? connection.isOomProtectedOrWasWhenDied() : mWasOomProtected;
        }

        void clearConnection() {
            mWasOomProtected = mConnection.isOomProtectedOrWasWhenDied();
            ModerateBindingPool moderateBindingPool = mModerateBindingPool;
            if (moderateBindingPool != null) moderateBindingPool.removeConnection(this);
            mConnection = null;
        }

        /** @return true iff the reference to the connection is no longer held */
        @VisibleForTesting
        boolean isConnectionCleared() {
            return mConnection == null;
        }
    }

    // Whether a moderate binding should be added to a render process on process creation and
    // removed when Chrome is sent to the background.
    private boolean mModerateBindingTillBackgrounded;

    // This can be manipulated on different threads, synchronize access on mManagedConnections.
    private final SparseArray<ManagedConnection> mManagedConnections =
            new SparseArray<ManagedConnection>();

    // The connection that was most recently set as foreground (using setInForeground()). This is
    // used to add additional binding on it when the embedder goes to background. On low-end, this
    // is also used to drop process bidnings when a new one is created, making sure that only one
    // renderer process at a time is protected from oom killing.
    private ManagedConnection mLastInForeground;

    // Synchronizes operations that access mLastInForeground: setInForeground() and
    // addNewConnection().
    private final Object mLastInForegroundLock = new Object();

    // The connection bound with additional binding in onSentToBackground().
    private ManagedConnection mBoundForBackgroundPeriod;

    // Whether this instance is used on testing.
    private final boolean mOnTesting;

    /**
     * The constructor is private to hide parameters exposed for testing from the regular consumer.
     * Use factory methods to create an instance.
     */
    private BindingManagerImpl(boolean isLowMemoryDevice, boolean onTesting) {
        mIsLowMemoryDevice = isLowMemoryDevice;
        mOnTesting = onTesting;
    }

    public static BindingManagerImpl createBindingManager() {
        return new BindingManagerImpl(SysUtils.isLowEndDevice(), false);
    }

    /**
     * Creates a testing instance of BindingManager. Testing instance will have the unbinding delays
     * set to 0, so that the tests don't need to deal with actual waiting.
     * @param isLowEndDevice true iff the created instance should apply low-end binding policies
     */
    public static BindingManagerImpl createBindingManagerForTesting(boolean isLowEndDevice) {
        return new BindingManagerImpl(isLowEndDevice, true);
    }

    @Override
    public void addNewConnection(int pid, ChildProcessConnection connection) {
        // This will reset the previous entry for the pid in the unlikely event of the OS
        // reusing renderer pids.
        synchronized (mManagedConnections) {
            mManagedConnections.put(pid, new ManagedConnection(connection));
        }
    }

    @Override
    public void setInForeground(int pid, boolean inForeground) {
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }

        if (managedConnection == null) {
            Log.w(TAG, "Cannot setInForeground() - never saw a connection for the pid: %d", pid);
            return;
        }

        synchronized (mLastInForegroundLock) {
            if (inForeground && mIsLowMemoryDevice && mLastInForeground != null
                    && mLastInForeground != managedConnection) {
                mLastInForeground.dropBindings();
            }

            managedConnection.setInForeground(inForeground);
            if (inForeground) mLastInForeground = managedConnection;
        }
    }

    @Override
    public void determinedVisibility(int pid) {
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }

        if (managedConnection == null) {
            Log.w(TAG, "Cannot call determinedVisibility() - never saw a connection for the pid: "
                    + "%d", pid);
            return;
        }

        managedConnection.determinedVisibility();
    }

    @Override
    public void onSentToBackground() {
        assert mBoundForBackgroundPeriod == null;
        synchronized (mLastInForegroundLock) {
            // mLastInForeground can be null at this point as the embedding application could be
            // used in foreground without spawning any renderers.
            if (mLastInForeground != null) {
                mLastInForeground.setBoundForBackgroundPeriod(true);
                mBoundForBackgroundPeriod = mLastInForeground;
            }
        }
        ModerateBindingPool moderateBindingPool;
        synchronized (mModerateBindingPoolLock) {
            moderateBindingPool = mModerateBindingPool;
        }
        if (moderateBindingPool != null) moderateBindingPool.onSentToBackground(mOnTesting);
    }

    @Override
    public void onBroughtToForeground() {
        if (mBoundForBackgroundPeriod != null) {
            mBoundForBackgroundPeriod.setBoundForBackgroundPeriod(false);
            mBoundForBackgroundPeriod = null;
        }
        ModerateBindingPool moderateBindingPool;
        synchronized (mModerateBindingPoolLock) {
            moderateBindingPool = mModerateBindingPool;
        }
        if (moderateBindingPool != null) moderateBindingPool.onBroughtToForeground();
    }

    @Override
    public boolean isOomProtected(int pid) {
        // In the unlikely event of the OS reusing renderer pid, the call will refer to the most
        // recent renderer of the given pid. The binding state for a pid is being reset in
        // addNewConnection().
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }
        return managedConnection != null ? managedConnection.isOomProtected() : false;
    }

    @Override
    public void clearConnection(int pid) {
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }
        if (managedConnection != null) managedConnection.clearConnection();
    }

    /** @return true iff the connection reference is no longer held */
    @VisibleForTesting
    public boolean isConnectionCleared(int pid) {
        synchronized (mManagedConnections) {
            return mManagedConnections.get(pid).isConnectionCleared();
        }
    }

    @Override
    public void startModerateBindingManagement(
            Context context, int maxSize, boolean moderateBindingTillBackgrounded) {
        synchronized (mModerateBindingPoolLock) {
            if (mIsLowMemoryDevice || mModerateBindingPool != null) return;

            mModerateBindingTillBackgrounded = moderateBindingTillBackgrounded;

            Log.i(TAG, "Moderate binding enabled: maxSize=%d", maxSize);
            mModerateBindingPool = new ModerateBindingPool(maxSize);
            if (context != null) context.registerComponentCallbacks(mModerateBindingPool);
        }
    }

    @Override
    public void releaseAllModerateBindings() {
        ModerateBindingPool moderateBindingPool;
        synchronized (mModerateBindingPoolLock) {
            moderateBindingPool = mModerateBindingPool;
        }
        if (moderateBindingPool != null) {
            Log.i(TAG, "Release all moderate bindings: %d", moderateBindingPool.size());
            moderateBindingPool.evictAll();
        }
    }
}
