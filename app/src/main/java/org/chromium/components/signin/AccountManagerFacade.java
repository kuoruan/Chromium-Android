// Copyright 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.signin;

import android.accounts.Account;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.CachedMetrics;
import org.chromium.net.NetworkChangeNotifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * AccountManagerFacade wraps our access of AccountManager in Android.
 *
 * Use the {@link #initializeAccountManagerFacade} to instantiate it.
 * After initialization, instance get be acquired by calling {@link #get}.
 */
public class AccountManagerFacade {
    private static final String TAG = "Sync_Signin";
    private static final Pattern AT_SYMBOL = Pattern.compile("@");
    private static final String GMAIL_COM = "gmail.com";
    private static final String GOOGLEMAIL_COM = "googlemail.com";
    public static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    /**
     * An account feature (corresponding to a Gaia service flag) that specifies whether the account
     * is a child account.
     */
    @VisibleForTesting
    public static final String FEATURE_IS_CHILD_ACCOUNT_KEY = "service_uca";

    private static AccountManagerFacade sInstance;
    private static AccountManagerFacade sTestingInstance;

    private static final AtomicReference<AccountManagerFacade> sAtomicInstance =
            new AtomicReference<>();

    private final AccountManagerDelegate mDelegate;
    private final ObserverList<AccountsChangeObserver> mObservers = new ObserverList<>();
    private final AtomicReference<AccountManagerResult<Account[]>> mMaybeAccounts =
            new AtomicReference<>();
    private final AsyncTask<Void, Void, AccountManagerResult<Account[]>> mPopulateAccountCacheTask;
    private final CachedMetrics.TimesHistogramSample mPopulateAccountCacheWaitingTimeHistogram =
            new CachedMetrics.TimesHistogramSample(
                    "Signin.AndroidPopulateAccountCacheWaitingTime", TimeUnit.MILLISECONDS);

    /**
     * A simple callback for getAuthToken.
     */
    public interface GetAuthTokenCallback {
        /**
         * Invoked on the UI thread if a token is provided by the AccountManager.
         *
         * @param token Auth token, guaranteed not to be null.
         */
        void tokenAvailable(String token);

        /**
         * Invoked on the UI thread if no token is available.
         *
         * @param isTransientError Indicates if the error is transient (network timeout or
         * unavailable, etc) or persistent (bad credentials, permission denied, etc).
         */
        void tokenUnavailable(boolean isTransientError);
    }

    /**
     * @param delegate the AccountManagerDelegate to use as a backend
     */
    private AccountManagerFacade(AccountManagerDelegate delegate) {
        ThreadUtils.assertOnUiThread();
        mDelegate = delegate;
        mDelegate.registerObservers();
        mDelegate.addObserver(this::updateAccounts);

        mPopulateAccountCacheTask = updateAccounts();
    }

    /**
     * Initializes AccountManagerFacade singleton instance. Can only be called once.
     * Tests can override the instance with {@link #overrideAccountManagerFacadeForTests}.
     *
     * @param delegate the AccountManagerDelegate to use
     */
    @MainThread
    @SuppressFBWarnings("LI_LAZY_INIT_UPDATE_STATIC")
    public static void initializeAccountManagerFacade(AccountManagerDelegate delegate) {
        ThreadUtils.assertOnUiThread();
        if (sInstance != null) {
            throw new IllegalStateException("AccountManagerFacade is already initialized!");
        }
        sInstance = new AccountManagerFacade(delegate);
        if (sTestingInstance != null) return;
        sAtomicInstance.set(sInstance);
    }

    /**
     * Overrides AccountManagerFacade singleton instance for tests. Only for use in Tests.
     * Overrides any previous or future calls to {@link #initializeAccountManagerFacade}.
     *
     * @param delegate the AccountManagerDelegate to use
     */
    @VisibleForTesting
    @AnyThread
    public static void overrideAccountManagerFacadeForTests(AccountManagerDelegate delegate) {
        ThreadUtils.runOnUiThreadBlocking(() -> {
            sTestingInstance = new AccountManagerFacade(delegate);
            sAtomicInstance.set(sTestingInstance);
        });
    }

    /**
     * Resets custom AccountManagerFacade set with {@link #overrideAccountManagerFacadeForTests}.
     * Only for use in Tests.
     */
    @VisibleForTesting
    @AnyThread
    public static void resetAccountManagerFacadeForTests() {
        ThreadUtils.runOnUiThreadBlocking(() -> {
            sTestingInstance = null;
            sAtomicInstance.set(sInstance);
        });
    }

    /**
     * Singleton instance getter. Singleton must be initialized before calling this by
     * {@link #initializeAccountManagerFacade} or {@link #overrideAccountManagerFacadeForTests}.
     *
     * @return a singleton instance
     */
    @AnyThread
    public static AccountManagerFacade get() {
        AccountManagerFacade instance = sAtomicInstance.get();
        assert instance != null : "AccountManagerFacade is not initialized!";
        return instance;
    }

    /**
     * Adds an observer to receive accounts change notifications.
     * @param observer the observer to add.
     */
    @MainThread
    public void addObserver(AccountsChangeObserver observer) {
        ThreadUtils.assertOnUiThread();
        boolean success = mObservers.addObserver(observer);
        assert success : "Observer already added!";
    }

    /**
     * Removes an observer that was previously added using {@link #addObserver}.
     * @param observer the observer to remove.
     */
    @MainThread
    public void removeObserver(AccountsChangeObserver observer) {
        ThreadUtils.assertOnUiThread();
        boolean success = mObservers.removeObserver(observer);
        assert success : "Can't find observer";
    }

    /**
     * Creates an Account object for the given name.
     */
    @AnyThread
    public static Account createAccountFromName(String name) {
        return new Account(name, GOOGLE_ACCOUNT_TYPE);
    }

    /**
     * Retrieves a list of the Google account names on the device.
     *
     * @throws AccountManagerDelegateException if Google Play Services are out of date,
     *         Chrome lacks necessary permissions, etc.
     */
    @AnyThread
    public List<String> getGoogleAccountNames() throws AccountManagerDelegateException {
        List<String> accountNames = new ArrayList<>();
        for (Account account : getGoogleAccounts()) {
            accountNames.add(account.name);
        }
        return accountNames;
    }

    /**
     * Retrieves a list of the Google account names on the device.
     * Returns an empty list if Google Play Services aren't available or out of date.
     */
    @AnyThread
    public List<String> tryGetGoogleAccountNames() {
        List<String> accountNames = new ArrayList<>();
        for (Account account : tryGetGoogleAccounts()) {
            accountNames.add(account.name);
        }
        return accountNames;
    }

    /**
     * Asynchronous version of {@link #tryGetGoogleAccountNames()}.
     */
    @MainThread
    public void tryGetGoogleAccountNames(final Callback<List<String>> callback) {
        tryGetGoogleAccounts(accounts -> {
            List<String> accountNames = new ArrayList<>();
            for (Account account : accounts) {
                accountNames.add(account.name);
            }
            callback.onResult(accountNames);
        });
    }

    /**
     * Asynchronous version of {@link #tryGetGoogleAccountNames()}.
     */
    @MainThread
    public void getGoogleAccountNames(
            final Callback<AccountManagerResult<List<String>>> callback) {
        getGoogleAccounts(accounts -> {
            final AccountManagerResult<List<String>> result;
            if (accounts.hasValue()) {
                List<String> accountNames = new ArrayList<>(accounts.getValue().length);
                for (Account account : accounts.getValue()) {
                    accountNames.add(account.name);
                }
                result = new AccountManagerResult<>(accountNames);
            } else {
                result = new AccountManagerResult<>(accounts.getException());
            }
            callback.onResult(result);
        });
    }

    /**
     * Retrieves all Google accounts on the device.
     *
     * @throws AccountManagerDelegateException if Google Play Services are out of date,
     *         Chrome lacks necessary permissions, etc.
     */
    @AnyThread
    public Account[] getGoogleAccounts() throws AccountManagerDelegateException {
        AccountManagerResult<Account[]> maybeAccounts = mMaybeAccounts.get();
        if (maybeAccounts == null) {
            try {
                // First call to update hasn't finished executing yet, get() will wait for it
                long now = SystemClock.elapsedRealtime();
                maybeAccounts = mPopulateAccountCacheTask.get();
                if (ThreadUtils.runningOnUiThread()) {
                    mPopulateAccountCacheWaitingTimeHistogram.record(
                            SystemClock.elapsedRealtime() - now);
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.w(TAG, "Update accounts task failed", e);
                return new Account[0];
            }
        }
        return maybeAccounts.get();
    }

    /**
     * Asynchronous version of {@link #getGoogleAccounts()}.
     */
    @MainThread
    public void getGoogleAccounts(final Callback<AccountManagerResult<Account[]>> callback) {
        ThreadUtils.assertOnUiThread();
        new AsyncTask<Void, Void, AccountManagerResult<Account[]>>() {
            @Override
            protected AccountManagerResult<Account[]> doInBackground(Void... params) {
                try {
                    return new AccountManagerResult<>(getGoogleAccounts());
                } catch (AccountManagerDelegateException ex) {
                    return new AccountManagerResult<>(ex);
                }
            }

            @Override
            protected void onPostExecute(AccountManagerResult<Account[]> accounts) {
                callback.onResult(accounts);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Retrieves all Google accounts on the device.
     * Returns an empty array if an error occurs while getting account list.
     */
    @AnyThread
    public Account[] tryGetGoogleAccounts() {
        try {
            return getGoogleAccounts();
        } catch (AccountManagerDelegateException e) {
            return new Account[0];
        }
    }

    /**
     * Asynchronous version of {@link #tryGetGoogleAccounts()}.
     */
    @MainThread
    public void tryGetGoogleAccounts(final Callback<Account[]> callback) {
        ThreadUtils.assertOnUiThread();
        new AsyncTask<Void, Void, Account[]>() {
            @Override
            protected Account[] doInBackground(Void... params) {
                return tryGetGoogleAccounts();
            }

            @Override
            protected void onPostExecute(Account[] accounts) {
                callback.onResult(accounts);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Determine whether there are any Google accounts on the device.
     * Returns false if an error occurs while getting account list.
     */
    @AnyThread
    public boolean hasGoogleAccounts() {
        return tryGetGoogleAccounts().length > 0;
    }

    /**
     * Asynchronous version of {@link #hasGoogleAccounts()}.
     */
    @MainThread
    public void hasGoogleAccounts(final Callback<Boolean> callback) {
        tryGetGoogleAccounts(accounts -> callback.onResult(accounts.length > 0));
    }

    private String canonicalizeName(String name) {
        String[] parts = AT_SYMBOL.split(name);
        if (parts.length != 2) return name;

        if (GOOGLEMAIL_COM.equalsIgnoreCase(parts[1])) {
            parts[1] = GMAIL_COM;
        }
        if (GMAIL_COM.equalsIgnoreCase(parts[1])) {
            parts[0] = parts[0].replace(".", "");
        }
        return (parts[0] + "@" + parts[1]).toLowerCase(Locale.US);
    }

    /**
     * Returns the account if it exists; null if account doesn't exists or an error occurs
     * while getting account list.
     */
    @AnyThread
    public Account getAccountFromName(String accountName) {
        String canonicalName = canonicalizeName(accountName);
        Account[] accounts = tryGetGoogleAccounts();
        for (Account account : accounts) {
            if (canonicalizeName(account.name).equals(canonicalName)) {
                return account;
            }
        }
        return null;
    }

    /**
     * Asynchronous version of {@link #getAccountFromName(String)}.
     */
    @MainThread
    public void getAccountFromName(String accountName, final Callback<Account> callback) {
        final String canonicalName = canonicalizeName(accountName);
        tryGetGoogleAccounts(accounts -> {
            Account accountForName = null;
            for (Account account : accounts) {
                if (canonicalizeName(account.name).equals(canonicalName)) {
                    accountForName = account;
                    break;
                }
            }
            callback.onResult(accountForName);
        });
    }

    /**
     * Returns whether an account exists with the given name.
     * Returns false if an error occurs while getting account list.
     */
    @AnyThread
    public boolean hasAccountForName(String accountName) {
        return getAccountFromName(accountName) != null;
    }

    /**
     * Asynchronous version of {@link #hasAccountForName(String)}.
     */
    // TODO(maxbogue): Remove once this function is used outside of tests.
    @VisibleForTesting
    @MainThread
    public void hasAccountForName(String accountName, final Callback<Boolean> callback) {
        getAccountFromName(accountName, account -> callback.onResult(account != null));
    }

    /**
     * @return Whether or not there is an account authenticator for Google accounts.
     */
    @AnyThread
    public boolean hasGoogleAccountAuthenticator() {
        AuthenticatorDescription[] descs = mDelegate.getAuthenticatorTypes();
        for (AuthenticatorDescription desc : descs) {
            if (GOOGLE_ACCOUNT_TYPE.equals(desc.type)) return true;
        }
        return false;
    }

    /**
     * Gets the auth token and returns the response asynchronously.
     * This should be called when we have a foreground activity that needs an auth token.
     * If encountered an IO error, it will attempt to retry when the network is back.
     *
     * - Assumes that the account is a valid account.
     */
    @MainThread
    public void getAuthToken(final Account account, final String authTokenType,
            final GetAuthTokenCallback callback) {
        ConnectionRetry.runAuthTask(new AuthTask<String>() {
            @Override
            public String run() throws AuthException {
                return mDelegate.getAuthToken(account, authTokenType);
            }
            @Override
            public void onSuccess(String token) {
                callback.tokenAvailable(token);
            }
            @Override
            public void onFailure(boolean isTransientError) {
                callback.tokenUnavailable(isTransientError);
            }
        });
    }

    /**
     * Invalidates the old token (if non-null/non-empty) and asynchronously generates a new one.
     *
     * - Assumes that the account is a valid account.
     */
    @MainThread
    public void getNewAuthToken(Account account, String authToken, String authTokenType,
            GetAuthTokenCallback callback) {
        invalidateAuthToken(authToken);
        getAuthToken(account, authTokenType, callback);
    }

    /**
     * Clear an auth token from the local cache with respect to the ApplicationContext.
     */
    @MainThread
    public void invalidateAuthToken(final String authToken) {
        if (authToken == null || authToken.isEmpty()) {
            return;
        }
        ConnectionRetry.runAuthTask(new AuthTask<Boolean>() {
            @Override
            public Boolean run() throws AuthException {
                mDelegate.invalidateAuthToken(authToken);
                return true;
            }
            @Override
            public void onSuccess(Boolean result) {}
            @Override
            public void onFailure(boolean isTransientError) {
                Log.e(TAG, "Failed to invalidate auth token: " + authToken);
            }
        });
    }

    @MainThread
    public void checkChildAccount(Account account, Callback<Boolean> callback) {
        hasFeatures(account, new String[] {FEATURE_IS_CHILD_ACCOUNT_KEY}, callback);
    }

    private boolean hasFeatures(Account account, String[] features) {
        return mDelegate.hasFeatures(account, features);
    }

    private void hasFeatures(
            final Account account, final String[] features, final Callback<Boolean> callback) {
        ThreadUtils.assertOnUiThread();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                return hasFeatures(account, features);
            }

            @Override
            public void onPostExecute(Boolean value) {
                callback.onResult(value);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Asks the user to enter a new password for an account, updating the saved credentials for the
     * account.
     */
    @MainThread
    public void updateCredentials(
            Account account, Activity activity, @Nullable Callback<Boolean> callback) {
        mDelegate.updateCredentials(account, activity, callback);
    }

    /**
     * Gets profile data source.
     * @return {@link ProfileDataSource} if it is supported by implementation, null otherwise.
     */
    @MainThread
    @Nullable
    public ProfileDataSource getProfileDataSource() {
        return mDelegate.getProfileDataSource();
    }

    private AsyncTask<Void, Void, AccountManagerResult<Account[]>> updateAccounts() {
        ThreadUtils.assertOnUiThread();
        AsyncTask<Void, Void, AccountManagerResult<Account[]>> updateAccountsTask =
                new AsyncTask<Void, Void, AccountManagerResult<Account[]>>() {
                    @Override
                    public AccountManagerResult<Account[]> doInBackground(Void... params) {
                        try {
                            return new AccountManagerResult<>(mDelegate.getAccountsSync());
                        } catch (AccountManagerDelegateException ex) {
                            return new AccountManagerResult<>(ex);
                        }
                    }

                    @Override
                    public void onPostExecute(AccountManagerResult<Account[]> accounts) {
                        mMaybeAccounts.set(accounts);
                        fireOnAccountsChangedNotification();
                    }
                };
        updateAccountsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        return updateAccountsTask;
    }

    private void fireOnAccountsChangedNotification() {
        for (AccountsChangeObserver observer : mObservers) {
            observer.onAccountsChanged();
        }
    }

    private interface AuthTask<T> {
        T run() throws AuthException;
        void onSuccess(T result);
        void onFailure(boolean isTransientError);
    }

    /**
     * A helper class to encapsulate network connection retry logic for AuthTasks.
     *
     * The task will be run on the background thread. If it encounters a transient error, it will
     * wait for a network change and retry up to MAX_TRIES times.
     */
    private static class ConnectionRetry<T>
            implements NetworkChangeNotifier.ConnectionTypeObserver {
        private static final int MAX_TRIES = 3;

        private final AuthTask<T> mAuthTask;
        private final AtomicInteger mNumTries;
        private final AtomicBoolean mIsTransientError;

        public static <T> void runAuthTask(AuthTask<T> authTask) {
            new ConnectionRetry<>(authTask).attempt();
        }

        private ConnectionRetry(AuthTask<T> authTask) {
            mAuthTask = authTask;
            mNumTries = new AtomicInteger(0);
            mIsTransientError = new AtomicBoolean(false);
        }

        /**
         * Tries running the {@link AuthTask} in the background. This object is never registered
         * as a {@link NetworkChangeNotifier.ConnectionTypeObserver} when this method is called.
         */
        private void attempt() {
            ThreadUtils.assertOnUiThread();
            // Clear any transient error.
            mIsTransientError.set(false);
            new AsyncTask<Void, Void, T>() {
                @Override
                public T doInBackground(Void... params) {
                    try {
                        return mAuthTask.run();
                    } catch (AuthException ex) {
                        Log.w(TAG, "Failed to perform auth task", ex);
                        mIsTransientError.set(ex.isTransientError());
                    }
                    return null;
                }
                @Override
                public void onPostExecute(T result) {
                    if (result != null) {
                        mAuthTask.onSuccess(result);
                    } else if (!mIsTransientError.get() || mNumTries.incrementAndGet() >= MAX_TRIES
                            || !NetworkChangeNotifier.isInitialized()) {
                        // Permanent error, ran out of tries, or we can't listen for network
                        // change events; give up.
                        mAuthTask.onFailure(mIsTransientError.get());
                    } else {
                        // Transient error with tries left; register for another attempt.
                        NetworkChangeNotifier.addConnectionTypeObserver(ConnectionRetry.this);
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        @Override
        public void onConnectionTypeChanged(int connectionType) {
            assert mNumTries.get() < MAX_TRIES;
            if (NetworkChangeNotifier.isOnline()) {
                // The network is back; stop listening and try again.
                NetworkChangeNotifier.removeConnectionTypeObserver(this);
                attempt();
            }
        }
    }
}
