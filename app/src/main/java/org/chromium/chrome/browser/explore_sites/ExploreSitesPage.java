// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.native_page.BasicNativePage;
import org.chromium.chrome.browser.native_page.ContextMenuManager;
import org.chromium.chrome.browser.native_page.NativePageHost;
import org.chromium.chrome.browser.native_page.NativePageNavigationDelegateImpl;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.RecyclerViewAdapter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Provides functionality when the user interacts with the explore sites page.
 */
public class ExploreSitesPage extends BasicNativePage {
    private static final String TAG = "ExploreSitesPage";
    private static final String CONTEXT_MENU_USER_ACTION_PREFIX = "ExploreSites";
    private static final int INITIAL_SCROLL_POSITION = 3;
    private static final int INITIAL_SCROLL_POSITION_PERSONALIZED = 0;
    private static final String NAVIGATION_ENTRY_SCROLL_POSITION_KEY =
            "ExploreSitesPageScrollPosition";
    static final PropertyModel.WritableIntPropertyKey STATUS_KEY =
            new PropertyModel.WritableIntPropertyKey();
    static final PropertyModel.WritableIntPropertyKey SCROLL_TO_CATEGORY_KEY =
            new PropertyModel.WritableIntPropertyKey();
    static final PropertyModel
            .ReadableObjectPropertyKey<ListModel<ExploreSitesCategory>> CATEGORY_LIST_KEY =
            new PropertyModel.ReadableObjectPropertyKey<>();

    @IntDef({CatalogLoadingState.LOADING, CatalogLoadingState.SUCCESS, CatalogLoadingState.ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CatalogLoadingState {
        int LOADING = 1; // Loading catalog info from disk.
        int SUCCESS = 2;
        int ERROR = 3; // Error retrieving catalog resources from internet.
        int LOADING_NET = 4; // Retrieving catalog resources from internet.
    }

    private NativePageHost mHost;
    private Tab mTab;
    private TabObserver mTabObserver;
    private Profile mProfile;
    private ViewGroup mView;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private String mTitle;
    private PropertyModel mModel;
    private ContextMenuManager mContextMenuManager;
    private String mNavFragment;
    private boolean mHasFetchedNetworkCatalog;
    private boolean mIsLoaded;
    private int mInitialScrollPosition;

    /**
     * Create a new instance of the explore sites page.
     */
    public ExploreSitesPage(ChromeActivity activity, NativePageHost host) {
        super(activity, host);
    }

    @Override
    protected void initialize(ChromeActivity activity, final NativePageHost host) {
        mHost = host;
        mTab = mHost.getActiveTab();

        mTitle = activity.getString(R.string.explore_sites_title);
        mView = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.explore_sites_page_layout, null);
        mProfile = mHost.getActiveTab().getProfile();
        mHasFetchedNetworkCatalog = false;

        mModel = new PropertyModel.Builder(STATUS_KEY, SCROLL_TO_CATEGORY_KEY, CATEGORY_LIST_KEY)
                         .with(CATEGORY_LIST_KEY, new ListModel<ExploreSitesCategory>())
                         .with(STATUS_KEY, CatalogLoadingState.LOADING)
                         .build();

        Context context = mView.getContext();
        mLayoutManager = new LinearLayoutManager(context);
        int iconSizePx = context.getResources().getDimensionPixelSize(R.dimen.tile_view_icon_size);
        RoundedIconGenerator iconGenerator = new RoundedIconGenerator(iconSizePx, iconSizePx,
                iconSizePx / 2,
                ApiCompatibilityUtils.getColor(
                        context.getResources(), R.color.default_favicon_background_color),
                context.getResources().getDimensionPixelSize(R.dimen.tile_view_icon_text_size));

        NativePageNavigationDelegateImpl navDelegate = new NativePageNavigationDelegateImpl(
                activity, mProfile, host, activity.getTabModelSelector());

        // Don't direct reference activity because it might change if tab is reparented.
        Runnable closeContextMenuCallback =
                () -> host.getActiveTab().getActivity().closeContextMenu();
        mContextMenuManager = new ContextMenuManager(navDelegate, this::setTouchEnabled,
                closeContextMenuCallback, CONTEXT_MENU_USER_ACTION_PREFIX);
        host.getActiveTab().getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);

        CategoryCardAdapter adapterDelegate = new CategoryCardAdapter(
                mModel, mLayoutManager, iconGenerator, mContextMenuManager, navDelegate, mProfile);

        mRecyclerView = (RecyclerView) mView.findViewById(R.id.explore_sites_category_recycler);
        RecyclerViewAdapter<CategoryCardViewHolderFactory.CategoryCardViewHolder, Void> adapter =
                new RecyclerViewAdapter<>(adapterDelegate, new CategoryCardViewHolderFactory());

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(adapter);

        // When we personalize, we don't want to scroll to the 4th category.
        mInitialScrollPosition =
                ExploreSitesBridge.getVariation() == ExploreSitesVariation.PERSONALIZED
                ? INITIAL_SCROLL_POSITION_PERSONALIZED
                : INITIAL_SCROLL_POSITION;

        ExploreSitesBridge.getEspCatalog(mProfile, this::translateToModel);
        RecordUserAction.record("Android.ExploreSitesPage.Open");
    }

