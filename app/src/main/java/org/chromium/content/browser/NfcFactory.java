// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.Activity;

import org.chromium.content_public.browser.WebContents;
import org.chromium.device.nfc.NfcImpl;
import org.chromium.device.nfc.mojom.Nfc;
import org.chromium.services.service_manager.InterfaceFactory;
import org.chromium.ui.base.WindowAndroid;

class NfcFactory implements InterfaceFactory<Nfc> {
    private final WebContents mContents;

    NfcFactory(WebContents contents) {
        mContents = contents;
    }

    private static class ContextAwareNfcImpl
            extends NfcImpl implements WindowAndroidChangedObserver {
        private final ContentViewCore mContentViewCore;

        ContextAwareNfcImpl(ContentViewCore contentViewCore) {
            super(contentViewCore.getContext().getApplicationContext());
            mContentViewCore = contentViewCore;
            mContentViewCore.addWindowAndroidChangedObserver(this);
            if (mContentViewCore.getWindowAndroid() != null) {
                setActivity(mContentViewCore.getWindowAndroid().getActivity().get());
            }
        }

        @Override
        public void close() {
            super.close();
            if (mContentViewCore != null) {
                mContentViewCore.removeWindowAndroidChangedObserver(this);
            }
        }

        @Override
        public void onWindowAndroidChanged(WindowAndroid newWindowAndroid) {
            Activity activity = null;
            if (newWindowAndroid != null) {
                activity = newWindowAndroid.getActivity().get();
            }
            setActivity(activity);
        }
    }

    @Override
    public Nfc createImpl() {
        ContentViewCore contentViewCore = ContentViewCore.fromWebContents(mContents);
        if (contentViewCore == null) {
            return null;
        }
        return new ContextAwareNfcImpl(contentViewCore);
    }
}
