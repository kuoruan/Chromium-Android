// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.photo_picker;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.ui.PhotoPickerListener;

import java.util.List;

/**
 * A class for keeping track of common data associated with showing photos in
 * the photo picker, for example the RecyclerView and the bitmap caches.
 */
public class PickerCategoryView extends RelativeLayout
        implements FileEnumWorkerTask.FilesEnumeratedCallback, OnMenuItemClickListener {
    // The dialog that owns us.
    private PhotoPickerDialog mDialog;

    // The view containing the RecyclerView and the toolbar, etc.
    private SelectableListLayout<PickerBitmap> mSelectableListLayout;

    // Our context.
    private Context mContext;

    // The list of images on disk, sorted by last-modified first.
    private List<PickerBitmap> mPickerBitmaps;

    // True if multi-selection is allowed in the picker.
    private boolean mMultiSelectionAllowed;

    // The callback to notify the listener of decisions reached in the picker.
    private PhotoPickerListener mListener;

    // The RecyclerView showing the images.
    private RecyclerView mRecyclerView;

    // The {@link PickerAdapter} for the RecyclerView.
    private PickerAdapter mPickerAdapter;

    // The {@link SelectionDelegate} keeping track of which images are selected.
    private SelectionDelegate<PickerBitmap> mSelectionDelegate;

    /**
     * The number of columns to show. Note: mColumns and mPadding (see below) should both be even
     * numbers or both odd, not a mix (the column padding will not be of uniform thickness if they
     * are a mix).
     */
    private int mColumns;

    // The padding between columns. See also comment for mColumns.
    private int mPadding;

    // The size of the bitmaps (equal length for width and height).
    private int mImageSize;

    // A worker task for asynchronously enumerating files off the main thread.
    private FileEnumWorkerTask mWorkerTask;

    public PickerCategoryView(Context context) {
        super(context);
        postConstruction(context);
    }

    /**
     * A helper function for initializing the PickerCategoryView.
     * @param context The context to use.
     */
    @SuppressWarnings("unchecked") // mSelectableListLayout
    private void postConstruction(Context context) {
        mContext = context;

        mSelectionDelegate = new SelectionDelegate<PickerBitmap>();

        View root = LayoutInflater.from(context).inflate(R.layout.photo_picker_dialog, this);
        mSelectableListLayout =
                (SelectableListLayout<PickerBitmap>) root.findViewById(R.id.selectable_list);

        mPickerAdapter = new PickerAdapter(this);
        mRecyclerView = mSelectableListLayout.initializeRecyclerView(mPickerAdapter);
        mSelectableListLayout.initializeToolbar(R.layout.photo_picker_toolbar, mSelectionDelegate,
                R.string.photo_picker_select_images, null, R.id.photo_picker_normal_menu_group,
                R.id.photo_picker_selection_mode_menu_group, R.color.default_primary_color, false,
                this);

        Rect appRect = new Rect();
        ((Activity) context).getWindow().getDecorView().getWindowVisibleDisplayFrame(appRect);
        calculateGridMetrics(appRect.width());

        RecyclerView.LayoutManager mLayoutManager = new GridLayoutManager(mContext, mColumns);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new GridSpacingItemDecoration(mColumns, mPadding));

        // TODO(finnur): Implement caching.
        // TODO(finnur): Remove this once the decoder service is in place.
        prepareBitmaps();
    }

    /**
     * Cancels any outstanding requests.
     */
    public void onDialogDismissed() {
        if (mWorkerTask != null) {
            mWorkerTask.cancel(true);
            mWorkerTask = null;
        }
    }

    /**
     * Initializes the PickerCategoryView object.
     * @param dialog The dialog showing us.
     * @param listener The listener who should be notified of actions.
     * @param multiSelectionAllowed Whether to allow the user to select more than one image.
     */
    public void initialize(
            PhotoPickerDialog dialog, PhotoPickerListener listener, boolean multiSelectionAllowed) {
        // TODO(finnur): Change selection delegate to support single-selection.

        mDialog = dialog;
        mMultiSelectionAllowed = multiSelectionAllowed;
        mListener = listener;
    }

    // FileEnumWorkerTask.FilesEnumeratedCallback:

    @Override
    public void filesEnumeratedCallback(List<PickerBitmap> files) {
        mPickerBitmaps = files;
        if (files != null && files.size() > 0) {
            mPickerAdapter.notifyDataSetChanged();
        }
    }

    // OnMenuItemClickListener:

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.close_menu_id) {
            mListener.onPickerUserAction(PhotoPickerListener.Action.CANCEL, null);
            mDialog.dismiss();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_done_menu_id) {
            notifyPhotosSelected();
            mDialog.dismiss();
            return true;
        }
        return false;
    }

    // Simple accessors:

    public int getImageSize() {
        return mImageSize;
    }

    public SelectionDelegate<PickerBitmap> getSelectionDelegate() {
        return mSelectionDelegate;
    }

    public List<PickerBitmap> getPickerBitmaps() {
        return mPickerBitmaps;
    }

    public boolean isMultiSelectAllowed() {
        return mMultiSelectionAllowed;
    }

    /**
     * Notifies the listener that the user selected to launch the gallery.
     */
    public void showGallery() {
        mListener.onPickerUserAction(PhotoPickerListener.Action.LAUNCH_GALLERY, null);
    }

    /**
     * Notifies the listener that the user selected to launch the camera intent.
     */
    public void showCamera() {
        mListener.onPickerUserAction(PhotoPickerListener.Action.LAUNCH_CAMERA, null);
    }

    /**
     * Calculates image size and how many columns can fit on-screen.
     * @param width The total width of the boundary to show the images in.
     */
    private void calculateGridMetrics(int width) {
        int minSize =
                mContext.getResources().getDimensionPixelSize(R.dimen.photo_picker_tile_min_size);
        mPadding = mContext.getResources().getDimensionPixelSize(R.dimen.photo_picker_tile_gap);
        mColumns = Math.max(1, (width - mPadding) / (minSize + mPadding));
        mImageSize = (width - mPadding * (mColumns + 1)) / (mColumns);

        // Make sure columns and padding are either both even or both odd.
        if (((mColumns % 2) == 0) != ((mPadding % 2) == 0)) {
            mPadding++;
        }
    }

    /**
     * Prepares bitmaps for loading.
     */
    private void prepareBitmaps() {
        if (mWorkerTask != null) {
            mWorkerTask.cancel(true);
        }

        mWorkerTask = new FileEnumWorkerTask(this, new MimeTypeFileFilter("image/*"));
        mWorkerTask.execute();
    }

    /**
     * Notifies any listeners that one or more photos have been selected.
     */
    private void notifyPhotosSelected() {
        List<PickerBitmap> selectedFiles = mSelectionDelegate.getSelectedItems();
        String[] photos = new String[selectedFiles.size()];
        int i = 0;
        for (PickerBitmap bitmap : selectedFiles) {
            photos[i++] = bitmap.getFilePath();
        }

        mListener.onPickerUserAction(PhotoPickerListener.Action.PHOTOS_SELECTED, photos);
    }

    /**
     * A class for implementing grid spacing between items.
     */
    private class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        // The number of spans to account for.
        private int mSpanCount;

        // The amount of spacing to use.
        private int mSpacing;

        public GridSpacingItemDecoration(int spanCount, int spacing) {
            mSpanCount = spanCount;
            mSpacing = spacing;
        }

        @Override
        public void getItemOffsets(
                Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int left = 0, right = 0, top = 0, bottom = 0;
            int position = parent.getChildAdapterPosition(view);

            if (position >= 0) {
                int column = position % mSpanCount;

                left = mSpacing - ((column * mSpacing) / mSpanCount);
                right = (column + 1) * mSpacing / mSpanCount;

                if (position < mSpanCount) {
                    top = mSpacing;
                }
                bottom = mSpacing;
            }

            outRect.set(left, top, right, bottom);
        }
    }
}
