// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.androidoverlay;

import android.content.Context;

import org.chromium.media.mojom.AndroidOverlay;
import org.chromium.media.mojom.AndroidOverlayClient;
import org.chromium.media.mojom.AndroidOverlayConfig;
import org.chromium.media.mojom.AndroidOverlayProvider;
import org.chromium.mojo.bindings.InterfaceRequest;
import org.chromium.mojo.system.MojoException;
import org.chromium.services.service_manager.InterfaceFactory;

/**
 * Default impl of AndroidOverlayProvider.  Creates AndroidOverlayImpls.
 */
public class AndroidOverlayProviderImpl implements AndroidOverlayProvider {
    private static final String TAG = "AndroidOverlayProvider";

    /**
     * Create an overlay matching |config| for |client|, and bind it to |request|.  Remember that
     * potentially many providers are created.
     */
    public void createOverlay(InterfaceRequest<AndroidOverlay> request, AndroidOverlayClient client,
            AndroidOverlayConfig config) {
        client.onDestroyed();
    }

    @Override
    public void close() {}

    @Override
    public void onConnectionError(MojoException e) {}

    /**
     * Mojo factory.
     */
    public static class Factory implements InterfaceFactory<AndroidOverlayProvider> {
        public Factory(Context context) {}

        @Override
        public AndroidOverlayProvider createImpl() {
            return new AndroidOverlayProviderImpl();
        }
    }
}
