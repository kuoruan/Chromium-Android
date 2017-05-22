// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.chromium.content.R;
import org.chromium.content.browser.SelectionPopupController;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Paste popup implementation based on floating ActionModes.
 */
@TargetApi(Build.VERSION_CODES.M)
public class FloatingPastePopupMenu implements PastePopupMenu {
    private static final int CONTENT_RECT_OFFSET_DIP = 15;
    private static final int SLOP_LENGTH_DIP = 10;

    private final View mParent;
    private final PastePopupMenuDelegate mDelegate;
    private final Context mContext;

    // Offset from the paste coordinates to provide the floating ActionMode.
    private final int mContentRectOffset;

    // Slack for ignoring small deltas in the paste popup position. The initial
    // position can change by a few pixels due to differences in how context
    // menu and selection coordinates are computed. Suppressing this small delta
    // avoids the floating ActionMode flicker when the popup is repositioned.
    private final int mSlopLengthSquared;

    private ActionMode mActionMode;
    private int mRawPositionX;
    private int mRawPositionY;

    public FloatingPastePopupMenu(Context context, View parent, PastePopupMenuDelegate delegate) {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

        mParent = parent;
        mDelegate = delegate;
        mContext = context;

        mContentRectOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                CONTENT_RECT_OFFSET_DIP, mContext.getResources().getDisplayMetrics());
        int slopLength = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SLOP_LENGTH_DIP, mContext.getResources().getDisplayMetrics());
        mSlopLengthSquared = slopLength * slopLength;
    }

    @Override
    public void show(int x, int y) {
        if (isShowing()) {
            int dx = mRawPositionX - x;
            int dy = mRawPositionY - y;
            if (dx * dx + dy * dy < mSlopLengthSquared) return;
        }

        mRawPositionX = x;
        mRawPositionY = y;
        if (mActionMode != null) {
            mActionMode.invalidateContentRect();
            return;
        }

        ensureActionMode();
    }

    @Override
    public void hide() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
    }

    @Override
    public boolean isShowing() {
        return mActionMode != null;
    }

    private void ensureActionMode() {
        if (mActionMode != null) return;

        ActionMode actionMode = mParent.startActionMode(
                new ActionModeCallback(), ActionMode.TYPE_FLOATING);
        if (actionMode != null) {
            // crbug.com/651706
            LGEmailActionModeWorkaround.runIfNecessary(mContext, actionMode);

            assert actionMode.getType() == ActionMode.TYPE_FLOATING;
            mActionMode = actionMode;
        }
    }

    private class ActionModeCallback extends ActionMode.Callback2 {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            createPasteMenu(mode, menu);
            return true;
        }

        private void createPasteMenu(ActionMode mode, Menu menu) {
            mode.setTitle(DeviceFormFactor.isTablet(mContext)
                    ? mContext.getString(R.string.actionbar_textselection_title) : null);
            mode.setSubtitle(null);
            SelectionPopupController.initializeMenu(mContext, mode, menu);
            if (!mDelegate.canPaste()) menu.removeItem(R.id.select_action_menu_paste);
            menu.removeItem(R.id.select_action_menu_select_all);
            menu.removeItem(R.id.select_action_menu_cut);
            menu.removeItem(R.id.select_action_menu_copy);
            menu.removeItem(R.id.select_action_menu_share);
            menu.removeItem(R.id.select_action_menu_web_search);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.select_action_menu_paste) {
                mDelegate.paste();
                mode.finish();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            // Use a rect that spans above and below the insertion point.
            // This avoids paste popup overlap with selection handles.
            outRect.set(mRawPositionX - mContentRectOffset, mRawPositionY - mContentRectOffset,
                    mRawPositionX + mContentRectOffset, mRawPositionY + mContentRectOffset);
        }
    };
}
