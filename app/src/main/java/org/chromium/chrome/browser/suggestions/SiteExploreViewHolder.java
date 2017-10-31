// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;

import java.util.List;

/**
 * A view holder for the Explore UI.
 */
public class SiteExploreViewHolder extends SiteSectionViewHolder {
    private final int mMaxTileColumns;
    private SiteExploreViewPager mSiteExploreViewPager;
    private SiteExploreViewHolder.ExploreSectionsAdapter mAdapter;

    public SiteExploreViewHolder(ViewGroup view, int maxTileColumns) {
        super(view);
        mMaxTileColumns = maxTileColumns;
        mSiteExploreViewPager = itemView.findViewById(R.id.site_explore_pager);

        mAdapter = new ExploreSectionsAdapter(itemView.getContext());
        mSiteExploreViewPager.setAdapter(mAdapter);
    }

    @Override
    public void refreshData() {
        mAdapter.updateTiles(mTileGroup.getTileSections());
    }

    @Nullable
    @Override
    protected TileView findTileView(SiteSuggestion data) {
        return mAdapter.findTileView(data);
    }

    /**
     * Adapter for the Explore UI view holder.
     */
    public class ExploreSectionsAdapter extends PagerAdapter {
        private final Context mContext;
        /**
         * We use the category {@link TileSectionType} as key. The value is the layout used for
         * that category in the {@link SiteExploreViewPager}. At any one time there will be at most
         * three layouts stored in this array - the one that is currently shown on the screen and
         * the two on either side of it.
         */
        private SparseArray<TileGridLayout> mTileSectionLayouts;

        /**
         * The latest tile sections which came with an update from
         * {@link #updateTiles(SparseArray)}. The key is the section id and the value is the
         * list of tiles within this section. See {@link TileSectionType}.
         */
        private SparseArray<List<Tile>> mLatestTileSections;

        public ExploreSectionsAdapter(Context context) {
            mContext = context;
            mTileSectionLayouts = new SparseArray<TileGridLayout>();
        }

        @Override
        public View instantiateItem(ViewGroup container, int position) {
            List<Tile> tileSectionList = mLatestTileSections.valueAt(position);
            if (tileSectionList == null) return null;

            @TileSectionType
            int sectionType = mTileGroup.getTileSections().keyAt(position);
            TileGridLayout layout = mTileSectionLayouts.get(sectionType);
            if (layout == null) {
                layout = new TileGridLayout(mContext, null);

                layout.setMaxRows(1);
                layout.setMaxColumns(mMaxTileColumns);
                mTileRenderer.renderTileSection(
                        tileSectionList, layout, mTileGroup.getTileSetupDelegate());
                mTileSectionLayouts.put(sectionType, layout);
            }

            if (sectionType == TileSectionType.PERSONALIZED) {
                mTileGroup.notifyTilesRendered();
            }

            container.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
            mTileSectionLayouts.remove(mTileSectionLayouts.keyAt(position));
        }

        @Override
        public int getCount() {
            return mLatestTileSections == null ? 0 : mLatestTileSections.size();
        }

        @Override
        public String getPageTitle(int position) {
            @TileSectionType
            int sectionType = (mLatestTileSections.keyAt(position));
            int stringRes;
            switch (sectionType) {
                case TileSectionType.PERSONALIZED:
                    stringRes = R.string.ntp_sites_exploration_category_personalized_title;
                    break;
                case TileSectionType.SOCIAL:
                    stringRes = R.string.ntp_sites_exploration_category_social_title;
                    break;
                case TileSectionType.ENTERTAINMENT:
                    stringRes = R.string.ntp_sites_exploration_category_entertainment_title;
                    break;
                case TileSectionType.NEWS:
                    stringRes = R.string.ntp_sites_exploration_category_news_title;
                    break;
                case TileSectionType.ECOMMERCE:
                    stringRes = R.string.ntp_sites_exploration_category_ecommerce_title;
                    break;
                case TileSectionType.TOOLS:
                    stringRes = R.string.ntp_sites_exploration_category_tools_title;
                    break;
                case TileSectionType.TRAVEL:
                    stringRes = R.string.ntp_sites_exploration_category_travel_title;
                    break;
                case TileSectionType.UNKNOWN:
                default:
                    stringRes = R.string.ntp_sites_exploration_category_other_title;
                    break;
            }

            return mContext.getResources().getString(stringRes);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Nullable
        public TileView findTileView(SiteSuggestion suggestion) {
            TileGridLayout layout = mTileSectionLayouts.get(suggestion.sectionType);
            if (layout == null) return null;
            return layout.getTileView(suggestion);
        }

        /**
         * Updates the data set for the adapter. We use this method instead of directly reading
         * data from the {@link TileGroup} because this allows us to update the tiles directly
         * and prevents inconsistencies with the data.
         *
         * @param freshTileSections The new information for the tile sections.
         */
        public void updateTiles(SparseArray<List<Tile>> freshTileSections) {
            mLatestTileSections = freshTileSections;

            // Get the category IDs of the section layouts which are already rendered.
            int[] layoutCategoryIds = new int[mTileSectionLayouts.size()];
            for (int i = 0; i < mTileSectionLayouts.size(); i++) {
                layoutCategoryIds[i] = mTileSectionLayouts.keyAt(i);
            }

            for (@TileSectionType int layoutCategoryId : layoutCategoryIds) {
                if (freshTileSections.get(layoutCategoryId) == null) {
                    // Remove the section layout from the view pager because it is not in the
                    // freshly fetched sections.
                    mSiteExploreViewPager.removeView(mTileSectionLayouts.get(layoutCategoryId));
                    mTileSectionLayouts.remove(layoutCategoryId);
                    continue;
                }
                mTileRenderer.renderTileSection(freshTileSections.get(layoutCategoryId),
                        mTileSectionLayouts.get(layoutCategoryId),
                        mTileGroup.getTileSetupDelegate());
            }
            notifyDataSetChanged();
        }
    }
}
