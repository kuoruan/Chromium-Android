// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.support.annotation.DrawableRes;
import android.support.annotation.StyleRes;
import android.support.v4.view.MarginLayoutParamsCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.ui.R;

/** The view responsible for displaying a material chip. */
public class ChipView extends LinearLayout {
    /** An id to use for {@link #setIcon(int)} when there is no icon on the chip. */
    public static final int INVALID_ICON_ID = -1;
    private final int mTextStartPaddingWithIconPx;
    private final int mTextStartPaddingWithNoIconPx;

    private final RippleBackgroundHelper mRippleBackgroundHelper;
    private final TextView mText;
    private final ChromeImageView mIcon;

    /**
     * Constructor for inflating from XML.
     */
    public ChipView(Context context, @StyleRes int chipStyle) {
        this(context, null, chipStyle);
    }

    /**
     * Constructor for inflating from XML.
     */
    public ChipView(Context context, AttributeSet attrs) {
        this(context, attrs, R.style.SuggestionChipThemeOverlay);
    }

    private ChipView(Context context, AttributeSet attrs, @StyleRes int themeOverlay) {
        super(new ContextThemeWrapper(context, themeOverlay), attrs, R.attr.chipStyle);

        mTextStartPaddingWithIconPx =
                getResources().getDimensionPixelSize(R.dimen.chip_icon_padding);
        mTextStartPaddingWithNoIconPx =
                getResources().getDimensionPixelSize(R.dimen.chip_no_icon_padding);

        TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.ChipView, R.attr.chipStyle, 0);
        int chipColorId =
                a.getResourceId(R.styleable.ChipView_chipColor, R.color.chip_background_color);
        int rippleColorId =
                a.getResourceId(R.styleable.ChipView_rippleColor, R.color.chip_ripple_color);
        int cornerRadius = a.getDimensionPixelSize(R.styleable.ChipView_cornerRadius,
                getContext().getResources().getDimensionPixelSize(R.dimen.chip_corner_radius));
        int iconWidth = a.getDimensionPixelSize(R.styleable.ChipView_iconWidth,
                getResources().getDimensionPixelSize(R.dimen.chip_icon_size));
        int iconHeight = a.getDimensionPixelSize(R.styleable.ChipView_iconHeight,
                getResources().getDimensionPixelSize(R.dimen.chip_icon_size));
        a.recycle();

        mIcon = new ChromeImageView(getContext());
        LayoutParams lp = new LayoutParams(iconWidth, iconHeight);
        MarginLayoutParamsCompat.setMarginStart(lp, mTextStartPaddingWithIconPx);
        mIcon.setLayoutParams(lp);
        addView(mIcon);

        mText = new TextView(new ContextThemeWrapper(getContext(), R.style.ChipTextView));
        ApiCompatibilityUtils.setTextAppearance(mText, R.style.TextAppearance_ChipText);
        addView(mText);

        // Reset icon and background:
        mRippleBackgroundHelper = new RippleBackgroundHelper(this, chipColorId, rippleColorId,
                cornerRadius, R.color.chip_stroke_color, R.dimen.chip_border_width);
        setIcon(INVALID_ICON_ID);

        ColorStateList textColors = mText.getTextColors();
        if (textColors != null) ApiCompatibilityUtils.setImageTintList(mIcon, textColors);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mRippleBackgroundHelper != null) {
            mRippleBackgroundHelper.onDrawableStateChanged();
        }
    }

    /**
     * Sets the icon at the start of the chip view.
     * @param icon The resource id pointing to the icon.
     */
    public void setIcon(@DrawableRes int icon) {
        final int textStartPadding;
        if (icon == INVALID_ICON_ID) {
            mIcon.setVisibility(ViewGroup.GONE);
            textStartPadding = mTextStartPaddingWithNoIconPx;
        } else {
            textStartPadding = mTextStartPaddingWithIconPx;
            mIcon.setVisibility(ViewGroup.VISIBLE);
            mIcon.setImageResource(icon);
        }

        ViewCompat.setPaddingRelative(mText, textStartPadding, mText.getPaddingTop(),
                ViewCompat.getPaddingEnd(mText), mText.getPaddingBottom());
    }

    /**
     * Returns the {@link TextView} that contains the label of the chip.
     * @return A {@link TextView}.
     */
    public TextView getInnerTextView() {
        return mText;
    }
}
