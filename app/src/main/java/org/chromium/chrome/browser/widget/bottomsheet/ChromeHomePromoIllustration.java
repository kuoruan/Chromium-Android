// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.Property;
import android.view.animation.Interpolator;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.animation.AnimatorProperties;
import org.chromium.chrome.browser.widget.animation.CancelAwareAnimatorListener;

/**
 * Promo illustration for Chrome Home.
 */
public class ChromeHomePromoIllustration extends Drawable implements Drawable.Callback {
    private static final long DURATION_BETWEEN_REPEATS_MS = 1000;

    private static final long DURATION_SHEET_COLLAPSED_MS = 500;
    private static final long DURATION_SHEET_HALF_EXPANSION_MS = 400;
    private static final long DURATION_SHEET_HALF_PAUSE_MS = 300;
    private static final long DURATION_SHEET_FULL_EXPANSION_MS = 500;
    private static final long DURATION_HIGHLIGHT_EXPANSION_MS = 400;
    private static final long DURATION_HIGHLIGHT_START_DELAY_MS = 200;

    private static final float PHONE_HEIGHT_PERCENT = .9f;
    private static final float PHONE_ASPECT_RATIO_PERCENT = .524f;
    private static final float HOME_SHEET_HEIGHT_PERCENT = .572f;
    private static final float HIGHLIGHT_DIAMETER_PERCENT = .888f;

    private static final int[] BOOKMARK_ROW_COLORS = {Color.parseColor("#01579B"),
            Color.parseColor("#039BE5"), Color.parseColor("#4FC3F7"), Color.parseColor("#B3E5FC")};

    private final PhoneDrawable mPhoneDrawable;
    private final VectorDrawableCompat mBackgroundDrawable;
    private final SheetDrawable mSheetDrawable;
    private final HomeSectionDrawable mHomeSectionDrawable;
    private final BookmarkSectionDrawable mBookmarkSectionDrawable;
    private final HighlightDrawable mHighlightDrawable;

    private Animator mAnimator;
    private float mSheetAnimationPercent;
    private float mHighlightAnimationPercent;

    private int mIntrinsicWidth;
    private int mIntrinsicHeight;

