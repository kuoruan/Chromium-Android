// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr;

/**
 * Class used to create ArDelegate instances.
 */
public class ArDelegateProvider {
    /**
     * Cached instance of ArDelegate implementation. It is ok to cache since the
     * inclusion of ArDelegateImpl is controlled at build time.
     */
    private static ArDelegate sDelegate;

    /**
     * True if sDelegate already contains cached result, false otherwise.
     */
    private static boolean sDelegateInitialized;

    /**
     * Provides an instance of ArDelegate.
     */
    public static ArDelegate getDelegate() {
        if (sDelegateInitialized) return sDelegate;

        try {
            sDelegate = (ArDelegate) Class.forName("org.chromium.chrome.browser.vr.ArDelegateImpl")
                                .newInstance();
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } finally {
            sDelegateInitialized = true;
        }

        return sDelegate;
    }
}
