// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * A preference that supports some Chrome-specific customizations:
 *
 * 1. This preference supports being managed. If this preference is managed (as determined by its
 *    ManagedPreferenceDelegate), it updates its appearance and behavior appropriately: shows an
 *    enterprise icon, disables clicks, etc.
 *
 * 2. This preference can have a multiline title.
 * 3. This preference can set an icon color in XML through app:iconTint. Note that if a
 *    ColorStateList is set, only the default color will be used.
 */
public class ChromeBasePreference extends Preference {
    private ColorStateList mIconTint;
    private ManagedPreferenceDelegate mManagedPrefDelegate;

    /**
     * Constructor for use in Java.
     */
    public ChromeBasePreference(Context context) {
        this(context, null);
    }

    /**
     * Constructor for inflating from XML.
     */
    public ChromeBasePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ChromeBasePreference);
        mIconTint = a.getColorStateList(R.styleable.ChromeBasePreference_iconTint);
        a.recycle();
    }

    /**
     * Sets the ManagedPreferenceDelegate which will determine whether this preference is managed.
     */
    public void setManagedPreferenceDelegate(ManagedPreferenceDelegate delegate) {
        mManagedPrefDelegate = delegate;
        ManagedPreferencesUtils.initPreference(mManagedPrefDelegate, this);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        ((TextView) view.findViewById(android.R.id.title)).setSingleLine(false);
        Drawable icon = getIcon();
        if (icon != null && mIconTint != null) {
            icon.setColorFilter(mIconTint.getDefaultColor(), PorterDuff.Mode.SRC_IN);
        }
        ManagedPreferencesUtils.onBindViewToPreference(mManagedPrefDelegate, this, view);
    }

    @Override
    protected void onClick() {
        if (ManagedPreferencesUtils.onClickPreference(mManagedPrefDelegate, this)) return;
        super.onClick();
    }
}
