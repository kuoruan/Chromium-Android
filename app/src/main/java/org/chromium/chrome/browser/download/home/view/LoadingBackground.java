// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.graphics.drawable.Animatable2Compat;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.widget.ImageView;

import org.chromium.chrome.R;

/**
 * A helper class to display the loading image and animation for image and video items. This class
 * can also be extended and/or modified to include download progress.
 */
public class LoadingBackground {
    private AnimatedVectorDrawableCompat mLoadingDrawable;

    public LoadingBackground(Context context) {
        mLoadingDrawable =
                AnimatedVectorDrawableCompat.create(context, R.drawable.image_loading_progress);
        Animatable2Compat.AnimationCallback animationCallback =
                new Animatable2Compat.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        mLoadingDrawable.start();
                    }
                };

        mLoadingDrawable.registerAnimationCallback(animationCallback);
    }

    /** Show loading animation for the given {@link view}. */
    public void show(ImageView view) {
        view.setImageDrawable(mLoadingDrawable);
        mLoadingDrawable.start();
    }

    /** Hide the loading animation. */
    public void hide() {
        mLoadingDrawable.clearAnimationCallbacks();
    }
}
