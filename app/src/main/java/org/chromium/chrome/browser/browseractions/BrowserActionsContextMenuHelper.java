// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browseractions;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.support.customtabs.browseractions.BrowserActionItem;
import android.support.customtabs.browseractions.BrowserActionsIntent;
import android.support.customtabs.browseractions.BrowserActionsIntent.BrowserActionsItemId;
import android.util.Pair;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnCreateContextMenuListener;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuItem;
import org.chromium.chrome.browser.contextmenu.ContextMenuItem;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.contextmenu.ContextMenuUi;
import org.chromium.chrome.browser.contextmenu.PlatformContextMenuUi;
import org.chromium.chrome.browser.contextmenu.ShareContextMenuItem;
import org.chromium.chrome.browser.contextmenu.TabularContextMenuUi;
import org.chromium.ui.base.WindowAndroid.OnCloseContextMenuListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A helper class that handles generating context menus for Browser Actions.
 */
public class BrowserActionsContextMenuHelper implements OnCreateContextMenuListener,
                                                        OnCloseContextMenuListener,
                                                        OnAttachStateChangeListener {
    /** Notified about events happening for Browser Actions tests. */
    public interface BrowserActionsTestDelegate {
        /** Called when menu is shown. */
        void onBrowserActionsMenuShown();

        /** Called when {@link BrowserActionActivity#finishNativeInitialization} is done. */
        void onFinishNativeInitialization();

        /** Called when Browser Actions start opening a tab in background */
        void onOpenTabInBackgroundStart();

        /** Called when Browser Actions start downloading a url */
        void onDownloadStart();

        /** Initializes data needed for testing. */
        void initialize(SparseArray<PendingIntent> customActions,
                List<Pair<Integer, List<ContextMenuItem>>> items,
                ProgressDialog progressDialog);
    }


    static final List<Integer> CUSTOM_BROWSER_ACTIONS_ID_GROUP =
            Arrays.asList(R.id.browser_actions_custom_item_one,
                    R.id.browser_actions_custom_item_two, R.id.browser_actions_custom_item_three,
                    R.id.browser_actions_custom_item_four, R.id.browser_actions_custom_item_five);

    private static final String TAG = "cr_BrowserActions";
    private static final boolean IS_NEW_UI_ENABLED = true;

    // Items list that could be included in the Browser Actions context menu for type {@code LINK}.
    private final List<? extends ContextMenuItem> mBrowserActionsLinkGroup;

    // Map each custom item's id with its PendingIntent action.
    private final SparseArray<PendingIntent> mCustomItemActionMap = new SparseArray<>();

    private final ContextMenuParams mCurrentContextMenuParams;
    private final BrowserActionsContextMenuItemDelegate mMenuItemDelegate;
    private final Activity mActivity;
    private final Callback<Integer> mItemSelectedCallback;
    private final Runnable mOnMenuShown;
    private final Runnable mOnMenuClosed;
    private final Runnable mOnMenuShownListener;
    private final Callback<Boolean> mOnShareClickedRunnable;
    private final PendingIntent mOnBrowserActionSelectedCallback;

    private final List<Pair<Integer, List<ContextMenuItem>>> mItems;

    private final ProgressDialog mProgressDialog;

    private BrowserActionsTestDelegate mTestDelegate;
    private int mPendingItemId;
    private boolean mIsNativeInitialized;

    public BrowserActionsContextMenuHelper(Activity activity, ContextMenuParams params,
            List<BrowserActionItem> customItems, String sourcePackageName,
            PendingIntent onBrowserActionSelectedCallback, final Runnable listener) {
        mActivity = activity;
        mCurrentContextMenuParams = params;
        mOnMenuShownListener = listener;
        mOnMenuShown = new Runnable() {
            @Override
            public void run() {
                mOnMenuShownListener.run();
                if (mTestDelegate != null) {
                    mTestDelegate.onBrowserActionsMenuShown();
                }
            }
        };
        mOnMenuClosed = new Runnable() {
            @Override
            public void run() {
                if (mPendingItemId == 0) {
                    mActivity.finish();
                }
            }
        };
        mItemSelectedCallback = new Callback<Integer>() {
            @Override
            public void onResult(Integer result) {
                onItemSelected(result);
            }
        };
        mOnShareClickedRunnable = new Callback<Boolean>() {
            @Override
            public void onResult(Boolean isShareLink) {
                mMenuItemDelegate.share(true, mCurrentContextMenuParams.getLinkUrl());
            }
        };
        ShareContextMenuItem shareItem = new ShareContextMenuItem(R.drawable.ic_share_white_24dp,
                R.string.browser_actions_share, R.id.browser_actions_share, true);
        shareItem.setCreatorPackageName(sourcePackageName);
        mBrowserActionsLinkGroup =
                Arrays.asList(ChromeContextMenuItem.BROWSER_ACTIONS_OPEN_IN_BACKGROUND,
                        ChromeContextMenuItem.BROWSER_ACTIONS_OPEN_IN_INCOGNITO_TAB,
                        ChromeContextMenuItem.BROWSER_ACTION_SAVE_LINK_AS,
                        ChromeContextMenuItem.BROWSER_ACTIONS_COPY_ADDRESS, shareItem);
        mMenuItemDelegate = new BrowserActionsContextMenuItemDelegate(mActivity, sourcePackageName);
        mOnBrowserActionSelectedCallback = onBrowserActionSelectedCallback;
        mProgressDialog = new ProgressDialog(mActivity);

        mItems = buildContextMenuItems(customItems);
    }

    /**
     * Sets the {@link BrowserActionsTestDelegate} for testing.
     * @param testDelegate The delegate used to notified Browser Actions events.
     */
    @VisibleForTesting
    void setTestDelegateForTesting(BrowserActionsTestDelegate testDelegate) {
        mTestDelegate = testDelegate;
        mTestDelegate.initialize(mCustomItemActionMap, mItems, mProgressDialog);
    }

    /**
     * Builds items for Browser Actions context menu.
     */
    private List<Pair<Integer, List<ContextMenuItem>>> buildContextMenuItems(
            List<BrowserActionItem> customItems) {
        List<Pair<Integer, List<ContextMenuItem>>> menuItems = new ArrayList<>();
        List<ContextMenuItem> items = new ArrayList<>();
        items.addAll(mBrowserActionsLinkGroup);
        addBrowserActionItems(items, customItems);

        menuItems.add(new Pair<>(R.string.contextmenu_link_title, items));
        return menuItems;
    }

    /**
     * Adds custom items to the context menu list and populates custom item action map.
     * @param items List of {@link ContextMenuItem} to display the context menu.
     * @param customItems List of {@link BrowserActionItem} for custom items.
     */
    private void addBrowserActionItems(
            List<ContextMenuItem> items, List<BrowserActionItem> customItems) {
        for (int i = 0; i < customItems.size() && i < BrowserActionsIntent.MAX_CUSTOM_ITEMS; i++) {
            items.add(new BrowserActionsCustomContextMenuItem(
                    CUSTOM_BROWSER_ACTIONS_ID_GROUP.get(i), customItems.get(i)));
            mCustomItemActionMap.put(
                    CUSTOM_BROWSER_ACTIONS_ID_GROUP.get(i), customItems.get(i).getAction());
        }
    }

    boolean onItemSelected(int itemId) {
        if (itemId == R.id.browser_actions_open_in_background) {
            if (mIsNativeInitialized) {
                handleOpenInBackground();
            } else {
                mPendingItemId = itemId;
                waitNativeInitialized();
            }
        } else if (itemId == R.id.browser_actions_open_in_incognito_tab) {
            mMenuItemDelegate.onOpenInIncognitoTab(mCurrentContextMenuParams.getLinkUrl());
            notifyBrowserActionSelected(BrowserActionsIntent.ITEM_OPEN_IN_INCOGNITO);
        } else if (itemId == R.id.browser_actions_save_link_as) {
            if (mIsNativeInitialized) {
                handleDownload();
            } else {
                mPendingItemId = itemId;
                waitNativeInitialized();
            }
        } else if (itemId == R.id.browser_actions_copy_address) {
            mMenuItemDelegate.onSaveToClipboard(mCurrentContextMenuParams.getLinkUrl());
            notifyBrowserActionSelected(BrowserActionsIntent.ITEM_COPY);
        } else if (itemId == R.id.browser_actions_share) {
            mMenuItemDelegate.share(false, mCurrentContextMenuParams.getLinkUrl());
            notifyBrowserActionSelected(BrowserActionsIntent.ITEM_SHARE);
        } else if (mCustomItemActionMap.indexOfKey(itemId) >= 0) {
            mMenuItemDelegate.onCustomItemSelected(mCustomItemActionMap.get(itemId));
        }
        return true;
    }

    private void notifyBrowserActionSelected(@BrowserActionsItemId int menuId) {
        if (mOnBrowserActionSelectedCallback == null) return;
        Intent additionalData = new Intent();
        additionalData.setData(Uri.parse(String.valueOf(menuId)));
        try {
            mOnBrowserActionSelectedCallback.send(mActivity, 0, additionalData, null, null);
        } catch (CanceledException e) {
            Log.e(TAG, "Browser Actions failed to send default items' pending intent.");
        }
    }

    /**
     * Display a progress dialog to wait for native libraries initialized.
     */
    private void waitNativeInitialized() {
        mProgressDialog.setMessage(
                mActivity.getString(R.string.browser_actions_loading_native_message));
        mProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    /**
     * Displays the Browser Actions context menu.
     * @param view The view to show the context menu if old UI is used.
     */
    public void displayBrowserActionsMenu(final View view) {
        if (IS_NEW_UI_ENABLED) {
            ContextMenuUi menuUi = new TabularContextMenuUi(mOnShareClickedRunnable);
            menuUi.displayMenu(mActivity, mCurrentContextMenuParams, mItems, mItemSelectedCallback,
                    mOnMenuShown, mOnMenuClosed);
        } else {
            view.setOnCreateContextMenuListener(BrowserActionsContextMenuHelper.this);
            assert view.getWindowToken() == null;
            view.addOnAttachStateChangeListener(this);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        ContextMenuUi menuUi = new PlatformContextMenuUi(menu);
        menuUi.displayMenu(mActivity, mCurrentContextMenuParams, mItems, mItemSelectedCallback,
                mOnMenuShown, mOnMenuClosed);
    }

    @Override
    public void onContextMenuClosed() {
        mOnMenuClosed.run();
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        if (view.showContextMenu()) {
            mOnMenuShown.run();
        }
    }

    @Override
    public void onViewDetachedFromWindow(View v) {}

    /**
     * Finishes all pending actions which requires Chrome native libraries.
     */
    public void onNativeInitialized() {
        mIsNativeInitialized = true;
        if (mTestDelegate != null) {
            mTestDelegate.onFinishNativeInitialization();
        }
        if (mPendingItemId != 0) {
            dismissProgressDialog();
            onItemSelected(mPendingItemId);
            mPendingItemId = 0;
            mActivity.finish();
        }
    }

    private void handleOpenInBackground() {
        mMenuItemDelegate.onOpenInBackground(mCurrentContextMenuParams.getLinkUrl());
        if (mTestDelegate != null) {
            mTestDelegate.onOpenTabInBackgroundStart();
        }
        notifyBrowserActionSelected(BrowserActionsIntent.ITEM_OPEN_IN_NEW_TAB);
    }

    private void handleDownload() {
        mMenuItemDelegate.startDownload(mCurrentContextMenuParams.getLinkUrl());
        if (mTestDelegate != null) {
            mTestDelegate.onDownloadStart();
        }
        notifyBrowserActionSelected(BrowserActionsIntent.ITEM_DOWNLOAD);
    }
}
