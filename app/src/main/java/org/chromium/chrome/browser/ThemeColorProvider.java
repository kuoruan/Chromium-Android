// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v7.content.res.AppCompatResources;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.ColorUtils;

/**
 * An abstract class that provides the current theme color.
 */
public abstract class ThemeColorProvider {
    /**
     * An interface to be notified about changes to the theme color.
     */
    public interface ThemeColorObserver {
        /**
         * @param color The new color the observer should use.
         * @param shouldAnimate Whether the change of color should be animated.
         */
        void onThemeColorChanged(int color, boolean shouldAnimate);
    }

    /**
     * An interface to be notified about changes to the tint.
     */
    public interface TintObserver {
        /**
         * @param tint The new tint the observer should use.
         * @param useLight Whether the observer should use light mode.
         */
        void onTintChanged(ColorStateList tint, boolean useLight);
    }

    /** Light mode tint (used when color is dark). */
    private final ColorStateList mLightModeTint;

    /** Dark mode tint (used when color is light). */
    private final ColorStateList mDarkModeTint;

    /** Current primary color. */
    private int mPrimaryColor;

    /** Whether should use light tint (corresponds to dark color). */
    private boolean mUseLight;

    /** List of {@link ThemeColorObserver}s. These are used to broadcast events to listeners. */
    private final ObserverList<ThemeColorObserver> mThemeColorObservers;

    /** List of {@link TintObserver}s. These are used to broadcast events to listeners. */
    private final ObserverList<TintObserver> mTintObservers;

    public ThemeColorProvider() {
        mThemeColorObservers = new ObserverList<ThemeColorObserver>();
        mTintObservers = new ObserverList<TintObserver>();
        final Context context = ContextUtils.getApplicationContext();
        mLightModeTint = AppCompatResources.getColorStateList(context, R.color.light_mode_tint);
        mDarkModeTint = AppCompatResources.getColorStateList(context, R.color.dark_mode_tint);
    }

    public void addThemeColorObserver(ThemeColorObserver observer) {
        mThemeColorObservers.addObserver(observer);
    }

    public void removeThemeColorObserver(ThemeColorObserver observer) {
        mThemeColorObservers.removeObserver(observer);
    }

    public void addTintObserver(TintObserver observer) {
        mTintObservers.addObserver(observer);
    }

    public void removeTintObserver(TintObserver observer) {
        mTintObservers.removeObserver(observer);
    }

    public void destroy() {
        mThemeColorObservers.clear();
        mTintObservers.clear();
    }

    protected void updatePrimaryColor(int color, boolean shouldAnimate) {
        if (mPrimaryColor == color) return;
        mPrimaryColor = color;
        for (ThemeColorObserver observer : mThemeColorObservers) {
            observer.onThemeColorChanged(color, shouldAnimate);
        }

        final boolean useLight = ColorUtils.shouldUseLightForegroundOnBackground(color);
        if (useLight == mUseLight) return;
        mUseLight = useLight;
        final ColorStateList tint = useLight ? mLightModeTint : mDarkModeTint;
        for (TintObserver observer : mTintObservers) {
            observer.onTintChanged(tint, useLight);
        }
    }
}
