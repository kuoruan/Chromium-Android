// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import android.net.Uri;
import android.view.View;

/**
 * The implementation of {@link IActivityHost}.
 */
public class ActivityHostImpl extends BaseActivityHost {
    private final DynamicModuleCoordinator mModuleCoordinator;

    public ActivityHostImpl(DynamicModuleCoordinator moduleCoordinator) {
        mModuleCoordinator = moduleCoordinator;
    }

    @Override
    public IObjectWrapper getActivityContext() {
        return ObjectWrapper.wrap(mModuleCoordinator.getActivityContext());
    }

    @Override
    public void setBottomBarView(IObjectWrapper bottomBarView) {
        mModuleCoordinator.setBottomBarContentView(ObjectWrapper.unwrap(bottomBarView, View.class));
    }

    @Override
    public void setOverlayView(IObjectWrapper overlayView) {
        mModuleCoordinator.setOverlayView(ObjectWrapper.unwrap(overlayView, View.class));
    }

    @Override
    public void setBottomBarHeight(int height) {
        mModuleCoordinator.setBottomBarHeight(height);
    }

    @Override
    public void loadUri(Uri uri) {
        mModuleCoordinator.loadUri(uri);
    }

    @Override
    public void setTopBarView(IObjectWrapper topBarView) {
        mModuleCoordinator.setTopBarContentView(ObjectWrapper.unwrap(topBarView, View.class));
    }

    @Override
    public boolean requestPostMessageChannel(Uri postMessageOrigin) {
        return mModuleCoordinator.requestPostMessageChannel(postMessageOrigin);
    }

    @Override
    public int postMessage(String message) {
        return mModuleCoordinator.postMessage(message);
    }

    @Override
    public void setTopBarHeight(int heightInPx) {
        mModuleCoordinator.setTopBarHeight(heightInPx);
    }

    @Override
    public void setTopBarMinHeight(int heightInPx) {
        // Do nothing for now.
    }
}
