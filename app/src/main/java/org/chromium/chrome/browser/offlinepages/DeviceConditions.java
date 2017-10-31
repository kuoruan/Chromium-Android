// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import org.chromium.base.VisibleForTesting;
import org.chromium.net.ConnectionType;
import org.chromium.net.NetworkChangeNotifier;

/** Device network and power conditions. */
public class DeviceConditions {
    private final boolean mPowerConnected;
    private final int mBatteryPercentage;
    private final int mNetConnectionType;
    private final boolean mPowerSaveOn;

    /**
     * Creates set of device network and power conditions.
     * @param powerConnected whether device is connected to power
     * @param batteryPercentage percentage (0-100) of remaining battery power
     * @param connectionType the org.chromium.net.ConnectionType value for the network connection
     */
    public DeviceConditions(boolean powerConnected, int batteryPercentage, int netConnectionType,
            boolean powerSaveOn) {
        mPowerConnected = powerConnected;
        mBatteryPercentage = batteryPercentage;
        mNetConnectionType = netConnectionType;
        mPowerSaveOn = powerSaveOn;
    }

    @VisibleForTesting
    DeviceConditions() {
        mPowerConnected = false;
        mBatteryPercentage = 0;
        mNetConnectionType = ConnectionType.CONNECTION_NONE;
        mPowerSaveOn = false;
    }

    /** Returns the current device conditions. May be overridden for testing. */
    public static DeviceConditions getCurrentConditions(Context context) {
        Intent batteryStatus = getBatteryStatus(context);
        if (batteryStatus == null) return null;

        return new DeviceConditions(isPowerConnected(context), getBatteryPercentage(context),
                getNetConnectionType(context), getInPowerSaveMode(context));
    }

    /** @return Whether power is connected. */
    public static boolean isPowerConnected(Context context) {
        Intent batteryStatus = getBatteryStatus(context);
        if (batteryStatus == null) return false;

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isConnected = (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);
        return isConnected;
    }

    /** @return Battery percentage. */
    public static int getBatteryPercentage(Context context) {
        Intent batteryStatus = getBatteryStatus(context);
        if (batteryStatus == null) return 0;

        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (scale == 0) return 0;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int percentage = Math.round(100 * level / (float) scale);
        return percentage;
    }

    /**
     * @return true if the device is in power save mode.
     */
    public static boolean getInPowerSaveMode(Context context) {
        // The PowerSaver feature is only available in Lollipop+, return false if earlier OS.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        return getInPowerSaveModeLollipop(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean getInPowerSaveModeLollipop(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(context.POWER_SERVICE);
        return powerManager.isPowerSaveMode();
    }

    /**
     * @return Network connection type, where possible values are defined by
     *     org.chromium.net.ConnectionType.
     */
    public static int getNetConnectionType(Context context) {
        int connectionType = ConnectionType.CONNECTION_NONE;

        // If we are starting in the background, native portion might not be initialized.
        if (NetworkChangeNotifier.isInitialized()) {
            connectionType = NetworkChangeNotifier.getInstance().getCurrentConnectionType();
        }

        // Sometimes the NetworkConnectionNotifier lags the actual connection type, especially when
        // the GCM NM wakes us from doze state.  If we are really connected, report the connection
        // type from android.
        if (connectionType == ConnectionType.CONNECTION_NONE) {
            // Get the connection type from android in case chromium's type is not yet set.
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            if (isConnected) {
                connectionType = convertAndroidNetworkTypeToConnectionType(activeNetwork.getType());
            }
        }
        return connectionType;
    }

    /**
     * @return true if the active network is a metered network
     */
    public static boolean isActiveNetworkMetered(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.isActiveNetworkMetered();
    }

    /** @return Whether power is connected. */
    public boolean isPowerConnected() {
        return mPowerConnected;
    }

    /** @return Battery percentage. */
    public int getBatteryPercentage() {
        return mBatteryPercentage;
    }

    /**
     * @return Network connection type, where possible values are defined by
     *     org.chromium.net.ConnectionType.
     */
    public int getNetConnectionType() {
        return mNetConnectionType;
    }

    /**
     * @return true if the device is in power save mode.
     */
    public boolean inPowerSaveMode() {
        return mPowerSaveOn;
    }

    private static Intent getBatteryStatus(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // Note this is a sticky intent, so we aren't really registering a receiver, just getting
        // the sticky intent.  That means that we don't need to unregister the filter later.
        return context.registerReceiver(null, filter);
    }

    /** Returns the NCN network type corresponding to the connectivity manager network type */
    private static int convertAndroidNetworkTypeToConnectionType(
            int connectivityManagerNetworkType) {
        if (connectivityManagerNetworkType == ConnectivityManager.TYPE_WIFI) {
            return ConnectionType.CONNECTION_WIFI;
        }
        // for mobile, we don't know if it is 2G, 3G, or 4G, default to worst case of 2G.
        if (connectivityManagerNetworkType == ConnectivityManager.TYPE_MOBILE) {
            return ConnectionType.CONNECTION_2G;
        }
        if (connectivityManagerNetworkType == ConnectivityManager.TYPE_BLUETOOTH) {
            return ConnectionType.CONNECTION_BLUETOOTH;
        }
        // Since NetworkConnectivityManager doesn't understand the other types, call them UNKNOWN.
        return ConnectionType.CONNECTION_UNKNOWN;
    }
}
