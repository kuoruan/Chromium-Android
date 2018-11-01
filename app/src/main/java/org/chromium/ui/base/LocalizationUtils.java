// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.content.res.Configuration;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.LocaleUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

import java.util.Locale;

/**
 * This class provides the locale related methods for the native library.
 */
@JNINamespace("l10n_util")
public class LocalizationUtils {

    // This is mirrored from base/i18n/rtl.h. Please keep in sync.
    public static final int UNKNOWN_DIRECTION = 0;
    public static final int RIGHT_TO_LEFT = 1;
    public static final int LEFT_TO_RIGHT = 2;

    private static Boolean sIsLayoutRtl;

    private LocalizationUtils() { /* cannot be instantiated */ }

    @CalledByNative
    private static Locale getJavaLocale(String language, String country, String variant) {
        return new Locale(language, country, variant);
    }

    @CalledByNative
    private static String getDisplayNameForLocale(Locale locale, Locale displayLocale) {
        return locale.getDisplayName(displayLocale);
    }

    /**
     * Returns whether the Android layout direction is RTL.
     *
     * Note that the locale direction can be different from layout direction. Two known cases:
     * - RTL languages on Android 4.1, due to the lack of RTL layout support on 4.1.
     * - When user turned on force RTL layout option under developer options.
     *
     * Therefore, only this function should be used to query RTL for layout purposes.
     */
    @CalledByNative
    public static boolean isLayoutRtl() {
        if (sIsLayoutRtl == null) {
            Configuration configuration =
                    ContextUtils.getApplicationContext().getResources().getConfiguration();
            sIsLayoutRtl = Boolean.valueOf(ApiCompatibilityUtils.getLayoutDirection(configuration)
                    == View.LAYOUT_DIRECTION_RTL);
        }

        return sIsLayoutRtl.booleanValue();
    }

    @VisibleForTesting
    public static void setRtlForTesting(boolean shouldBeRtl) {
        sIsLayoutRtl = shouldBeRtl;
    }

    /**
     * Jni binding to base::i18n::GetFirstStrongCharacterDirection
     * @param string String to decide the direction.
     * @return One of the UNKNOWN_DIRECTION, RIGHT_TO_LEFT, and LEFT_TO_RIGHT.
     */
    public static int getFirstStrongCharacterDirection(String string) {
        assert string != null;
        return nativeGetFirstStrongCharacterDirection(string);
    }

    public static String substituteLocalePlaceholder(String str) {
        return str.replace("$LOCALE", LocaleUtils.getDefaultLocaleString().replace('-', '_'));
    }

    /**
     * @return the current Chromium locale used to display UI elements.
     *
     * This matches what the Android framework resolves localized string resources to, using the
     * system locale and the application's resources. For example, if the system uses a locale
     * that is not supported by Chromium resources (e.g. 'fur-rIT'), Android will likely fallback
     * to 'en-rUS' strings when Resources.getString() is called, and this method will return the
     * matching Chromium name (i.e. 'en-US').
     *
     * Using this value is only necessary to ensure that the strings accessed from the locale .pak
     * files from C++ match the resources displayed by the Java-based UI views.
     */
    public static String getUiLocaleStringForCompressedPak() {
        String uiLocale = ContextUtils.getApplicationContext().getResources().getString(
                org.chromium.ui.R.string.current_detected_ui_locale_name);
        return uiLocale;
    }

    /**
     * @return the language of the current Chromium locale used to display UI elements.
     */
    public static String getUiLanguageStringForCompressedPak() {
        String uiLocale = getUiLocaleStringForCompressedPak();
        int pos = uiLocale.indexOf('-');
        if (pos > 0) {
            return uiLocale.substring(0, pos);
        }
        return uiLocale;
    }

    /**
     * Return one default locale-specific PAK file name associated with a given language.
     *
     * @param language Language name (e.g. "en").
     * @return A Chromium-specific locale name (e.g. "en-US") matching the input language
     *         that can be used to access compressed locale pak files.
     */
    public static String getDefaultCompressedPakLocaleForLanguage(String language) {
        // IMPORTANT: Keep in sync with the mapping found in:
        // //build/android/gyp/resource_utils.py

        // NOTE: All languages provide locale files named '<language>.pak', except
        //       for a few exceptions listed below. E.g. for the English language,
        //       the 'en-US.pak' and 'en-GB.pak' files are provided, and there is
        //       no 'en.pak' file.
        switch (language) {
            case "en":
                return "en-US";
            case "pt":
                return "pt-PT";
            case "zh":
                return "zh-CN";
            default:
                // NOTE: for Spanish (es), both es.pak and es-419.pak are used. Hence this works.
                return language;
        }
    }

    private static native int nativeGetFirstStrongCharacterDirection(String string);
}
