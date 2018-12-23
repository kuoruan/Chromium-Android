// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.caf.remoting;

import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.FlingingController;
import org.chromium.chrome.browser.media.router.MediaController;
import org.chromium.chrome.browser.media.router.MediaStatusBridge;
import org.chromium.chrome.browser.media.router.MediaStatusObserver;

/** Adapter class for bridging {@link RemoteMediaClient} and {@link FlingController}. */
public class FlingingControllerAdapter implements FlingingController, MediaController {
    private static final String TAG = "FlingCtrlAdptr";

    private final RemotingSessionController mSessionController;
    private MediaStatusObserver mMediaStatusObserver;

    FlingingControllerAdapter(RemotingSessionController sessionController) {
        mSessionController = sessionController;
    }

    ////////////////////////////////////////////
    // FlingingController implementation begin
    ////////////////////////////////////////////

    @Override
    public MediaController getMediaController() {
        return this;
    }

    @Override
    public void setMediaStatusObserver(MediaStatusObserver observer) {
        assert mMediaStatusObserver == null;
        mMediaStatusObserver = observer;
    }

    @Override
    public void clearMediaStatusObserver() {
        assert mMediaStatusObserver != null;
        mMediaStatusObserver = null;
    }

    @Override
    public long getApproximateCurrentTime() {
        return mSessionController.getRemoteMediaClient().getApproximateStreamPosition();
    }

    ////////////////////////////////////////////
    // FlingingController implementation end
    ////////////////////////////////////////////

    ////////////////////////////////////////////
    // MediaController implementation begin
    ////////////////////////////////////////////

    @Override
    public void play() {
        if (!mSessionController.isConnected()) return;
        mSessionController.getRemoteMediaClient().play().setResultCallback(
                this ::onMediaCommandResult);
    }

    @Override
    public void pause() {
        if (!mSessionController.isConnected()) return;
        mSessionController.getRemoteMediaClient().pause().setResultCallback(
                this ::onMediaCommandResult);
    }

    @Override
    public void setMute(boolean mute) {
        if (!mSessionController.isConnected()) return;
        mSessionController.getRemoteMediaClient().setStreamMute(mute).setResultCallback(
                this ::onMediaCommandResult);
    }

    @Override
    public void setVolume(double volume) {
        if (!mSessionController.isConnected()) return;
        mSessionController.getRemoteMediaClient().setStreamVolume(volume).setResultCallback(
                this ::onMediaCommandResult);
    }

    @Override
    public void seek(long position) {
        if (!mSessionController.isConnected()) return;
        mSessionController.getRemoteMediaClient().seek(position).setResultCallback(
                this ::onMediaCommandResult);
    }

    ////////////////////////////////////////////
    // MediaController implementation end
    ////////////////////////////////////////////

    public void onStatusUpdated() {
        if (mMediaStatusObserver == null) return;

        MediaStatus mediaStatus = mSessionController.getRemoteMediaClient().getMediaStatus();
        if (mediaStatus != null) {
            mMediaStatusObserver.onMediaStatusUpdate(new MediaStatusBridge(mediaStatus));
        }
    }

    private void onMediaCommandResult(RemoteMediaClient.MediaChannelResult result) {
        // When multiple API calls are made in quick succession, "Results have already been set"
        // IllegalStateExceptions might be thrown from GMS code. We prefer to catch the exception
        // and noop it, than to crash. This might lead to some API calls never getting their
        // onResult() called, so we should not rely on onResult() being called for every API call.
        // See https://crbug.com/853923.
        if (!result.getStatus().isSuccess()) {
            Log.e(TAG, "Error when sending command. Status code: %d",
                    result.getStatus().getStatusCode());
        }
    }
}
