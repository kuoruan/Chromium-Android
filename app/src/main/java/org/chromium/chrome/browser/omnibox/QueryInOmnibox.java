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
    private long mNativeQueryInOmniboxAndroid;

    public QueryInOmnibox(Profile profile) {
        assert profile != null;
        assert profile.isNativeInitialized();
        mNativeQueryInOmniboxAndroid = nativeInit(profile);
    }

    public void destroy() {
        nativeDestroy(mNativeQueryInOmniboxAndroid);
        mNativeQueryInOmniboxAndroid = 0;
    }

    /**
     * Extracts query terms from the current URL if it's a SRP URL from the default search engine.
     *
     * @param securityLevel The {@link ConnectionSecurityLevel} of the tab.
     * @param url The URL to extract search terms from.
     * @return The extracted search terms. Returns null if the Omnibox should not display the
     *         search terms.
     */
    public String getDisplaySearchTerms(@ConnectionSecurityLevel int securityLevel, String url) {
        assert mNativeQueryInOmniboxAndroid != 0;
        return nativeGetDisplaySearchTerms(mNativeQueryInOmniboxAndroid, securityLevel, url);
    }

    /**
     * Sets a flag telling the model to ignore the security level in its check for whether to
     * display search terms or not. This is useful for avoiding the flicker that occurs when loading
     * a SRP URL before our SSL state updates.
     *
     * @param ignore Whether or not we should ignore the security level.
     */
    public void setIgnoreSecurityLevelForSearchTerms(boolean ignore) {
        assert mNativeQueryInOmniboxAndroid != 0;
        nativeSetIgnoreSecurityLevel(mNativeQueryInOmniboxAndroid, ignore);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeQueryInOmniboxAndroid);
    private native String nativeGetDisplaySearchTerms(
            long nativeQueryInOmniboxAndroid, int securityLevel, String url);
    private native void nativeSetIgnoreSecurityLevel(
            long nativeQueryInOmniboxAndroid, boolean ignore);
}