    void translateToModel(@Nullable List<ExploreSitesCategory> categoryList) {
        // If list is null or we received an empty catalog from network, show error.
        if (categoryList == null || (categoryList.isEmpty() && mHasFetchedNetworkCatalog)) {
            onUpdatedCatalog(false);
            return;
        }
        // If list is empty and we never fetched from network before, fetch from network.
        if (categoryList.isEmpty()) {
            mModel.set(STATUS_KEY, CatalogLoadingState.LOADING_NET);
            mHasFetchedNetworkCatalog = true;
            ExploreSitesBridge.updateCatalogFromNetwork(
                    mProfile, /* isImmediateFetch =*/true, this::onUpdatedCatalog);
            RecordHistogram.recordEnumeratedHistogram("ExploreSites.CatalogUpdateRequestSource",
                    ExploreSitesEnums.CatalogUpdateRequestSource.EXPLORE_SITES_PAGE,
                    ExploreSitesEnums.CatalogUpdateRequestSource.COUNT);
            return;
        }
        mModel.set(STATUS_KEY, CatalogLoadingState.SUCCESS);

        ListModel<ExploreSitesCategory> categoryListModel = mModel.get(CATEGORY_LIST_KEY);

        // Filter empty categories and categories with fewer sites originally than would fill a row.
        for (ExploreSitesCategory category : categoryList) {
            if ((category.getNumDisplayed() > 0) && (category.getMaxRows() > 0)) {
                categoryListModel.add(category);
            }
        }

        Parcelable savedScrollPosition = getLayoutManagerStateFromNavigationEntry();
        if (savedScrollPosition != null) {
            mLayoutManager.onRestoreInstanceState(savedScrollPosition);
        } else if (mNavFragment != null) {
            lookupCategoryAndScroll();
        } else {
            mModel.set(SCROLL_TO_CATEGORY_KEY,
                    Math.min(categoryListModel.size() - 1, mInitialScrollPosition));
        }
        if (mTab != null) {
            // We want to observe page load start so that we can store the recycler view layout
            // state, for making "back" work correctly.
            mTabObserver = new EmptyTabObserver() {
                @Override
                public void onPageLoadStarted(Tab tab, String url) {
                    try {
                        URI uri = new URI(url);
                        if (UrlConstants.CHROME_NATIVE_SCHEME.equals(uri.getScheme())
                                && UrlConstants.EXPLORE_HOST.equals(uri.getHost())) {
                            return;
                        }
                        saveLayoutManagerState();
                    } catch (URISyntaxException e) {
                    }
                }
            };
            mTab.addObserver(mTabObserver);
        }

        mIsLoaded = true;
    }

    private void onUpdatedCatalog(Boolean hasFetchedCatalog) {
        if (hasFetchedCatalog) {
            ExploreSitesBridge.getEspCatalog(mProfile, this::translateToModel);
        } else {
            mModel.set(STATUS_KEY, CatalogLoadingState.ERROR);
            mIsLoaded = true;
        }
    }

    public boolean isLoadedForTests() {
        return mIsLoaded;
    }

    @Override
    public String getHost() {
        return UrlConstants.EXPLORE_HOST;
    }

    @Override
    public View getView() {
        return mView;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public void updateForUrl(String url) {
        super.updateForUrl(url);
        try {
            mNavFragment = new URI(url).getFragment();
        } catch (URISyntaxException e) {
            mNavFragment = null;
        }
        if (mModel.get(STATUS_KEY) == CatalogLoadingState.SUCCESS) {
            lookupCategoryAndScroll();
        }
    }

    /* Gets the state of layout manager as a marshalled Parcel that's Base64 Encoded. */
    private String getLayoutManagerState() {
        Parcelable layoutManagerState = mLayoutManager.onSaveInstanceState();
        Parcel parcel = Parcel.obtain();
        layoutManagerState.writeToParcel(parcel, 0);
        String marshalledState = Base64.encodeToString(parcel.marshall(), 0);
        parcel.recycle();
        return marshalledState;
    }

    /* Saves the state of the layout manager in the NavigationEntry for the current tab. */
    private void saveLayoutManagerState() {
        if (mTab == null || mTab.getWebContents() == null) return;

        NavigationController controller = mTab.getWebContents().getNavigationController();
        int index = controller.getLastCommittedEntryIndex();
        NavigationEntry entry = controller.getEntryAtIndex(index);
        if (entry == null) return;

        controller.setEntryExtraData(
                index, NAVIGATION_ENTRY_SCROLL_POSITION_KEY, getLayoutManagerState());
    }

    /*
     * Retrieves the layout manager state from the navigation entry and reconstitutes it into a
     * Parcelable using LinearLayoutManager.SavedState.CREATOR.
     */
    private Parcelable getLayoutManagerStateFromNavigationEntry() {
        if (mTab.getWebContents() == null) return null;

        NavigationController controller = mTab.getWebContents().getNavigationController();
        int index = controller.getLastCommittedEntryIndex();
        String layoutManagerState =
                controller.getEntryExtraData(index, NAVIGATION_ENTRY_SCROLL_POSITION_KEY);
        if (TextUtils.isEmpty(layoutManagerState)) return null;

        byte[] parcelData = Base64.decode(layoutManagerState, 0);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(parcelData, 0, parcelData.length);
        parcel.setDataPosition(0);
        Parcelable scrollPosition = LinearLayoutManager.SavedState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        return scrollPosition;
    }

    @Override
    public void destroy() {
        if (mTabObserver != null) {
            mTab.removeObserver(mTabObserver);
        }
        mHost.getActiveTab().getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
        super.destroy();
    }

    private void setTouchEnabled(boolean enabled) {} // Does nothing.

    private void lookupCategoryAndScroll() {
        try {
            int id = Integer.parseInt(mNavFragment);
            ListModel<ExploreSitesCategory> categoryList = mModel.get(CATEGORY_LIST_KEY);
            for (int i = 0; i < categoryList.size(); i++) {
                if (categoryList.get(i).getId() == id) {
                    mModel.set(SCROLL_TO_CATEGORY_KEY, i);
                    break;
                }
            }

        } catch (NumberFormatException e) {
        } // do nothing
    }
}
