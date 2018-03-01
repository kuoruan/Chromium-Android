// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.permissions;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.support.annotation.IntDef;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.EmptyBottomSheetObserver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedList;
import java.util.List;

/**
 * Singleton instance which controls the display of modal permission dialogs. This class is lazily
 * initiated when getInstance() is first called.
 *
 * Unlike permission infobars, which stack on top of each other, only one permission dialog may be
 * visible on the screen at once. Any additional request for a modal permissions dialog is queued,
 * and will be displayed once the user responds to the current dialog.
 */
public class PermissionDialogController implements AndroidPermissionRequester.RequestDelegate {
    private static final int NOT_SHOWING = 0;
    // We don't show prompts while Chrome Home is showing.
    private static final int PROMPT_PENDING = 1;
    private static final int PROMPT_OPEN = 2;
    private static final int PROMPT_ACCEPTED = 3;
    private static final int PROMPT_DENIED = 4;
    private static final int REQUEST_ANDROID_PERMISSIONS = 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NOT_SHOWING, PROMPT_PENDING, PROMPT_OPEN, PROMPT_ACCEPTED, PROMPT_DENIED,
            REQUEST_ANDROID_PERMISSIONS})
    private @interface State {}

    private AlertDialog mDialog;
    private PermissionDialogDelegate mDialogDelegate;

    // As the PermissionRequestManager handles queueing for a tab and only shows prompts for active
    // tabs, we typically only have one request. This class only handles multiple requests at once
    // when either:
    // 1) Multiple open windows request permissions due to Android split-screen
    // 2) A tab navigates or is closed while the Android permission request is open, and the
    // subsequent page requests a permission
    private List<PermissionDialogDelegate> mRequestQueue;

    /** The current state, whether we have a prompt showing and so on. */
    private @State int mState;

    // Static holder to ensure safe initialization of the singleton instance.
    private static class Holder {
        @SuppressLint("StaticFieldLeak")
        private static final PermissionDialogController sInstance =
                new PermissionDialogController();
    }

    public static PermissionDialogController getInstance() {
        return Holder.sInstance;
    }

    private PermissionDialogController() {
        mRequestQueue = new LinkedList<>();
        mState = NOT_SHOWING;
    }

    /**
     * Called by native code to create a modal permission dialog. The PermissionDialogController
     * will decide whether the dialog needs to be queued (because another dialog is on the screen)
     * or whether it can be shown immediately.
     */
    @CalledByNative
    private static void createDialog(PermissionDialogDelegate delegate) {
        PermissionDialogController.getInstance().queueDialog(delegate);
    }

    /**
     * Queues a modal permission dialog for display. If there are currently no dialogs on screen, it
     * will be displayed immediately. Otherwise, it will be displayed as soon as the user responds
     * to the current dialog.
     * @param context  The context to use to get the dialog layout.
     * @param delegate The wrapper for the native-side permission delegate.
     */
    private void queueDialog(PermissionDialogDelegate delegate) {
        mRequestQueue.add(delegate);
        delegate.setDialogController(this);
        scheduleDisplay();
    }

    private void scheduleDisplay() {
        if (mState == NOT_SHOWING && !mRequestQueue.isEmpty()) dequeueDialog();
    }

    @VisibleForTesting
    public AlertDialog getCurrentDialogForTesting() {
        return mDialog;
    }

    @Override
    public void onAndroidPermissionAccepted() {
        assert mState == REQUEST_ANDROID_PERMISSIONS;

        // The tab may have navigated or been closed behind the Android permission prompt.
        if (mDialogDelegate == null) {
            mState = NOT_SHOWING;
        } else {
            mDialogDelegate.onAccept();
            destroyDelegate();
        }
        scheduleDisplay();
    }

    @Override
    public void onAndroidPermissionCanceled() {
        assert mState == REQUEST_ANDROID_PERMISSIONS;

        // The tab may have navigated or been closed behind the Android permission prompt.
        if (mDialogDelegate == null) {
            mState = NOT_SHOWING;
        } else {
            mDialogDelegate.onDismiss();
            destroyDelegate();
        }
        scheduleDisplay();
    }

    /**
     * Shows the dialog asking the user for a web API permission.
     */
    public void dequeueDialog() {
        assert mState == NOT_SHOWING;

        mDialogDelegate = mRequestQueue.remove(0);
        mState = PROMPT_PENDING;
        ChromeActivity activity = mDialogDelegate.getTab().getActivity();

        // It's possible for the activity to be null if we reach here just after the user
        // backgrounds the browser and cleanup has happened. In that case, we can't show a prompt,
        // so act as though the user dismissed it.
        if (activity == null) {
            // TODO(timloh): This probably doesn't work, as this happens synchronously when creating
            // the PermissionPromptAndroid, so the PermissionRequestManager won't be ready yet.
            mDialogDelegate.onDismiss();
            destroyDelegate();
            return;
        }

        // Suppress modals while Chrome Home is open. Eventually we will want to handle other cases
        // whereby the tab is obscured so modals don't pop up on top of (e.g.) the tab switcher or
        // the three-dot menu.
        final BottomSheet bottomSheet = activity.getBottomSheet();
        if (bottomSheet == null || !bottomSheet.isVisible()) {
            showDialog();
        } else {
            bottomSheet.addObserver(new EmptyBottomSheetObserver() {
                @Override
                public void onSheetClosed(int reason) {
                    bottomSheet.removeObserver(this);
                    if (reason == BottomSheet.StateChangeReason.NAVIGATION
                            || reason == BottomSheet.StateChangeReason.NEW_TAB) {
                        // Dismiss the prompt as it would otherwise be dismissed momentarily once
                        // the navigation completes.
                        // TODO(timloh): This logs a dismiss (and we also already logged a show),
                        // even though the user didn't see anything.
                        mDialogDelegate.onDismiss();
                        destroyDelegate();
                    } else {
                        showDialog();
                    }
                }
            });
        }
    }

    private void showDialog() {
        assert mState == PROMPT_PENDING;

        // The tab may have navigated or been closed while we were waiting for Chrome Home to close.
        if (mDialogDelegate == null) {
            mState = NOT_SHOWING;
            scheduleDisplay();
            return;
        }

        ChromeActivity activity = mDialogDelegate.getTab().getActivity();
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.permission_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);

        mDialog = builder.create();
        mDialog.getDelegate().setHandleNativeActionModesEnabled(false);
        mDialog.setCanceledOnTouchOutside(false);

        TextView messageTextView = (TextView) view.findViewById(R.id.text);
        messageTextView.setText(prepareMainMessageString(mDialogDelegate));
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.announceForAccessibility(mDialogDelegate.getMessageText());
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                messageTextView, mDialogDelegate.getDrawableId(), 0, 0, 0);

        mDialog.setView(view);

        // Set the buttons to call the appropriate delegate methods. When the dialog is dismissed,
        // the delegate's native pointers are freed, and the next queued dialog (if any) is
        // displayed.
        mDialog.setButton(DialogInterface.BUTTON_POSITIVE, mDialogDelegate.getPrimaryButtonText(),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        assert mState == PROMPT_OPEN;
                        mState = PROMPT_ACCEPTED;
                    }
                });

        mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, mDialogDelegate.getSecondaryButtonText(),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        assert mState == PROMPT_OPEN;
                        mState = PROMPT_DENIED;
                    }
                });

        // Called when the dialog is dismissed. Interacting with either button in the dialog will
        // call this handler after the primary/secondary handler.
        mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mDialog = null;

                if (mDialogDelegate == null) {
                    // We get into here if a tab navigates or is closed underneath the prompt.
                    mState = NOT_SHOWING;
                    return;
                }

                if (mState == PROMPT_ACCEPTED) {
                    // Request Android permissions if necessary. This will call back into either
                    // onAndroidPermissionAccepted or onAndroidPermissionCanceled, which will
                    // schedule the next permission dialog. If it returns false, no system level
                    // permissions need to be requested, so just run the accept callback.
                    mState = REQUEST_ANDROID_PERMISSIONS;
                    if (!AndroidPermissionRequester.requestAndroidPermissions(
                                mDialogDelegate.getTab(), mDialogDelegate.getContentSettingsTypes(),
                                PermissionDialogController.this)) {
                        onAndroidPermissionAccepted();
                    }
                } else {
                    // Otherwise, run the necessary delegate callback immediately and schedule the
                    // next dialog.
                    if (mState == PROMPT_DENIED) {
                        mDialogDelegate.onCancel();
                    } else {
                        assert mState == PROMPT_OPEN;
                        mDialogDelegate.onDismiss();
                    }
                    destroyDelegate();
                    scheduleDisplay();
                }
            }
        });

        mDialog.show();
        mState = PROMPT_OPEN;
    }

    private CharSequence prepareMainMessageString(final PermissionDialogDelegate delegate) {
        String messageText = delegate.getMessageText();
        assert !TextUtils.isEmpty(messageText);

        // TODO(timloh): Currently the strings are shared with infobars, so we for now manually
        // remove the full stop (this code catches most but not all languages). Update the strings
        // after removing the infobar path.
        if (messageText.endsWith(".") || messageText.endsWith("。")) {
            messageText = messageText.substring(0, messageText.length() - 1);
        }

        return messageText;
    }

    public void dismissFromNative(PermissionDialogDelegate delegate) {
        if (mDialogDelegate == delegate) {
            // Some caution is required here to handle cases where the user actions or dismisses
            // the prompt at roughly the same time as native. Due to asynchronicity, this function
            // may be called after onClick and before onDismiss, or before both of those listeners.
            mDialogDelegate = null;
            if (mState == PROMPT_OPEN) {
                mDialog.dismiss();
            } else {
                assert mState == PROMPT_PENDING || mState == REQUEST_ANDROID_PERMISSIONS
                        || mState == PROMPT_DENIED || mState == PROMPT_ACCEPTED;
            }
        } else {
            assert mRequestQueue.contains(delegate);
            mRequestQueue.remove(delegate);
        }
        delegate.destroy();
    }

    private void destroyDelegate() {
        mDialogDelegate.destroy();
        mDialogDelegate = null;
        mState = NOT_SHOWING;
    }
}
