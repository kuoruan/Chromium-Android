// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;

/** ChromeSwitchPreference with fixed icon size for Android payment apps. */
public class AndroidPaymentAppPreference extends ChromeSwitchPreference {
    public AndroidPaymentAppPreference(Context context) {
        super(context, null);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        int iconSize =
                getContext().getResources().getDimensionPixelSize(R.dimen.payments_favicon_size);
        View iconView = view.findViewById(android.R.id.icon);
        ViewGroup.LayoutParams layoutParams = iconView.getLayoutParams();
        layoutParams.width = iconSize;
        layoutParams.height = iconSize;
        iconView.setLayoutParams(layoutParams);

        return view;
    }
}