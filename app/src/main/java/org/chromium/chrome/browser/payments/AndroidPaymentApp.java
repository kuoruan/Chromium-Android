// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.util.JsonWriter;

import org.chromium.IsReadyToPayService;
import org.chromium.IsReadyToPayServiceCallback;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
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

import javax.annotation.Nullable;

/** The point of interaction with a locally installed 3rd party native Android payment app. */
public class AndroidPaymentApp
        extends PaymentInstrument implements PaymentApp, WindowAndroid.IntentCallback {
    /** The action name for the Pay Intent. */
    public static final String ACTION_PAY = "org.chromium.intent.action.PAY";

    /** The maximum number of milliseconds to wait for a response from a READY_TO_PAY service. */
    private static final long READY_TO_PAY_TIMEOUT_MS = 400;

    /** The maximum number of milliseconds to wait for a connection to READY_TO_PAY service. */
    private static final long SERVICE_CONNECTION_TIMEOUT_MS = 1000;

    private static final String EXTRA_MERCHANT_NAME = "merchantName";
    private static final String EXTRA_METHOD_NAME = "methodName";
    private static final String EXTRA_METHOD_NAMES = "methodNames";
    private static final String EXTRA_DATA = "data";
    private static final String EXTRA_ORIGIN = "origin";
    private static final String EXTRA_IFRAME_ORIGIN = "iframeOrigin";
    private static final String EXTRA_DATA_MAP = "dataMap";
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
    private final boolean mIsIncognito;
    private InstrumentsCallback mInstrumentsCallback;
    private InstrumentDetailsCallback mInstrumentDetailsCallback;
    private ServiceConnection mServiceConnection;
    private boolean mIsReadyToPayQueried;

    /**
     * Builds the point of interaction with a locally installed 3rd party native Android payment
     * app.
     *
     * @param webContents The web contents.
     * @param packageName The name of the package of the payment app.
     * @param activity    The name of the payment activity in the payment app.
     * @param label       The UI label to use for the payment app.
     * @param icon        The icon to use in UI for the payment app.
     * @param isIncognito Whether the user is in incognito mode.
     */
    public AndroidPaymentApp(WebContents webContents, String packageName, String activity,
            String label, Drawable icon, boolean isIncognito) {
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
        mIsIncognito = isIncognito;
    }

    /** @param methodName A payment method that this app supports, e.g., "https://bobpay.com". */
    public void addMethodName(String methodName) {
        mMethodNames.add(methodName);
    }

    /** @param className The class name of the "is ready to pay" service in the payment app. */
    public void setIsReadyToPayAction(String className) {
        mIsReadyToPayIntent.setClassName(mIsReadyToPayIntent.getPackage(), className);
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> methodDataMap, String origin,
            String iframeOrigin, @Nullable byte[][] certificateChain,
            InstrumentsCallback callback) {
        assert mMethodNames.containsAll(methodDataMap.keySet());
        assert mInstrumentsCallback
                == null : "Have not responded to previous request for instruments yet";

        mInstrumentsCallback = callback;
        if (mIsReadyToPayIntent.getComponent() == null) {
            respondToGetInstrumentsQuery(AndroidPaymentApp.this);
            return;
        }

        assert !mIsIncognito;
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IsReadyToPayService isReadyToPayService =
                        IsReadyToPayService.Stub.asInterface(service);
                if (isReadyToPayService == null) {
                    respondToGetInstrumentsQuery(null);
                } else {
                    sendIsReadyToPayIntentToPaymentApp(isReadyToPayService);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        mIsReadyToPayIntent.putExtras(buildExtras(
                null, origin, iframeOrigin, certificateChain, methodDataMap, null, null, null));
        try {
            if (!ContextUtils.getApplicationContext().bindService(
                        mIsReadyToPayIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
                respondToGetInstrumentsQuery(null);
                return;
            }
        } catch (SecurityException e) {
            respondToGetInstrumentsQuery(null);
            return;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mIsReadyToPayQueried) respondToGetInstrumentsQuery(null);
            }
        }, SERVICE_CONNECTION_TIMEOUT_MS);
    }

    private void respondToGetInstrumentsQuery(final PaymentInstrument instrument) {
        if (mServiceConnection != null) {
            ContextUtils.getApplicationContext().unbindService(mServiceConnection);
            mServiceConnection = null;
        }

        if (mInstrumentsCallback == null) return;
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

    private void sendIsReadyToPayIntentToPaymentApp(IsReadyToPayService isReadyToPayService) {
        if (mInstrumentsCallback == null) return;
        mIsReadyToPayQueried = true;
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
            isReadyToPayService.isReadyToPay(callback);
        } catch (Throwable e) {
            // Many undocumented exceptions are not caught in the remote Service but passed on to
            // the Service caller, see writeException in Parcel.java.
            respondToGetInstrumentsQuery(null);
            return;
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                respondToGetInstrumentsQuery(null);
            }
        }, READY_TO_PAY_TIMEOUT_MS);
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
    public void invokePaymentApp(final String merchantName, final String origin,
            final String iframeOrigin, final byte[][] certificateChain,
            final Map<String, PaymentMethodData> methodDataMap, final PaymentItem total,
            final List<PaymentItem> displayItems,
            final Map<String, PaymentDetailsModifier> modifiers,
            InstrumentDetailsCallback callback) {
        mInstrumentDetailsCallback = callback;

        if (!mIsIncognito) {
            launchPaymentApp(merchantName, origin, iframeOrigin, certificateChain, methodDataMap,
                    total, displayItems, modifiers);
            return;
        }

        ChromeActivity activity = ChromeActivity.fromWebContents(mWebContents);
        if (activity == null) {
            notifyErrorInvokingPaymentApp();
            return;
        }

        new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                .setTitle(R.string.external_app_leave_incognito_warning_title)
                .setMessage(R.string.external_payment_app_leave_incognito_warning)
                .setPositiveButton(R.string.ok,
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                launchPaymentApp(merchantName, origin, iframeOrigin,
                                        certificateChain, methodDataMap, total, displayItems,
                                        modifiers);
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                notifyErrorInvokingPaymentApp();
                            }
                        })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        notifyErrorInvokingPaymentApp();
                    }
                })
                .show();
    }

    private void launchPaymentApp(String merchantName, String origin, String iframeOrigin,
            byte[][] certificateChain, Map<String, PaymentMethodData> methodDataMap,
            PaymentItem total, List<PaymentItem> displayItems,
            Map<String, PaymentDetailsModifier> modifiers) {
        assert mMethodNames.containsAll(methodDataMap.keySet());
        assert mInstrumentDetailsCallback != null;

        if (mWebContents.isDestroyed()) {
            notifyErrorInvokingPaymentApp();
            return;
        }

        WindowAndroid window = mWebContents.getTopLevelNativeWindow();
        if (window == null) {
            notifyErrorInvokingPaymentApp();
            return;
        }

        mPayIntent.putExtras(buildExtras(merchantName, origin, iframeOrigin, certificateChain,
                methodDataMap, total, displayItems, modifiers));
        if (!window.showIntent(mPayIntent, this, R.string.payments_android_app_error)) {
            notifyErrorInvokingPaymentApp();
        }
    }

    private static Bundle buildExtras(@Nullable String merchantName, String origin,
            String iframeOrigin, @Nullable byte[][] certificateChain,
            Map<String, PaymentMethodData> methodDataMap, @Nullable PaymentItem total,
            @Nullable List<PaymentItem> displayItems,
            @Nullable Map<String, PaymentDetailsModifier> modifiers) {
        Bundle extras = new Bundle();

        if (merchantName != null) extras.putString(EXTRA_MERCHANT_NAME, merchantName);
        extras.putString(EXTRA_ORIGIN, origin);
        extras.putString(EXTRA_IFRAME_ORIGIN, iframeOrigin);

        if (certificateChain != null && certificateChain.length > 0) {
            extras.putParcelableArray(
                    EXTRA_CERTIFICATE_CHAIN, buildCertificateChain(certificateChain));
        }

        // Deprecated:
        String methodName = methodDataMap.entrySet().iterator().next().getKey();
        extras.putString(EXTRA_METHOD_NAME, methodName);
        PaymentMethodData firstMethodData = methodDataMap.get(methodName);
        extras.putString(EXTRA_DATA,
                firstMethodData == null ? EMPTY_JSON_DATA : firstMethodData.stringifiedData);

        extras.putStringArrayList(EXTRA_METHOD_NAMES, new ArrayList<>(methodDataMap.keySet()));
        Bundle methodDataBundle = new Bundle();
        for (Map.Entry<String, PaymentMethodData> methodData : methodDataMap.entrySet()) {
            methodDataBundle.putString(methodData.getKey(),
                    methodData.getValue() == null ? EMPTY_JSON_DATA
                                                  : methodData.getValue().stringifiedData);
        }
        extras.putParcelable(EXTRA_DATA_MAP, methodDataBundle);

        if (total != null) {
            String details = serializeDetails(total, displayItems);
            extras.putString(EXTRA_DETAILS, details == null ? EMPTY_JSON_DATA : details);
        }

        return extras;
    }

    private static Parcelable[] buildCertificateChain(byte[][] certificateChain) {
        Parcelable[] result = new Parcelable[certificateChain.length];
        for (int i = 0; i < certificateChain.length; i++) {
            Bundle bundle = new Bundle();
            bundle.putByteArray(EXTRA_CERTIFICATE, certificateChain[i]);
            result[i] = bundle;
        }
        return result;
    }

    private void notifyErrorInvokingPaymentApp() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mInstrumentDetailsCallback.onInstrumentDetailsError();
            }
        });
    }

    private static String serializeDetails(
            PaymentItem total, @Nullable List<PaymentItem> displayItems) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter json = new JsonWriter(stringWriter);
        try {
            // details {{{
            json.beginObject();

            // total {{{
            json.name("total");
            serializePaymentItem(total, json);
            // }}} total

            // displayitems {{{
            if (displayItems != null) {
                json.name("displayItems").beginArray();
                for (int i = 0; i < displayItems.size(); i++) {
                    serializePaymentItem(displayItems.get(i), json);
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

    private static void serializePaymentItem(PaymentItem item, JsonWriter json) throws IOException {
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
