// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.RadioButtonWithDescription;

import java.util.Arrays;
import java.util.List;

/**
 * A 3-state Allowed/Ask/Blocked radio group Preference used for SiteSettings.
 */
public class TriStateSiteSettingsPreference
        extends Preference implements RadioButtonWithDescription.OnCheckedChangeListener {
    private @ContentSettingValues int mSetting = ContentSettingValues.DEFAULT;
    private int[] mDescriptionIds;
    private RadioButtonWithDescription mAllowed;
    private RadioButtonWithDescription mAsk;
    private RadioButtonWithDescription mBlocked;

    public TriStateSiteSettingsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param setting        The initial setting for this Preference
     * @param descriptionIds An array of 3 resource IDs for descriptions for
     *                       Allowed, Ask and Blocked states, in that order.
     */
    public void initialize(@ContentSettingValues int setting, int[] descriptionIds) {
        mSetting = setting;
        mDescriptionIds = descriptionIds;
    }

    /**
     * @return The current checked setting.
     */
    public @ContentSettingValues int getCheckedSetting() {
        return mSetting;
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        // Inflating from XML.
        setLayoutResource(R.layout.tri_state_site_settings_preference);
        return super.onCreateView(parent);
    }

    @Override
    public void onCheckedChanged() {
        if (mAllowed.isChecked())
            mSetting = ContentSettingValues.ALLOW;
        else if (mAsk.isChecked())
            mSetting = ContentSettingValues.ASK;
        else if (mBlocked.isChecked())
            mSetting = ContentSettingValues.BLOCK;

        callChangeListener(mSetting);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        mAllowed = (RadioButtonWithDescription) view.findViewById(R.id.allowed);
        mAsk = (RadioButtonWithDescription) view.findViewById(R.id.ask);
        mBlocked = (RadioButtonWithDescription) view.findViewById(R.id.blocked);

        mAllowed.setDescriptionText(getContext().getText(mDescriptionIds[0]));
        mAsk.setDescriptionText(getContext().getText(mDescriptionIds[1]));
        mBlocked.setDescriptionText(getContext().getText(mDescriptionIds[2]));

        List<RadioButtonWithDescription> radioGroup = Arrays.asList(mAllowed, mAsk, mBlocked);
        for (RadioButtonWithDescription option : radioGroup) {
            option.setRadioButtonGroup(radioGroup);
            option.setOnCheckedChangeListener(this);
        }

        RadioButtonWithDescription radioButton = findRadioButton(mSetting);
        if (radioButton != null) radioButton.setChecked(true);
    }

    /**
     * @param setting The setting to find RadioButton for.
     */
    private RadioButtonWithDescription findRadioButton(@ContentSettingValues int setting) {
        if (setting == ContentSettingValues.ALLOW)
            return mAllowed;
        else if (setting == ContentSettingValues.ASK)
            return mAsk;
        else if (setting == ContentSettingValues.BLOCK)
            return mBlocked;
        else
            return null;
    }
}
