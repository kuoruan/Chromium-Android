// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.autofill_assistant.carousel.AssistantCarouselCoordinator;
import org.chromium.chrome.browser.autofill_assistant.details.AssistantDetailsCoordinator;
import org.chromium.chrome.browser.autofill_assistant.header.AssistantHeaderCoordinator;
import org.chromium.chrome.browser.autofill_assistant.payment.AssistantPaymentRequestCoordinator;
import org.chromium.content_public.browser.WebContents;

/**
 * Coordinator responsible for the Autofill Assistant bottom bar. This coordinator allows to enable
 * or disable the swipeable behavior of the bottom bar and ensures that the bottom bar height is
 * constant during the script execution (if possible) by adapting the spacing between its child
 * views (details, payment request and carousel).
 */
class AssistantBottomBarCoordinator {
    // Spacings between child views.
    private static final int CHILDREN_HORIZONTAL_MARGIN_DP = 24;
    private static final int CHILDREN_VERTICAL_MARGIN_DP = 20;
    private static final int DETAILS_ONLY_VERTICAL_MARGIN_DP = 48;
    private static final int PAYMENT_REQUEST_MARGIN_BOTTOM_DP = 16;

    // The top padding that should be applied to the bottom bar when the swiping indicator is
    // hidden.
    private static final int BOTTOM_BAR_WITHOUT_INDICATOR_PADDING_TOP_DP = 16;

    private final ViewGroup mBottomBarView;
    private final View mSwipeIndicatorView;
    private final BottomSheetBehavior mBottomBarBehavior;

    // Dimensions in device pixels.
    private final int mChildrenHorizontalMargin;
    private final int mDetailsOnlyVerticalMargin;
    private final int mChildrenVerticalSpacing;
    private final int mBottomBarWithoutIndicatorPaddingTop;
    private final int mPaymentRequestMarginBottom;

    // Child coordinators.
    private final AssistantHeaderCoordinator mHeaderCoordinator;
    private final AssistantDetailsCoordinator mDetailsCoordinator;
    private final AssistantPaymentRequestCoordinator mPaymentRequestCoordinator;
    private final AssistantCarouselCoordinator mCarouselCoordinator;

