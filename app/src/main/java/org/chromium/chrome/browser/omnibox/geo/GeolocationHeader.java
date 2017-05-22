// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.geo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.util.Base64;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Provides methods for building the X-Geo HTTP header, which provides device location to a server
 * when making an HTTP request.
 *
 * X-Geo header spec: https://goto.google.com/xgeospec.
 */
public class GeolocationHeader {
    private static final String TAG = "GeolocationHeader";

    // Values for the histogram Geolocation.HeaderSentOrNot. Values 1, 5, 6, and 7 are defined in
    // histograms.xml and should not be used in other ways.
    public static final int UMA_LOCATION_DISABLED_FOR_GOOGLE_DOMAIN = 0;
    public static final int UMA_LOCATION_NOT_AVAILABLE = 2;
    public static final int UMA_LOCATION_STALE = 3;
    public static final int UMA_HEADER_SENT = 4;
    public static final int UMA_LOCATION_DISABLED_FOR_CHROME_APP = 5;
    public static final int UMA_MAX = 8;

    // Values for the histogram Geolocation.Header.PermissionState.
    // These are used to back an UMA histogram and so should be treated as append-only.
    //
    // In order to keep the names of these constants from being too long, the following were used:
    // UMA_PERM to indicate UMA location permission related metrics,
    // APP_YES (instead of APP_GRANTED) to indicate App permission granted,
    // DOMAIN_YES (instead of DOMAIN_GRANTED) to indicate Domain permission granted.
    public static final int UMA_PERM_UNKNOWN = 0;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_YES_LOCATION = 1;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_YES_NO_LOCATION = 2;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_PROMPT_LOCATION = 3;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_PROMPT_NO_LOCATION = 4;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_BLOCKED = 5;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_YES = 6;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_PROMPT = 7;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_BLOCKED = 8;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_YES = 9;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_PROMPT = 10;
    public static final int UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_BLOCKED = 11;
    public static final int UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_YES_LOCATION = 12;
    public static final int UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_YES_NO_LOCATION = 13;
    public static final int UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_PROMPT_LOCATION = 14;
    public static final int UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_PROMPT_NO_LOCATION = 15;
    public static final int UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_BLOCKED = 16;
    public static final int UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_YES = 17;
    public static final int UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_PROMPT = 18;
    public static final int UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_BLOCKED = 19;
    public static final int UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_YES = 20;
    public static final int UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_PROMPT = 21;
    public static final int UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_BLOCKED = 22;
    public static final int UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_YES_LOCATION = 23;
    public static final int UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_YES_NO_LOCATION = 24;
    public static final int UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_PROMPT_LOCATION = 25;
    public static final int UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_PROMPT_NO_LOCATION = 26;
    public static final int UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_BLOCKED = 27;
    public static final int UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_YES = 28;
    public static final int UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_PROMPT = 29;
    public static final int UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_BLOCKED = 30;
    public static final int UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_YES = 31;
    public static final int UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_PROMPT = 32;
    public static final int UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_BLOCKED = 33;
    public static final int UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_YES = 34;
    public static final int UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_PROMPT = 35;
    public static final int UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_BLOCKED = 36;
    public static final int UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_YES = 37;
    public static final int UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_PROMPT = 38;
    public static final int UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_BLOCKED = 39;
    public static final int UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_YES = 40;
    public static final int UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_PROMPT = 41;
    public static final int UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_BLOCKED = 42;
    public static final int UMA_PERM_UNSUITABLE_URL = 43;
    public static final int UMA_PERM_NOT_HTTPS = 44;
    public static final int UMA_PERM_COUNT = 45;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({UMA_PERM_UNKNOWN, UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_YES_LOCATION,
            UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_YES_NO_LOCATION,
            UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_PROMPT_LOCATION,
            UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_PROMPT_NO_LOCATION,
            UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_BLOCKED,
            UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_YES,
            UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_PROMPT,
            UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_BLOCKED,
            UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_YES,
            UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_PROMPT,
            UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_BLOCKED,
            UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_YES_LOCATION,
            UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_YES_NO_LOCATION,
            UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_PROMPT_LOCATION,
            UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_PROMPT_NO_LOCATION,
            UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_BLOCKED,
            UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_YES,
            UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_PROMPT,
            UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_BLOCKED,
            UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_YES,
            UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_PROMPT,
            UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_BLOCKED,
            UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_YES_LOCATION,
            UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_YES_NO_LOCATION,
            UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_PROMPT_LOCATION,
            UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_PROMPT_NO_LOCATION,
            UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_BLOCKED, UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_YES,
            UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_PROMPT, UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_BLOCKED,
            UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_YES, UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_PROMPT,
            UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_BLOCKED, UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_YES,
            UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_PROMPT, UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_BLOCKED,
            UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_YES, UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_PROMPT,
            UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_BLOCKED,
            UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_YES,
            UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_PROMPT,
            UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_BLOCKED, UMA_PERM_UNSUITABLE_URL,
            UMA_PERM_NOT_HTTPS})
    public @interface UmaPermission {}

