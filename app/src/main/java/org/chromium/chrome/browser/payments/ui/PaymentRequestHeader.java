// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.widget.TintedDrawable;

/** This class represents a bar to display at the top of the payment request UI. */
public class PaymentRequestHeader extends FrameLayout {
    private Context mContext;

    /** Constructor for when the PaymentRequestHeader is inflated from XML. */
    public PaymentRequestHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    /**
     * Sets the bitmap of the icon on the left of the header.
     *
     * @param bitmap The bitmap to display.
     */
    public void setTitleBitmap(Bitmap bitmap) {
        ((ImageView) findViewById(R.id.icon_view)).setImageBitmap(bitmap);
    }

    /**
     * Sets the title and origin on the header.
     *
     * @param title  The title to display on the header.
     * @param origin The origin to display on the header.
     */
    public void setTitleAndOrigin(String title, String origin) {
        ((TextView) findViewById(R.id.page_title)).setText(title);

        TextView hostName = (TextView) findViewById(R.id.hostname);
        if (origin.startsWith(UrlConstants.HTTPS_URL_PREFIX)) {
            // Tint https scheme and add compound drawable for security display.
            hostName.setText(tintUrlSchemeForSecurityDisplay(origin));
            ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(hostName,
                    TintedDrawable.constructTintedDrawable(mContext.getResources(),
                            R.drawable.omnibox_https_valid, R.color.google_green_700),
                    null, null, null);

            // Remove left padding to align left compound drawable with the title. Note that the
            // left compound drawable has transparent boundary.
            hostName.setPaddingRelative(0, 0, 0, 0);
        } else {
            hostName.setText(origin);
        }
    }

    private CharSequence tintUrlSchemeForSecurityDisplay(String uri) {
        SpannableString spannableUri = new SpannableString(uri);
        int color =
                ApiCompatibilityUtils.getColor(mContext.getResources(), R.color.google_green_700);
        spannableUri.setSpan(new ForegroundColorSpan(color), 0,
                spannableUri.toString().indexOf(":"), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableUri;
    }
}