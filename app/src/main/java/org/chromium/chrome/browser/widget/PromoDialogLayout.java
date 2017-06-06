// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.PromoDialog.DialogParams;

/**
 * View that handles orientation changes for the promo dialogs. When the width is greater than the
 * height, the promo content switches from vertical to horizontal and moves the illustration from
 * the top of the text to the side of the text.
 */
public final class PromoDialogLayout extends BoundedLinearLayout {
    /** Content in the dialog that can be scrolled. */
    private LinearLayout mScrollableContent;

    /** Illustration that teases the thing being promoted. */
    private ImageView mIllustrationView;

    /** View containing the header of the promo. */
    private TextView mHeaderView;

    /** View containing text explaining the promo. */
    private TextView mSubheaderView;

    /** Paramters used to build the promo. */
    private DialogParams mParams;

    public PromoDialogLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mScrollableContent = (LinearLayout) findViewById(R.id.promo_content);
        mIllustrationView = (ImageView) findViewById(R.id.illustration);
        mHeaderView = (TextView) findViewById(R.id.header);
        mSubheaderView = (TextView) findViewById(R.id.subheader);

        super.onFinishInflate();
    }

    /** Initializes the dialog contents using the given params.  Should only be called once. */
    void initialize(DialogParams params) {
        assert mParams == null && params != null;
        mParams = params;

        if (mParams.drawableResource == 0) {
            ((ViewGroup) mIllustrationView.getParent()).removeView(mIllustrationView);
        } else {
            mIllustrationView.setImageResource(mParams.drawableResource);
        }

        // TODO(dfalcantara): Lock the title in place, if requested by the DialogParams.
        mHeaderView.setText(mParams.headerStringResource);

        if (mParams.subheaderStringResource == 0) {
            ((ViewGroup) mSubheaderView.getParent()).removeView(mSubheaderView);
        } else {
            mSubheaderView.setText(mParams.subheaderStringResource);
        }

        DualControlLayout buttonBar = (DualControlLayout) findViewById(R.id.button_bar);
        if (mParams.primaryButtonStringResource != 0) {
            String primaryString = getResources().getString(mParams.primaryButtonStringResource);
            buttonBar.addView(DualControlLayout.createButtonForLayout(
                    getContext(), true, primaryString, null));

            if (mParams.secondaryButtonStringResource != 0) {
                String secondaryString =
                        getResources().getString(mParams.secondaryButtonStringResource);
                buttonBar.addView(DualControlLayout.createButtonForLayout(
                        getContext(), false, secondaryString, null));
            }
        } else {
            assert mParams.secondaryButtonStringResource == 0;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (availableWidth > availableHeight * 1.5) {
            mScrollableContent.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            mScrollableContent.setOrientation(LinearLayout.VERTICAL);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
