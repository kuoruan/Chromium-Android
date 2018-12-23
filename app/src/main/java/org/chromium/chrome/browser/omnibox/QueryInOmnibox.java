// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.security_state.ConnectionSecurityLevel;

/**
 * Bridge to the native QueryInOmniboxAndroid.
 */
public class QueryInOmnibox {
    /**
     * Extracts query terms from the current URL if it's a SRP URL from the default search engine.
     *
     * @param profile The Profile associated with the tab.
     * @param securityLevel The {@link ConnectionSecurityLevel} of the tab.
     * @param url The URL to extract search terms from.
     * @return The extracted search terms. Returns null if the Omnibox should not display the
     *         search terms.
     */
    public static String getDisplaySearchTerms(
            Profile profile, @ConnectionSecurityLevel int securityLevel, String url) {
        return nativeGetDisplaySearchTerms(profile, securityLevel, url);
    }

    /**
     * Sets a flag telling the model to ignore the security level in its check for whether to
     * display search terms or not. This is useful for avoiding the flicker that occurs when
     * loading a SRP URL before our SSL state updates.
     *
     * @param profile The Profile associated with the tab.
     * @param ignore Whether or not we should ignore the security level.
     */
    public static void setIgnoreSecurityLevelForSearchTerms(Profile profile, boolean ignore) {
        nativeSetIgnoreSecurityLevel(profile, ignore);
    }

    private static native String nativeGetDisplaySearchTerms(
            Profile profile, int securityLevel, String url);
    private static native void nativeSetIgnoreSecurityLevel(Profile profile, boolean ignore);
}
