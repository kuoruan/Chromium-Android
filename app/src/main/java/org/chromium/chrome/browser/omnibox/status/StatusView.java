// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.status;

import static org.chromium.chrome.browser.toolbar.top.ToolbarPhone.URL_FOCUS_CHANGE_ANIMATION_DURATION_MS;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.widget.TintedDrawable;

/**
 * StatusView is a location bar's view displaying status (icons and/or text).
 */
public class StatusView extends LinearLayout {
    private ImageView mIconView;
    private TextView mVerboseStatusTextView;
    private View mSeparatorView;
    private View mStatusExtraSpace;

    private boolean mAnimationsEnabled;
    private boolean mAnimatingStatusIconShow;
    private boolean mAnimatingStatusIconHide;

    private @DrawableRes int mIconRes;
    private @ColorRes int mIconTintRes;
    private @StringRes int mAccessibilityToast;

    public StatusView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIconView = findViewById(R.id.location_bar_status_icon);
        mVerboseStatusTextView = findViewById(R.id.location_bar_verbose_status);
        mSeparatorView = findViewById(R.id.location_bar_verbose_status_separator);
        mStatusExtraSpace = findViewById(R.id.location_bar_verbose_status_extra_space);

        configureAccessibilityDescriptions();
    }

    /**
     * Start animating transition of status icon.
     */
    private void animateStatusIcon() {
        Drawable targetIcon = null;
        boolean wantIconHidden = false;

        if (mIconRes != 0 && mIconTintRes != 0) {
            targetIcon =
                    TintedDrawable.constructTintedDrawable(getContext(), mIconRes, mIconTintRes);
        } else if (mIconRes != 0) {
            targetIcon = ApiCompatibilityUtils.getDrawable(getContext().getResources(), mIconRes);
        } else {
            // Do not specify any icon here and do not replace existing icon, either.
            // TransitionDrawable uses different timing mechanism than Animations, and that may,
            // depending on animation scale factor, produce a visible glitch.
            targetIcon = null;
            wantIconHidden = true;
        }

        // Ensure proper handling of animations.
        // Possible variants:
        // 1. Is: shown,           want: hidden  => animate hiding,
        // 2. Is: shown,           want: shown   => crossfade w/TransitionDrawable,
        // 3. Is: animating(show), want: hidden  => cancel animation; animate hiding,
        // 4. Is: animating(show), want: shown   => crossfade (carry on showing),
        // 5. Is: animating(hide), want: hidden  => no op,
        // 6. Is: animating(hide), want: shown   => cancel animation; animate showing; crossfade,
        // 7. Is: hidden,          want: hidden  => no op,
        // 8. Is: hidden,          want: shown   => animate showing.
        //
        // This gives 3 actions:
        // - Animate showing, if hidden or previously hiding (6 + 8); cancel previous animation (6)
        // - Animate hiding, if shown or previously showing (1 + 3); cancel previous animation (3)
        // - crossfade w/TransitionDrawable, if visible (2, 4, 6), otherwise use image directly.
        // All other options (5, 7) are no-op.
        //
        // Note: this will be compacted once we start using LayoutTransition with StatusView.

        boolean isIconHidden = mIconView.getVisibility() == View.GONE;

        if (!wantIconHidden && (isIconHidden || mAnimatingStatusIconHide)) {
            // Action 1: animate showing, if icon was either hidden or hiding.
            if (mAnimatingStatusIconHide) mIconView.animate().cancel();
            mAnimatingStatusIconHide = false;

            mAnimatingStatusIconShow = true;
            mIconView.setVisibility(View.VISIBLE);
            mIconView.animate()
                    .alpha(1.0f)
                    .setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS)
                    .withEndAction(() -> { mAnimatingStatusIconShow = false; })
                    .start();
        } else if (wantIconHidden && (!isIconHidden || mAnimatingStatusIconShow)) {
            // Action 2: animate hiding, if icon was either shown or showing.
            if (mAnimatingStatusIconShow) mIconView.animate().cancel();
            mAnimatingStatusIconShow = false;

            mAnimatingStatusIconHide = true;
            // Do not animate phase-out when animations are disabled.
            // While this looks nice in some cases (navigating to insecure sites),
            // it has a side-effect of briefly showing padlock (phase-out) when navigating
            // back and forth between secure and insecure sites, which seems like a glitch.
            // See bug: crbug.com/919449
            mIconView.animate()
                    .setDuration(mAnimationsEnabled ? URL_FOCUS_CHANGE_ANIMATION_DURATION_MS : 0)
                    .alpha(0.0f)
                    .withEndAction(() -> {
                        mIconView.setVisibility(View.GONE);
                        mAnimatingStatusIconHide = false;
                    })
                    .start();
        }

        // Action 3: Specify icon content. Use TransitionDrawable whenever object is visible.
        if (targetIcon != null) {
            if (!isIconHidden) {
                Drawable existingDrawable = mIconView.getDrawable();
                if (existingDrawable instanceof TransitionDrawable
                        && ((TransitionDrawable) existingDrawable).getNumberOfLayers() == 2) {
                    existingDrawable = ((TransitionDrawable) existingDrawable).getDrawable(1);
                }
                TransitionDrawable newImage =
                        new TransitionDrawable(new Drawable[] {existingDrawable, targetIcon});

                mIconView.setImageDrawable(newImage);

                // Note: crossfade controls blending, not animation.
                newImage.setCrossFadeEnabled(true);
                newImage.startTransition(
                        mAnimationsEnabled ? URL_FOCUS_CHANGE_ANIMATION_DURATION_MS : 0);
            } else {
                mIconView.setImageDrawable(targetIcon);
            }
        }
    }

    /**
     * Specify object to receive click events.
     *
     * @param listener Instance of View.OnClickListener or null.
     */
    void setStatusClickListener(View.OnClickListener listener) {
        mIconView.setOnClickListener(listener);
        mVerboseStatusTextView.setOnClickListener(listener);
    }

    /**
     * Configure accessibility toasts.
     */
    void configureAccessibilityDescriptions() {
        View.OnLongClickListener listener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mAccessibilityToast == 0) return false;
                Context context = getContext();
                return AccessibilityUtil.showAccessibilityToast(
                        context, view, context.getResources().getString(mAccessibilityToast));
            }
        };
        mIconView.setOnLongClickListener(listener);
    }

    /**
     * Toggle use of animations.
     */
    void setAnimationsEnabled(boolean enabled) {
        mAnimationsEnabled = enabled;
    }

    /**
     * Specify navigation button image.
     */
    void setStatusIcon(@DrawableRes int imageRes) {
        mIconRes = imageRes;
        animateStatusIcon();
    }

    /**
     * Specify navigation icon tint color.
     */
    void setStatusIconTint(@ColorRes int colorRes) {
        // TODO(ender): combine icon and tint into a single class describing icon properties to
        // avoid multi-step crossfade animation configuration.
        // This is technically still invisible, since animations only begin after all operations in
        // UI thread (including status icon configuration) are complete, but can be avoided
        // entirely, making the code also more intuitive.
        mIconTintRes = colorRes;
        animateStatusIcon();
    }

    /**
     * Specify accessibility string presented to user upon long click.
     */
    void setStatusIconAccessibilityToast(@StringRes int description) {
        mAccessibilityToast = description;
    }

    /**
     * Specify content description for security icon.
     */
    void setStatusIconDescription(@StringRes int descriptionRes) {
        String description = null;
        if (descriptionRes != 0) {
            description = getResources().getString(descriptionRes);
        }
        mIconView.setContentDescription(description);
    }

    /**
     * Select color of Separator view.
     */
    void setSeparatorColor(@ColorRes int separatorColor) {
        mSeparatorView.setBackgroundColor(
                ApiCompatibilityUtils.getColor(getResources(), separatorColor));
    }

    /**
     * Select color of verbose status text.
     */
    void setVerboseStatusTextColor(@ColorRes int textColor) {
        mVerboseStatusTextView.setTextColor(
                ApiCompatibilityUtils.getColor(getResources(), textColor));
    }

    /**
     * Specify content of the verbose status text.
     */
    void setVerboseStatusTextContent(@StringRes int content) {
        mVerboseStatusTextView.setText(content);
    }

    /**
     * Specify visibility of the verbose status text.
     */
    void setVerboseStatusTextVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        mVerboseStatusTextView.setVisibility(visibility);
        mSeparatorView.setVisibility(visibility);
        mStatusExtraSpace.setVisibility(visibility);
    }

    /**
     * Specify width of the verbose status text.
     */
    void setVerboseStatusTextWidth(int width) {
        mVerboseStatusTextView.setMaxWidth(width);
    }

    // TODO(ender): The final last purpose of this method is to allow
    // ToolbarButtonInProductHelpController set up help bubbles. This dependency is about to
    // change. Do not depend on this method when creating new code.
    View getSecurityButton() {
        return mIconView;
    }
}