    private static final int LOCATION_SOURCE_HIGH_ACCURACY = 0;
    private static final int LOCATION_SOURCE_BATTERY_SAVING = 1;
    private static final int LOCATION_SOURCE_GPS_ONLY = 2;
    private static final int LOCATION_SOURCE_MASTER_OFF = 3;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LOCATION_SOURCE_HIGH_ACCURACY, LOCATION_SOURCE_BATTERY_SAVING,
            LOCATION_SOURCE_GPS_ONLY, LOCATION_SOURCE_MASTER_OFF})
    private @interface LocationSource {}

    private static final int PERMISSION_GRANTED = 0;
    private static final int PERMISSION_PROMPT = 1;
    private static final int PERMISSION_BLOCKED = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PERMISSION_GRANTED, PERMISSION_PROMPT, PERMISSION_BLOCKED})
    private @interface Permission {}

    /** The maximum value for the GeolocationHeader.TimeListening* histograms. */
    public static final int TIME_LISTENING_HISTOGRAM_MAX_MILLIS = 50 * 60 * 1000; // 50 minutes

    /** The maximum value for the GeolocationHeader.LocationAge* histograms. */
    public static final int LOCATION_AGE_HISTOGRAM_MAX_SECONDS = 30 * 24 * 60 * 60; // 30 days

    private static final int HEADER_ENABLED = 0;
    private static final int INCOGNITO = 1;
    private static final int UNSUITABLE_URL = 2;
    private static final int NOT_HTTPS = 3;
    private static final int LOCATION_PERMISSION_BLOCKED = 4;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({HEADER_ENABLED, INCOGNITO, UNSUITABLE_URL, NOT_HTTPS, LOCATION_PERMISSION_BLOCKED})
    private @interface HeaderState {}

    /** The maximum age in milliseconds of a location that we'll send in an X-Geo header. */
    private static final int MAX_LOCATION_AGE = 24 * 60 * 60 * 1000;  // 24 hours

    /** The maximum age in milliseconds of a location before we'll request a refresh. */
    private static final int REFRESH_LOCATION_AGE = 5 * 60 * 1000;  // 5 minutes

    /** The time of the first location refresh. Contains Long.MAX_VALUE if not set. */
    private static long sFirstLocationTime = Long.MAX_VALUE;

    /**
     * Requests a location refresh so that a valid location will be available for constructing
     * an X-Geo header in the near future (i.e. within 5 minutes).
     */
    public static void primeLocationForGeoHeader() {
        if (!hasGeolocationPermission()) return;

        if (sFirstLocationTime == Long.MAX_VALUE) {
            sFirstLocationTime = SystemClock.elapsedRealtime();
        }
        GeolocationTracker.refreshLastKnownLocation(
                ContextUtils.getApplicationContext(), REFRESH_LOCATION_AGE);
    }

    /**
     * Returns whether the X-Geo header is allowed to be sent for the current URL.
     *
     * @param url The URL of the request with which this header will be sent.
     * @param isIncognito Whether the request will happen in an incognito tab.
     */
    public static boolean isGeoHeaderEnabledForUrl(String url, boolean isIncognito) {
        return geoHeaderStateForUrl(url, isIncognito, false) == HEADER_ENABLED;
    }

    @HeaderState
    private static int geoHeaderStateForUrl(String url, boolean isIncognito, boolean recordUma) {
        // Only send X-Geo in normal mode.
        if (isIncognito) return INCOGNITO;

        // Only send X-Geo header to Google domains.
        if (!UrlUtilities.nativeIsGoogleSearchUrl(url)) return UNSUITABLE_URL;

        Uri uri = Uri.parse(url);
        if (!UrlConstants.HTTPS_SCHEME.equals(uri.getScheme())) return NOT_HTTPS;

        if (!hasGeolocationPermission()) {
            if (recordUma) recordHistogram(UMA_LOCATION_DISABLED_FOR_CHROME_APP);
            return LOCATION_PERMISSION_BLOCKED;
        }

        // Only send X-Geo header if the user hasn't disabled geolocation for url.
        if (isLocationDisabledForUrl(uri, isIncognito)) {
            if (recordUma) recordHistogram(UMA_LOCATION_DISABLED_FOR_GOOGLE_DOMAIN);
            return LOCATION_PERMISSION_BLOCKED;
        }

        return HEADER_ENABLED;
    }

    /**
     * Returns an X-Geo HTTP header string if:
     *  1. The current mode is not incognito.
     *  2. The url is a google search URL (e.g. www.google.co.uk/search?q=cars), and
     *  3. The user has not disabled sharing location with this url, and
     *  4. There is a valid and recent location available.
     *
     * Returns null otherwise.
     *
     * @param url The URL of the request with which this header will be sent.
     * @param tab The Tab currently being accessed.
     * @return The X-Geo header string or null.
     */
    public static String getGeoHeader(String url, Tab tab) {
        boolean isIncognito = tab.isIncognito();
        boolean locationAttached = true;
        Location location = null;
        long locationAge = Long.MAX_VALUE;
        @HeaderState int headerState = geoHeaderStateForUrl(url, isIncognito, true);
        if (headerState == HEADER_ENABLED) {
            // Only send X-Geo header if there's a fresh location available.
            location =
                    GeolocationTracker.getLastKnownLocation(ContextUtils.getApplicationContext());
            if (location == null) {
                recordHistogram(UMA_LOCATION_NOT_AVAILABLE);
                locationAttached = false;
            } else {
                locationAge = GeolocationTracker.getLocationAge(location);
                if (locationAge > MAX_LOCATION_AGE) {
                    recordHistogram(UMA_LOCATION_STALE);
                    locationAttached = false;
                }
            }
        } else {
            locationAttached = false;
        }

        @LocationSource int locationSource = getLocationSource();
        @Permission int appPermission = getGeolocationPermission(tab);
        @Permission int domainPermission = getDomainPermission(url, isIncognito);

        // Record the permission state with a histogram.
        recordPermissionHistogram(
                locationSource, appPermission, domainPermission, locationAttached, headerState);

        if (locationSource != LOCATION_SOURCE_MASTER_OFF && appPermission != PERMISSION_BLOCKED
                && domainPermission != PERMISSION_BLOCKED && !isIncognito) {
            // Record the Location Age with a histogram.
            recordLocationAgeHistogram(locationSource, locationAge);
            long duration = sFirstLocationTime == Long.MAX_VALUE
                    ? 0
                    : SystemClock.elapsedRealtime() - sFirstLocationTime;
            // Record the Time Listening with a histogram.
            recordTimeListeningHistogram(locationSource, locationAttached, duration);
        }

        // Note that strictly speaking "location == null" is not needed here as the
        // logic above prevents location being null when locationAttached is true.
        // It is here to prevent problems if the logic above is changed.
        if (!locationAttached || location == null) return null;

        recordHistogram(UMA_HEADER_SENT);

        // Timestamp in microseconds since the UNIX epoch.
        long timestamp = location.getTime() * 1000;
        // Latitude times 1e7.
        int latitude = (int) (location.getLatitude() * 10000000);
        // Longitude times 1e7.
        int longitude = (int) (location.getLongitude() * 10000000);
        // Radius of 68% accuracy in mm.
        int radius = (int) (location.getAccuracy() * 1000);

        // Encode location using ascii protobuf format followed by base64 encoding.
        // https://goto.google.com/partner_location_proto
        String locationAscii = String.format(Locale.US,
                "role:1 producer:12 timestamp:%d latlng{latitude_e7:%d longitude_e7:%d} radius:%d",
                timestamp, latitude, longitude, radius);
        String locationBase64 = new String(Base64.encode(locationAscii.getBytes(), Base64.NO_WRAP));

        return "X-Geo: a " + locationBase64;
    }

    @CalledByNative
    static boolean hasGeolocationPermission() {
        int pid = Process.myPid();
        int uid = Process.myUid();
        if (ApiCompatibilityUtils.checkPermission(ContextUtils.getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION, pid, uid)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        // Work around a bug in OnePlus2 devices running Lollipop, where the NETWORK_PROVIDER
        // incorrectly requires FINE_LOCATION permission (it should only require COARSE_LOCATION
        // permission). http://crbug.com/580733
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                && ApiCompatibilityUtils.checkPermission(ContextUtils.getApplicationContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION, pid, uid)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return true;
    }

    /**
     * Returns the app level geolocation permission.
     * This permission can be either granted, blocked or prompt.
     */
    @Permission
    static int getGeolocationPermission(Tab tab) {
        if (hasGeolocationPermission()) return PERMISSION_GRANTED;
        return tab.getWindowAndroid().canRequestPermission(
                       Manifest.permission.ACCESS_COARSE_LOCATION)
                ? PERMISSION_PROMPT
                : PERMISSION_BLOCKED;
    }

    /**
     * Returns true if the user has disabled sharing their location with url (e.g. via the
     * geolocation infobar).
     */
    static boolean isLocationDisabledForUrl(Uri uri, boolean isIncognito) {
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CONSISTENT_OMNIBOX_GEOLOCATION)) {
            boolean enabled = WebsitePreferenceBridge.shouldUseDSEGeolocationSetting(
                                      uri.toString(), isIncognito)
                    && WebsitePreferenceBridge.getDSEGeolocationSetting();
            return !enabled;
        } else {
            return locationContentSettingForUrl(uri, isIncognito) == ContentSetting.BLOCK;
        }
    }

    /**
     * Returns the location permission for sharing their location with url (e.g. via the
     * geolocation infobar).
     */
    static ContentSetting locationContentSettingForUrl(Uri uri, boolean isIncognito) {
        GeolocationInfo locationSettings = new GeolocationInfo(uri.toString(), null, isIncognito);
        ContentSetting locationPermission = locationSettings.getContentSetting();
        return locationPermission;
    }

    /** Records a data point for the Geolocation.HeaderSentOrNot histogram. */
    private static void recordHistogram(int result) {
        RecordHistogram.recordEnumeratedHistogram("Geolocation.HeaderSentOrNot", result, UMA_MAX);
    }

    /** Returns the location source. */
    @LocationSource
    // We should replace our usage of LOCATION_PROVIDERS_ALLOWED when the min API is 19.
    @SuppressWarnings("deprecation")
    private static int getLocationSource() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int locationMode;
            try {
                locationMode = Settings.Secure.getInt(
                        ContextUtils.getApplicationContext().getContentResolver(),
                        Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                Log.e(TAG, "Error getting the LOCATION_MODE");
                return LOCATION_SOURCE_MASTER_OFF;
            }
            if (locationMode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY) {
                return LOCATION_SOURCE_HIGH_ACCURACY;
            } else if (locationMode == Settings.Secure.LOCATION_MODE_SENSORS_ONLY) {
                return LOCATION_SOURCE_GPS_ONLY;
            } else if (locationMode == Settings.Secure.LOCATION_MODE_BATTERY_SAVING) {
                return LOCATION_SOURCE_BATTERY_SAVING;
            } else {
                return LOCATION_SOURCE_MASTER_OFF;
            }
        } else {
            String locationProviders = Settings.Secure.getString(
                    ContextUtils.getApplicationContext().getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            if (locationProviders.contains(LocationManager.GPS_PROVIDER)
                    && locationProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                return LOCATION_SOURCE_HIGH_ACCURACY;
            } else if (locationProviders.contains(LocationManager.GPS_PROVIDER)) {
                return LOCATION_SOURCE_GPS_ONLY;
            } else if (locationProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                return LOCATION_SOURCE_BATTERY_SAVING;
            } else {
                return LOCATION_SOURCE_MASTER_OFF;
            }
        }
    }

    /**
     * Returns the domain permission as either granted, blocked or prompt.
     * This is based upon the location permission for sharing their location with url (e.g. via the
     * geolocation infobar).
     */
    @Permission
    private static int getDomainPermission(String url, boolean isIncognito) {
        ContentSetting domainPermission = locationContentSettingForUrl(Uri.parse(url), isIncognito);
        switch (domainPermission) {
            case ALLOW:
                return PERMISSION_GRANTED;
            case ASK:
                return PERMISSION_PROMPT;
            default:
                return PERMISSION_BLOCKED;
        }
    }

    /**
     * Returns the enum to use in the Geolocation.Header.PermissionState histogram.
     * Unexpected input values return UMA_PERM_UNKNOWN.
     */
    @UmaPermission
    private static int getPermissionHistogramEnum(@LocationSource int locationSource,
            @Permission int appPermission, @Permission int domainPermission,
            boolean locationAttached, @HeaderState int headerState) {
        if (headerState == UNSUITABLE_URL) return UMA_PERM_UNSUITABLE_URL;
        if (headerState == NOT_HTTPS) return UMA_PERM_NOT_HTTPS;
        if (locationSource == LOCATION_SOURCE_HIGH_ACCURACY) {
            if (appPermission == PERMISSION_GRANTED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return locationAttached ? UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_YES_LOCATION
                                            : UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_YES_NO_LOCATION;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return locationAttached
                            ? UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_PROMPT_LOCATION
                            : UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_PROMPT_NO_LOCATION;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_HIGH_ACCURACY_APP_YES_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_PROMPT) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_HIGH_ACCURACY_APP_PROMPT_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_BLOCKED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_HIGH_ACCURACY_APP_BLOCKED_DOMAIN_BLOCKED;
                }
            }
        } else if (locationSource == LOCATION_SOURCE_BATTERY_SAVING) {
            if (appPermission == PERMISSION_GRANTED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return locationAttached
                            ? UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_YES_LOCATION
                            : UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_YES_NO_LOCATION;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return locationAttached
                            ? UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_PROMPT_LOCATION
                            : UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_PROMPT_NO_LOCATION;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_BATTERY_SAVING_APP_YES_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_PROMPT) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_BATTERY_SAVING_APP_PROMPT_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_BLOCKED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_BATTERY_SAVING_APP_BLOCKED_DOMAIN_BLOCKED;
                }
            }
        } else if (locationSource == LOCATION_SOURCE_GPS_ONLY) {
            if (appPermission == PERMISSION_GRANTED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return locationAttached ? UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_YES_LOCATION
                                            : UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_YES_NO_LOCATION;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return locationAttached ? UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_PROMPT_LOCATION
                                            : UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_PROMPT_NO_LOCATION;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_GPS_ONLY_APP_YES_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_PROMPT) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_GPS_ONLY_APP_PROMPT_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_BLOCKED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_GPS_ONLY_APP_BLOCKED_DOMAIN_BLOCKED;
                }
            }
        } else if (locationSource == LOCATION_SOURCE_MASTER_OFF) {
            if (appPermission == PERMISSION_GRANTED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_MASTER_OFF_APP_YES_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_PROMPT) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_MASTER_OFF_APP_PROMPT_DOMAIN_BLOCKED;
                }
            } else if (appPermission == PERMISSION_BLOCKED) {
                if (domainPermission == PERMISSION_GRANTED) {
                    return UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_YES;
                } else if (domainPermission == PERMISSION_PROMPT) {
                    return UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_PROMPT;
                } else if (domainPermission == PERMISSION_BLOCKED) {
                    return UMA_PERM_MASTER_OFF_APP_BLOCKED_DOMAIN_BLOCKED;
                }
            }
        }
        return UMA_PERM_UNKNOWN;
    }

    /** Records a data point for the Geolocation.Header.PermissionState histogram. */
    private static void recordPermissionHistogram(@LocationSource int locationSource,
            @Permission int appPermission, @Permission int domainPermission,
            boolean locationAttached, @HeaderState int headerState) {
        if (headerState == INCOGNITO) return;
        @UmaPermission
        int result = getPermissionHistogramEnum(
                locationSource, appPermission, domainPermission, locationAttached, headerState);
        RecordHistogram.recordEnumeratedHistogram(
                "Geolocation.Header.PermissionState", result, UMA_PERM_COUNT);
    }

    /**
     * Determines the name for a Time Listening Histogram. Returns empty string if the location
     * source is MASTER_OFF as we do not record histograms for that case.
     */
    private static String getTimeListeningHistogramEnum(
            int locationSource, boolean locationAttached) {
        switch (locationSource) {
            case LOCATION_SOURCE_HIGH_ACCURACY:
                return locationAttached
                        ? "Geolocation.Header.TimeListening.HighAccuracy.LocationAttached"
                        : "Geolocation.Header.TimeListening.HighAccuracy.LocationNotAttached";
            case LOCATION_SOURCE_GPS_ONLY:
                return locationAttached
                        ? "Geolocation.Header.TimeListening.GpsOnly.LocationAttached"
                        : "Geolocation.Header.TimeListening.GpsOnly.LocationNotAttached";
            case LOCATION_SOURCE_BATTERY_SAVING:
                return locationAttached
                        ? "Geolocation.Header.TimeListening.BatterySaving.LocationAttached"
                        : "Geolocation.Header.TimeListening.BatterySaving.LocationNotAttached";
            default:
                Log.e(TAG, "Unexpected locationSource: " + locationSource);
                assert false : "Unexpected locationSource: " + locationSource;
                return null;
        }
    }

    /** Records a data point for one of the GeolocationHeader.TimeListening* histograms. */
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private static void recordTimeListeningHistogram(
            int locationSource, boolean locationAttached, long duration) {
        String name = getTimeListeningHistogramEnum(locationSource, locationAttached);
        if (name == null) return;
        RecordHistogram.recordCustomTimesHistogram(
                name, duration, 1, TIME_LISTENING_HISTOGRAM_MAX_MILLIS, TimeUnit.MILLISECONDS, 50);
    }

    /** Records a data point for one of the GeolocationHeader.LocationAge* histograms. */
    private static void recordLocationAgeHistogram(int locationSource, long durationMillis) {
        String name = "";
        if (locationSource == LOCATION_SOURCE_HIGH_ACCURACY) {
            name = "Geolocation.Header.LocationAge.HighAccuracy";
        } else if (locationSource == LOCATION_SOURCE_GPS_ONLY) {
            name = "Geolocation.Header.LocationAge.GpsOnly";
        } else if (locationSource == LOCATION_SOURCE_BATTERY_SAVING) {
            name = "Geolocation.Header.LocationAge.BatterySaving";
        } else {
            Log.e(TAG, "Unexpected locationSource: " + locationSource);
            assert false : "Unexpected locationSource: " + locationSource;
            return;
        }
        long durationSeconds = durationMillis / 1000;
        int duration = durationSeconds >= (long) Integer.MAX_VALUE ? Integer.MAX_VALUE
                                                                   : (int) durationSeconds;
        RecordHistogram.recordCustomCountHistogram(
                name, duration, 1, LOCATION_AGE_HISTOGRAM_MAX_SECONDS, 50);
    }
}
