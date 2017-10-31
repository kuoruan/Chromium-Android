// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * A dialog to notify users that WebAPKs need a network connection to launch.
 */

public class WebApkOfflineDialog {
    /** A listener which is notified when user quits the dialog. */
    public interface DialogListener { void onQuit(); }

    private Dialog mDialog;

    /**
     * Shows the dialog that notifies users that the WebAPK is offline.
     * @param context The current context.
     * @param listener The listener for the dialog.
     * @param appName The name of the WebAPK for which the dialog is shown.
     */
    public void show(Context context, final DialogListener listener, String appName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme);
        builder.setMessage(context.getString(R.string.webapk_offline_dialog, appName))
                .setNegativeButton(R.string.webapk_offline_dialog_quit_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                listener.onQuit();
                            }
                        });

        mDialog = builder.create();
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.show();
    };

    /** Closes the dialog. */
    public void cancel() {
        mDialog.cancel();
    }
}
