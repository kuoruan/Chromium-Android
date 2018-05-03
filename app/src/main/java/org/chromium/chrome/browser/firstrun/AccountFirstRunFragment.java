// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.cards.SignInPromo;
import org.chromium.chrome.browser.signin.AccountSigninView;
import org.chromium.chrome.browser.signin.SigninAccessPoint;
import org.chromium.chrome.browser.signin.SigninManager;

/**
 * A {@link Fragment} meant to handle sync setup for the first run experience.
 */
public class AccountFirstRunFragment
        extends Fragment implements FirstRunFragment, AccountSigninView.Delegate {
    /** FRE page that instantiates this fragment. */
    public static class Page implements FirstRunPage<AccountFirstRunFragment> {
        @Override
        public AccountFirstRunFragment instantiateFragment() {
            return new AccountFirstRunFragment();
        }
    }

    // Per-page parameters:
    public static final String FORCE_SIGNIN_ACCOUNT_TO = "ForceSigninAccountTo";
    public static final String PRESELECT_BUT_ALLOW_TO_CHANGE = "PreselectButAllowToChange";
    public static final String IS_CHILD_ACCOUNT = "IsChildAccount";

    private AccountSigninView mView;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = (AccountSigninView) inflater.inflate(
                R.layout.account_signin_view, container, false);
        return mView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle freProperties = getPageDelegate().getProperties();
        boolean isChildAccount = freProperties.getBoolean(IS_CHILD_ACCOUNT);
        String forceAccountTo = freProperties.getString(FORCE_SIGNIN_ACCOUNT_TO);

        AccountSigninView.Listener listener = new AccountSigninView.Listener() {
            @Override
            public void onAccountSelectionCanceled() {
                SignInPromo.temporarilySuppressPromos();
                getPageDelegate().refuseSignIn();
                getPageDelegate().advanceToNextPage();
            }

            @Override
            public void onNewAccount() {
                FirstRunUtils.openAccountAdder(AccountFirstRunFragment.this);
            }

            @Override
            public void onAccountSelected(
                    String accountName, boolean isDefaultAccount, boolean settingsClicked) {
                getPageDelegate().acceptSignIn(accountName, isDefaultAccount);
                if (settingsClicked) {
                    getPageDelegate().askToOpenSignInSettings();
                }
                getPageDelegate().advanceToNextPage();
            }

            @Override
            public void onFailedToSetForcedAccount(String forcedAccountName) {
                // Somehow the forced account disappeared while we were in the FRE.
                // The user would have to go through the FRE again.
                getPageDelegate().abortFirstRunExperience();
            }
        };

        final Bundle arguments;
        if (forceAccountTo == null) {
            arguments = AccountSigninView.createArgumentsForDefaultFlow(
                    SigninAccessPoint.START_PAGE, isChildAccount);
        } else {
            arguments = AccountSigninView.createArgumentsForConfirmationFlow(
                    SigninAccessPoint.START_PAGE, isChildAccount, forceAccountTo, false,
                    AccountSigninView.UNDO_INVISIBLE);
        }
        mView.init(arguments, this, listener);

        RecordUserAction.record("MobileFre.SignInShown");
        RecordUserAction.record("Signin_Signin_FromStartPage");
        SigninManager.logSigninStartAccessPoint(SigninAccessPoint.START_PAGE);
    }

    // FirstRunFragment:
    @Override
    public boolean interceptBackPressed() {
        Bundle freProperties = getPageDelegate().getProperties();
        boolean forceSignin = freProperties.getString(FORCE_SIGNIN_ACCOUNT_TO) != null;
        if (!mView.isInConfirmationScreen()
                || (forceSignin && !freProperties.getBoolean(PRESELECT_BUT_ALLOW_TO_CHANGE))) {
            return false;
        }

        if (forceSignin && freProperties.getBoolean(PRESELECT_BUT_ALLOW_TO_CHANGE)) {
            // Don't force signin if Activity is recreated.
            freProperties.remove(FORCE_SIGNIN_ACCOUNT_TO);
        }

        mView.cancelConfirmationScreen();
        return true;
    }
}
