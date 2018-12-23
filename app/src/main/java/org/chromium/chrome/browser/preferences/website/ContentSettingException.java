// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.support.annotation.IntDef;

import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Exception information for a given origin.
 */
public class ContentSettingException implements Serializable {
    @IntDef({Type.ADS, Type.AUTOMATIC_DOWNLOADS, Type.AUTOPLAY,
             Type.BACKGROUND_SYNC, Type.COOKIE, Type.JAVASCRIPT, Type.POPUP,
             Type.SOUND})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
        // Values used to address array index below, inside Website and SingleWebsitePreferences.
        // Should be enumerated from 0 and can't have gaps.
        int ADS = 0;
        int AUTOPLAY = 1;
        int BACKGROUND_SYNC = 2;
        int COOKIE = 3;
        int JAVASCRIPT = 4;
        int POPUP = 5;
        int SOUND = 6;
        int AUTOMATIC_DOWNLOADS = 7;
        /**
         * Number of handled exceptions used for calculating array sizes.
         */
        int NUM_ENTRIES = 8;
    }

    // Mapping from {@link Type} to ContentSettingType.
    // TODO(https://crbug.com/616321) Add a unit test to verify that Type and
    // CONTENT_TYPES are in sync.
    final static int[] CONTENT_TYPES = {
            ContentSettingsType.CONTENT_SETTINGS_TYPE_ADS,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_AUTOPLAY,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_BACKGROUND_SYNC,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_SOUND,
            ContentSettingsType.CONTENT_SETTINGS_TYPE_AUTOMATIC_DOWNLOADS};

    private final int mContentSettingType;
    private final String mPattern;
    private final ContentSetting mContentSetting;
    private final String mSource;

    /**
     * Construct a ContentSettingException.
     * @param type The content setting type this exception covers.
     * @param pattern The host/domain pattern this exception covers.
     * @param setting The setting for this exception, e.g. ALLOW or BLOCK.
     * @param source The source for this exception, e.g. "policy".
     */
    public ContentSettingException(
            int type, String pattern, ContentSetting setting, String source) {
        mContentSettingType = type;
        mPattern = pattern;
        mContentSetting = setting;
        mSource = source;
    }

    public String getPattern() {
        return mPattern;
    }

    public ContentSetting getContentSetting() {
        return mContentSetting;
    }

    public String getSource() {
        return mSource;
    }

    /**
     * Sets the content setting value for this exception.
     */
    public void setContentSetting(ContentSetting value) {
        PrefServiceBridge.getInstance().nativeSetContentSettingForPattern(mContentSettingType,
                mPattern, value.toInt());
    }
}
