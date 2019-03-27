// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.carousel;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;

import org.chromium.ui.modelutil.ListObservable;
import org.chromium.ui.modelutil.ListObservable.ListObserver;
import org.chromium.ui.modelutil.RecyclerViewAdapter;
import org.chromium.ui.modelutil.SimpleRecyclerViewMcp;

/**
 * Coordinator responsible for suggesting chips to the user.
 */
public class AssistantCarouselCoordinator {
    // TODO(crbug.com/806868): Get those from XML.
    private static final int CHIPS_INNER_SPACING_DP = 16;
    private static final int CHIPS_OUTER_SPACING_DP = 24;

    @Nullable
    private Runnable mOnVisibilityChanged;

    private final LinearLayoutManager mLayoutManager;
    private final RecyclerView mView;

    public AssistantCarouselCoordinator(Context context, AssistantCarouselModel model) {
        mLayoutManager = new LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, /* reverseLayout= */ false);
        mView = new RecyclerView(context);
        mView.setLayoutManager(mLayoutManager);
        mView.addItemDecoration(new SpaceItemDecoration(context));
        mView.getItemAnimator().setAddDuration(0);
        mView.getItemAnimator().setChangeDuration(0);
        mView.getItemAnimator().setMoveDuration(0);
        mView.getItemAnimator().setRemoveDuration(0);
        mView.setAdapter(new RecyclerViewAdapter<>(
                new SimpleRecyclerViewMcp<>(model.getChipsModel(), AssistantChip::getType,
                        AssistantChipViewHolder::bind),
                AssistantChipViewHolder::create));

        // Carousel is initially hidden.
        setVisible(false);

        // Listen for changes on REVERSE_LAYOUT.
        model.addObserver((source, propertyKey) -> {
            if (AssistantCarouselModel.REVERSE_LAYOUT == propertyKey) {
                mLayoutManager.setReverseLayout(model.get(AssistantCarouselModel.REVERSE_LAYOUT));
            } else {
                assert false : "Unhandled property detected in AssistantCarouselCoordinator!";
            }
        });

        // Listen for changes on chips, and set visibility accordingly.
        model.getChipsModel().addObserver(new ListObserver<Void>() {
            @Override
            public void onItemRangeInserted(ListObservable source, int index, int count) {
                onChipsChanged();
            }

            @Override
            public void onItemRangeRemoved(ListObservable source, int index, int count) {
                onChipsChanged();
            }

            @Override
            public void onItemRangeChanged(
                    ListObservable<Void> source, int index, int count, @Nullable Void payload) {
                onChipsChanged();
            }

            private void onChipsChanged() {
                setVisible(model.getChipsModel().size() > 0);
            }
        });
    }

    /**
     * Return the view associated to this carousel.
     */
    public RecyclerView getView() {
        return mView;
    }

    /**
     * Show or hide this carousel within its parent and call the {@code mOnVisibilityChanged}
     * listener.
     */
    private void setVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        boolean changed = mView.getVisibility() != visibility;
        if (changed) {
            mView.setVisibility(visibility);
            if (mOnVisibilityChanged != null) {
                mOnVisibilityChanged.run();
            }
        }
    }

    /**
     * Set the listener that should be triggered when changing the listener of this coordinator
     * view.
     */
    public void setVisibilityChangedListener(Runnable listener) {
        mOnVisibilityChanged = listener;
    }

    private class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int mInnerSpacePx;
        private final int mOuterSpacePx;

        SpaceItemDecoration(Context context) {
            mInnerSpacePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    CHIPS_INNER_SPACING_DP / 2, context.getResources().getDisplayMetrics());
            mOuterSpacePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    CHIPS_OUTER_SPACING_DP, context.getResources().getDisplayMetrics());
        }

        @Override
        public void getItemOffsets(
                Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int left;
            int right;
            if (position == 0) {
                left = mOuterSpacePx;
            } else {
                left = mInnerSpacePx;
            }

            if (position == parent.getAdapter().getItemCount() - 1) {
                right = mOuterSpacePx;
            } else {
                right = mInnerSpacePx;
            }

            if (!mLayoutManager.getReverseLayout()) {
                outRect.left = left;
                outRect.right = right;
            } else {
                outRect.left = right;
                outRect.right = left;
            }
        }
    }
}
