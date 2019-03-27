// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Checkable;
import android.widget.FrameLayout;

import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;

import java.util.List;

/**
 * Provides a generic base class for representing an item that can be selected. When selected, the
 * view will be updated to indicate that it is selected. The exact UI changes for selection state
 * should be provided by the implementing class.
 *
 * A selection is initially established via long-press. If a selection is already established,
 * clicking on the item will toggle its selection.
 *
 * @param <E> The type of the item associated with this SelectableItemViewBase.
 */
public abstract class SelectableItemViewBase<E> extends FrameLayout
        implements Checkable, OnClickListener, OnLongClickListener, SelectionObserver<E> {
    private SelectionDelegate<E> mSelectionDelegate;
    private E mItem;
    private boolean mIsChecked;

    // Controls whether selection should happen during onLongClick.
    private boolean mSelectOnLongClick = true;

    /**
     * Constructor for inflating from XML.
     */
    public SelectableItemViewBase(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Destroys and cleans up itself.
     */
    public void destroy() {
        if (mSelectionDelegate != null) {
            mSelectionDelegate.removeObserver(this);
        }
    }

    /**
     * Sets the SelectionDelegate and registers this object as an observer. The SelectionDelegate
     * must be set before the item can respond to click events.
     * @param delegate The SelectionDelegate that will inform this item of selection changes.
     */
    public void setSelectionDelegate(SelectionDelegate<E> delegate) {
        if (mSelectionDelegate != delegate) {
            if (mSelectionDelegate != null) mSelectionDelegate.removeObserver(this);
            mSelectionDelegate = delegate;
            mSelectionDelegate.addObserver(this);
        }
    }

    /**
     * Controls whether selection happens during onLongClick or onClick.
     * @param selectOnLongClick True if selection should happen on longClick, false if selection
     *                          should happen on click instead.
     */
    public void setSelectionOnLongClick(boolean selectOnLongClick) {
        mSelectOnLongClick = selectOnLongClick;
    }

    /**
     * @param item The item associated with this SelectableItemViewBase.
     */
    public void setItem(E item) {
        mItem = item;
        setChecked(mSelectionDelegate.isItemSelected(item));
    }

    /**
     * @return The item associated with this SelectableItemViewBase.
     */
    public E getItem() {
        return mItem;
    }

    // FrameLayout implementations.
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        setOnClickListener(this);
        setOnLongClickListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSelectionDelegate != null) {
            setChecked(mSelectionDelegate.isItemSelected(mItem));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setChecked(false);
    }

    // OnClickListener implementation.
    @Override
    public final void onClick(View view) {
        assert view == this;

        if (!mSelectOnLongClick) {
            handleSelection();
            return;
        }

        if (isSelectionModeActive()) {
            onLongClick(view);
        } else {
            onClick();
        }
    }

    // OnLongClickListener implementation.
    @Override
    public boolean onLongClick(View view) {
        assert view == this;
        handleSelection();
        return true;
    }

    private void handleSelection() {
        boolean checked = toggleSelectionForItem(mItem);
        setChecked(checked);
    }

    /**
     * @return Whether we are currently in selection mode.
     */
    protected boolean isSelectionModeActive() {
        return mSelectionDelegate.isSelectionEnabled();
    }

    /**
     * Toggles the selection state for a given item.
     * @param item The given item.
     * @return Whether the item was in selected state after the toggle.
     */
    protected boolean toggleSelectionForItem(E item) {
        return mSelectionDelegate.toggleSelectionForItem(item);
    }

    // Checkable implementations.
    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked == mIsChecked) return;
        mIsChecked = checked;
        updateView();
    }

    // SelectionObserver implementation.
    @Override
    public void onSelectionStateChange(List<E> selectedItems) {
        setChecked(mSelectionDelegate.isItemSelected(mItem));
    }

    /**
     * Update the view based on whether this item is selected.
     */
    protected void updateView() {}

    /**
     * Same as {@link OnClickListener#onClick(View)} on this.
     * Subclasses should override this instead of setting their own OnClickListener because this
     * class handles onClick events in selection mode, and won't forward events to subclasses in
     * that case.
     */
    protected abstract void onClick();
}