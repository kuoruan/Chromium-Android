// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.ViewGroup;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.ui.base.ViewAndroidDelegate;

/**
 * Implementation of the abstract class {@link ViewAndroidDelegate} for Chrome.
 */
class TabViewAndroidDelegate extends ViewAndroidDelegate {
    /** Used for logging. */
    private static final String TAG = "TabVAD";

    private final Tab mTab;
    private final ViewGroup mContainerView;

    TabViewAndroidDelegate(Tab tab, ViewGroup containerView) {
        mTab = tab;
        mContainerView = containerView;
    }

    @Override
    public void onBackgroundColorChanged(int color) {
        mTab.onBackgroundColorChanged(color);
    }

    @Override
    public void onTopControlsChanged(float topControlsOffsetY, float topContentOffsetY) {
        mTab.onOffsetsChanged(topControlsOffsetY, Float.NaN, topContentOffsetY);
    }

    @Override
    public void onBottomControlsChanged(float bottomControlsOffsetY, float bottomContentOffsetY) {
        mTab.onOffsetsChanged(Float.NaN, bottomControlsOffsetY, Float.NaN);
    }

    @Override
    public void startContentIntent(Intent intent, String intentUrl, boolean isMainFrame) {
        try {
            RecordUserAction.record("Android.ContentDetectorActivated");
            mContainerView.getContext().startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.w(TAG, "No application can handle %s", intentUrl);
        }
    }

    @Override
    public ViewGroup getContainerView() {
        return mContainerView;
    }
}
