// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;

import org.chromium.chrome.browser.ChromeActivity;

/**
 * A menu header that is displayed when the Chrome Home promo is enabled. Shows the
 * {@link ChromeHomePromoDialog} on click.
 */
public class ChromeHomePromoMenuHeader extends LinearLayout implements OnClickListener {
    private ChromeActivity mActivity;

    public ChromeHomePromoMenuHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes the menu header.
     * @param activity The {@link ChromeActivity} that will display the app menu containing this
     *                 header.
     */
    public void initialize(ChromeActivity activity) {
        mActivity = activity;
        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        new ChromeHomePromoDialog(mActivity, ChromeHomePromoDialog.ShowReason.MENU).show();

        mActivity.getAppMenuHandler().hideAppMenu();
    }
}
