// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.newtab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.content.res.AppCompatResources;
import android.util.AttributeSet;
import android.widget.Button;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider.IncognitoStateObserver;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Button for creating new tabs.
 */
public class NewTabButton extends Button implements Drawable.Callback, IncognitoStateObserver {
    private final ColorStateList mLightModeTint;
    private final ColorStateList mDarkModeTint;
    private VectorDrawableCompat mModernDrawable;
    private boolean mIsIncognito;
    private boolean mIsNativeReady;
    private IncognitoStateProvider mIncognitoStateProvider;

    /**
     * Constructor for inflating from XML.
     */
    public NewTabButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        mIsIncognito = false;
        mLightModeTint =
                AppCompatResources.getColorStateList(getContext(), R.color.light_mode_tint);
        mDarkModeTint = AppCompatResources.getColorStateList(getContext(), R.color.dark_mode_tint);
        mModernDrawable = VectorDrawableCompat.create(
                getContext().getResources(), R.drawable.new_tab_icon, getContext().getTheme());
        mModernDrawable.setBounds(
                0, 0, mModernDrawable.getIntrinsicWidth(), mModernDrawable.getIntrinsicHeight());
        mModernDrawable.setCallback(this);
        updateDrawableTint();
    }

    /**
     * Called to finish initializing the NewTabButton. Must be called after native initialization
     * is finished.
     */
    public void postNativeInitialization() {
        mIsNativeReady = true;
        updateDrawableTint();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = mModernDrawable.getIntrinsicWidth();
        desiredWidth += getPaddingLeft() + getPaddingRight();
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);
        int paddingStart = ViewCompat.getPaddingStart(this);
        int widthWithoutPadding = getWidth() - paddingStart;

        canvas.save();
        if (!isRtl) canvas.translate(paddingStart, 0);

        drawIcon(canvas, mModernDrawable, isRtl, widthWithoutPadding);

        canvas.restore();
    }

    private void drawIcon(Canvas canvas, Drawable drawable, boolean isRtl, int widthNoPadding) {
        canvas.save();
        canvas.translate(0, (getHeight() - drawable.getIntrinsicHeight()) / 2.f);
        if (isRtl) {
            canvas.translate(widthNoPadding - drawable.getIntrinsicWidth(), 0);
        }
        drawable.draw(canvas);
        canvas.restore();
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        if (dr == mModernDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }

    /**
     * Updates the visual state based on whether incognito or normal tabs are being created.
     * @param incognito Whether the button is now used for creating incognito tabs.
     */
    public void setIsIncognito(boolean incognito) {
        if (mIsIncognito == incognito) return;
        mIsIncognito = incognito;

        updateDrawableTint();
        invalidateDrawable(mModernDrawable);
    }

    /** Called when accessibility status is changed. */
    public void onAccessibilityStatusChanged() {
        if (mModernDrawable != null) updateDrawableTint();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        mModernDrawable.setState(getDrawableState());
    }

    /** Update the tint for the icon drawable for Chrome Modern. */
    private void updateDrawableTint() {
        final boolean shouldUseLightMode =
                DeviceFormFactor.isNonMultiDisplayContextOnTablet(getContext())
                || (mIsNativeReady
                           && (DeviceClassManager.enableAccessibilityLayout()
                                      || ChromeFeatureList.isEnabled(
                                                 ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID)
                                      || FeatureUtilities.isBottomToolbarEnabled())
                           && mIsIncognito);
        mModernDrawable.setTintList(shouldUseLightMode ? mLightModeTint : mDarkModeTint);
    }

    public void setIncognitoStateProvider(IncognitoStateProvider incognitoStateProvider) {
        mIncognitoStateProvider = incognitoStateProvider;
        mIncognitoStateProvider.addObserver(this);
    }

    @Override
    public void onIncognitoStateChanged(boolean isIncognito) {
        setIsIncognito(isIncognito);
    }

    /**
     * Clean up any state when the new tab button is destroyed.
     */
    public void destroy() {
        if (mIncognitoStateProvider != null) {
            mIncognitoStateProvider.removeObserver(this);
            mIncognitoStateProvider = null;
        }
    }
}
