// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar.translate;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.util.List;

/**
 * A Helper class for managing the Translate Overflow Menu.
 */
public class TranslateMenuHelper implements AdapterView.OnItemClickListener {
    private static final String EMPTY_STRING = "";
    private final TranslateMenuItemListener mItemListener;
    private ContextThemeWrapper mWrapper;
    private View mAnchorView;
    private TranslateMenuAdapter mAdapter;
    private ListPopupWindow mPopup;

    /**
     * Add a divider menu item to list.
     * @param list The list of items to show in the menu.
     */
    public static void addDivider(List<TranslateMenuElement> list) {
        list.add(new TranslateMenuElement(
                EMPTY_STRING, EMPTY_STRING, TranslateMenuAdapter.MENU_DIVIDER));
    }

    /**
     * Add an overflow menu item to list.
     * @param title Title of the menu item
     * @param code  Code of the menu item
     * @param list  The list of items to show in the menu.
     */
    public static void addOverflowMenuItem(
            String title, String code, List<TranslateMenuElement> list) {
        list.add(new TranslateMenuElement(title, code, TranslateMenuAdapter.OVERFLOW_MENU_ITEM));
    }

    /**
     * Add a source language menu item to list.
     * @param title Title of the language
     * @param code  ISO code of the language
     * @param list  The list of items to show in the menu.
     */
    public static void addSourceLanguageMenuItem(
            String title, String code, List<TranslateMenuElement> list) {
        list.add(new TranslateMenuElement(
                title, code, TranslateMenuAdapter.SOURCE_LANGUAGE_MENU_ITEM));
    }

    /**
     * Add a target language menu item to list.
     * @param title Title of the language
     * @param code  ISO code of the language
     * @param list  The list of items to show in the menu.
     */
    public static void addTargetLanguageMenuItem(
            String title, String code, List<TranslateMenuElement> list) {
        list.add(new TranslateMenuElement(
                title, code, TranslateMenuAdapter.TARGET_LANGUAGE_MENU_ITEM));
    }

    /**
     * Interface for receiving the click event of menu item.
     */
    public interface TranslateMenuItemListener {
        // return true if the menu is dismissed after clicking on the item;  return false otherwise.
        // (when clicking on 'More languages' or 'Page not in this language', we show the language
        // menu by keeping the menu but replace the items inside.)
        boolean onTranslateMenuItemClicked(String code, int type);
    }

    public TranslateMenuHelper(
            Context context, View anchorView, TranslateMenuItemListener itemListener) {
        mWrapper = new ContextThemeWrapper(context, R.style.OverflowMenuTheme);
        mAnchorView = anchorView;
        mItemListener = itemListener;
    }

    /**
     * Show the overflow menu.
     * @param list List of menuitems.
     */
    public void show(List<TranslateMenuElement> list) {
        if (mPopup == null) {
            mPopup = new ListPopupWindow(mWrapper, null, android.R.attr.popupMenuStyle);
            mPopup.setModal(true);
            mPopup.setAnchorView(mAnchorView);
            mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

            // Need to explicitly set the background here.  Relying on it being set in the style
            // caused an incorrectly drawn background.
            mPopup.setBackgroundDrawable(
                    ContextCompat.getDrawable(mWrapper, R.drawable.edge_menu_bg));

            int popupWidth = mWrapper.getResources().getDimensionPixelSize(
                    R.dimen.infobar_translate_menu_width);

            // TODO (martiw) make the width dynamic to handle longer items.
            mPopup.setWidth(popupWidth);

            mAdapter = new TranslateMenuAdapter(
                    mWrapper, R.id.menu_item_text, list, LayoutInflater.from(mWrapper));
            mPopup.setAdapter(mAdapter);

            mPopup.setOnItemClickListener(this);
        } else {
            mAdapter.clear();
            mAdapter.addAll(list);
        }

        if (!mPopup.isShowing()) {
            mPopup.show();
            mPopup.getListView().setItemsCanFocus(true);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mItemListener.onTranslateMenuItemClicked(
                    mAdapter.getItem(position).getCode(), mAdapter.getItemViewType(position))) {
            dismiss();
        }
    }

    /**
     * Dismisses the app menu.
     */
    void dismiss() {
        if (isShowing()) {
            mPopup.dismiss();
        }
    }

    /**
     * @return Whether the app menu is currently showing.
     */
    boolean isShowing() {
        if (mPopup == null) {
            return false;
        }
        return mPopup.isShowing();
    }

    /**
     * The provides the views of the menu items and dividers.
     */
    public static final class TranslateMenuAdapter extends ArrayAdapter<TranslateMenuElement> {
        public static final int MENU_DIVIDER = 0;
        public static final int OVERFLOW_MENU_ITEM = 1;
        public static final int SOURCE_LANGUAGE_MENU_ITEM = 2;
        public static final int TARGET_LANGUAGE_MENU_ITEM = 3;
        // TODO(martiw) create OVERFLOW_MENU_ITEM_WITH_CHECKBOX_CHECKED and
        // OVERFLOW_MENU_ITEM_WITH_CHECKBOX_UNCHECKED for "Always Translate Language"

        LayoutInflater mInflater;

        public TranslateMenuAdapter(Context context, int textViewResourceId,
                List<TranslateMenuElement> items, LayoutInflater inflater) {
            super(context, textViewResourceId, items);
            mInflater = inflater;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getType();
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != MENU_DIVIDER;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            switch (getItemViewType(position)) {
                case MENU_DIVIDER:
                    convertView = mInflater.inflate(R.layout.translate_menu_divider, parent, false);
                    break;
                case OVERFLOW_MENU_ITEM:
                case SOURCE_LANGUAGE_MENU_ITEM:
                case TARGET_LANGUAGE_MENU_ITEM:
                    convertView = mInflater.inflate(R.layout.translate_menu_item, parent, false);
                    ((TextView) convertView.findViewById(R.id.menu_item_text))
                            .setText(getItem(position).toString());
                    break;
                // TODO(martiw) create the layout for OVERFLOW_MENU_ITEM_WITH_CHECKBOX_CHECKED and
                // OVERFLOW_MENU_ITEM_WITH_CHECKBOX_UNCHECKED for "Always Translate Language"
                default:
                    assert false : "Unexpected MenuItem type";
            }
            return convertView;
        }
    }

    /**
     * The element that goes inside the menu.
     */
    public static final class TranslateMenuElement {
        private final String mTitle;
        private final String mCode;
        private final int mType;

        public TranslateMenuElement(String title, String code, int type) {
            mTitle = title;
            mCode = code;
            mType = type;
        }

        public String getCode() {
            return mCode;
        }

        public int getType() {
            return mType;
        }

        /**
         * This is the text displayed in the menu item.
         */
        public String toString() {
            return mTitle;
        }
    }
}
