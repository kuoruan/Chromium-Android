// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.modaldialog.ModalDialogView;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.download.DownloadDirectoryList;
import org.chromium.chrome.browser.preferences.download.DownloadDirectoryPreference;
import org.chromium.chrome.browser.widget.AlertDialogEditText;

import java.io.File;

import javax.annotation.Nullable;

/**
 * Dialog that is displayed to ask user where they want to download the file.
 */
public class DownloadLocationDialog extends ModalDialogView implements View.OnFocusChangeListener {
    private DownloadDirectoryList mDownloadDirectoryUtil;

    private AlertDialogEditText mFileName;
    private AlertDialogEditText mFileLocation;
    private CheckBox mDontShowAgain;

    /**
     * Create a {@link DownloadLocationDialog} with the given properties.
     *
     * @param controller    Controller that listens to the events from the dialog.
     * @param context       Context from which the dialog emerged.
     * @param suggestedPath The path that was automatically generated, used as a starting point.
     * @return              A {@link DownloadLocationDialog} with the given properties.
     */
    public static DownloadLocationDialog create(
            Controller controller, Context context, File suggestedPath) {
        Params params = new Params();
        params.title = context.getString(R.string.download_location_dialog_title);
        params.positiveButtonTextId = R.string.duplicate_download_infobar_download_button;
        params.negativeButtonTextId = R.string.cancel;
        params.customView =
                LayoutInflater.from(context).inflate(R.layout.download_location_dialog, null);

        return new DownloadLocationDialog(controller, context, suggestedPath, params);
    }

    private DownloadLocationDialog(
            Controller controller, Context context, File suggestedPath, Params params) {
        super(controller, params);

        mDownloadDirectoryUtil = new DownloadDirectoryList(context);

        mFileName = (AlertDialogEditText) params.customView.findViewById(R.id.file_name);
        mFileName.setText(suggestedPath.getName());

        mFileLocation = (AlertDialogEditText) params.customView.findViewById(R.id.file_location);
        // NOTE: This makes the EditText correctly styled but not editable.
        mFileLocation.setInputType(InputType.TYPE_NULL);
        mFileLocation.setOnFocusChangeListener(this);
        setFileLocation(suggestedPath.getParentFile());

        // Automatically check "don't show again" the first time the user is seeing the dialog.
        mDontShowAgain = (CheckBox) params.customView.findViewById(R.id.show_again_checkbox);
        boolean isInitial = PrefServiceBridge.getInstance().getPromptForDownloadAndroid()
                == DownloadPromptStatus.SHOW_INITIAL;
        mDontShowAgain.setChecked(isInitial);
    }

    // Helper methods available to DownloadLocationDialogBridge.

    /**
     * Update the string in the file location text view.
     *
     * @param location  The location that the download will go to.
     */
    void setFileLocation(File location) {
        if (mFileLocation == null) return;
        mFileLocation.setText(mDownloadDirectoryUtil.getNameForFile(location));
    }

    /**
     * @return  The text that the user inputted as the name of the file.
     */
    @Nullable
    String getFileName() {
        if (mFileName == null) return null;
        return mFileName.getText().toString();
    }

    /**
     * @return  The file path based on what the user selected as the location of the file.
     */
    @Nullable
    File getFileLocation() {
        if (mFileLocation == null) return null;
        return mDownloadDirectoryUtil.getFileForName(mFileLocation.getText().toString());
    }

    /**
     * @return  Whether the "don't show again" checkbox is checked.
     */
    boolean getDontShowAgain() {
        return mDontShowAgain != null && mDontShowAgain.isChecked();
    }

    // View.OnFocusChange implementation.

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        // When the file location text view is clicked.
        if (hasFocus) {
            Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                    getContext(), DownloadDirectoryPreference.class.getName());
            getContext().startActivity(intent);
        }
    }
}
