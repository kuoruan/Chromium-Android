// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.content.R;

/**
 * Class for listening to Android MediaServer Crashes to throttle media decoding
 * when needed.
 */
@JNINamespace("content")
class MediaThrottler implements MediaPlayer.OnErrorListener {
    private static final String TAG = "cr_MediaThrottler";
    private static final long UNKNOWN_LAST_SERVER_CRASH_TIME = -1;

    // Number of active decode requests.
    private int mRequestCount;

    // Application context.
    private final Context mContext;

    // Watch dog player. Used to listen to all media server crashes.
    private MediaPlayer mPlayer;

    // The last media server crash time since Chrome lauches.
    private long mLastCrashTime = UNKNOWN_LAST_SERVER_CRASH_TIME;

    // Server crash count since last reset() call.
    private int mServerCrashCount;

    // Object for synchronized access to memeber variables.
    private final Object mLock = new Object();

    // Handler for posting delayed tasks.
    private Handler mHandler;

    // Intervals between media server crashes that are considered normal. It
    // takes about 5 seconds to restart the media server. So this value has to
    // be larger than 5 seconds.
    private static final long SERVER_CRASH_INTERVAL_THRESHOLD_IN_MILLIS = 60000;

    // Delay to keep the watch dog player alive When there are no decoding
    // requests. This is introduced to avoid recreating the watch dog over and
    // over if a burst of small decoding requests arrive.
    private static final int RELEASE_WATCH_DOG_PLAYER_DELAY_IN_MILLIS = 5000;

    // When |mServerCrashCount| reaches this threshold, throttling will start.
    // This is to prevent a page from loading a malformed video over and over
    // to crash the media server excessively.
    private static final int SERVER_CRASH_COUNT_THRESHOLD_FOR_THROTTLING = 4;

    /**
     * A background task to release the watch dog player.
     */
    private class ReleaseWatchDogTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            synchronized (mLock) {
                if (mRequestCount == 0 && mPlayer != null) {
                    mPlayer.release();
                    mPlayer = null;
                }
            }
            return null;
        }
    }

    private final Runnable mDelayedReleaseRunnable = new Runnable() {
        @Override
        public void run() {
            new ReleaseWatchDogTask().execute();
        }
    };

    @CalledByNative
    private static MediaThrottler create(Context context) {
        return new MediaThrottler(context);
    }

    private MediaThrottler(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * A background task to start the watch dog player.
     */
    private class StartWatchDogTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            synchronized (mLock) {
                if (mPlayer != null || mRequestCount == 0) return null;
                try {
                    mPlayer = MediaPlayer.create(mContext, R.raw.empty);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Exception happens while creating the watch dog player.", e);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception happens while creating the watch dog player.", e);
                }
                if (mPlayer == null) {
                    Log.e(TAG, "Unable to create watch dog player, treat it as server crash.");
                    onMediaServerCrash();
                } else {
                    mPlayer.setOnErrorListener(MediaThrottler.this);
                }
            }
            return null;
        }
    }

    /**
     * Called to request the permission to decode media data.
     *
     * @return true if the request is permitted, or false otherwise.
     */
    @CalledByNative
    private boolean requestDecoderResources() {
        synchronized (mLock) {
            long currentTime = SystemClock.elapsedRealtime();
            if (mLastCrashTime != UNKNOWN_LAST_SERVER_CRASH_TIME
                    && (currentTime - mLastCrashTime < SERVER_CRASH_INTERVAL_THRESHOLD_IN_MILLIS)
                    && mServerCrashCount >= SERVER_CRASH_COUNT_THRESHOLD_FOR_THROTTLING) {
                Log.e(TAG, "Request to decode media data denied due to throttling.");
                return false;
            }
            mRequestCount++;
            if (mRequestCount == 1 || mPlayer == null) {
                mHandler.removeCallbacks(mDelayedReleaseRunnable);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        new StartWatchDogTask().execute();
                    }
                });
            }
        }
        return true;
    }

    /**
     * Called to signal that a decode request has been completed.
     */
    @CalledByNative
    private void onDecodeRequestFinished() {
        synchronized (mLock) {
            mRequestCount--;
            if (mRequestCount == 0) {
                // Don't release the watch dog immediately, there could be a
                // number of small requests coming together.
                prepareToStopWatchDog();
            }
        }
    }

    /**
     * Posts a delayed task to stop the watch dog player.
     */
    private void prepareToStopWatchDog() {
        mHandler.postDelayed(mDelayedReleaseRunnable, RELEASE_WATCH_DOG_PLAYER_DELAY_IN_MILLIS);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            synchronized (mLock) {
                onMediaServerCrash();
            }
        }
        return true;
    }

    /**
     * Called when media server crashes.
     */
    private void onMediaServerCrash() {
        assert Thread.holdsLock(mLock);
        long currentTime = SystemClock.elapsedRealtime();
        if (mLastCrashTime != UNKNOWN_LAST_SERVER_CRASH_TIME
                && (currentTime - mLastCrashTime < SERVER_CRASH_INTERVAL_THRESHOLD_IN_MILLIS)) {
            mServerCrashCount++;
        } else {
            recordNumMediaServerCrashes();
            mServerCrashCount = 1;
        }
        mLastCrashTime = currentTime;
    }

    /**
     * Resets the MediaThrottler to its initial state so that subsequent requests
     * will not be throttled.
     */
    @CalledByNative
    private void reset() {
        synchronized (mLock) {
            recordNumMediaServerCrashes();
            mServerCrashCount = 0;
            mLastCrashTime = UNKNOWN_LAST_SERVER_CRASH_TIME;
        }
    }

    /**
     * Records the number of consecutive media server crashes in UMA.
     */
    private void recordNumMediaServerCrashes() {
        assert Thread.holdsLock(mLock);
        if (mServerCrashCount > 0) {
            RecordHistogram.recordCountHistogram(
                    "Media.Android.NumMediaServerCrashes", mServerCrashCount);
        }
    }
}
