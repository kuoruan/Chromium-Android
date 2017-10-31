/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.chromium.chrome.browser.widget.bottomsheet.base;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.support.design.R;
import android.support.v4.util.Pools;
import android.support.v4.view.ViewCompat;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuItemImpl;
import android.support.v7.view.menu.MenuView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Forked from android.support.design.internal.BottomNavigationMenuView.
 */
public class BottomNavigationMenuView extends LinearLayout implements MenuView {
    private final int mInactiveItemMaxWidth;
    private final int mInactiveItemMinWidth;
    private final int mActiveItemMaxWidth;
    private final int mItemHeight;
    private final OnClickListener mOnClickListener;
    private static final Pools.Pool<BottomNavigationItemView> sItemPool =
            new Pools.SynchronizedPool<>(5);

    private BottomNavigationItemView[] mButtons;
    private int mActiveButton = 0;
    private ColorStateList mItemIconTint;
    private ColorStateList mItemTextColor;
    private int mItemBackgroundRes;
    private int mMenuWidth;

    private BottomNavigationPresenter mPresenter;
    private MenuBuilder mMenu;

    public BottomNavigationMenuView(Context context) {
        this(context, null);
    }

    public BottomNavigationMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final Resources res = getResources();
        mInactiveItemMaxWidth =
                res.getDimensionPixelSize(R.dimen.design_bottom_navigation_item_max_width);
        mInactiveItemMinWidth =
                res.getDimensionPixelSize(R.dimen.design_bottom_navigation_item_min_width);
        mActiveItemMaxWidth =
                res.getDimensionPixelSize(R.dimen.design_bottom_navigation_active_item_max_width);
        mItemHeight = res.getDimensionPixelSize(R.dimen.design_bottom_navigation_height);

        mOnClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                BottomNavigationItemView itemView;
                try {
                    itemView = (BottomNavigationItemView) v;
                } catch (ClassCastException e) {
                    return;
                }
                final int itemPosition = itemView.getItemPosition();
                if (!mMenu.performItemAction(itemView.getItemData(), mPresenter, 0)) {
                    activateNewButton(itemPosition);
                }
            }
        };
    }

    @Override
    public void initialize(MenuBuilder menu) {
        mMenu = menu;
    }

    @Override
    public int getWindowAnimations() {
        return 0;
    }

    /**
     * Sets the tint which is applied to the menu items' icons.
     *
     * @param tint the tint to apply
     */
    public void setIconTintList(ColorStateList tint) {
        mItemIconTint = tint;
        if (mButtons == null) return;
        for (BottomNavigationItemView item : mButtons) {
            item.setIconTintList(tint);
        }
    }

    /**
     * Returns the tint which is applied to menu items' icons.
     *
     * @return the ColorStateList that is used to tint menu items' icons
     */
    @Nullable
    public ColorStateList getIconTintList() {
        return mItemIconTint;
    }

    /**
     * Sets the text color to be used on menu items.
     *
     * @param color the ColorStateList used for menu items' text.
     */
    public void setItemTextColor(ColorStateList color) {
        mItemTextColor = color;
        if (mButtons == null) return;
        for (BottomNavigationItemView item : mButtons) {
            item.setTextColor(color);
        }
    }

    /**
     * Returns the text color used on menu items.
     *
     * @return the ColorStateList used for menu items' text
     */
    public ColorStateList getItemTextColor() {
        return mItemTextColor;
    }

    /**
     * Sets the resource ID to be used for item background.
     *
     * @param background the resource ID of the background
     */
    public void setItemBackgroundRes(int background) {
        mItemBackgroundRes = background;
        if (mButtons == null) return;
        for (BottomNavigationItemView item : mButtons) {
            item.setItemBackground(background);
        }
    }

    /**
     * Returns the resource ID for the background of the menu items.
     *
     * @return the resource ID for the background
     */
    public int getItemBackgroundRes() {
        return mItemBackgroundRes;
    }

    public void setPresenter(BottomNavigationPresenter presenter) {
        mPresenter = presenter;
    }

    public void buildMenuView() {
        if (mButtons != null) {
            for (BottomNavigationItemView item : mButtons) {
                sItemPool.release(item);
            }
        }
        removeAllViews();
        if (mMenu.size() == 0) {
            return;
        }
        mButtons = new BottomNavigationItemView[mMenu.size()];
        for (int i = 0; i < mMenu.size(); i++) {
            mPresenter.setUpdateSuspended(true);
            mMenu.getItem(i).setCheckable(true);
            mPresenter.setUpdateSuspended(false);
            BottomNavigationItemView child = getNewItem();
            mButtons[i] = child;
            child.setIconTintList(mItemIconTint);
            child.setTextColor(mItemTextColor);
            child.setItemBackground(mItemBackgroundRes);
            child.initialize((MenuItemImpl) mMenu.getItem(i), 0);
            child.setItemPosition(i);
            child.setOnClickListener(mOnClickListener);
            addView(child);
        }
        mActiveButton = Math.min(mMenu.size() - 1, mActiveButton);
        mMenu.getItem(mActiveButton).setChecked(true);
    }

    public void updateMenuView() {
        final int menuSize = mMenu.size();
        if (menuSize != mButtons.length) {
            // The size has changed. Rebuild menu view from scratch.
            buildMenuView();
            return;
        }
        for (int i = 0; i < menuSize; i++) {
            mPresenter.setUpdateSuspended(true);
            if (mMenu.getItem(i).isChecked()) {
                mActiveButton = i;
            }
            mButtons[i].initialize((MenuItemImpl) mMenu.getItem(i), 0);
            mPresenter.setUpdateSuspended(false);
        }
    }

    private void activateNewButton(int newButton) {
        if (mActiveButton == newButton) return;

        mMenu.getItem(newButton).setChecked(true);

        mActiveButton = newButton;
    }

    private BottomNavigationItemView getNewItem() {
        BottomNavigationItemView item = sItemPool.acquire();
        if (item == null) {
            item = new BottomNavigationItemView(getContext());
        }
        return item;
    }

    /**
     * Spaces menu items apart based on the minimum width of the bottom navigation menu, so the
     * items are equally spaced in landscape and portrait mode.
     *
     * @param layoutWidth Width of the navigation menu's container.
     * @param layoutHeight Height of the navigation menu's container.
     */
    public void updateMenuItemSpacingForMinWidth(int layoutWidth, int layoutHeight) {
        if (mButtons.length == 0) return;

        int menuWidth = Math.min(layoutWidth, layoutHeight);
        if (menuWidth != mMenuWidth) {
            mMenuWidth = menuWidth;
            int itemWidth = menuWidth / mButtons.length;
            for (int i = 0; i < mButtons.length; i++) {
                BottomNavigationItemView item = mButtons[i];

                LayoutParams layoutParams = (LayoutParams) item.getLayoutParams();
                layoutParams.width = itemWidth;
                item.setLayoutParams(layoutParams);
            }
        }
    }
}
