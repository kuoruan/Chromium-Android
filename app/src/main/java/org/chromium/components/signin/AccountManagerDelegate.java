// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.signin;

import android.accounts.Account;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;

import org.chromium.base.Callback;

/**
 * Wrapper around the Android account manager, to facilitate dependency injection during testing.
 */
public interface AccountManagerDelegate {
    /**
     * This method is deprecated; please use the asynchronous version below instead.
     *
     * See http://crbug.com/517697 for details.
     */
    Account[] getAccountsByType(String type);

    /**
     * Get all the accounts for a given {@code type}.
     */
    void getAccountsByType(String type, Callback<Account[]> callback);

    /**
     * Get an auth token. This should only be called on a background thread.
     *
     * @param account The {@link Account} for which the auth token is requested.
     * @param authTokenScope The scope of the authToken being requested.
     * @return The auth token fetched from the authenticator.
     * @throws AuthException Indicates a failure in fetching the auth token perhaps due to a
     * transient error or when user intervention is required (like confirming the credentials)
     * which is expressed as an {@link Intent} to the handler.
     */
    String getAuthToken(Account account, String authTokenScope) throws AuthException;

    /**
     * @param authToken The auth token to invalidate.
     * @throws AuthException Indicates a failure clearing the auth token; can be transient.
     */
    void invalidateAuthToken(String authToken) throws AuthException;

    /**
     * Get all the available authenticator types.
     */
    AuthenticatorDescription[] getAuthenticatorTypes();

    /**
     * Check whether the {@code account} has all the features listed in {@code features}.
     */
    void hasFeatures(Account account, String[] features, Callback<Boolean> callback);

    /**
     * Asks the user to enter a new password for an account, updating the saved credentials for the
     * account.
     * @param account The {@link Account} for which the update is requested.
     * @param activity The {@link Activity} context to use for launching a new authenticator-defined
     * sub-Activity to prompt the user to enter a password.
     * @param callback The callback to indicate whether update is succeed or not.
     */
    void updateCredentials(Account account, Activity activity, Callback<Boolean> callback);
}
