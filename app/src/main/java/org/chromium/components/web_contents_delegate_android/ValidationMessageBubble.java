// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.web_contents_delegate_android;

import android.graphics.Point;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.CalledByNative;

/**
 * This class is an implementation of validation message bubble UI.
 */
class ValidationMessageBubble {
    private final View mView;
    private PopupWindow mPopup;

    /**
     * Creates a popup window to show the specified messages.
     *
     * @param containerView A parent view for the message bubble.
     * @param mainText The main message. It will shown at the top of the popup window, and its font
     *                 size is larger.
     * @param subText The sub message. It will shown below the main message, and its font size is
     *                smaller.
     */
    @CalledByNative
    private static ValidationMessageBubble createIfApplicable(
            View containerView, String mainText, String subText) {
        if (!canShowBubble(containerView)) return null;
        return new ValidationMessageBubble(containerView, mainText, subText);
    }

    private static boolean canShowBubble(View containerView) {
        return containerView != null && containerView.getWindowToken() != null;
    }

    private ValidationMessageBubble(View containerView, String mainText, String subText) {
        mView = containerView;
        ViewGroup root = (ViewGroup) View.inflate(
                mView.getContext(), R.layout.validation_message_bubble, null);
        mPopup = new PopupWindow(root);
        updateTextViews(root, mainText, subText);
    }

    @CalledByNative
    private void close() {
        if (mPopup == null) return;
        mPopup.dismiss();
        mPopup = null;
    }

    /**
     * Moves the popup window on the specified anchor rectangle. All the values are in device
     * pixel unit.
     *
     * @param viewportWidthPx Viewport width.
     * @param viewportHeightPx Viewport height.
     * @param contentOffsetYPx Content offset from the top.
     * @param anchorXPx Anchor x position.
     * @param anchorYPx Anchor y position.
     * @param anchorWidthPx Anchor width.
     * @param anchorHeightPx Anchor height.
     */
    @CalledByNative
    private void showAtPositionRelativeToAnchor(int viewportWidthPx, int viewportHeightPx,
            float contentOffsetYPx, int anchorXPx, int anchorYPx, int anchorWidthPx,
            int anchorHeightPx) {
        RectF anchor = makeRectInScreen(
                mView, contentOffsetYPx, anchorXPx, anchorYPx, anchorWidthPx, anchorHeightPx);
        Point origin = adjustWindowPosition(viewportWidthPx, viewportHeightPx, contentOffsetYPx,
                (int) (anchor.centerX() - getAnchorOffset()), (int) anchor.bottom);
        if (!mPopup.isShowing()) {
            measure(viewportWidthPx, viewportHeightPx);
            mPopup.showAtLocation(mView, Gravity.NO_GRAVITY, origin.x, origin.y);
        } else {
            mPopup.update(origin.x, origin.y, mPopup.getWidth(), mPopup.getHeight());
        }
    }

    private static RectF makeRectInScreen(View containerView, float contentOffsetYPxPix,
            int anchorXPx, int anchorYPx, int anchorWidthPx, int anchorHeightPx) {
        final float yOffset = getWebViewOffsetYPixInScreen(containerView, contentOffsetYPxPix);
        return new RectF(anchorXPx, anchorYPx + yOffset, anchorXPx + anchorWidthPx,
                anchorYPx + anchorHeightPx + yOffset);
    }

    private static float getWebViewOffsetYPixInScreen(View containerView, float contentOffsetYPx) {
        int[] location = new int[2];
        containerView.getLocationOnScreen(location);
        return location[1] + contentOffsetYPx;
    }

    private static void updateTextViews(ViewGroup root, String mainText, String subText) {
        ((TextView) root.findViewById(R.id.main_text)).setText(mainText);
        final TextView subTextView = (TextView) root.findViewById(R.id.sub_text);
        if (!TextUtils.isEmpty(subText)) {
            subTextView.setText(subText);
        } else {
            ((ViewGroup) subTextView.getParent()).removeView(subTextView);
        }
    }

    private void measure(int viewportWidthPx, int viewportHeightPx) {
        mPopup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopup.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopup.getContentView().setLayoutParams(
                new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT));
        mPopup.getContentView().measure(
                View.MeasureSpec.makeMeasureSpec(viewportWidthPx, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(viewportHeightPx, View.MeasureSpec.AT_MOST));
    }

    private float getAnchorOffset() {
        final View root = mPopup.getContentView();
        final int width = root.getMeasuredWidth();
        final int arrowWidth = root.findViewById(R.id.arrow_image).getMeasuredWidth();
        return ApiCompatibilityUtils.isLayoutRtl(root) ? (width * 3 / 4 - arrowWidth / 2)
                                                       : (width / 4 + arrowWidth / 2);
    }

    /**
     * This adjusts the position if the popup protrudes the web view.
     */
    private Point adjustWindowPosition(
            int viewWidthPx, int viewHeightPx, float contentOffsetYPx, int x, int y) {
        final int viewBottom =
                (int) getWebViewOffsetYPixInScreen(mView, contentOffsetYPx) + viewHeightPx;
        final int width = mPopup.getContentView().getMeasuredWidth();
        final int height = mPopup.getContentView().getMeasuredHeight();
        if (x < 0) {
            x = 0;
        } else if (x + width > viewWidthPx) {
            x = viewWidthPx - width;
        }
        if (y + height > viewBottom) y = viewBottom - height;
        return new Point(x, y);
    }
}
