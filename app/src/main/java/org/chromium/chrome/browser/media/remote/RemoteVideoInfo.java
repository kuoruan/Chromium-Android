// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.support.annotation.IntDef;
import android.text.TextUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Exposes information about the current video to the external clients.
 */
public class RemoteVideoInfo {
    /**
     * This lists all the states that the remote video can be in.
     */
    @IntDef({PlayerState.STOPPED, PlayerState.LOADING, PlayerState.PLAYING, PlayerState.PAUSED,
            PlayerState.ERROR, PlayerState.INVALIDATED, PlayerState.FINISHED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PlayerState {
        /** The remote player is currently stopped. */
        int STOPPED = 0;
        /** The remote player is buffering this video. */
        int LOADING = 1;
        /** The remote player is playing this video. */
        int PLAYING = 2;
        /** The remote player is paused. */
        int PAUSED = 3;
        /** The remote player is in an error state. */
        int ERROR = 4;
        /** The remote player has been replaced by another player (so the current session has
         * finished) */
        int INVALIDATED = 5;
        /** The remote video has completed playing. */
        int FINISHED = 6;
    }

    /**
     * The title of the video
     */
    public String title;
    /**
     * The duration of the video
     */
    public long durationMillis;
    /**
     * The current state of the video
     */
    public @PlayerState int state;
    /**
     * The last known position in the video
     */
    public long currentTimeMillis;
    /**
     * The current error message, if any
     */
    // TODO(aberent) At present nothing sets this to anything other than Null.
    public String errorMessage;

    /**
     * Create a new RemoteVideoInfo
     * @param title
     * @param durationMillis
     * @param state
     * @param currentTimeMillis
     * @param errorMessage
     */
    public RemoteVideoInfo(String title, long durationMillis, @PlayerState int state,
            long currentTimeMillis, String errorMessage) {
        this.title = title;
        this.durationMillis = durationMillis;
        this.state = state;
        this.currentTimeMillis = currentTimeMillis;
        this.errorMessage = errorMessage;
    }

    /**
     * Copy a remote video info
     * @param other the source.
     */
    public RemoteVideoInfo(RemoteVideoInfo other) {
        this(other.title, other.durationMillis, other.state, other.currentTimeMillis,
                other.errorMessage);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof RemoteVideoInfo)) return false;

        RemoteVideoInfo other = (RemoteVideoInfo) obj;
        return durationMillis == other.durationMillis
               && currentTimeMillis == other.currentTimeMillis
               && state == other.state
               && TextUtils.equals(title, other.title)
               && TextUtils.equals(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
        int result = (int) durationMillis;
        result = 31 * result + (int) (durationMillis >> 32);
        result = 31 * result + (int) currentTimeMillis;
        result = 31 * result + (int) (currentTimeMillis >> 32);
        result = 31 * result + (title == null ? 0 : title.hashCode());
        result = 31 * result + state;
        result = 31 * result + (errorMessage == null ? 0 : errorMessage.hashCode());
        return result;
    }
}
