// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;

import org.chromium.base.Log;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.preferences.ManagedPreferencesUtils;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.signin.SigninManager.SignInCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An Activity displayed from the MainPreferences to allow the user to pick an account to
 * sign in to. The AccountSigninView.Delegate interface is fulfilled by the AppCompatActivity.
 */
public class AccountSigninActivity extends AppCompatActivity
        implements AccountSigninView.Listener, AccountSigninView.Delegate {
    private static final String TAG = "AccountSigninActivity";
    private static final String INTENT_SIGNIN_ACCESS_POINT =
            "AccountSigninActivity.SigninAccessPoint";
    private static final String INTENT_SIGNIN_FLOW_TYPE = "AccountSigninActivity.SigninFlowType";
    private static final String INTENT_ACCOUNT_NAME = "AccountSigninActivity.AccountName";
    private static final String INTENT_IS_DEFAULT_ACCOUNT =
            "AccountSigninActivity.IsDefaultAccount";
    private static final String INTENT_IS_FROM_PERSONALIZED_PROMO =
            "AccountSigninActivity.IsFromPersonalizedPromo";

    @IntDef({SIGNIN_FLOW_DEFAULT, SIGNIN_FLOW_CONFIRMATION_ONLY, SIGNIN_FLOW_ADD_NEW_ACCOUNT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SigninFlowType {}

    private static final int SIGNIN_FLOW_DEFAULT = 0;
    private static final int SIGNIN_FLOW_CONFIRMATION_ONLY = 1;
    private static final int SIGNIN_FLOW_ADD_NEW_ACCOUNT = 2;

    @IntDef({SigninAccessPoint.SETTINGS, SigninAccessPoint.BOOKMARK_MANAGER,
            SigninAccessPoint.RECENT_TABS, SigninAccessPoint.SIGNIN_PROMO,
            SigninAccessPoint.NTP_CONTENT_SUGGESTIONS, SigninAccessPoint.AUTOFILL_DROPDOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AccessPoint {}

    private @AccessPoint int mAccessPoint;
    private @SigninFlowType int mSigninFlowType;
    private boolean mIsFromPersonalizedPromo;

    /**
     * A convenience method to create a AccountSigninActivity passing the access point as an
     * intent. Checks if the sign in flow can be started before showing the activity.
     * @param accessPoint {@link AccessPoint} for starting signin flow. Used in metrics.
     * @return {@code true} if sign in has been allowed.
     */
    public static boolean startIfAllowed(Context context, @AccessPoint int accessPoint) {
        if (!SigninManager.get(context).isSignInAllowed()) {
            if (SigninManager.get(context).isSigninDisabledByPolicy()) {
                ManagedPreferencesUtils.showManagedByAdministratorToast(context);
            }
            return false;
        }

        context.startActivity(createIntentForDefaultSigninFlow(context, accessPoint, false));
        return true;
    }

    /**
     * Creates an {@link Intent} which can be used to start the default signin flow.
     * @param accessPoint {@link AccessPoint} for starting signin flow. Used in metrics.
     * @param isFromPersonalizedPromo Whether the signin activity is started from a personalized
     *         promo.
     */
    public static Intent createIntentForDefaultSigninFlow(
            Context context, @AccessPoint int accessPoint, boolean isFromPersonalizedPromo) {
        Intent intent = new Intent(context, AccountSigninActivity.class);
        intent.putExtra(INTENT_SIGNIN_ACCESS_POINT, accessPoint);
        intent.putExtra(INTENT_SIGNIN_FLOW_TYPE, SIGNIN_FLOW_DEFAULT);
        intent.putExtra(INTENT_IS_FROM_PERSONALIZED_PROMO, isFromPersonalizedPromo);
        return intent;
    }

    /**
     * Creates an {@link Intent} which can be used to start the signin flow from the confirmation
     * screen.
     * @param accessPoint {@link AccessPoint} for starting signin flow. Used in metrics.
     * @param selectAccount Account for which signin confirmation page should be shown.
     * @param isDefaultAccount Whether {@param selectedAccount} is the default account on
     *         the device. Used in metrics.
     * @param isFromPersonalizedPromo Whether the signin activity is started from a personalized
     *         promo.
     */
    public static Intent createIntentForConfirmationOnlySigninFlow(Context context,
            @AccessPoint int accessPoint, String selectAccount, boolean isDefaultAccount,
            boolean isFromPersonalizedPromo) {
        Intent intent = new Intent(context, AccountSigninActivity.class);
        intent.putExtra(INTENT_SIGNIN_ACCESS_POINT, accessPoint);
        intent.putExtra(INTENT_SIGNIN_FLOW_TYPE, SIGNIN_FLOW_CONFIRMATION_ONLY);
        intent.putExtra(INTENT_ACCOUNT_NAME, selectAccount);
        intent.putExtra(INTENT_IS_DEFAULT_ACCOUNT, isDefaultAccount);
        intent.putExtra(INTENT_IS_FROM_PERSONALIZED_PROMO, isFromPersonalizedPromo);
        return intent;
    }

    /**
     * Creates an {@link Intent} which can be used to start the signin flow from the "Add Account"
     * page.
     * @param accessPoint {@link AccessPoint} for starting signin flow. Used in metrics.
     * @param isFromPersonalizedPromo Whether the signin activity is started from a personalized
     *         promo.
     */
    public static Intent createIntentForAddAccountSigninFlow(
            Context context, @AccessPoint int accessPoint, boolean isFromPersonalizedPromo) {
        Intent intent = new Intent(context, AccountSigninActivity.class);
        intent.putExtra(INTENT_SIGNIN_ACCESS_POINT, accessPoint);
        intent.putExtra(INTENT_SIGNIN_FLOW_TYPE, SIGNIN_FLOW_ADD_NEW_ACCOUNT);
        intent.putExtra(INTENT_IS_FROM_PERSONALIZED_PROMO, isFromPersonalizedPromo);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The browser process must be started here because this activity may be started from the
        // recent apps list and it relies on other activities and the native library to be loaded.
        try {
            ChromeBrowserInitializer.getInstance(this).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.e(TAG, "Failed to start browser process.", e);
            // Since the library failed to initialize nothing in the application
            // can work, so kill the whole application not just the activity
            System.exit(-1);
        }

        // We don't trust android to restore the saved state correctly, so pass null.
        super.onCreate(null);

        mAccessPoint = getIntent().getIntExtra(INTENT_SIGNIN_ACCESS_POINT, -1);
        assert mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER
                || mAccessPoint == SigninAccessPoint.RECENT_TABS
                || mAccessPoint == SigninAccessPoint.SETTINGS
                || mAccessPoint == SigninAccessPoint.SIGNIN_PROMO
                || mAccessPoint == SigninAccessPoint.NTP_CONTENT_SUGGESTIONS
                || mAccessPoint == SigninAccessPoint.AUTOFILL_DROPDOWN
                : "invalid access point: " + mAccessPoint;

        mIsFromPersonalizedPromo =
                getIntent().getBooleanExtra(INTENT_IS_FROM_PERSONALIZED_PROMO, false);

        AccountSigninView view = (AccountSigninView) LayoutInflater.from(this).inflate(
                R.layout.account_signin_view, null);

        mSigninFlowType = getIntent().getIntExtra(INTENT_SIGNIN_FLOW_TYPE, -1);
        switch (mSigninFlowType) {
            case SIGNIN_FLOW_DEFAULT:
                view.initFromSelectionPage(false, this, this);
                break;
            case SIGNIN_FLOW_CONFIRMATION_ONLY: {
                String accountName = getIntent().getStringExtra(INTENT_ACCOUNT_NAME);
                if (accountName == null) {
                    throw new IllegalArgumentException("Account name can't be null!");
                }
                boolean isDefaultAccount =
                        getIntent().getBooleanExtra(INTENT_IS_DEFAULT_ACCOUNT, false);
                view.initFromConfirmationPage(false, accountName, isDefaultAccount,
                        AccountSigninView.UNDO_ABORT, this, this);
                break;
            }
            case SIGNIN_FLOW_ADD_NEW_ACCOUNT:
                view.initFromAddAccountPage(this, this);
                break;
            default:
                throw new IllegalArgumentException("Unknown signin flow type: " + mSigninFlowType);
        }

        if (mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER
                || mAccessPoint == SigninAccessPoint.RECENT_TABS) {
            view.configureForRecentTabsOrBookmarksPage();
        }

        setContentView(view);

        SigninManager.logSigninStartAccessPoint(mAccessPoint);
        recordSigninStartedHistogramAccountInfo();
        recordSigninStartedUserAction();
    }

    @Override
    public void onAccountSelectionCanceled() {
        finish();
    }

    @Override
    public void onNewAccount() {
        AccountAdder.getInstance().addAccount(this, AccountAdder.ADD_ACCOUNT_RESULT);
    }

    @Override
    public void onAccountSelected(
            final String accountName, boolean isDefaultAccount, final boolean settingsClicked) {
        final Context context = this;
        SigninManager.get(this).signIn(accountName, this, new SignInCallback() {
            @Override
            public void onSignInComplete() {
                if (settingsClicked) {
                    Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                            context, AccountManagementFragment.class.getName());
                    startActivity(intent);
                }

                recordSigninCompletedHistogramAccountInfo();
                finish();
            }

            @Override
            public void onSignInAborted() {}
        });
    }

    @Override
    public void onFailedToSetForcedAccount(String forcedAccountName) {}

    private void recordSigninCompletedHistogramAccountInfo() {
        if (!mIsFromPersonalizedPromo) {
            return;
        }

        final String histogram;
        switch (mSigninFlowType) {
            case SIGNIN_FLOW_ADD_NEW_ACCOUNT:
                histogram = "Signin.SigninCompletedAccessPoint.NewAccount";
                break;
            case SIGNIN_FLOW_CONFIRMATION_ONLY:
                histogram = "Signin.SigninCompletedAccessPoint.WithDefault";
                break;
            case SIGNIN_FLOW_DEFAULT:
                histogram = "Signin.SigninCompletedAccessPoint.NotDefault";
                break;
            default:
                assert false : "Unexpected signin flow type!";
                return;
        }

        RecordHistogram.recordEnumeratedHistogram(histogram, mAccessPoint, SigninAccessPoint.MAX);
    }

    private void recordSigninStartedHistogramAccountInfo() {
        if (!mIsFromPersonalizedPromo) {
            return;
        }

        final String histogram;
        switch (mSigninFlowType) {
            case SIGNIN_FLOW_ADD_NEW_ACCOUNT:
                histogram = "Signin.SigninStartedAccessPoint.NewAccount";
                break;
            case SIGNIN_FLOW_CONFIRMATION_ONLY:
                histogram = "Signin.SigninStartedAccessPoint.WithDefault";
                break;
            case SIGNIN_FLOW_DEFAULT:
                histogram = "Signin.SigninStartedAccessPoint.NotDefault";
                break;
            default:
                assert false : "Unexpected signin flow type!";
                return;
        }

        RecordHistogram.recordEnumeratedHistogram(histogram, mAccessPoint, SigninAccessPoint.MAX);
    }

    private void recordSigninStartedUserAction() {
        switch (mAccessPoint) {
            case SigninAccessPoint.AUTOFILL_DROPDOWN:
                RecordUserAction.record("Signin_Signin_FromAutofillDropdown");
                break;
            case SigninAccessPoint.BOOKMARK_MANAGER:
                RecordUserAction.record("Signin_Signin_FromBookmarkManager");
                break;
            case SigninAccessPoint.RECENT_TABS:
                RecordUserAction.record("Signin_Signin_FromRecentTabs");
                break;
            case SigninAccessPoint.SETTINGS:
                RecordUserAction.record("Signin_Signin_FromSettings");
                break;
            case SigninAccessPoint.SIGNIN_PROMO:
                RecordUserAction.record("Signin_Signin_FromSigninPromo");
                break;
            case SigninAccessPoint.NTP_CONTENT_SUGGESTIONS:
                RecordUserAction.record("Signin_Signin_FromNTPContentSuggestions");
                break;
            default:
                assert false : "Invalid access point.";
        }
    }

    // AccountSigninView.Delegate implementation.
    @Override
    public Activity getActivity() {
        return this;
    }
}
