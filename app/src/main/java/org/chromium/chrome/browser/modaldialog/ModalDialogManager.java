// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.modaldialog;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;

import org.chromium.base.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for managing the display of a queue of {@link ModalDialogView}s.
 */
public class ModalDialogManager {
    /**
     * Present a {@link ModalDialogView} in a container.
     */
    public static abstract class Presenter {
        private Runnable mCancelCallback;
        private ModalDialogView mModalDialog;
        private View mCurrentView;

        /**
         * @param dialog The dialog that's currently showing in this presenter. If null, no dialog
         *               is currently showing.
         */
        private void setModalDialog(
                @Nullable ModalDialogView dialog, @Nullable Runnable cancelCallback) {
            if (dialog == null) {
                removeDialogView(mCurrentView);
                mModalDialog = null;
                mCancelCallback = null;
            } else {
                assert mModalDialog
                        == null : "Should call setModalDialog(null) before setting a modal dialog.";
                mModalDialog = dialog;
                mCurrentView = dialog.getView();
                mCancelCallback = cancelCallback;
                addDialogView(mCurrentView);
            }
        }

        /**
         * Run the cached cancel callback and reset the cached callback.
         */
        protected final void cancelCurrentDialog() {
            if (mCancelCallback == null) return;

            // Set #mCancelCallback to null before calling the callback to avoid it being
            // updated during the callback.
            Runnable callback = mCancelCallback;
            mCancelCallback = null;
            callback.run();
        }

        /**
         * @return The modal dialog that this presenter is showing.
         */
        protected final ModalDialogView getModalDialog() {
            return mModalDialog;
        }

        /**
         * Add the specified {@link ModalDialogView} in a container.
         * @param dialogView The {@link ModalDialogView} that needs to be shown.
         */
        protected abstract void addDialogView(View dialogView);

        /**
         * Remove the specified {@link ModalDialogView} from a container.
         * @param dialogView The {@link ModalDialogView} that needs to be removed.
         */
        protected abstract void removeDialogView(View dialogView);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({APP_MODAL, TAB_MODAL})
    public @interface ModalDialogType {}
    public static final int APP_MODAL = 0;
    public static final int TAB_MODAL = 1;

    /** Mapping of the {@link Presenter}s and the type of dialogs they are showing. */
    private final SparseArray<Presenter> mPresenters = new SparseArray<>();

    /** The list of pending dialogs */
    private final List<Pair<ModalDialogView, Integer>> mPendingDialogs = new ArrayList<>();

    /** The default presenter to be used if a specified type is not supported. */
    private final Presenter mDefaultPresenter;

    /** The presenter of the type of the dialog that is currently showing. */
    private Presenter mCurrentPresenter;

    /**
     * Constructor for initializing default {@link Presenter}.
     * @param defaultPresenter The default presenter to be used when no presenter specified.
     * @param defaultType The dialog type of the default presenter.
     */
    public ModalDialogManager(
            @NonNull Presenter defaultPresenter, @ModalDialogType int defaultType) {
        mDefaultPresenter = defaultPresenter;
        registerPresenter(defaultPresenter, defaultType);
    }

    /**
     * Register a {@link Presenter} that shows a specific type of dialog. Note that only one
     * presenter of each type can be registered.
     * @param presenter The {@link Presenter} to be registered.
     * @param dialogType The type of the dialog shown by the specified presenter.
     */
    public void registerPresenter(Presenter presenter, @ModalDialogType int dialogType) {
        assert mPresenters.get(dialogType)
                == null : "Only one presenter can be registered for each type.";
        mPresenters.put(dialogType, presenter);
    }

    /**
     * @return Whether a dialog is currently showing.
     */
    public boolean isShowing() {
        return mCurrentPresenter != null;
    }

    /**
     * Show the specified dialog. If another dialog is currently showing, the specified dialog will
     * be added to the pending dialog list.
     * @param dialog The dialog to be shown or added to pending list.
     * @param dialogType The type of the dialog to be shown.
     */
    public void showDialog(ModalDialogView dialog, @ModalDialogType int dialogType) {
        if (isShowing()) {
            mPendingDialogs.add(Pair.create(dialog, dialogType));
            return;
        }

        dialog.prepareBeforeShow();
        mCurrentPresenter = mPresenters.get(dialogType, mDefaultPresenter);
        mCurrentPresenter.setModalDialog(dialog, () -> cancelDialog(dialog));
    }

    /**
     * Dismiss the specified dialog. If the dialog is not currently showing, it will be removed from
     * the pending dialog list.
     * @param dialog The dialog to be dismissed or removed from pending list.
     */
    public void dismissDialog(ModalDialogView dialog) {
        if (mCurrentPresenter == null || dialog != mCurrentPresenter.getModalDialog()) {
            for (int i = 0; i < mPendingDialogs.size(); ++i) {
                if (mPendingDialogs.get(i).first == dialog) {
                    mPendingDialogs.remove(i);
                    break;
                }
            }
            return;
        }

        if (!isShowing()) return;
        assert dialog == mCurrentPresenter.getModalDialog();

        mCurrentPresenter.setModalDialog(null, null);
        mCurrentPresenter = null;

        if (!mPendingDialogs.isEmpty()) {
            Pair<ModalDialogView, Integer> nextDialog = mPendingDialogs.remove(0);
            showDialog(nextDialog.first, nextDialog.second);
        }
    }

    /**
     * Cancel showing the specified dialog. This is essentially the same as
     * {@link #dismissDialog(ModalDialogView)} but will also call the onCancelled callback from the
     * modal dialog.
     * @param dialog The dialog to be cancelled.
     */
    public void cancelDialog(ModalDialogView dialog) {
        dismissDialog(dialog);
        dialog.getController().onCancel();
    }

    /**
     * Dismiss the dialog currently shown and remove all pending dialogs and call the onCancelled
     * callbacks from the modal dialogs.
     */
    protected void cancelAllDialogs() {
        while (!mPendingDialogs.isEmpty()) {
            mPendingDialogs.remove(0).first.getController().onCancel();
        }
        if (isShowing()) cancelDialog(mCurrentPresenter.getModalDialog());
    }

    @VisibleForTesting
    public ModalDialogView getCurrentDialogForTest() {
        return mCurrentPresenter == null ? null : mCurrentPresenter.getModalDialog();
    }

    @VisibleForTesting
    List getPendingDialogsForTest() {
        return mPendingDialogs;
    }

    @VisibleForTesting
    Presenter getPresenterForTest(@ModalDialogType int dialogType) {
        return mPresenters.get(dialogType);
    }

    @VisibleForTesting
    Presenter getCurrentPresenterForTest() {
        return mCurrentPresenter;
    }
}
