// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ssl;

import android.content.Context;
import android.net.ConnectivityManager;

import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Helper class for captive portal related methods on Android. */
@JNINamespace("chrome::android")
public class CaptivePortalHelper {
    private static final String DEFAULT_PORTAL_CHECK_URL =
            "http://connectivitycheck.gstatic.com/generate_204";

    public static void setCaptivePortalCertificateForTesting(String spkiHash) {
        nativeSetCaptivePortalCertificateForTesting(spkiHash);
    }

    public static void setOSReportsCaptivePortalForTesting(boolean osReportsCaptivePortal) {
        nativeSetOSReportsCaptivePortalForTesting(osReportsCaptivePortal);
    }

    @CalledByNative
    private static String getCaptivePortalServerUrl() {
        // Since Android N MR2 it is possible that a captive portal was detected with a
        // different URL than getCaptivePortalServerUrl(). By default, Android uses the URL from
        // getCaptivePortalServerUrl() first, but there are also two additional fallback HTTP
        // URLs to probe if the first HTTP probe does not find anything. Using the default URL
        // is acceptable as the return value is only used by the captive portal interstitial.
        try {
            Context context = ContextUtils.getApplicationContext();
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Method getCaptivePortalServerUrlMethod =
                    connectivityManager.getClass().getMethod("getCaptivePortalServerUrl");
            return (String) getCaptivePortalServerUrlMethod.invoke(connectivityManager);
        } catch (NoSuchMethodException e) {
            // To avoid crashing, return the default portal check URL on Android.
            return DEFAULT_PORTAL_CHECK_URL;
        } catch (IllegalAccessException e) {
            return DEFAULT_PORTAL_CHECK_URL;
        } catch (InvocationTargetException e) {
            return DEFAULT_PORTAL_CHECK_URL;
        }
    }

    private CaptivePortalHelper() {}

    private static native void nativeSetCaptivePortalCertificateForTesting(String spkiHash);

    private static native void nativeSetOSReportsCaptivePortalForTesting(
            boolean osReportsCaptivePortal);
}
