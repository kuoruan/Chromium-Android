// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.details;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.autofill_assistant.R;
import org.chromium.chrome.browser.cached_image_fetcher.CachedImageFetcher;
import org.chromium.chrome.browser.compositor.animation.CompositorAnimator;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * This class is responsible for pushing updates to the Autofill Assistant details view. These
 * updates are pulled from the {@link AssistantDetailsModel} when a notification of an update is
 * received.
 */
class AssistantDetailsViewBinder
        implements PropertyModelChangeProcessor.ViewBinder<AssistantDetailsModel,
        AssistantDetailsViewBinder.ViewHolder, PropertyKey> {
    private static final int IMAGE_BORDER_RADIUS = 4;
    private static final int PULSING_DURATION_MS = 1_000;
    private static final String DETAILS_TIME_FORMAT = "H:mma";
    private static final String DETAILS_DATE_FORMAT = "EEE, MMM d";

    /**
     * A wrapper class that holds the different views of the header.
     */
    static class ViewHolder {
        final GradientDrawable mDefaultImage;
        final ImageView mImageView;
        final TextView mTitleView;
        final TextView mSubtextView;
        final TextView mTotalPriceView;

        public ViewHolder(Context context, View detailsView) {
            mDefaultImage = (GradientDrawable) context.getResources().getDrawable(
                    R.drawable.autofill_assistant_default_details);
            mImageView = detailsView.findViewById(R.id.details_image);
            mTitleView = detailsView.findViewById(R.id.details_title);
            mSubtextView = detailsView.findViewById(R.id.details_text);
            mTotalPriceView = detailsView.findViewById(R.id.total_price);
        }
    }

    private final Context mContext;

    private final int mImageWidth;
    private final int mImageHeight;
    private final int mPulseAnimationStartColor;
    private final int mPulseAnimationEndColor;

    private final Set<View> mViewsToAnimate = new HashSet<>();
    private ValueAnimator mPulseAnimation;

    AssistantDetailsViewBinder(Context context) {
        mContext = context;
        mImageWidth = context.getResources().getDimensionPixelSize(
                R.dimen.autofill_assistant_details_image_size);
        mImageHeight = context.getResources().getDimensionPixelSize(
                R.dimen.autofill_assistant_details_image_size);
        mPulseAnimationStartColor = context.getResources().getColor(R.color.modern_grey_100);
        mPulseAnimationEndColor = context.getResources().getColor(R.color.modern_grey_50);
    }

    @Override
    public void bind(AssistantDetailsModel model, ViewHolder view, PropertyKey propertyKey) {
        if (AssistantDetailsModel.DETAILS == propertyKey) {
            AssistantDetails details = model.get(AssistantDetailsModel.DETAILS);
            if (details == null) {
                // Handled by the AssistantDetailsCoordinator.
                return;
            }

            setDetails(details, view);
        } else {
            assert false : "Unhandled property detected in AssistantDetailsViewBinder!";
        }
    }

    private void setDetails(AssistantDetails details, ViewHolder viewHolder) {
        String detailsText = makeDetailsText(details);
        viewHolder.mTitleView.setText(details.getTitle());
        viewHolder.mSubtextView.setText(detailsText);
        viewHolder.mTotalPriceView.setText(details.getPrice());

        if (viewHolder.mImageView.getDrawable() == null) {
            // Set default image if no image was set before.
            viewHolder.mImageView.setImageDrawable(viewHolder.mDefaultImage);
        }

        setTextStyles(details, viewHolder);

        // Download image and then set it in the model.
        if (!details.getUrl().isEmpty()) {
            CachedImageFetcher.getInstance().fetchImage(details.getUrl(), image -> {
                if (image != null) {
                    viewHolder.mImageView.setImageDrawable(getRoundedImage(image));
                }
            });
        }
    }

    private void setTextStyles(AssistantDetails details, ViewHolder viewHolder) {
        setTitleStyle(details, viewHolder);
        setSubtextStyle(details, viewHolder);
    }

    private void setTitleStyle(AssistantDetails details, ViewHolder viewHolder) {
        boolean animateBackground = false;
        TextView titleView = viewHolder.mTitleView;

        if (details.getUserApprovalRequired() && !details.getHighlightTitle()) {
            // De-emphasize title if user needs to accept details but the title should not be
            // highlighted.
            titleView.setTextColor(ApiCompatibilityUtils.getColor(
                    mContext.getResources(), R.color.modern_grey_300));
        } else {
            // Normal style: bold black text.
            ApiCompatibilityUtils.setTextAppearance(
                    titleView, R.style.TextAppearance_BlackCaptionDefault);
            titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);

            if (titleView.length() == 0 && details.getShowPlaceholdersForEmptyFields()) {
                animateBackground = true;
            }
        }

        if (animateBackground) {
            addViewToAnimation(titleView, viewHolder);
        } else {
            removeViewFromAnimation(titleView);
        }
    }

    private void setSubtextStyle(AssistantDetails details, ViewHolder viewHolder) {
        boolean animateBackground = false;
        TextView subtextView = viewHolder.mSubtextView;

        if (details.getUserApprovalRequired()) {
            if (details.getHighlightDate()) {
                // Emphasized style.
                subtextView.setTypeface(subtextView.getTypeface(), Typeface.BOLD_ITALIC);
            } else {
                // De-emphasized style.
                subtextView.setTextColor(ApiCompatibilityUtils.getColor(
                        mContext.getResources(), R.color.modern_grey_300));
            }
        } else {
            // Normal style.
            ApiCompatibilityUtils.setTextAppearance(
                    subtextView, R.style.TextAppearance_BlackCaption);

            if (subtextView.length() == 0 && details.getShowPlaceholdersForEmptyFields()) {
                animateBackground = true;
            }
        }

        if (animateBackground) {
            addViewToAnimation(subtextView, viewHolder);
        } else {
            removeViewFromAnimation(subtextView);
        }
    }

    private String makeDetailsText(AssistantDetails details) {
        List<String> parts = new ArrayList<>();
        Date date = details.getDate();
        if (date != null) {
            parts.add(formatDetailsTime(date));
            parts.add(formatDetailsDate(date));
        }

        String description = details.getDescription();
        if (description != null && !description.isEmpty()) {
            parts.add(description);
        }

        // TODO(crbug.com/806868): Use a view instead of this dot text.
        return TextUtils.join(" • ", parts);
    }

    private Locale getLocale() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? mContext.getResources().getConfiguration().getLocales().get(0)
                : mContext.getResources().getConfiguration().locale;
    }

    private String formatDetailsTime(Date date) {
        DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        String timeFormatPattern = (df instanceof SimpleDateFormat)
                ? ((SimpleDateFormat) df).toPattern()
                : DETAILS_TIME_FORMAT;
        return new SimpleDateFormat(timeFormatPattern, getLocale()).format(date);
    }

    private String formatDetailsDate(Date date) {
        return new SimpleDateFormat(DETAILS_DATE_FORMAT, getLocale()).format(date);
    }

    private Drawable getRoundedImage(Bitmap bitmap) {
        RoundedBitmapDrawable roundedBitmap =
                RoundedBitmapDrawableFactory.create(mContext.getResources(),
                        ThumbnailUtils.extractThumbnail(bitmap, mImageWidth, mImageHeight));
        roundedBitmap.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                IMAGE_BORDER_RADIUS, mContext.getResources().getDisplayMetrics()));
        return roundedBitmap;
    }

    private void addViewToAnimation(View view, ViewHolder viewHolder) {
        mViewsToAnimate.add(view);
        if (mPulseAnimation == null) {
            mPulseAnimation =
                    ValueAnimator.ofInt(mPulseAnimationStartColor, mPulseAnimationEndColor);
            mPulseAnimation.setDuration(PULSING_DURATION_MS);
            mPulseAnimation.setEvaluator(new ArgbEvaluator());
            mPulseAnimation.setRepeatCount(ValueAnimator.INFINITE);
            mPulseAnimation.setRepeatMode(ValueAnimator.REVERSE);
            mPulseAnimation.setInterpolator(CompositorAnimator.ACCELERATE_INTERPOLATOR);
            mPulseAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    viewHolder.mTitleView.setBackgroundColor(Color.WHITE);
                    viewHolder.mSubtextView.setBackgroundColor(Color.WHITE);
                    viewHolder.mDefaultImage.setColor(mPulseAnimationStartColor);
                }
            });
            mPulseAnimation.addUpdateListener(animation -> {
                int animatedValue = (int) animation.getAnimatedValue();
                for (View viewToAnimate : mViewsToAnimate) {
                    viewToAnimate.setBackgroundColor(animatedValue);
                }
                viewHolder.mDefaultImage.setColor(animatedValue);
            });
            mPulseAnimation.start();
        }
    }

    private void removeViewFromAnimation(View view) {
        mViewsToAnimate.remove(view);
        if (mViewsToAnimate.isEmpty() && mPulseAnimation != null) {
            mPulseAnimation.cancel();
            mPulseAnimation = null;
        }
    }
}
