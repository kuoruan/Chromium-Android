// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.IntDef;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.BottomNavigationView.OnNavigationItemSelectedListener;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkSheetContent;
import org.chromium.chrome.browser.download.DownloadSheetContent;
import org.chromium.chrome.browser.history.HistorySheetContent;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.suggestions.SuggestionsBottomSheetContent;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Displays and controls a {@link BottomNavigationView} fixed to the bottom of the
 * {@link BottomSheet}. Also manages {@link BottomSheetContent} displayed in the BottomSheet.
 */
public class BottomSheetContentController extends BottomNavigationView
        implements OnNavigationItemSelectedListener {
    /** The different types of content that may be displayed in the bottom sheet. */
    @IntDef({TYPE_SUGGESTIONS, TYPE_DOWNLOADS, TYPE_BOOKMARKS, TYPE_HISTORY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContentType {}
    public static final int TYPE_SUGGESTIONS = 0;
    public static final int TYPE_DOWNLOADS = 1;
    public static final int TYPE_BOOKMARKS = 2;
    public static final int TYPE_HISTORY = 3;

    private final Map<Integer, BottomSheetContent> mBottomSheetContents = new HashMap<>();

    private final BottomSheetObserver mBottomSheetObserver = new EmptyBottomSheetObserver() {
        @Override
        public void onSheetOffsetChanged(float heightFraction) {
            float offsetY = (mBottomSheet.getMinOffset() - mBottomSheet.getSheetOffsetFromBottom())
                    + mDistanceBelowToolbarPx;
            setTranslationY((int) Math.max(offsetY, 0f));
            setVisibility(MathUtils.areFloatsEqual(heightFraction, 0f) ? View.GONE : View.VISIBLE);

            mSnackbarManager.dismissAllSnackbars();
        }

        @Override
        public void onSheetOpened() {
            if (!mDefaultContentInitialized && mTabModelSelector.getCurrentTab() != null) {
                initializeDefaultContent();
            }
        }

        @Override
        public void onSheetClosed() {
            Iterator<Entry<Integer, BottomSheetContent>> contentIterator =
                    mBottomSheetContents.entrySet().iterator();
            while (contentIterator.hasNext()) {
                Entry<Integer, BottomSheetContent> entry = contentIterator.next();
                if (entry.getKey() == R.id.action_home) continue;

                entry.getValue().destroy();
                contentIterator.remove();
            }
            // TODO(twellington): determine a policy for destroying the
            //                    SuggestionsBottomSheetContent.

            if (mSelectedItemId == 0 || mSelectedItemId == R.id.action_home) return;

            showBottomSheetContent(R.id.action_home);
        }
    };

    private BottomSheet mBottomSheet;
    private TabModelSelector mTabModelSelector;
    private SnackbarManager mSnackbarManager;
    private float mDistanceBelowToolbarPx;
    private int mSelectedItemId;
    private boolean mDefaultContentInitialized;

    public BottomSheetContentController(Context context, AttributeSet atts) {
        super(context, atts);
    }

    /**
     * Initializes the {@link BottomSheetContentController}.
     * @param bottomSheet The {@link BottomSheet} associated with this bottom nav.
     * @param controlContainerHeight The height of the control container in px.
     * @param tabModelSelector The {@link TabModelSelector} for the application.
     * @param activity The {@link Activity} that owns the BottomSheet.
     */
    public void init(BottomSheet bottomSheet, int controlContainerHeight,
            TabModelSelector tabModelSelector, Activity activity) {
        mBottomSheet = bottomSheet;
        mBottomSheet.addObserver(mBottomSheetObserver);
        mTabModelSelector = tabModelSelector;

        Resources res = getContext().getResources();
        mDistanceBelowToolbarPx = controlContainerHeight
                + res.getDimensionPixelOffset(R.dimen.bottom_nav_space_from_toolbar);

        setOnNavigationItemSelectedListener(this);
        disableShiftingMode();

        mSnackbarManager = new SnackbarManager(
                activity, (ViewGroup) activity.findViewById(R.id.bottom_sheet_snackbar_container));
        mSnackbarManager.onStart();

        ApplicationStatus.registerStateListenerForActivity(new ActivityStateListener() {
            @Override
            public void onActivityStateChange(Activity activity, int newState) {
                if (newState == ActivityState.STARTED) mSnackbarManager.onStart();
                if (newState == ActivityState.STOPPED) mSnackbarManager.onStop();
            }
        }, activity);
    }

    /**
     * Initialize the default {@link BottomSheetContent}.
     */
    public void initializeDefaultContent() {
        if (mDefaultContentInitialized) return;
        showBottomSheetContent(R.id.action_home);
        mDefaultContentInitialized = true;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if (mSelectedItemId == item.getItemId()) return false;

        mSnackbarManager.dismissAllSnackbars();
        showBottomSheetContent(item.getItemId());
        return true;
    }

    // TODO(twellington): remove this once the support library is updated to allow disabling
    //                    shifting mode or determines shifting mode based on the width of the
    //                    child views.
    private void disableShiftingMode() {
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) getChildAt(0);
        try {
            Field shiftingMode = menuView.getClass().getDeclaredField("mShiftingMode");
            shiftingMode.setAccessible(true);
            shiftingMode.setBoolean(menuView, false);
            shiftingMode.setAccessible(false);
            for (int i = 0; i < menuView.getChildCount(); i++) {
                BottomNavigationItemView item = (BottomNavigationItemView) menuView.getChildAt(i);
                item.setShiftingMode(false);
                // Set the checked value so that the view will be updated.
                item.setChecked(item.getItemData().isChecked());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Do nothing if reflection fails.
        }
    }

    private BottomSheetContent getSheetContentForId(int navItemId) {
        BottomSheetContent content = mBottomSheetContents.get(navItemId);
        if (content != null) return content;

        if (navItemId == R.id.action_home) {
            content = new SuggestionsBottomSheetContent(
                    mTabModelSelector.getCurrentTab().getActivity(), mBottomSheet,
                    mTabModelSelector, mSnackbarManager);
        } else if (navItemId == R.id.action_downloads) {
            content = new DownloadSheetContent(mTabModelSelector.getCurrentTab().getActivity(),
                    mTabModelSelector.getCurrentModel().isIncognito(), mSnackbarManager);
        } else if (navItemId == R.id.action_bookmarks) {
            content = new BookmarkSheetContent(
                    mTabModelSelector.getCurrentTab().getActivity(), mSnackbarManager);
        } else if (navItemId == R.id.action_history) {
            content = new HistorySheetContent(
                    mTabModelSelector.getCurrentTab().getActivity(), mSnackbarManager);
        }
        mBottomSheetContents.put(navItemId, content);
        return content;
    }

    private void showBottomSheetContent(int navItemId) {
        // There are some bugs related to programatically selecting menu items that are fixed in
        // newer support library versions.
        // TODO(twellington): remove this after the support library is rolled.
        if (mSelectedItemId != 0) getMenu().findItem(mSelectedItemId).setChecked(false);
        mSelectedItemId = navItemId;
        getMenu().findItem(mSelectedItemId).setChecked(true);

        mBottomSheet.showContent(getSheetContentForId(mSelectedItemId));
    }

    /**
     * @param itemId The id of the MenuItem to select.
     */
    @VisibleForTesting
    public void selectItemForTests(int itemId) {
        // TODO(twellington): A #setSelectedItemId() method was added to the support library
        //                    recently. Replace this custom implementation with that method after
        //                    the support library is rolled.
        onNavigationItemSelected(getMenu().findItem(itemId));
    }
}
