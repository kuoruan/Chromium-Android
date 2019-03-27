// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.usage_stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that tracks the mapping between tokens and fully-qualified domain names (FQDNs).
 */
public class TokenTracker {
    private Map<String, String> mFqdnToTokenMap;
    private Map<String, String> mTokenToFqdnMap;
    private TokenGenerator mTokenGenerator;

    public TokenTracker() {
        mTokenToFqdnMap = new HashMap<>();
        mFqdnToTokenMap = new HashMap<>();
        mTokenGenerator = new TokenGenerator();
    }

    /**
     * Associate a new token with FQDN, and return that token.
     * If we're already tracking FQDN, return the corresponding token.
     */
    public String startTrackingWebsite(String fqdn) {
        if (isTrackingFqdn(fqdn)) {
            return mFqdnToTokenMap.get(fqdn);
        } else {
            String token = mTokenGenerator.nextToken();
            putMapping(token, fqdn);
            return token;
        }
    }

    /** Remove token and its associated FQDN, if we're  already tracking token. */
    public void stopTrackingToken(String token) {
        if (isTrackingToken(token)) {
            mFqdnToTokenMap.remove(getFqdnForToken(token));
            mTokenToFqdnMap.remove(token);
        }
    }

    /** Returns the token for a given FQDN, or null if we're not tracking that FQDN. */
    public String getTokenForFqdn(String fqdn) {
        return mFqdnToTokenMap.get(fqdn);
    }

    /** Get all the tokens we're tracking. */
    public List<String> getAllTrackedTokens() {
        List<String> result = new ArrayList<>();
        result.addAll(mTokenToFqdnMap.keySet());
        return result;
    }

    private String getFqdnForToken(String token) {
        return mTokenToFqdnMap.get(token);
    }

    private void putMapping(String token, String fqdn) {
        mTokenToFqdnMap.put(token, fqdn);
        mFqdnToTokenMap.put(fqdn, token);
    }

    private boolean isTrackingFqdn(String fqdn) {
        return mFqdnToTokenMap.containsKey(fqdn);
    }

    private boolean isTrackingToken(String token) {
        return mTokenToFqdnMap.containsKey(token);
    }
}