// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.support.v4.widget.TextViewCompat;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Helper methods that can be used across multiple Autofill UIs.
 */
public class AutofillUiUtils {
    /**
     * Interface to provide the horizontal and vertical offset for the tooltip.
     */
    public interface OffsetProvider {
        /** Returns the X offset for the tooltip. */
        int getXOffset(TextView textView);
        /** Returns the Y offset for the tooltip. */
        int getYOffset(TextView textView);
    }

    // 200ms is chosen small enough not to be detectable to human eye, but big
    // enough for to avoid any race conditions on modern machines.
    private static final int TOOLTIP_DEFERRED_PERIOD_MS = 200;

    /**
     * Show Tooltip UI.
     *
     * @param context Context required to get resources.
     * @param popup {@PopupWindow} that shows tooltip UI.
     * @param text  Text to be shown in tool tip UI.
     * @param offsetProvider Interface to provide the X and Y offsets.
     * @param anchorView Anchor view under which tooltip popup has to be shown
     * @param dismissAction Tooltip dismissive action.
     */
    public static void showTooltip(Context context, PopupWindow popup, int text,
            OffsetProvider offsetProvider, View anchorView, final Runnable dismissAction) {
        TextView textView = new TextView(context);
        textView.setText(text);
        TextViewCompat.setTextAppearance(textView, R.style.TextAppearance_WhiteBody);
        Resources resources = context.getResources();
        int hPadding = resources.getDimensionPixelSize(R.dimen.autofill_tooltip_horizontal_padding);
        int vPadding = resources.getDimensionPixelSize(R.dimen.autofill_tooltip_vertical_padding);
        textView.setPadding(hPadding, vPadding, hPadding, vPadding);
        textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

        popup.setContentView(textView);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(ApiCompatibilityUtils.getDrawable(
                resources, R.drawable.store_locally_tooltip_background));
        popup.setOnDismissListener(() -> {
            Handler h = new Handler();
            h.postDelayed(dismissAction, TOOLTIP_DEFERRED_PERIOD_MS);
        });
        popup.showAsDropDown(anchorView, offsetProvider.getXOffset(textView),
                offsetProvider.getYOffset(textView));
        textView.announceForAccessibility(textView.getText());
    }
}
