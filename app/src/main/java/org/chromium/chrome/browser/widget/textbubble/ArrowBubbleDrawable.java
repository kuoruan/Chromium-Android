// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.textbubble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.support.annotation.ColorInt;
import android.support.v4.graphics.drawable.DrawableCompat;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * A {@link Drawable} that is a bubble with an arrow pointing out of either the top or bottom.
 */
class ArrowBubbleDrawable extends Drawable implements Drawable.Callback {
    private final Rect mCachedBubblePadding = new Rect();

    private final int mRadiusPx;
    private final ShapeDrawable mBubbleDrawable;
    private final BitmapDrawable mArrowDrawable;

    private int mArrowXOffsetPx;
    private boolean mArrowOnTop;

    public ArrowBubbleDrawable(Context context) {
        mRadiusPx = context.getResources().getDimensionPixelSize(R.dimen.text_bubble_corner_radius);
        mBubbleDrawable = new ShapeDrawable(
                new RoundRectShape(new float[] {mRadiusPx, mRadiusPx, mRadiusPx, mRadiusPx,
                        mRadiusPx, mRadiusPx, mRadiusPx, mRadiusPx},
                        null, null));
        mArrowDrawable = (BitmapDrawable) ApiCompatibilityUtils.getDrawable(
                context.getResources(), R.drawable.bubble_point_white);

        mBubbleDrawable.setCallback(this);
        mArrowDrawable.setCallback(this);
    }

    /**
     * Updates the arrow offset and whether or not it is on top.
     * @param arrowXOffsetPx The horizontal offset of where the arrow should be in pixels.  This
     *                       offset is where the center of the arrow will position itself.
     * @param arrowOnTop     Whether or not the arrow should be on top of the bubble.
     */
    public void setPositionProperties(int arrowXOffsetPx, boolean arrowOnTop) {
        if (arrowXOffsetPx == mArrowXOffsetPx && arrowOnTop == mArrowOnTop) return;
        mArrowXOffsetPx = arrowXOffsetPx;
        mArrowOnTop = arrowOnTop;
        onBoundsChange(getBounds());
    }

    /**
     * @return The spacing needed on the left side of the {@link Drawable} for the arrow to fit.
     */
    public int getArrowLeftSpacing() {
        mBubbleDrawable.getPadding(mCachedBubblePadding);
        return mRadiusPx + mCachedBubblePadding.left + mArrowDrawable.getIntrinsicWidth() / 2;
    }

    /**
     * @return The spacing needed on the right side of the {@link Drawable} for the arrow to fit.
     */
    public int getArrowRightSpacing() {
        mBubbleDrawable.getPadding(mCachedBubblePadding);
        return mRadiusPx + mCachedBubblePadding.right + mArrowDrawable.getIntrinsicWidth() / 2;
    }

    /**
     * @return Whether or not the arrow is currently drawing on top of this {@link Drawable}.
     */
    public boolean isArrowOnTop() {
        return mArrowOnTop;
    }

    /**
     * @param color The color to make the bubble and arrow.
     */
    public void setBubbleColor(@ColorInt int color) {
        DrawableCompat.setTint(mBubbleDrawable, color);
        DrawableCompat.setTint(mArrowDrawable, color);
        invalidateSelf();
    }

    // Drawable.Callback implementation.
    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }

    // Drawable implementation.
    @Override
    public void draw(Canvas canvas) {
        mBubbleDrawable.draw(canvas);

        // If the arrow is on the bottom, flip the arrow before drawing.
        if (!mArrowOnTop) {
            canvas.save();
            canvas.scale(1, -1, mArrowDrawable.getBounds().exactCenterX(),
                    mArrowDrawable.getBounds().exactCenterY());
        }
        mArrowDrawable.draw(canvas);
        if (!mArrowOnTop) canvas.restore();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds == null) return;

        // Calculate the arrow bounds.
        int halfArrowWidth = mArrowDrawable.getIntrinsicWidth() / 2;
        int arrowHeight = mArrowDrawable.getIntrinsicHeight();
        mArrowDrawable.setBounds(bounds.left + mArrowXOffsetPx - halfArrowWidth,
                mArrowOnTop ? bounds.top : bounds.bottom - arrowHeight,
                bounds.left + mArrowXOffsetPx + halfArrowWidth,
                mArrowOnTop ? bounds.top + arrowHeight : bounds.bottom);

        // Calculate the bubble bounds.  Account for the arrow size requiring more space.
        mBubbleDrawable.getPadding(mCachedBubblePadding);
        mBubbleDrawable.setBounds(bounds.left,
                bounds.top + (mArrowOnTop ? (arrowHeight - mCachedBubblePadding.top) : 0),
                bounds.right,
                bounds.bottom - (mArrowOnTop ? 0 : (arrowHeight - mCachedBubblePadding.bottom)));
    }

    @Override
    public void setAlpha(int alpha) {
        mBubbleDrawable.setAlpha(alpha);
        mArrowDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        assert false : "Unsupported";
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean getPadding(Rect padding) {
        mBubbleDrawable.getPadding(padding);

        int arrowHeight = mArrowDrawable.getIntrinsicHeight();
        padding.set(padding.left, Math.max(padding.top, mArrowOnTop ? arrowHeight : 0),
                padding.right, Math.max(padding.bottom, mArrowOnTop ? 0 : arrowHeight));
        return true;
    }
}