// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageButton;

import org.chromium.chrome.browser.widget.ImageViewTinter.ImageViewTinterOwner;

/**
 * Implementation of ImageButton that allows tinting the Drawable for all states.
 * For usage, see {@link ImageViewTinter}.
 */
public class TintedImageButton extends ImageButton implements ImageViewTinterOwner {
    private ImageViewTinter mTinter;

    public TintedImageButton(Context context) {
        super(context);
        init(null, 0);
    }

    public TintedImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public TintedImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        mTinter = new ImageViewTinter(this, attrs, defStyle);
    }

    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        mTinter.drawableStateChanged();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        maybeUpdateTint();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        maybeUpdateTint();
    }

    @Override
    public void setTint(ColorStateList tintList) {
        mTinter.setTint(tintList);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    private void maybeUpdateTint() {
        if (mTinter == null) {
            // Got indirectly invoked from the superclass constructor, nothing to do yet.
            return;
        }
        mTinter.drawableStateChanged();
    }
}
