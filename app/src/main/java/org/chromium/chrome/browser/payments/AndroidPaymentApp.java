// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.JsonWriter;

import org.chromium.IsReadyToPayService;
import org.chromium.IsReadyToPayServiceCallback;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;
import org.chromium.ui.base.WindowAndroid;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The point of interaction with a locally installed 3rd party native Android payment app. */
public class AndroidPaymentApp extends PaymentInstrument implements PaymentApp,
        WindowAndroid.IntentCallback {
    /** The action name for the Pay Intent. */
    public static final String ACTION_PAY = "org.chromium.intent.action.PAY";

    private static final String EXTRA_METHOD_NAME = "methodName";
    private static final String EXTRA_DATA = "data";
    private static final String EXTRA_ORIGIN = "origin";
    private static final String EXTRA_DETAILS = "details";
    private static final String EXTRA_INSTRUMENT_DETAILS = "instrumentDetails";
    private static final String EXTRA_CERTIFICATE_CHAIN = "certificateChain";
    private static final String EXTRA_CERTIFICATE = "certificate";
    private static final String EMPTY_JSON_DATA = "{}";
    private final Handler mHandler;
    private final WebContents mWebContents;
    private final Intent mIsReadyToPayIntent;
    private final Intent mPayIntent;
    private final Set<String> mMethodNames;
    private IsReadyToPayService mIsReadyToPayService;
    private InstrumentsCallback mInstrumentsCallback;
    private InstrumentDetailsCallback mInstrumentDetailsCallback;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mIsReadyToPayService = IsReadyToPayService.Stub.asInterface(service);
            if (mIsReadyToPayService == null) {
                respondToGetInstrumentsQuery(null);
            } else {
                sendIsReadyToPayIntentToPaymentApp();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            respondToGetInstrumentsQuery(null);
        }
    };

    /**
     * Builds the point of interaction with a locally installed 3rd party native Android payment
     * app.
     *
     * @param webContents The web contents.
     * @param packageName The name of the package of the payment app.
     * @param activity    The name of the payment activity in the payment app.
     * @param label       The UI label to use for the payment app.
     * @param icon        The icon to use in UI for the payment app.
     */
    public AndroidPaymentApp(WebContents webContents, String packageName, String activity,
            String label, Drawable icon) {
        super(packageName, label, null, icon);
        ThreadUtils.assertOnUiThread();
        mHandler = new Handler();
        mWebContents = webContents;
        mPayIntent = new Intent();
        mIsReadyToPayIntent = new Intent();
        mIsReadyToPayIntent.setPackage(packageName);
        mPayIntent.setClassName(packageName, activity);
        mPayIntent.setAction(ACTION_PAY);
        mMethodNames = new HashSet<>();
    }

    /** @param methodName A payment method that this app supports, e.g., "https://bobpay.com". */
    public void addMethodName(String methodName) {
        mMethodNames.add(methodName);
    }

    /** @param className The class name of the "is ready to pay" service in the payment app. */
    public void setIsReadyToPayAction(String className) {
        mIsReadyToPayIntent.setClassName(mIsReadyToPayIntent.getPackage(), className);
    }

    private void addCertificateChain(Bundle extras, byte[][] certificateChain) {
        if (certificateChain != null && certificateChain.length > 0) {
            Parcelable[] certificateArray = new Parcelable[certificateChain.length];
            for (int i = 0; i < certificateChain.length; i++) {
                Bundle bundle = new Bundle();
                bundle.putByteArray(EXTRA_CERTIFICATE, certificateChain[i]);
                certificateArray[i] = bundle;
            }
            extras.putParcelableArray(EXTRA_CERTIFICATE_CHAIN, certificateArray);
        }
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> methodData, String origin,
            byte[][] certificateChain, InstrumentsCallback callback) {
        assert mInstrumentsCallback == null
                : "Have not responded to previous request for instruments yet";
        mInstrumentsCallback = callback;
        if (mIsReadyToPayIntent.getPackage() == null) {
            respondToGetInstrumentsQuery(AndroidPaymentApp.this);
            return;
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_METHOD_NAME, mMethodNames.iterator().next());
        extras.putString(EXTRA_ORIGIN, origin);
        PaymentMethodData data = methodData.get(mMethodNames.iterator().next());
        extras.putString(EXTRA_DATA, data == null ? EMPTY_JSON_DATA : data.stringifiedData);
        addCertificateChain(extras, certificateChain);
        mIsReadyToPayIntent.putExtras(extras);

        if (mIsReadyToPayService != null) {
            sendIsReadyToPayIntentToPaymentApp();
        } else {
            ContentViewCore contentView = ContentViewCore.fromWebContents(mWebContents);
            if (contentView == null) {
                respondToGetInstrumentsQuery(null);
                return;
            }

            WindowAndroid window = contentView.getWindowAndroid();
            if (window == null) {
                respondToGetInstrumentsQuery(null);
                return;
            }

            try {
                window.getApplicationContext().bindService(
                        mIsReadyToPayIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                respondToGetInstrumentsQuery(null);
            }
        }
    }

    private void respondToGetInstrumentsQuery(final PaymentInstrument instrument) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ThreadUtils.assertOnUiThread();
                if (mInstrumentsCallback == null) return;
                List<PaymentInstrument> instruments = null;
                if (instrument != null) {
                    instruments = new ArrayList<>();
                    instruments.add(instrument);
                }
                mInstrumentsCallback.onInstrumentsReady(AndroidPaymentApp.this, instruments);
                mInstrumentsCallback = null;
            }
        });
    }

    private void sendIsReadyToPayIntentToPaymentApp() {
        assert mIsReadyToPayService != null;
        IsReadyToPayServiceCallback.Stub callback = new IsReadyToPayServiceCallback.Stub() {
            @Override
            public void handleIsReadyToPay(boolean isReadyToPay) throws RemoteException {
                if (isReadyToPay) {
                    respondToGetInstrumentsQuery(AndroidPaymentApp.this);
                } else {
                    respondToGetInstrumentsQuery(null);
                }
            }
        };
        try {
            mIsReadyToPayService.isReadyToPay(callback);
        } catch (Throwable e) {
            /** Many undocument exceptions are not caught in the remote Service but passed on to
                the Service caller, see writeException in Parcel.java. */
            respondToGetInstrumentsQuery(null);
        }
    }

    @Override
    public boolean supportsMethodsAndData(Map<String, PaymentMethodData> methodsAndData) {
        assert methodsAndData != null;
        Set<String> methodNames = new HashSet<>(methodsAndData.keySet());
        methodNames.retainAll(getAppMethodNames());
        return !methodNames.isEmpty();
    }

    @Override
    public String getAppIdentifier() {
        return getIdentifier();
    }

    @Override
    public Set<String> getAppMethodNames() {
        return Collections.unmodifiableSet(mMethodNames);
    }

    @Override
    public Set<String> getInstrumentMethodNames() {
        return getAppMethodNames();
    }

    @Override
    public void invokePaymentApp(String merchantName, String origin, byte[][] certificateChain,
            Map<String, PaymentMethodData> methodDataMap, PaymentItem total,
            List<PaymentItem> displayItems, Map<String, PaymentDetailsModifier> modifiers,
            InstrumentDetailsCallback callback) {
        assert !mMethodNames.isEmpty();
        Bundle extras = new Bundle();
        extras.putString(EXTRA_ORIGIN, origin);
        addCertificateChain(extras, certificateChain);

        String methodName = mMethodNames.iterator().next();
        extras.putString(EXTRA_METHOD_NAME, methodName);

        PaymentMethodData methodData = methodDataMap.get(methodName);
        extras.putString(
                EXTRA_DATA, methodData == null ? EMPTY_JSON_DATA : methodData.stringifiedData);

        String details = serializeDetails(total, displayItems);
        extras.putString(EXTRA_DETAILS, details == null ? EMPTY_JSON_DATA : details);
        mPayIntent.putExtras(extras);

        mInstrumentDetailsCallback = callback;

        ContentViewCore contentView = ContentViewCore.fromWebContents(mWebContents);
        if (contentView == null) {
            notifyError();
            return;
        }

        WindowAndroid window = contentView.getWindowAndroid();
        if (window == null) {
            notifyError();
            return;
        }

        if (!window.showIntent(mPayIntent, this, R.string.payments_android_app_error)) {
            notifyError();
        }
    }

    private void notifyError() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mInstrumentDetailsCallback.onInstrumentDetailsError();
            }
        });
    }

    private static String serializeDetails(PaymentItem total, List<PaymentItem> displayItems) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter json = new JsonWriter(stringWriter);
        try {
            // details {{{
            json.beginObject();

            // total {{{
            json.name("total");
            serializePaymentItem(json, total);
            // }}} total

            // displayitems {{{
            if (displayItems != null) {
                json.name("displayItems").beginArray();
                for (int i = 0; i < displayItems.size(); i++) {
                    serializePaymentItem(json, displayItems.get(i));
                }
                json.endArray();
            }
            // }}} displayItems

            json.endObject();
            // }}} details
        } catch (IOException e) {
            return null;
        }

        return stringWriter.toString();
    }

    private static void serializePaymentItem(JsonWriter json, PaymentItem item) throws IOException {
        // item {{{
        json.beginObject();
        json.name("label").value(item.label);

        // amount {{{
        json.name("amount").beginObject();
        json.name("currency").value(item.amount.currency);
        json.name("value").value(item.amount.value);
        json.endObject();
        // }}} amount

        json.endObject();
        // }}} item
    }

    @Override
    public void onIntentCompleted(WindowAndroid window, int resultCode, Intent data) {
        ThreadUtils.assertOnUiThread();
        window.removeIntentCallback(this);
        if (data == null || data.getExtras() == null || resultCode != Activity.RESULT_OK) {
            mInstrumentDetailsCallback.onInstrumentDetailsError();
        } else {
            mInstrumentDetailsCallback.onInstrumentDetailsReady(
                    data.getExtras().getString(EXTRA_METHOD_NAME),
                    data.getExtras().getString(EXTRA_INSTRUMENT_DETAILS));
        }
        mInstrumentDetailsCallback = null;
    }

    @Override
    public void dismissInstrument() {}
}