    public ChromeHomePromoIllustration(@NonNull Context context) {
        Resources resources = context.getResources();

        mIntrinsicWidth =
                resources.getDimensionPixelSize(R.dimen.chrome_home_promo_illustration_width);
        mIntrinsicHeight =
                resources.getDimensionPixelSize(R.dimen.chrome_home_promo_illustration_height);

        int phoneOmniboxNtpTileColor =
                ApiCompatibilityUtils.getColor(resources, R.color.modern_grey_300);

        Drawable bookmarkStar =
                ApiCompatibilityUtils.getDrawable(resources, R.drawable.btn_star_filled);

        mPhoneDrawable = new PhoneDrawable(phoneOmniboxNtpTileColor);
        mBackgroundDrawable = VectorDrawableCompat.create(
                resources, R.drawable.chrome_home_promo_phone_background, context.getTheme());
        mSheetDrawable = new SheetDrawable();

        mHomeSectionDrawable = new HomeSectionDrawable(resources,
                ApiCompatibilityUtils.getDrawable(resources, R.drawable.ic_file_download_white_24dp)
                        .mutate(),
                bookmarkStar.getConstantState().newDrawable(resources).mutate(),
                ApiCompatibilityUtils.getDrawable(resources, R.drawable.ic_watch_later_24dp)
                        .mutate(),
                phoneOmniboxNtpTileColor);
        mHomeSectionDrawable.setCallback(this);

        mBookmarkSectionDrawable = new BookmarkSectionDrawable(
                bookmarkStar.getConstantState().newDrawable(resources).mutate());
        mBookmarkSectionDrawable.setCallback(this);

        mHighlightDrawable = new HighlightDrawable(
                resources, bookmarkStar.getConstantState().newDrawable(resources).mutate());

        buildAnimation();
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        int viewHeight = bounds.height();

        int phoneHeight = (int) (viewHeight * PHONE_HEIGHT_PERCENT);
        int phoneWidth = (int) (phoneHeight * PHONE_ASPECT_RATIO_PERCENT);
        mPhoneDrawable.setBounds(0, 0, phoneWidth, phoneHeight);

        int phoneInnerHeight = mPhoneDrawable.getInnerHeight();
        int phoneInnerWidth = mPhoneDrawable.getInnerWidth();
        mSheetDrawable.setBounds(0, 0,
                (int) (phoneInnerWidth + (phoneWidth - phoneInnerWidth) / 2f), phoneInnerHeight);
        mHomeSectionDrawable.setBounds(
                0, 0, phoneInnerWidth, (int) (phoneInnerHeight * HOME_SHEET_HEIGHT_PERCENT));
        mBookmarkSectionDrawable.setBounds(mSheetDrawable.getBounds());
        mBackgroundDrawable.setBounds(0, 0, phoneInnerWidth,
                phoneInnerHeight - mHomeSectionDrawable.getCollapsedShowHeight());

        int highlightDiameter = (int) (phoneWidth * HIGHLIGHT_DIAMETER_PERCENT);
        mHighlightDrawable.setBounds(0, 0, highlightDiameter, highlightDiameter);
        mHighlightDrawable.setIconSize(mHomeSectionDrawable.getBookmarkIconWidth());
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) {
            mAnimator.cancel();
        } else {
            if (!mAnimator.isRunning() && !mAnimator.isStarted()) {
                mAnimator.start();
            }
        }
        return changed;
    }

    private void buildAnimation() {
        Interpolator interpolator = new FastOutSlowInInterpolator();
        AnimatorSet set = new AnimatorSet();

        Property<ChromeHomePromoIllustration, Float> sheetAnimationProperty =
                new Property<ChromeHomePromoIllustration, Float>(Float.class, "") {
                    @Override
                    public Float get(ChromeHomePromoIllustration illustration) {
                        return mSheetAnimationPercent;
                    }

                    @Override
                    public void set(ChromeHomePromoIllustration illustration, Float value) {
                        mSheetAnimationPercent = value;
                        invalidateSelf();
                    }
                };

        Animator sheetHalfHeightAnimation =
                ObjectAnimator.ofFloat(this, sheetAnimationProperty, 0, 0.5f);
        sheetHalfHeightAnimation.setDuration(DURATION_SHEET_HALF_EXPANSION_MS);

        Animator highlightAnimation = ObjectAnimator.ofFloat(
                this, new Property<ChromeHomePromoIllustration, Float>(Float.class, "") {
                    @Override
                    public Float get(ChromeHomePromoIllustration illustration) {
                        return mHighlightAnimationPercent;
                    }

                    @Override
                    public void set(ChromeHomePromoIllustration illustration, Float value) {
                        mHighlightAnimationPercent = value;
                        invalidateSelf();
                    }
                }, 0, 1f);
        highlightAnimation.setStartDelay(DURATION_HIGHLIGHT_START_DELAY_MS);
        highlightAnimation.setDuration(DURATION_HIGHLIGHT_EXPANSION_MS);

        Animator sheetFullHeightAnimation =
                ObjectAnimator.ofFloat(this, sheetAnimationProperty, 0.5f, 1f);
        sheetFullHeightAnimation.setStartDelay(DURATION_SHEET_HALF_PAUSE_MS);
        sheetFullHeightAnimation.setDuration(DURATION_SHEET_FULL_EXPANSION_MS);

        long fadeDuration = DURATION_SHEET_FULL_EXPANSION_MS / 2;
        Animator homeFadeOutAnimation = ObjectAnimator.ofInt(
                mHomeSectionDrawable, AnimatorProperties.DRAWABLE_ALPHA_PROPERTY, 255, 0);
        homeFadeOutAnimation.setDuration(fadeDuration);

        Animator bookmarkFadeInAnimation = ObjectAnimator.ofInt(
                mBookmarkSectionDrawable, AnimatorProperties.DRAWABLE_ALPHA_PROPERTY, 0, 255);
        bookmarkFadeInAnimation.setDuration(fadeDuration);

        AnimatorSet sheetFadeAnimation = new AnimatorSet();
        sheetFadeAnimation.setStartDelay(DURATION_SHEET_HALF_PAUSE_MS);
        sheetFadeAnimation.playSequentially(homeFadeOutAnimation, bookmarkFadeInAnimation);

        AnimatorSet fullHeightTransition = new AnimatorSet();
        fullHeightTransition.playTogether(sheetFullHeightAnimation, sheetFadeAnimation);

        set.playSequentially(sheetHalfHeightAnimation, highlightAnimation, fullHeightTransition);
        set.setStartDelay(DURATION_SHEET_COLLAPSED_MS);
        set.setInterpolator(interpolator);
        set.addListener(new CancelAwareAnimatorListener() {
            private final Handler mHandler = new Handler();
            private final Runnable mRepeatRunnable = new Runnable() {
                @Override
                public void run() {
                    resetInitialState();
                    invalidateSelf();
                    mAnimator.start();
                }
            };

            private void resetInitialState() {
                mSheetAnimationPercent = 0f;
                mHighlightAnimationPercent = 0f;

                mHomeSectionDrawable.setAlpha(255);
                mBookmarkSectionDrawable.setAlpha(0);
            }

            @Override
            public void onCancel(Animator animator) {
                resetInitialState();
                mHandler.removeCallbacks(mRepeatRunnable);
            }

            @Override
            public void onEnd(Animator animator) {
                if (!isVisible()) {
                    resetInitialState();
                    return;
                }
                mHandler.postDelayed(mRepeatRunnable, DURATION_BETWEEN_REPEATS_MS);
            }
        });
        mAnimator = set;
    }

    @Override
    @SuppressLint("NewApi") // getAlpha() requires API 19, but the inner classes do not rely on
                            // super.getAlpha() for the value.
    public void draw(Canvas canvas) {
        canvas.save();
        // Center the phone in the drawable.
        canvas.translate((getBounds().width() - mPhoneDrawable.getBounds().width()) / 2f, 0);

        canvas.save();
        Rect phoneBounds = mPhoneDrawable.getBounds();
        Rect sheetBounds = mSheetDrawable.getBounds();
        float sheetPhoneWidthDiff = (phoneBounds.width() - sheetBounds.width()) / 2f;
        canvas.translate(
                mPhoneDrawable.getInnerLeft() - sheetPhoneWidthDiff, mPhoneDrawable.getInnerTop());
        canvas.clipRect(0, 0, sheetBounds.width(), sheetBounds.height());

        if (mSheetAnimationPercent < 1f) {
            canvas.save();
            canvas.translate(sheetPhoneWidthDiff, 0);
            mBackgroundDrawable.draw(canvas);
            canvas.restore();
        }

        float sheetTranslationY;
        if (mSheetAnimationPercent <= 0.5) {
            int hideableAmount = mHomeSectionDrawable.getBounds().height()
                    - mHomeSectionDrawable.getCollapsedShowHeight();
            sheetTranslationY = sheetBounds.height() - mHomeSectionDrawable.getBounds().height()
                    + hideableAmount * (1f - mSheetAnimationPercent * 2f);
        } else {
            sheetTranslationY = (sheetBounds.height() - mHomeSectionDrawable.getBounds().height())
                    * (1f - ((mSheetAnimationPercent - .5f) * 2f));
        }
        canvas.translate(0, sheetTranslationY);
        mSheetDrawable.draw(canvas);

        canvas.translate(sheetPhoneWidthDiff, 0);
        if (mHomeSectionDrawable.getAlpha() > 0) mHomeSectionDrawable.draw(canvas);
        if (mBookmarkSectionDrawable.getAlpha() > 0) mBookmarkSectionDrawable.draw(canvas);
        canvas.restore();

        mPhoneDrawable.draw(canvas);

        if (mHighlightAnimationPercent > 0
                && MathUtils.areFloatsEqual(mSheetAnimationPercent, 0.5f)) {
            canvas.save();
            Rect highlightBounds = mHighlightDrawable.getBounds();
            int highlightWidth = highlightBounds.width();
            int highlightHeight = highlightBounds.height();
            canvas.translate((phoneBounds.width() - highlightWidth) / 2f,
                    mPhoneDrawable.getInnerTop() + sheetTranslationY
                            + mHomeSectionDrawable.getHighlightCenterYPoint()
                            - highlightHeight / 2f);
            mHighlightDrawable.setExpansionPercent(mHighlightAnimationPercent);
            mHighlightDrawable.draw(canvas);
            canvas.restore();
        }

        canvas.restore();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        assert false : "Unsupported";
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        assert false : "Unsupported";
    }

    // Drawable.Callback implementation.
    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }

    private static class HomeSectionDrawable extends Drawable {
        private static final int NTP_TILE_PER_ROW_COUNT = 3;
        private static final int ICON_ROW_COUNT = 3;

        private static final float HANDLE_TOP_PADDING_PERCENT = .033f;
        private static final float HANDLE_HEIGHT_PERCENT = .013f;
        private static final float OMNIBOX_HEIGHT_PERCENT = .132f;
        private static final float OMNIBOX_TOP_PADDING_PERCENT = .031f;
        private static final float OMNIBOX_SIDE_PADDING_PERCENT = 0.0375f;
        private static final float NTP_TILE_DIAMETER_PERCENT = .17f;
        private static final float NTP_TILE_TOP_PADDING_INITIAL_PERCENT = .066f;
        private static final float NTP_TILE_TOP_PADDING_PERCENT = .086f;
        private static final float NTP_TILE_INNER_PADDING_PERCENT = .153f;
        private static final float ICON_HEIGHT_PERCENT = .143f;

        private final Paint mPaint = new Paint();
        private final RectF mTempRect = new RectF();
        private final Drawable mDownloadIcon;
        private final Drawable mBookmarkStarIcon;
        private final Drawable mHistoryIcon;
        @ColorRes
        private final int mOmniboxNtpTileColor;

        private int mHandleHeight;
        private int mHandleTopInset;
        private int mOmniboxSidePadding;
        private int mOmniboxTopPadding;
        private int mOmniboxHeight;
        private int mOmniboxCornerRadius;
        private int mNtpTileTopPadding;
        private int mNtpTileInitialTopPadding;
        private int mNtpTileOuterPadding;
        private int mNtpTileInnerPadding;
        private int mNtpTileRadius;
        private int mIconWidth;

        private int mAlpha = 255;

        private int mBottomBarOffsetY;

        public HomeSectionDrawable(Resources resources, Drawable downloadIconDrawable,
                Drawable bookmarkStarDrawable, Drawable historyIconDrawable,
                @ColorRes int omniboxNtpTileColor) {
            int tintColor = ApiCompatibilityUtils.getColor(resources, R.color.black_alpha_65);
            mDownloadIcon = downloadIconDrawable;
            mDownloadIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);

            mBookmarkStarIcon = bookmarkStarDrawable;
            mBookmarkStarIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);

            mHistoryIcon = historyIconDrawable;
            mHistoryIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);

            mOmniboxNtpTileColor = omniboxNtpTileColor;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mAlpha == 0) return;

            canvas.clipRect(getBounds());
            int w = getBounds().width();

            mPaint.setColor(mOmniboxNtpTileColor);
            mPaint.setAlpha(mAlpha);

            mTempRect.set(mOmniboxSidePadding, mHandleTopInset + mOmniboxTopPadding + mHandleHeight,
                    w - mOmniboxSidePadding,
                    mHandleTopInset + mOmniboxTopPadding + mHandleHeight + mOmniboxHeight);
            canvas.drawRoundRect(mTempRect, mOmniboxCornerRadius, mOmniboxCornerRadius, mPaint);

            int translateY = mHandleTopInset + mOmniboxTopPadding + mHandleHeight + mOmniboxHeight
                    + mNtpTileInitialTopPadding;
            mBottomBarOffsetY = translateY;
            canvas.translate(0, translateY);
            drawNtpTiles(canvas);

            translateY = 2 * mNtpTileRadius + mNtpTileTopPadding;
            mBottomBarOffsetY += translateY;
            canvas.translate(0, translateY);
            drawNtpTiles(canvas);

            translateY = 2 * mNtpTileRadius + mNtpTileTopPadding;
            mBottomBarOffsetY += translateY;
            canvas.translate(0, translateY);
            drawBottomBar(canvas);
        }

        private void drawNtpTiles(Canvas canvas) {
            mPaint.setColor(mOmniboxNtpTileColor);
            mPaint.setAlpha(mAlpha);

            for (int i = 0; i < NTP_TILE_PER_ROW_COUNT; i++) {
                int cx = mNtpTileOuterPadding + mNtpTileInnerPadding * i + (2 * i * mNtpTileRadius)
                        + mNtpTileRadius;
                canvas.drawCircle(cx, mNtpTileRadius, mNtpTileRadius, mPaint);
            }
        }

        private void drawBottomBar(Canvas canvas) {
            int iconSpacing = (getBounds().width() - mIconWidth * 3) / 4;
            for (int i = 0; i < ICON_ROW_COUNT; i++) {
                Drawable icon;
                if (i == 0) {
                    icon = mDownloadIcon;
                } else if (i == 1) {
                    icon = mBookmarkStarIcon;
                } else {
                    icon = mHistoryIcon;
                }
                icon.setAlpha(mAlpha);

                canvas.save();
                canvas.translate(iconSpacing * (i + 1) + (i * mIconWidth), 0);
                icon.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            int height = bounds.height();
            int width = bounds.width();

            mHandleHeight = (int) (HANDLE_HEIGHT_PERCENT * height);
            mHandleTopInset = (int) (HANDLE_TOP_PADDING_PERCENT * height);

            mOmniboxSidePadding = (int) (OMNIBOX_SIDE_PADDING_PERCENT * width);
            mOmniboxTopPadding = (int) (OMNIBOX_TOP_PADDING_PERCENT * height);
            mOmniboxHeight = (int) (OMNIBOX_HEIGHT_PERCENT * height);
            mOmniboxCornerRadius = mOmniboxHeight / 2;

            mNtpTileInitialTopPadding = (int) (NTP_TILE_TOP_PADDING_INITIAL_PERCENT * height);
            mNtpTileTopPadding = (int) (NTP_TILE_TOP_PADDING_PERCENT * height);
            mNtpTileRadius = (int) ((NTP_TILE_DIAMETER_PERCENT * height) / 2);
            mNtpTileInnerPadding = (int) (NTP_TILE_INNER_PADDING_PERCENT * width);
            mNtpTileOuterPadding = (width - 6 * mNtpTileRadius - 2 * mNtpTileInnerPadding) / 2;

            mIconWidth = (int) (ICON_HEIGHT_PERCENT * height);
            mDownloadIcon.setBounds(0, 0, mIconWidth, mIconWidth);
            mBookmarkStarIcon.setBounds(0, 0, mIconWidth, mIconWidth);
            mHistoryIcon.setBounds(0, 0, mIconWidth, mIconWidth);
        }

        /**
         * @return The amount of the home sheet to be visible in the collapsed/peeking state.
         */
        public int getCollapsedShowHeight() {
            return mHandleTopInset + mOmniboxTopPadding + mHandleHeight + mOmniboxHeight
                    + mNtpTileTopPadding / 2;
        }

        /**
         * @return The center Y position for the highlight over the middle icon.  Based off the
         *         top of the home sheet being 0.
         */
        public int getHighlightCenterYPoint() {
            return mBottomBarOffsetY + mIconWidth / 2;
        }

        /**
         * @return The width
         */
        public int getBookmarkIconWidth() {
            return mIconWidth;
        }

        @Override
        public int getAlpha() {
            return mAlpha;
        }

        @Override
        public void setAlpha(int i) {
            int previousAlpha = mAlpha;
            mAlpha = i;
            if (previousAlpha != mAlpha) invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            assert false : "Unsupported";
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static class BookmarkSectionDrawable extends Drawable {
        private static final float BOOKMARK_WIDTH_PERCENT = .174f;
        private static final float BOOKMARK_INITIAL_TOP_PADDING_PERCENT = .107f;
        private static final float BOOKMARK_TOP_PADDING_PERCENT = .055f;
        private static final float BOOKMARK_TEXT_BLOCK_HEIGHT_PERCENT = .031f;
        private static final float BOOKMARK_TEXT_BLOCK_WIDTH_PERCENT = .435f;
        private static final float SIDE_PADDING_PERCENT = .141f;
        private static final float STAR_TEXT_HORIZONTAL_PADDING = .087f;

        private Paint mPaint = new Paint();
        private Drawable mBookmarkStar;

        private int mSidePadding;
        private int mBookmarkHeight;
        private int mBookmarkInitialVerticalPadding;
        private int mBookmarkVerticalPadding;
        private int mBookmarkStarTextSpacing;
        private int mBookmarkTextBlockHeight;
        private int mBookmarkTextBlockWidth;

        private int mAlpha;

        public BookmarkSectionDrawable(Drawable bookmarkStarDrawable) {
            mBookmarkStar = bookmarkStarDrawable;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mAlpha == 0) return;

            canvas.save();
            canvas.translate(0, mBookmarkInitialVerticalPadding);
            for (int i = 0; i < BOOKMARK_ROW_COLORS.length; i++) {
                if (i > 0) {
                    canvas.translate(0, mBookmarkVerticalPadding + mBookmarkHeight);
                }
                mPaint.setColor(BOOKMARK_ROW_COLORS[i]);
                mPaint.setAlpha(mAlpha);

                canvas.save();
                canvas.translate(mSidePadding, 0);
                mBookmarkStar.setColorFilter(BOOKMARK_ROW_COLORS[i], PorterDuff.Mode.SRC_IN);
                mBookmarkStar.draw(canvas);
                canvas.restore();

                int textBoxTop = (mBookmarkHeight - mBookmarkTextBlockHeight) / 2;
                int textBoxLeft = mBookmarkHeight + mSidePadding + mBookmarkStarTextSpacing;
                canvas.drawRect(textBoxLeft, textBoxTop, textBoxLeft + mBookmarkTextBlockWidth,
                        textBoxTop + mBookmarkTextBlockHeight, mPaint);
            }
            canvas.restore();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            int width = bounds.width();
            int height = bounds.height();

            mSidePadding = (int) (SIDE_PADDING_PERCENT * width);
            mBookmarkHeight = (int) (BOOKMARK_WIDTH_PERCENT * width);
            mBookmarkInitialVerticalPadding = (int) (BOOKMARK_INITIAL_TOP_PADDING_PERCENT * height);
            mBookmarkVerticalPadding = (int) (BOOKMARK_TOP_PADDING_PERCENT * height);
            mBookmarkStarTextSpacing = (int) (STAR_TEXT_HORIZONTAL_PADDING * width);
            mBookmarkTextBlockHeight = (int) (BOOKMARK_TEXT_BLOCK_HEIGHT_PERCENT * height);
            mBookmarkTextBlockWidth = (int) (BOOKMARK_TEXT_BLOCK_WIDTH_PERCENT * width);
            mBookmarkStar.setBounds(0, 0, mBookmarkHeight, mBookmarkHeight);
        }

        @Override
        public int getAlpha() {
            return mAlpha;
        }

        @Override
        public void setAlpha(int i) {
            int previousAlpha = mAlpha;
            mAlpha = i;
            if (previousAlpha != mAlpha) invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            assert false : "Unsupported";
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class SheetDrawable extends Drawable {
        private static final float HANDLE_TOP_PADDING_PERCENT = .019f;
        private static final float HANDLE_HEIGHT_PERCENT = .008f;
        private static final float HANDLE_WIDTH_PERCENT = .065f;

        private Paint mPaint = new Paint();
        private RectF mTempRect = new RectF();

        private int mHandleWidth;
        private int mHandleHeight;
        private int mHandleTopInset;
        private int mHandleRoundedRadius;

        @Override
        public void draw(@NonNull Canvas canvas) {
            int w = getBounds().width();

            mPaint.setColor(Color.WHITE);
            canvas.drawRect(getBounds(), mPaint);

            mPaint.setColor(Color.DKGRAY);
            mTempRect.set(w / 2 - mHandleWidth / 2, mHandleTopInset, w / 2 + mHandleWidth / 2,
                    mHandleTopInset + mHandleHeight);
            canvas.drawRoundRect(mTempRect, mHandleRoundedRadius, mHandleRoundedRadius, mPaint);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            mHandleWidth = (int) (HANDLE_WIDTH_PERCENT * bounds.width());
            mHandleHeight = (int) (HANDLE_HEIGHT_PERCENT * bounds.height());
            mHandleRoundedRadius = mHandleHeight / 2;
            mHandleTopInset = (int) (HANDLE_TOP_PADDING_PERCENT * bounds.height());
        }

        @Override
        public void setAlpha(int i) {
            assert false : "Unsupported";
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            assert false : "Unsupported";
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static class HighlightDrawable extends Drawable {
        private static final float INNER_CIRCLE_PERCENT = 0.467f;
        private static final float ICON_MAX_GROWTH_PERCENT = 1.3f;
        private static final float INVERSE_GROWTH_PERCENT = 1 / ICON_MAX_GROWTH_PERCENT;

        private final Paint mPaint = new Paint();
        private final Drawable mIcon;

        private float mExpansionPercent;

        public HighlightDrawable(Resources resources, Drawable iconDrawable) {
            mIcon = iconDrawable;
            mIcon.setColorFilter(
                    ApiCompatibilityUtils.getColor(resources, R.color.light_active_color),
                    PorterDuff.Mode.SRC_IN);
        }

        public void setIconSize(int iconSize) {
            mIcon.setBounds(0, 0, (int) (iconSize * ICON_MAX_GROWTH_PERCENT),
                    (int) (iconSize * ICON_MAX_GROWTH_PERCENT));
        }

        public void setExpansionPercent(float percent) {
            mExpansionPercent = percent;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            float centerPoint = w / 2f;

            mPaint.setColor(Color.parseColor("#1929B6F6"));
            canvas.drawCircle(centerPoint, centerPoint, centerPoint * mExpansionPercent, mPaint);

            mPaint.setColor(Color.parseColor("#3D29B6F6"));
            canvas.drawCircle(centerPoint, centerPoint,
                    ((w * INNER_CIRCLE_PERCENT) / 2f) * mExpansionPercent, mPaint);

            int iconWidth = mIcon.getBounds().width();
            int iconHeight = mIcon.getBounds().height();
            canvas.save();
            canvas.translate((w - iconWidth) / 2f, (h - iconHeight) / 2f);
            float scale = MathUtils.interpolate(INVERSE_GROWTH_PERCENT, 1f, mExpansionPercent);
            canvas.scale(scale, scale, iconWidth / 2f, iconHeight / 2f);
            mIcon.draw(canvas);
            canvas.restore();
        }

        @Override
        public void setAlpha(int i) {
            assert false : "Unsupported";
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            assert false : "Unsupported";
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class PhoneDrawable extends Drawable {
        private static final float STROKE_WIDTH_PERCENT = .0048f;
        private static final float OUTER_CORNER_RADIUS_PERCENT = .13f;
        private static final float INNER_CORNER_RADIUS_PERCENT = .07f;
        private static final float BEZEL_TOP_PERCENT = .107f;
        private static final float BEZEL_BOTTOM_PERCENT = .121f;
        private static final float BEZEL_SIDE_PERCENT = .074f;
        private static final float SPEAKER_WIDTH_PERCENT = .203f;
        private static final float SPEAKER_HEIGHT_PERCENT = .015f;
        private static final float SPEAKER_TOP_PADDING_PERCENT = .039f;

        private final Paint mPaint = new Paint();
        @ColorRes
        private final int mPhoneColor;

        private Bitmap mBitmap;

        private int mOuterCornerRadius;
        private int mInnerCornerRadius;
        private int mBezelTopHeight;
        private int mBezelBottomHeight;
        private int mBezelSideWidth;
        private int mStrokeWidth;

        private int mSpeakerWidth;
        private int mSpeakerHeight;
        private int mSpeakerInset;
        private int mSpeakerCornerRadius;

        public PhoneDrawable(@ColorRes int phoneColor) {
            mPhoneColor = phoneColor;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            int height = bounds.height();
            int width = bounds.width();

            mStrokeWidth = (int) (STROKE_WIDTH_PERCENT * width);
            mOuterCornerRadius = (int) (OUTER_CORNER_RADIUS_PERCENT * width);
            mInnerCornerRadius = (int) (INNER_CORNER_RADIUS_PERCENT * width);
            mBezelTopHeight = (int) (BEZEL_TOP_PERCENT * height);
            mBezelBottomHeight = (int) (BEZEL_BOTTOM_PERCENT * height);
            mBezelSideWidth = (int) (BEZEL_SIDE_PERCENT * width);

            mSpeakerWidth = (int) (SPEAKER_WIDTH_PERCENT * width);
            mSpeakerHeight = (int) (SPEAKER_HEIGHT_PERCENT * height);
            mSpeakerInset = (int) (SPEAKER_TOP_PADDING_PERCENT * height);
            mSpeakerCornerRadius = mSpeakerHeight / 2;

            mBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
            drawPhone();
        }

        /**
         * @return The inner height of the phone drawable.
         */
        public int getInnerHeight() {
            return getBounds().height() - mBezelTopHeight - mBezelBottomHeight;
        }

        /**
         * @return The inner width of the phone drawable.
         */
        public int getInnerWidth() {
            return getBounds().width() - mBezelSideWidth * 2;
        }

        /**
         * @return The left X position of the inner content area of the phone drawable.
         */
        public int getInnerLeft() {
            return mBezelSideWidth;
        }

        /**
         * @return The top Y position of the inner content area of the phone drawable.
         */
        public int getInnerTop() {
            return mBezelTopHeight;
        }

        private void drawPhone() {
            Canvas canvas = new Canvas(mBitmap);

            int w = getBounds().width();
            int h = getBounds().height();

            int left = 0;
            int right = w;
            int top = 0;
            int bottom = h;

            canvas.save();
            RectF rectF = new RectF(left, top, right, bottom);
            mPaint.setColor(Color.parseColor("#BDC1C6"));
            canvas.drawRoundRect(rectF, mOuterCornerRadius, mOuterCornerRadius, mPaint);

            mPaint.setColor(mPhoneColor);
            rectF.inset(mStrokeWidth, mStrokeWidth);
            canvas.drawRoundRect(rectF, mOuterCornerRadius, mOuterCornerRadius, mPaint);

            int halfWidth = w / 2;
            rectF.set(halfWidth - (mSpeakerWidth / 2), mSpeakerInset,
                    halfWidth + (mSpeakerWidth / 2), mSpeakerInset + mSpeakerHeight);
            mPaint.setColor(Color.parseColor("#9C9C9C"));
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rectF, mSpeakerCornerRadius, mSpeakerCornerRadius, mPaint);

            rectF.set(mBezelSideWidth, mBezelTopHeight, right - mBezelSideWidth,
                    bottom - mBezelBottomHeight);
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
            canvas.drawRoundRect(rectF, mInnerCornerRadius, mInnerCornerRadius, mPaint);
            canvas.restore();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mBitmap == null) return;

            canvas.drawBitmap(mBitmap, 0, 0, null);
        }

        @Override
        public void setAlpha(int i) {
            assert false : "Unsupported";
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            assert false : "Unsupported";
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
