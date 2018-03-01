// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.languages;

import android.content.Context;
import android.preference.Preference;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.widget.ListMenuButton;
import org.chromium.chrome.browser.widget.ListMenuButton.Item;
import org.chromium.chrome.browser.widget.TintedImageView;

import java.util.List;

/**
 * A preference that displays the current accept language list.
 */
public class LanguageListPreference extends Preference {
    public LanguageListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        RecyclerView listView = (RecyclerView) view.findViewById(R.id.language_list);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Get accepted language list from native.
        List<String> languageList = PrefServiceBridge.getInstance().getChromeLanguageList();

        listView.setAdapter(new LanguageListAdapter(languageList));
    }

    // TODO(crbug/783049): Pull all the inner classes below out and make the item in the list
    // drag-able.
    private static class LanguageListAdapter extends RecyclerView.Adapter<LanguageItemViewHolder> {
        private List<String> mLanguageList;

        LanguageListAdapter(List<String> languageList) {
            mLanguageList = languageList;
        }

        @Override
        public LanguageItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View languageItem = LayoutInflater.from(parent.getContext())
                                        .inflate(R.layout.accept_languages_item, parent, false);
            return new LanguageItemViewHolder(languageItem);
        }

        @Override
        public void onBindViewHolder(LanguageItemViewHolder holder, int position) {
            String code = mLanguageList.get(position);
            // TODO(crbug/783049): Get language name by code and Locale from LocalizationUtils.
            // displayLocale = context.getResources().getConfiguration().locale;
            holder.setupUI(code);
        }

        @Override
        public int getItemCount() {
            return mLanguageList.size();
        }
    }

    private static class LanguageItemViewHolder
            extends RecyclerView.ViewHolder implements ListMenuButton.Delegate {
        private static final int OFFER_TRANSLATE_POSITION = 0;

        // Menu items definition.
        private final Item[] mItems;

        private Context mContext;
        private View mRow;
        private TextView mTitle;
        private TintedImageView mStartIcon;
        private ListMenuButton mMoreButton;

        LanguageItemViewHolder(View view) {
            super(view);
            mContext = view.getContext();
            mRow = view;
            mTitle = (TextView) view.findViewById(R.id.title);
            mStartIcon = (TintedImageView) view.findViewById(R.id.icon_view);
            mMoreButton = (ListMenuButton) view.findViewById(R.id.more);

            // TODO(crbug/783049): Set "enabled" based on whether Chrome supports to translate this
            // language.
            mItems = new Item[] {
                    new Item(mContext, R.string.languages_item_option_offer_to_translate,
                            R.drawable.ic_check_googblue_24dp, true),
                    new Item(mContext, R.string.remove, true)};
        }

        private void setupUI(String title) {
            mTitle.setText(title);
            mStartIcon.setImageResource(R.drawable.ic_drag_handle_grey600_24dp);
            mMoreButton.setDelegate(this);
        }

        // ListMenuButton.Delegate implementation.
        @Override
        public Item[] getItems() {
            return mItems;
        }

        @Override
        public void onItemSelected(Item item) {
            if (item.getTextId() == R.string.languages_item_option_offer_to_translate) {
                // TODO(crbug/783049): Handle "offer to translate" event.
            } else if (item.getTextId() == R.string.remove) {
                // TODO(crbug/783049): Handle "remove" event.
            }
        }
    }
}
