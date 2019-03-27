// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.native_page.ContextMenuManager;
import org.chromium.chrome.browser.native_page.NativePageNavigationDelegate;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.widget.LoadingView;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.ui.modelutil.ForwardingListObservable;
import org.chromium.ui.modelutil.ListObservable.ListObserver;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyObservable;
import org.chromium.ui.modelutil.RecyclerViewAdapter;
import org.chromium.ui.widget.ChromeBulletSpan;
import org.chromium.ui.widget.TextViewWithLeading;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Recycler view adapter delegate for a model containing a list of category cards and an error code.
 */
class CategoryCardAdapter extends ForwardingListObservable<Void>
        implements RecyclerViewAdapter
                           .Delegate<CategoryCardViewHolderFactory.CategoryCardViewHolder, Void>,
                   PropertyObservable.PropertyObserver<PropertyKey>, ListObserver<Void> {
    @IntDef({ViewType.CATEGORY, ViewType.LOADING, ViewType.ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int CATEGORY = 0;
        int LOADING = 1;
        int ERROR = 2;
    }

    private final RoundedIconGenerator mIconGenerator;
    private final ContextMenuManager mContextMenuManager;
    private final NativePageNavigationDelegate mNavDelegate;
    private final Profile mProfile;

    private LinearLayoutManager mLayoutManager;
    private PropertyModel mCategoryModel;

    public CategoryCardAdapter(PropertyModel model, LinearLayoutManager layoutManager,
            RoundedIconGenerator iconGenerator, ContextMenuManager contextMenuManager,
            NativePageNavigationDelegate navDelegate, Profile profile) {
        mCategoryModel = model;
        mCategoryModel.addObserver(this);
        mCategoryModel.get(ExploreSitesPage.CATEGORY_LIST_KEY).addObserver(this);

        mLayoutManager = layoutManager;
        mIconGenerator = iconGenerator;
        mContextMenuManager = contextMenuManager;
        mNavDelegate = navDelegate;
        mProfile = profile;
    }

    @Override
    public int getItemCount() {
        // If not loaded, return 1 error/loading section
        if (mCategoryModel.get(ExploreSitesPage.STATUS_KEY)
                != ExploreSitesPage.CatalogLoadingState.SUCCESS) {
            return 1;
        }
        return mCategoryModel.get(ExploreSitesPage.CATEGORY_LIST_KEY).size();
    }

    @Override
    @ViewType
    public int getItemViewType(int position) {
        switch (mCategoryModel.get(ExploreSitesPage.STATUS_KEY)) {
            case ExploreSitesPage.CatalogLoadingState.ERROR:
                return ViewType.ERROR;
            case ExploreSitesPage.CatalogLoadingState.LOADING: // fall-through
            case ExploreSitesPage.CatalogLoadingState.LOADING_NET:
                return ViewType.LOADING;
            case ExploreSitesPage.CatalogLoadingState.SUCCESS:
                return ViewType.CATEGORY;
            default:
                assert(false);
                return ViewType.ERROR;
        }
    }

    @Override
    public void onBindViewHolder(CategoryCardViewHolderFactory.CategoryCardViewHolder holder,
            int position, @Nullable Void payload) {
        if (holder.getItemViewType() == ViewType.CATEGORY) {
            ExploreSitesCategoryCardView view = (ExploreSitesCategoryCardView) holder.itemView;
            view.setCategory(mCategoryModel.get(ExploreSitesPage.CATEGORY_LIST_KEY).get(position),
                    position, mIconGenerator, mContextMenuManager, mNavDelegate, mProfile);
        } else if (holder.getItemViewType() == ViewType.LOADING) {
            // Start spinner.
            LoadingView spinner = holder.itemView.findViewById(R.id.loading);
            spinner.showLoadingUI();
        } else if (holder.getItemViewType() == ViewType.ERROR) {
            TextViewWithLeading textView = holder.itemView.findViewById(R.id.error_view_next_steps);
            Context context = textView.getContext();
            SpannableStringBuilder spannableString = new SpannableStringBuilder();
            SpannableString tip1 = new SpannableString(
                    context.getString(R.string.explore_sites_loading_error_next_steps_reload));
            SpannableString tip2 = new SpannableString(context.getString(
                    R.string.explore_sites_loading_error_next_steps_check_connection));
            tip1.setSpan(new ChromeBulletSpan(context), 0, tip1.length(), 0);
            tip2.setSpan(new ChromeBulletSpan(context), 0, tip2.length(), 0);
            spannableString.append(tip1).append("\n").append(tip2);
            textView.setText(spannableString);
        }
    }

    @Override
    public void onPropertyChanged(
            PropertyObservable<PropertyKey> source, @Nullable PropertyKey key) {
        if (key == ExploreSitesPage.STATUS_KEY) {
            int status = mCategoryModel.get(ExploreSitesPage.STATUS_KEY);
            if (status != ExploreSitesPage.CatalogLoadingState.SUCCESS) {
                notifyItemChanged(0);
            } else {
                // If it's success, we must "remove" the loading view.
                // Loading the categories is taken care of by the list observer.
                notifyItemRangeRemoved(/* index= */ 0, /* count= */ 1);
            }
        }
        if (key == ExploreSitesPage.SCROLL_TO_CATEGORY_KEY) {
            // NOTE: LinearLayoutManager#scrollToPosition has strange behavior if the scrolling
            // happens between the time that the adapter has an item and the time that the view has
            // actually added its children.  In that case, the LinearLayoutManager will only scroll
            // the requested position /into view/.
            //
            // To work around that, we use LinearLayoutManager#scrollToPositionWithOffset, and set
            // the offset to 0.  This allows us to always scroll the desired view to the top of the
            // screen.
            mLayoutManager.scrollToPositionWithOffset(
                    mCategoryModel.get(ExploreSitesPage.SCROLL_TO_CATEGORY_KEY), 0);
        }
    }
}
