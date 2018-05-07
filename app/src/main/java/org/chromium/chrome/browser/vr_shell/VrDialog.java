// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Manages how dialogs look inside of VR.
 */
public class VrDialog extends FrameLayout {
    private static final int DIALOG_WIDTH = 1200;
    private VrDialogManager mVrDialogManager;

    /**
     * Constructor of VrDialog. Sets the DialogManager that will be used to
     * communicate with the vr presentation of the dialog.
     */
    public VrDialog(Context context, VrDialogManager vrDialogManager) {
        super(context);
        setLayoutParams(
                new FrameLayout.LayoutParams(DIALOG_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
        mVrDialogManager = vrDialogManager;
    }

    /**
     * Dismiss whatever dialog that is shown in VR.
     */
    public void dismiss() {
        mVrDialogManager.closeVrDialog();
    }

    /**
     * Initialize a dialog in VR based on the layout that was set by {@link
     * #setLayout(FrameLayout)}. This also adds a OnLayoutChangeListener to make sure that Dialog in
     * VR has the correct size.
     */
    public void initVrDialog() {
        addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mVrDialogManager.setDialogSize(getWidth(), getHeight());
            }
        });
        // TODO(asimjour): remove this when Keyboard supports native ui.
        disableSoftKeyboard(this);
        mVrDialogManager.initVrDialog(getWidth(), getHeight());
    }

    private void disableSoftKeyboard(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof ViewGroup) {
                disableSoftKeyboard((ViewGroup) view);
            } else if (view instanceof TextView) {
                ((TextView) view).setInputType(InputType.TYPE_NULL);
            }
        }
    }
}