    AssistantBottomBarCoordinator(ChromeActivity activity, WebContents webContents,
            View assistantView, AssistantModel model) {
        mBottomBarView = assistantView.findViewById(
                org.chromium.chrome.autofill_assistant.R.id.autofill_assistant_bottombar);
        mSwipeIndicatorView = mBottomBarView.findViewById(
                org.chromium.chrome.autofill_assistant.R.id.swipe_indicator);
        mBottomBarBehavior = BottomSheetBehavior.from(mBottomBarView);

        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        mChildrenHorizontalMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CHILDREN_HORIZONTAL_MARGIN_DP, displayMetrics);
        mDetailsOnlyVerticalMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, DETAILS_ONLY_VERTICAL_MARGIN_DP, displayMetrics);
        mChildrenVerticalSpacing = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CHILDREN_VERTICAL_MARGIN_DP, displayMetrics);
        mBottomBarWithoutIndicatorPaddingTop =
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        BOTTOM_BAR_WITHOUT_INDICATOR_PADDING_TOP_DP, displayMetrics);
        mPaymentRequestMarginBottom = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, PAYMENT_REQUEST_MARGIN_BOTTOM_DP, displayMetrics);

        // Instantiate child components.
        mHeaderCoordinator =
                new AssistantHeaderCoordinator(activity, mBottomBarView, model.getHeaderModel());
        mDetailsCoordinator = new AssistantDetailsCoordinator(activity, model.getDetailsModel());
        mPaymentRequestCoordinator = new AssistantPaymentRequestCoordinator(activity, webContents);
        mCarouselCoordinator = new AssistantCarouselCoordinator(activity, model.getCarouselModel());

        // Add child views to bottom bar.
        mBottomBarView.addView(mDetailsCoordinator.getView());
        mBottomBarView.addView(mPaymentRequestCoordinator.getView());
        mBottomBarView.addView(mCarouselCoordinator.getView());

        // Ensure the margin are computed correctly from the beginning.
        onChildViewVisibilityChanged();

        // Add child views visibility listener.
        mDetailsCoordinator.setVisibilityChangedListener(this::onChildViewVisibilityChanged);
        mPaymentRequestCoordinator.setVisibilityChangedListener(this::onChildViewVisibilityChanged);
        mCarouselCoordinator.setVisibilityChangedListener(this::onChildViewVisibilityChanged);
    }

    /**
     * Return the container view representing the bottom bar. Adding child views to this view should
     * add them below the header.
     */
    public ViewGroup getView() {
        return mBottomBarView;
    }

    /**
     * Make sure the bottom bar is expanded and text is visible.
     */
    public void expand() {
        mBottomBarBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    /**
     * Enable or disable to swipeable behavior of the bottom bar.
     */
    public void allowSwipingBottomSheet(boolean allowed) {
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) mBottomBarView.getLayoutParams();
        if (allowed) {
            params.setBehavior(mBottomBarBehavior);
            mSwipeIndicatorView.setVisibility(View.VISIBLE);
            setBottomBarPaddingTop(0);
        } else {
            params.setBehavior(null);
            mSwipeIndicatorView.setVisibility(View.GONE);
            setBottomBarPaddingTop(mBottomBarWithoutIndicatorPaddingTop);
        }
    }

    public AssistantPaymentRequestCoordinator getPaymentRequestCoordinator() {
        return mPaymentRequestCoordinator;
    }

    public AssistantCarouselCoordinator getCarouselCoordinator() {
        return mCarouselCoordinator;
    }

    private void setBottomBarPaddingTop(int paddingPx) {
        mBottomBarView.setPadding(0, paddingPx, 0, 0);
    }

    /**
     * Called when one of its child views visibility has changed. This method set the margin of
     * those views such that the height of the bottom bar is constant most of the time.
     */
    private void onChildViewVisibilityChanged() {
        View detailsView = mDetailsCoordinator.getView();
        View carouselView = mCarouselCoordinator.getView();
        View paymentRequestView = mPaymentRequestCoordinator.getView();

        boolean detailsVisible = detailsView != null && detailsView.getVisibility() == View.VISIBLE;
        boolean carouselVisible =
                carouselView != null && carouselView.getVisibility() == View.VISIBLE;
        boolean paymentRequestVisible =
                paymentRequestView != null && paymentRequestView.getVisibility() == View.VISIBLE;

        int topMargin = mChildrenVerticalSpacing;
        if (detailsVisible) {
            // Set details margins.
            LinearLayout.LayoutParams detailsLayoutParams =
                    (LinearLayout.LayoutParams) detailsView.getLayoutParams();
            int detailsVerticalMargin = carouselVisible || paymentRequestVisible
                    ? topMargin
                    : mDetailsOnlyVerticalMargin;
            detailsLayoutParams.setMargins(mChildrenHorizontalMargin, detailsVerticalMargin,
                    mChildrenHorizontalMargin, detailsVerticalMargin);
            detailsView.setLayoutParams(detailsLayoutParams);

            topMargin = 0;
        }

        if (paymentRequestVisible) {
            // Set payment request margins.
            LinearLayout.LayoutParams paymentRequestLayoutParams =
                    (LinearLayout.LayoutParams) paymentRequestView.getLayoutParams();
            paymentRequestLayoutParams.setMargins(mChildrenHorizontalMargin, topMargin,
                    mChildrenHorizontalMargin, mPaymentRequestMarginBottom);
            paymentRequestView.setLayoutParams(paymentRequestLayoutParams);

            topMargin = 0;
        }

        if (carouselVisible) {
            // Set carousel margins.
            LinearLayout.LayoutParams carouselLayoutParams =
                    (LinearLayout.LayoutParams) carouselView.getLayoutParams();
            carouselLayoutParams.setMargins(
                    /* left= */ 0, topMargin, /* right= */ 0, mChildrenVerticalSpacing);
            carouselView.setLayoutParams(carouselLayoutParams);
        }
    }
}
