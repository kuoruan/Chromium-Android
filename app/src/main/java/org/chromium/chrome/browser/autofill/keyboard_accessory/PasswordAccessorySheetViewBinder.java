// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.text.method.PasswordTransformationMethod;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece;
import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabViewBinder.ElementViewHolder;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.FooterCommand;
import org.chromium.ui.modelutil.ListModel;

/**
 * This stateless class provides methods to bind the items in a {@link ListModel <Item>}
 * to the {@link RecyclerView} used as view of the Password accessory sheet component.
 */
class PasswordAccessorySheetViewBinder {
    static ElementViewHolder create(ViewGroup parent, @AccessorySheetDataPiece.Type int viewType) {
        switch (viewType) {
            case AccessorySheetDataPiece.Type.TITLE:
                return new PasswordsTitleViewHolder(parent);
            case AccessorySheetDataPiece.Type.PASSWORD_INFO:
                return new PasswordsInfoViewHolder(parent);
            case AccessorySheetDataPiece.Type.FOOTER_COMMAND:
                return new FooterCommandViewHolder(parent);
        }
        assert false : "Unhandled type of data piece: " + viewType;
        return null;
    }

    /**
     * Holds a the TextView with the title of the sheet and a divider for the accessory bar.
     */
    static class PasswordsTitleViewHolder extends ElementViewHolder<String, LinearLayout> {
        PasswordsTitleViewHolder(ViewGroup parent) {
            super(parent, R.layout.password_accessory_sheet_label);
        }

        @Override
        protected void bind(String displayText, LinearLayout view) {
            TextView titleView = view.findViewById(R.id.tab_title);
            titleView.setText(displayText);
            titleView.setContentDescription(displayText);
        }
    }

    /**
     * Holds a TextView that represents a bottom command and is separated to the top by a divider.
     */
    static class FooterCommandViewHolder extends ElementViewHolder<FooterCommand, LinearLayout> {
        FooterCommandViewHolder(ViewGroup parent) {
            super(parent, R.layout.password_accessory_sheet_legacy_option);
        }

        @Override
        protected void bind(FooterCommand footerCommand, LinearLayout layout) {
            TextView view = layout.findViewById(R.id.footer_text);
            view.setText(footerCommand.getDisplayText());
            view.setContentDescription(footerCommand.getDisplayText());
            view.setOnClickListener(v -> footerCommand.execute());
            view.setClickable(true);
        }
    }

    /**
     * Holds a layout for a username and a password with a small icon.
     */
    static class PasswordsInfoViewHolder
            extends ElementViewHolder<KeyboardAccessoryData.UserInfo, LinearLayout> {
        private final int mPadding;
        private final int mIconSize;

        PasswordsInfoViewHolder(ViewGroup parent) {
            super(parent, R.layout.keyboard_accessory_sheet_tab_legacy_password_info);
            mPadding = itemView.getContext().getResources().getDimensionPixelSize(
                    R.dimen.keyboard_accessory_suggestion_padding);
            mIconSize = itemView.getContext().getResources().getDimensionPixelSize(
                    R.dimen.keyboard_accessory_suggestion_icon_size);
        }

        @Override
        protected void bind(KeyboardAccessoryData.UserInfo info, LinearLayout layout) {
            TextView username = layout.findViewById(R.id.suggestion_text);
            TextView password = layout.findViewById(R.id.password_text);
            bindTextView(username, info.getFields().get(0));
            bindTextView(password, info.getFields().get(1));

            // Set the default icon for username, then try to get a better one.
            setIconForBitmap(username, null);
            if (info.getFaviconProvider() != null) {
                info.getFaviconProvider().fetchFavicon(
                        mIconSize, icon -> setIconForBitmap(username, icon));
            }
            username.setPadding(mPadding, 0, mPadding, 0);
            // Passwords have no icon, so increase the offset.
            password.setPadding(2 * mPadding + mIconSize, 0, mPadding, 0);
        }

        private void bindTextView(TextView text, KeyboardAccessoryData.UserInfo.Field field) {
            text.setTransformationMethod(
                    field.isObfuscated() ? new PasswordTransformationMethod() : null);
            text.setText(field.getDisplayText());
            text.setContentDescription(field.getA11yDescription());
            text.setOnClickListener(!field.isSelectable() ? null : src -> field.triggerSelection());
            text.setClickable(true); // Ensures that "disabled" is announced.
            text.setEnabled(field.isSelectable());
            text.setBackground(getBackgroundDrawable(field.isSelectable()));
        }

        private @Nullable Drawable getBackgroundDrawable(boolean selectable) {
            if (!selectable) return null;
            TypedArray a = itemView.getContext().obtainStyledAttributes(
                    new int[] {R.attr.selectableItemBackground});
            Drawable suggestionBackground = a.getDrawable(0);
            a.recycle();
            return suggestionBackground;
        }

        private void setIconForBitmap(TextView text, @Nullable Bitmap favicon) {
            Drawable icon;
            if (favicon == null) {
                icon = AppCompatResources.getDrawable(
                        itemView.getContext(), R.drawable.ic_globe_36dp);
            } else {
                icon = new BitmapDrawable(itemView.getContext().getResources(), favicon);
            }
            if (icon != null) { // AppCompatResources.getDrawable is @Nullable.
                icon.setBounds(0, 0, mIconSize, mIconSize);
            }
            text.setCompoundDrawablePadding(mPadding);
            ApiCompatibilityUtils.setCompoundDrawablesRelative(text, icon, null, null, null);
        }
    }

    public static void initializeView(RecyclerView view, AccessorySheetTabModel model) {
        view.setAdapter(PasswordAccessorySheetCoordinator.createAdapter(model));
    }
}
