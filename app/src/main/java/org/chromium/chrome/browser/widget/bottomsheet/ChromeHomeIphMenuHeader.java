// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.feature_engagement.EventConstants;
import org.chromium.components.feature_engagement.FeatureConstants;

/**
 * A menu header helping users find where their content moved when Chrome Home is enabled.
 * The Chrome Home in-product help bubble pointing toward the bottom sheet is displayed on click.
 */
public class ChromeHomeIphMenuHeader extends LinearLayout implements OnClickListener {
    /** Notified about events related to the menu header. */
    public static interface ChromeHomeIphMenuHeaderTestObserver {
        void onMenuItemClicked();
        void onMenuDismissed(boolean dimissIph);
    }

    private static ChromeHomeIphMenuHeaderTestObserver sTestObserver;

    private ChromeActivity mActivity;
    private boolean mDismissIPHOnMenuDismissed;

    public ChromeHomeIphMenuHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes the menu header.
     * @param activity The {@link ChromeActivity} that will display the app menu containing this
     *                 header.
     */
    public void initialize(ChromeActivity activity) {
        mActivity = activity;
        mDismissIPHOnMenuDismissed = true;
        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        mDismissIPHOnMenuDismissed = false;

        TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile())
                .notifyEvent(EventConstants.CHROME_HOME_MENU_HEADER_CLICKED);

        mActivity.getBottomSheet().getBottomSheetMetrics().recordInProductHelpMenuItemClicked();
        mActivity.getBottomSheet().maybeShowHelpBubble(true, false);

        mActivity.getAppMenuHandler().hideAppMenu();

        if (sTestObserver != null) sTestObserver.onMenuItemClicked();
    }

    /**
     * Must be called when the app menu is dismissed. Used to notify the IPH system that the menu
     * header is no longer showing.
     */
    public void onMenuDismissed() {
        if (sTestObserver != null) {
            sTestObserver.onMenuDismissed(mDismissIPHOnMenuDismissed);
        }

        if (!mDismissIPHOnMenuDismissed) return;

        TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile())
                .dismissed(FeatureConstants.CHROME_HOME_MENU_HEADER_FEATURE);
        mDismissIPHOnMenuDismissed = false;
    }

    /**
     * An observer to be notified about events related to the menu header. Used for testing. Must be
     * called on the UI thread.
     */
    @VisibleForTesting
    public static void setObserverForTests(ChromeHomeIphMenuHeaderTestObserver observer) {
        ThreadUtils.assertOnUiThread();
        sTestObserver = observer;
    }
}