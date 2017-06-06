// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.androidoverlay;

import org.chromium.gfx.mojom.Rect;
import org.chromium.media.mojom.AndroidOverlay;
import org.chromium.media.mojom.AndroidOverlayClient;
import org.chromium.media.mojom.AndroidOverlayConfig;
import org.chromium.mojo.system.MojoException;

/**
 * Default AndroidOverlay impl.  Will use a separate (shared) overlay-ui thread to own a Dialog
 * instance, probably via a separate object that operates only on that thread.  We will post
 * messages to / from that thread from the main thread.
 */
public class AndroidOverlayImpl implements AndroidOverlay {
    private static final String TAG = "AndroidOverlay";

    public AndroidOverlayImpl(AndroidOverlayClient client, AndroidOverlayConfig config) {}

    @Override
    public void close() {
        // Client has closed the connection.
        // TODO(liberato): Allow any sync surfaceDestroyed to proceed.
        // TODO(liberato): Notify our provider that we've been destroyed.
    }

    @Override
    public void onConnectionError(MojoException e) {}

    @Override
    public void scheduleLayout(Rect rect) {}
}
