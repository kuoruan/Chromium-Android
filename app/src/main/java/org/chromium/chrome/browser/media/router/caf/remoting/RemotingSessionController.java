// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.caf.remoting;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastSession;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.CastSessionUtil;
import org.chromium.chrome.browser.media.router.FlingingController;
import org.chromium.chrome.browser.media.router.caf.BaseSessionController;
import org.chromium.chrome.browser.media.router.caf.CafBaseMediaRouteProvider;
import org.chromium.chrome.browser.media.router.cast.remoting.RemotingMediaSource;

/** Wrapper for {@link CastSession} for remoting. */
public class RemotingSessionController extends BaseSessionController {
    private static final String TAG = "RmtSessionCtrl";

    private FlingingControllerAdapter mFlingingControllerAdapter;
    RemotingSessionController(CafBaseMediaRouteProvider provider) {
        super(provider);
        mFlingingControllerAdapter = new FlingingControllerAdapter(this);
    }

    @Override
    public void attachToCastSession(CastSession session) {
        super.attachToCastSession(session);

        try {
            getSession().setMessageReceivedCallbacks(
                    CastSessionUtil.MEDIA_NAMESPACE, this ::onMessageReceived);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register namespace listener for %s",
                    CastSessionUtil.MEDIA_NAMESPACE, e);
        }
    }

    @Override
    public void onSessionStarted() {
        getRemoteMediaClient().load(
                new MediaInfo.Builder(((RemotingMediaSource) getSource()).getMediaUrl()).build());
    }

    @Override
    protected void onStatusUpdated() {
        super.onStatusUpdated();
        mFlingingControllerAdapter.onStatusUpdated();
    }

    @Override
    public FlingingController getFlingingController() {
        return mFlingingControllerAdapter;
    }
}
