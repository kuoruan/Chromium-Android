// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import org.chromium.base.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * An expandable preference group that subclasses {@link ExpandablePreferenceGroup} to hide/reveal
 * child preferences on clicks.
 */
public class SigninExpandablePreferenceGroup extends ExpandablePreferenceGroup {
    private final List<Preference> mAllPreferences = new ArrayList<>();

    public SigninExpandablePreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        super.onClick();
        ThreadUtils.postOnUiThread(() -> setExpanded(!isExpanded()));
    }

    @Override
    public boolean addPreference(Preference preference) {
        if (mAllPreferences.contains(preference)) return true;
        mAllPreferences.add(preference);
        if (!isExpanded()) return true;
        return addPreferenceInternal(preference);
    }

    @Override
    public boolean removePreference(Preference preference) {
        if (!mAllPreferences.remove(preference)) return false;
        if (!isExpanded()) return true;
        return removePreferenceInternal(preference);
    }

    @Override
    protected void onExpandedChanged(boolean expanded) {
        if (expanded) {
            for (Preference preference : mAllPreferences) {
                addPreferenceInternal(preference);
            }
        } else {
            for (Preference preference : mAllPreferences) {
                removePreferenceInternal(preference);
            }
        }
    }

    private boolean addPreferenceInternal(Preference preference) {
        return super.addPreference(preference);
    }

    private boolean removePreferenceInternal(Preference preference) {
        return super.removePreference(preference);
    }
}
