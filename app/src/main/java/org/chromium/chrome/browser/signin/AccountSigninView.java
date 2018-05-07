// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.consent_auditor.ConsentAuditorBridge;
import org.chromium.chrome.browser.consent_auditor.ConsentAuditorFeature;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.signin.AccountTrackerService.OnSystemAccountsSeededListener;
import org.chromium.chrome.browser.signin.ConfirmImportSyncDataDialog.ImportSyncType;
import org.chromium.components.signin.AccountManagerDelegateException;
import org.chromium.components.signin.AccountManagerFacade;
import org.chromium.components.signin.AccountManagerResult;
import org.chromium.components.signin.AccountsChangeObserver;
import org.chromium.components.signin.GmsAvailabilityException;
import org.chromium.components.signin.GmsJustUpdatedException;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;
import org.chromium.ui.widget.ButtonCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This view allows the user to select an account to log in to, add an account, cancel account
 * selection, etc. Users of this class should call {@link #init} after the view has been inflated.
 */
public class AccountSigninView extends FrameLayout {
    /**
     * Callbacks for various account selection events.
     */
    public interface Listener {
        /**
         * The user canceled account selection.
         */
        void onAccountSelectionCanceled();

        /**
         * The user wants to make a new account.
         */
        void onNewAccount();

        /**
         * The user completed the View and selected an account.
         * @param accountName The name of the account
         * @param isDefaultAccount Whether selected account is a default one (first of all accounts)
         * @param settingsClicked If true, user requested to see their sync settings, if false
         *                        they just clicked Done.
         */
        void onAccountSelected(
                String accountName, boolean isDefaultAccount, boolean settingsClicked);

        /**
         * Failed to set the forced account because it wasn't found.
         * @param forcedAccountName The name of the forced-sign-in account
         */
        void onFailedToSetForcedAccount(String forcedAccountName);
    }

    /**
     * Provides UI objects for new UI component creation.
     */
    public interface Delegate {
        /**
         * Provides an Activity for the View to check GMSCore version.
         */
        Activity getActivity();

        /**
         * Provides a FragmentManager for the View to create dialogs. This is done through a
         * different mechanism than getActivity().getFragmentManager() as a potential fix to
         * https://crbug.com/646978 on the theory that getActivity() and getFragmentManager()
         * return null at different times.
         */
        FragmentManager getFragmentManager();
    }

    /**
     * Stores metadata about the text associated with a given TextView in order to extract and
     * validate the Sync consent text.
     */
    private static class TextViewMetadata {
        private final String mString;
        private final @StringRes int mId;

        /**
         * @param Text The text which was programatically assigned to the associated TextView.
         * @param id The ID of the string resource assigned to the associated TextView that
         *         will be used for consent recording, or 0 if this string should not be recorded
         *         as a part of the consent.
         */
        public TextViewMetadata(String text, @StringRes int id) {
            mString = text;
            mId = id;
        }

        public String getString() {
            return mString;
        }

        public int getId() {
            return mId;
        }
    }

    /** A CharSequence -> CharSequence transformation. */
    private interface TextTransformation { public CharSequence transform(CharSequence input); }

    private static final String TAG = "AccountSigninView";

    private static final String SETTINGS_LINK_OPEN = "<LINK1>";
    private static final String SETTINGS_LINK_CLOSE = "</LINK1>";

    private static final String ARGUMENT_ACCESS_POINT = "AccountSigninView.AccessPoint";
    private static final String ARGUMENT_SIGNIN_FLOW_TYPE = "AccountSigninView.FlowType";
    private static final String ARGUMENT_ACCOUNT_NAME = "AccountSigninView.AccountName";
    private static final String ARGUMENT_IS_DEFAULT_ACCOUNT = "AccountSigninView.IsDefaultAccount";
    private static final String ARGUMENT_IS_CHILD_ACCOUNT = "AccountSigninView.IsChildAccount";
    private static final String ARGUMENT_UNDO_BEHAVIOR = "AccountSigninView.UndoBehavior";

    @IntDef({SIGNIN_FLOW_DEFAULT, SIGNIN_FLOW_CONFIRMATION_ONLY, SIGNIN_FLOW_ADD_NEW_ACCOUNT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SigninFlowType {}

    public static final int SIGNIN_FLOW_DEFAULT = 0;
    public static final int SIGNIN_FLOW_CONFIRMATION_ONLY = 1;
    public static final int SIGNIN_FLOW_ADD_NEW_ACCOUNT = 2;

    /** Specifies different behaviors for "Undo" button on signin confirmation page. */
    @IntDef({UNDO_INVISIBLE, UNDO_BACK_TO_SELECTION, UNDO_ABORT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface UndoBehavior {}

    /** "Undo" button is invisible. */
    public static final int UNDO_INVISIBLE = 0;
    /** "Undo" button opens account selection page. */
    public static final int UNDO_BACK_TO_SELECTION = 1;
    /** "Undo" button calls {@link Listener#onAccountSelectionCanceled()}. */
    public static final int UNDO_ABORT = 2;

    private final AccountsChangeObserver mAccountsChangedObserver;
    private final ProfileDataCache.Observer mProfileDataCacheObserver;
    private final ProfileDataCache mProfileDataCache;
    private List<String> mAccountNames;
    private AccountSigninChooseView mSigninChooseView;
    private ButtonCompat mPositiveButton;
    private Button mNegativeButton;
    private Button mMoreButton;
    private Listener mListener;
    private Delegate mDelegate;
    private @SigninAccessPoint int mSigninAccessPoint;
    private @SigninFlowType int mSigninFlowType;
    private @UndoBehavior int mUndoBehavior;
    private String mSelectedAccountName;
    private boolean mIsDefaultAccountSelected;
    private @StringRes int mCancelButtonTextId = R.string.cancel;
    private boolean mIsChildAccount;
    private UserRecoverableErrorHandler.ModalDialog mGooglePlayServicesUpdateErrorHandler;
    private AlertDialog mGmsIsUpdatingDialog;
    private long mGmsIsUpdatingDialogShowTime;
    private boolean mShouldShowConfirmationPageWhenAttachedToWindow;

    private AccountSigninConfirmationView mSigninConfirmationView;
    private ImageView mSigninAccountImage;
    private TextView mSigninAccountName;
    private TextView mSigninAccountEmail;
    private TextView mSigninSyncTitle;
    private TextView mSigninSyncDescription;
    private TextView mSigninPersonalizeServiceTitle;
    private TextView mSigninPersonalizeServiceDescription;
    private TextView mSigninSettingsControl;
    private ConfirmSyncDataStateMachine mConfirmSyncDataStateMachine;

    private final Map<TextView, TextViewMetadata> mTextViewToMetadataMap = new HashMap<>();

    public AccountSigninView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAccountsChangedObserver = this::triggerUpdateAccounts;
        mProfileDataCacheObserver = (String accountId) -> updateProfileData();
        mProfileDataCache = new ProfileDataCache(context,
                context.getResources().getDimensionPixelSize(R.dimen.signin_account_image_size));
    }

    /**
     * Creates an argument bundle to start AccountSigninView from the account selection page.
     *
     * @param accessPoint The access point for starting signin flow.
     * @param isChildAccount Whether this view is for a child account.
     */
    public static Bundle createArgumentsForDefaultFlow(
            @SigninAccessPoint int accessPoint, boolean isChildAccount) {
        Bundle result = new Bundle();
        result.putInt(ARGUMENT_SIGNIN_FLOW_TYPE, SIGNIN_FLOW_DEFAULT);
        result.putInt(ARGUMENT_ACCESS_POINT, accessPoint);
        result.putBoolean(ARGUMENT_IS_CHILD_ACCOUNT, isChildAccount);
        result.putInt(ARGUMENT_UNDO_BEHAVIOR, UNDO_BACK_TO_SELECTION);
        return result;
    }

    /**
     * Creates an argument bundle to start AccountSigninView from the new account creation screen.
     *
     * @param accessPoint The access point for starting signin flow.
     */
    public static Bundle createArgumentsForAddAccountFlow(@SigninAccessPoint int accessPoint) {
        Bundle result = new Bundle();
        result.putInt(ARGUMENT_SIGNIN_FLOW_TYPE, SIGNIN_FLOW_ADD_NEW_ACCOUNT);
        result.putInt(ARGUMENT_ACCESS_POINT, accessPoint);
        result.putBoolean(ARGUMENT_IS_CHILD_ACCOUNT, false); // Children profiles can't add accounts
        result.putInt(ARGUMENT_UNDO_BEHAVIOR, UNDO_ABORT);
        return result;
    }

    /**
     * Creates an argument bundle to start AccountSigninView from the signin confirmation page.
     *
     * @param accessPoint The access point for starting signin flow.
     * @param isChildAccount Whether this view is for a child account.
     * @param accountName An account that should be used for confirmation page and signin.
     * @param isDefaultAccount Whether {@param accountName} is a default account, used for metrics.
     * @param undoBehavior "Undo" button behavior (see {@link UndoBehavior}).
     */
    public static Bundle createArgumentsForConfirmationFlow(@SigninAccessPoint int accessPoint,
            boolean isChildAccount, String accountName, boolean isDefaultAccount,
            @UndoBehavior int undoBehavior) {
        Bundle result = new Bundle();
        result.putInt(ARGUMENT_SIGNIN_FLOW_TYPE, SIGNIN_FLOW_CONFIRMATION_ONLY);
        result.putInt(ARGUMENT_ACCESS_POINT, accessPoint);
        result.putBoolean(ARGUMENT_IS_CHILD_ACCOUNT, isChildAccount);
        result.putString(ARGUMENT_ACCOUNT_NAME, accountName);
        result.putBoolean(ARGUMENT_IS_DEFAULT_ACCOUNT, isDefaultAccount);
        result.putInt(ARGUMENT_UNDO_BEHAVIOR, undoBehavior);
        return result;
    }

    /**
     * Initializes the view.
     *
     * @param arguments The argument bundle created by {@link #createArgumentsForDefaultFlow},
     *         {@link #createArgumentsForAddAccountFlow} or
     *         {@link #createArgumentsForConfirmationFlow}.
     * @param delegate The UI object creation delegate.
     * @param listener The account selection event listener.
     */
    public void init(Bundle arguments, Delegate delegate, Listener listener) {
        @SigninAccessPoint int accessPoint = arguments.getInt(ARGUMENT_ACCESS_POINT, -1);
        assert accessPoint != -1;

        initAccessPoint(accessPoint);
        mIsChildAccount = arguments.getBoolean(ARGUMENT_IS_CHILD_ACCOUNT, false);
        mUndoBehavior = arguments.getInt(ARGUMENT_UNDO_BEHAVIOR, -1);
        mSigninFlowType = arguments.getInt(ARGUMENT_SIGNIN_FLOW_TYPE, -1);
        mDelegate = delegate;
        mListener = listener;

        updateConsentText();

        switch (mSigninFlowType) {
            case SIGNIN_FLOW_DEFAULT:
                showSigninPage();
                break;
            case SIGNIN_FLOW_CONFIRMATION_ONLY: {
                String accountName = arguments.getString(ARGUMENT_ACCOUNT_NAME);
                assert accountName != null;
                boolean isDefaultAccount = arguments.getBoolean(ARGUMENT_IS_DEFAULT_ACCOUNT, false);
                showConfirmationPageForAccount(accountName, isDefaultAccount);
                triggerUpdateAccounts();
                break;
            }
            case SIGNIN_FLOW_ADD_NEW_ACCOUNT:
                showSigninPage();
                RecordUserAction.record("Signin_AddAccountToDevice");
                mListener.onNewAccount();
                break;
            default:
                assert false : "Unknown or missing signin flow type: " + mSigninFlowType;
                return;
        }
    }

    public @SigninFlowType int getSigninFlowType() {
        return mSigninFlowType;
    }

    public @SigninAccessPoint int getSigninAccessPoint() {
        return mSigninAccessPoint;
    }

    private void initAccessPoint(@SigninAccessPoint int accessPoint) {
        mSigninAccessPoint = accessPoint;
        if (accessPoint == SigninAccessPoint.START_PAGE
                || accessPoint == SigninAccessPoint.SIGNIN_PROMO) {
            mCancelButtonTextId = R.string.no_thanks;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSigninChooseView = (AccountSigninChooseView) findViewById(R.id.account_signin_choose_view);
        mSigninChooseView.setAddNewAccountObserver(() -> {
            mListener.onNewAccount();
            RecordUserAction.record("Signin_AddAccountToDevice");
        });

        mPositiveButton = (ButtonCompat) findViewById(R.id.positive_button);
        mNegativeButton = (Button) findViewById(R.id.negative_button);
        mMoreButton = (Button) findViewById(R.id.more_button);
        mSigninConfirmationView =
                (AccountSigninConfirmationView) findViewById(R.id.signin_confirmation_view);
        mSigninAccountImage = (ImageView) findViewById(R.id.signin_account_image);
        mSigninAccountName = (TextView) findViewById(R.id.signin_account_name);
        mSigninAccountEmail = (TextView) findViewById(R.id.signin_account_email);
        mSigninSyncTitle = (TextView) findViewById(R.id.signin_sync_title);
        mSigninSyncDescription = (TextView) findViewById(R.id.signin_sync_description);
        mSigninPersonalizeServiceTitle =
                (TextView) findViewById(R.id.signin_personalize_service_title);
        mSigninPersonalizeServiceDescription =
                (TextView) findViewById(R.id.signin_personalize_service_description);
        mSigninSettingsControl = (TextView) findViewById(R.id.signin_settings_control);
        // For the spans to be clickable.
        mSigninSettingsControl.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void updateConsentText() {
        // Static strings.
        setText(mSigninSyncTitle, R.string.sync_confirmation_chrome_sync_title);
        setText(mSigninSyncDescription, R.string.sync_confirmation_chrome_sync_message);
        setText(mSigninPersonalizeServiceTitle,
                R.string.sync_confirmation_personalize_services_title);
        setText(mSigninPersonalizeServiceDescription,
                mIsChildAccount ? R.string.sync_confirmation_personalize_services_body_child_account
                                : R.string.sync_confirmation_personalize_services_body);
        setText(mSigninSettingsControl, R.string.signin_signed_in_settings_description);
        setText(mNegativeButton, mCancelButtonTextId);
        setText(mPositiveButton, R.string.choose_account_sign_in);
        setText(mMoreButton, R.string.more);

        // The clickable "Settings" link.
        NoUnderlineClickableSpan settingsSpan = new NoUnderlineClickableSpan() {
            @Override
            public void onClick(View widget) {
                mListener.onAccountSelected(mSelectedAccountName, mIsDefaultAccountSelected, true);
                RecordUserAction.record("Signin_Signin_WithAdvancedSyncSettings");

                // Record the fact that the user consented to the consent text by clicking
                // on |mSigninSettingsControl|.
                recordConsent((TextView) widget);
            }
        };
        setText(mSigninSettingsControl, getSettingsControlDescription(mIsChildAccount), input -> {
            return SpanApplier.applySpans(input.toString(),
                    new SpanInfo(SETTINGS_LINK_OPEN, SETTINGS_LINK_CLOSE, settingsSpan));
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        triggerUpdateAccounts();
        AccountManagerFacade.get().addObserver(mAccountsChangedObserver);
        mProfileDataCache.addObserver(mProfileDataCacheObserver);
        if (mShouldShowConfirmationPageWhenAttachedToWindow) {
            // Can happen if init is invoked before attaching to window (https://crbug.com/800665).
            seedAccountsAndShowConfirmationPage();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mConfirmSyncDataStateMachine != null) {
            mConfirmSyncDataStateMachine.cancel(/* isBeingDestroyed = */ true);
            mConfirmSyncDataStateMachine = null;
        }
        mProfileDataCache.removeObserver(mProfileDataCacheObserver);
        AccountManagerFacade.get().removeObserver(mAccountsChangedObserver);
        super.onDetachedFromWindow();
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            triggerUpdateAccounts();
            return;
        }
        if (visibility == View.INVISIBLE && mGooglePlayServicesUpdateErrorHandler != null) {
            mGooglePlayServicesUpdateErrorHandler.cancelDialog();
            mGooglePlayServicesUpdateErrorHandler = null;
        }
    }

    /**
     * @return Whether the view is in signed in mode.
     */
    public boolean isInConfirmationScreen() {
        return mSelectedAccountName != null;
    }

    /**
     * Cancels signin confirmation and shows account selection page.
     */
    public void cancelConfirmationScreen() {
        assert isInConfirmationScreen();
        mUndoBehavior = UNDO_BACK_TO_SELECTION;
        showSigninPage();
    }

    private void setButtonsEnabled(boolean enabled) {
        mPositiveButton.setEnabled(enabled);
        mNegativeButton.setEnabled(enabled);
    }

    /**
     * Refresh the list of available system accounts asynchronously.
     */
    private void triggerUpdateAccounts() {
        AccountManagerFacade.get().getGoogleAccountNames(this::updateAccounts);
    }

    private void updateAccounts(AccountManagerResult<List<String>> result) {
        if (!ViewCompat.isAttachedToWindow(AccountSigninView.this)) {
            // This callback is invoked after AccountSigninView is detached from window
            // (e.g., Chrome is minimized). Updating view now is redundant and dangerous
            // (getFragmentManager() can return null, etc.). See https://crbug.com/733117.
            return;
        }

        final List<String> accountNames;
        try {
            accountNames = result.get();
        } catch (GmsAvailabilityException e) {
            dismissGmsUpdatingDialog();
            if (e.isUserResolvableError()) {
                showGmsErrorDialog(e.getGmsAvailabilityReturnCode());
            } else {
                Log.e(TAG, "Unresolvable GmsAvailabilityException.", e);
            }
            return;
        } catch (GmsJustUpdatedException e) {
            dismissGmsErrorDialog();
            showGmsUpdatingDialog();
            return;
        } catch (AccountManagerDelegateException e) {
            Log.e(TAG, "Unknown exception from AccountManagerFacade.", e);
            dismissGmsErrorDialog();
            dismissGmsUpdatingDialog();
            return;
        }
        dismissGmsErrorDialog();
        dismissGmsUpdatingDialog();

        if (mSelectedAccountName != null) {
            if (accountNames.contains(mSelectedAccountName)) return;

            if (mUndoBehavior == UNDO_BACK_TO_SELECTION) {
                RecordUserAction.record("Signin_Undo_Signin");
                showSigninPage();
            } else {
                mListener.onFailedToSetForcedAccount(mSelectedAccountName);
            }
            return;
        }

        List<String> oldAccountNames = mAccountNames;
        mAccountNames = accountNames;

        int oldSelectedAccount = mSigninChooseView.getSelectedAccountPosition();
        AccountSelectionResult selection = selectAccountAfterAccountsUpdate(
                oldAccountNames, mAccountNames, oldSelectedAccount);
        int accountToSelect = selection.getSelectedAccountIndex();
        boolean shouldJumpToConfirmationScreen = selection.shouldJumpToConfirmationScreen();

        mSigninChooseView.updateAccounts(mAccountNames, accountToSelect, mProfileDataCache);
        setUpSigninButton(!mAccountNames.isEmpty());
        mProfileDataCache.update(mAccountNames);

        boolean selectedAccountChanged = oldAccountNames != null && !oldAccountNames.isEmpty()
                && (mAccountNames.isEmpty()
                           || !mAccountNames.get(accountToSelect)
                                       .equals(oldAccountNames.get(oldSelectedAccount)));
        if (selectedAccountChanged && mConfirmSyncDataStateMachine != null) {
            // Any dialogs that may have been showing are now invalid (they were created
            // for the previously selected account).
            mConfirmSyncDataStateMachine.cancel(/* isBeingDestroyed = */ false);
            mConfirmSyncDataStateMachine = null;
        }

        if (shouldJumpToConfirmationScreen) {
            showConfirmationPageForSelectedAccount();
        }
    }

    private boolean hasGmsError() {
        return mGooglePlayServicesUpdateErrorHandler != null || mGmsIsUpdatingDialog != null;
    }

    private void showGmsErrorDialog(int gmsErrorCode) {
        if (mGooglePlayServicesUpdateErrorHandler != null
                && mGooglePlayServicesUpdateErrorHandler.isShowing()) {
            return;
        }
        boolean cancelable = !SigninManager.get().isForceSigninEnabled();
        mGooglePlayServicesUpdateErrorHandler =
                new UserRecoverableErrorHandler.ModalDialog(mDelegate.getActivity(), cancelable);
        mGooglePlayServicesUpdateErrorHandler.handleError(getContext(), gmsErrorCode);
    }

    private void showGmsUpdatingDialog() {
        if (mGmsIsUpdatingDialog != null) {
            return;
        }
        mGmsIsUpdatingDialog = new AlertDialog.Builder(getContext())
                .setCancelable(false)
                .setView(R.layout.updating_gms_progress_view)
                .create();
        mGmsIsUpdatingDialog.show();
        mGmsIsUpdatingDialogShowTime = SystemClock.elapsedRealtime();
    }

    private void dismissGmsErrorDialog() {
        if (mGooglePlayServicesUpdateErrorHandler == null) {
            return;
        }
        mGooglePlayServicesUpdateErrorHandler.cancelDialog();
        mGooglePlayServicesUpdateErrorHandler = null;
    }

    private void dismissGmsUpdatingDialog() {
        if (mGmsIsUpdatingDialog == null) {
            return;
        }
        mGmsIsUpdatingDialog.dismiss();
        mGmsIsUpdatingDialog = null;
        RecordHistogram.recordTimesHistogram("Signin.AndroidGmsUpdatingDialogShownTime",
                SystemClock.elapsedRealtime() - mGmsIsUpdatingDialogShowTime,
                TimeUnit.MILLISECONDS);
    }

    private static class AccountSelectionResult {
        private final int mSelectedAccountIndex;
        private final boolean mShouldJumpToConfirmationScreen;

        AccountSelectionResult(int selectedAccountIndex, boolean shouldJumpToConfirmationScreen) {
            mSelectedAccountIndex = selectedAccountIndex;
            mShouldJumpToConfirmationScreen = shouldJumpToConfirmationScreen;
        }

        int getSelectedAccountIndex() {
            return mSelectedAccountIndex;
        }

        boolean shouldJumpToConfirmationScreen() {
            return mShouldJumpToConfirmationScreen;
        }
    }

    /**
     * Determine what account should be selected after account list update. This function also
     * decides whether AccountSigninView should jump to confirmation screen.
     *
     * @param oldList Old list of user accounts.
     * @param newList New list of user accounts.
     * @param oldIndex Index of the selected account in the old list.
     * @return {@link AccountSelectionResult} that encapsulates new index and jump/no jump flag.
     */
    private static AccountSelectionResult selectAccountAfterAccountsUpdate(
            List<String> oldList, List<String> newList, int oldIndex) {
        if (oldList == null || newList == null) return new AccountSelectionResult(0, false);
        // Return the old index if nothing changed
        if (oldList.size() == newList.size() && oldList.containsAll(newList)) {
            return new AccountSelectionResult(oldIndex, false);
        }
        if (newList.containsAll(oldList)) {
            // A new account(s) has been added and no accounts have been deleted. Select new account
            // and jump to the confirmation screen if only one account was added.
            boolean shouldJumpToConfirmationScreen = newList.size() == oldList.size() + 1;
            for (int i = 0; i < newList.size(); i++) {
                if (!oldList.contains(newList.get(i))) {
                    return new AccountSelectionResult(i, shouldJumpToConfirmationScreen);
                }
            }
        }
        return new AccountSelectionResult(0, false);
    }

    private void updateProfileData() {
        mSigninChooseView.updateAccountProfileImages(mProfileDataCache);

        if (mSelectedAccountName != null) updateSignedInAccountInfo();
    }

    private void updateSignedInAccountInfo() {
        DisplayableProfileData profileData =
                mProfileDataCache.getProfileDataOrDefault(mSelectedAccountName);
        mSigninAccountImage.setImageDrawable(profileData.getImage());
        String name = null;
        if (mIsChildAccount) name = profileData.getGivenName();
        if (name == null) name = profileData.getFullNameOrEmail();
        setTextNonRecordable(
                mSigninAccountName, getResources().getString(R.string.signin_hi_name, name));
        setTextNonRecordable(mSigninAccountEmail, mSelectedAccountName);
    }

    private void showSigninPage() {
        mSelectedAccountName = null;

        mSigninConfirmationView.setVisibility(View.GONE);
        mSigninChooseView.setVisibility(View.VISIBLE);

        setUpCancelButton();
        triggerUpdateAccounts();
    }

    private void showConfirmationPage() {
        updateSignedInAccountInfo();
        mProfileDataCache.update(Collections.singletonList(mSelectedAccountName));

        mSigninChooseView.setVisibility(View.GONE);
        mSigninConfirmationView.setVisibility(View.VISIBLE);

        setButtonsEnabled(true);
        setUpConfirmButton();
        setUpUndoButton();
    }

    private void showConfirmationPageForSelectedAccount() {
        int index = mSigninChooseView.getSelectedAccountPosition();
        showConfirmationPageForAccount(mAccountNames.get(index), index == 0);
    }

    private void showConfirmationPageForAccount(String accountName, boolean isDefaultAccount) {
        assert accountName != null;

        // Disable the buttons to prevent them being clicked again while waiting for the callbacks.
        setButtonsEnabled(false);

        mSelectedAccountName = accountName;
        mIsDefaultAccountSelected = isDefaultAccount;
        seedAccountsAndShowConfirmationPage();
    }

    private void seedAccountsAndShowConfirmationPage() {
        // Ensure that the AccountTrackerService has a fully up to date GAIA id <-> email mapping,
        // as this is needed for the previous account check.
        final long seedingStartTime = SystemClock.elapsedRealtime();
        if (AccountTrackerService.get().checkAndSeedSystemAccounts()) {
            recordAccountTrackerServiceSeedingTime(seedingStartTime);
            runStateMachineAndShowConfirmationPage();
        } else {
            AccountTrackerService.get().addSystemAccountsSeededListener(
                    new OnSystemAccountsSeededListener() {
                        @Override
                        public void onSystemAccountsSeedingComplete() {
                            AccountTrackerService.get().removeSystemAccountsSeededListener(this);
                            recordAccountTrackerServiceSeedingTime(seedingStartTime);
                            // Don't show dialogs and confirmation page if activity was destroyed.
                            if (ViewCompat.isAttachedToWindow(AccountSigninView.this)) {
                                runStateMachineAndShowConfirmationPage();
                            } else {
                                mShouldShowConfirmationPageWhenAttachedToWindow = true;
                            }
                        }

                        @Override
                        public void onSystemAccountsChanged() {}
                    });
        }
    }

    private void runStateMachineAndShowConfirmationPage() {
        mConfirmSyncDataStateMachine = new ConfirmSyncDataStateMachine(getContext(),
                mDelegate.getFragmentManager(), ImportSyncType.PREVIOUS_DATA_FOUND,
                PrefServiceBridge.getInstance().getSyncLastAccountName(), mSelectedAccountName,
                new ConfirmImportSyncDataDialog.Listener() {
                    @Override
                    public void onConfirm(boolean wipeData) {
                        mConfirmSyncDataStateMachine = null;
                        SigninManager.wipeSyncUserDataIfRequired(wipeData).then(
                                (Void v) -> showConfirmationPage());
                    }

                    @Override
                    public void onCancel() {
                        mConfirmSyncDataStateMachine = null;
                        setButtonsEnabled(true);
                        onSigninConfirmationCancel();
                    }
                });
    }

    private static void recordAccountTrackerServiceSeedingTime(long seedingStartTime) {
        RecordHistogram.recordTimesHistogram("Signin.AndroidAccountSigninViewSeedingTime",
                SystemClock.elapsedRealtime() - seedingStartTime, TimeUnit.MILLISECONDS);
    }

    private void setUpCancelButton() {
        setNegativeButtonVisible(true);

        setText(mNegativeButton, mCancelButtonTextId);
        mNegativeButton.setOnClickListener(view -> {
            setButtonsEnabled(false);
            mListener.onAccountSelectionCanceled();
        });
    }

    private void setUpSigninButton(boolean hasAccounts) {
        if (hasAccounts) {
            setText(mPositiveButton, R.string.continue_sign_in);
            mPositiveButton.setOnClickListener(view -> showConfirmationPageForSelectedAccount());
        } else {
            setText(mPositiveButton, R.string.choose_account_sign_in);
            mPositiveButton.setOnClickListener(view -> {
                if (hasGmsError()) return;

                RecordUserAction.record("Signin_AddAccountToDevice");
                mListener.onNewAccount();
            });
        }
        setUpMoreButtonVisible(false);
    }

    private void setUpUndoButton() {
        if (mUndoBehavior == UNDO_INVISIBLE) {
            setNegativeButtonVisible(false);
            return;
        }
        setNegativeButtonVisible(true);
        setText(mNegativeButton, R.string.undo);
        mNegativeButton.setOnClickListener(view -> {
            RecordUserAction.record("Signin_Undo_Signin");
            onSigninConfirmationCancel();
        });
    }

    private void onSigninConfirmationCancel() {
        if (mUndoBehavior == UNDO_BACK_TO_SELECTION) {
            showSigninPage();
        } else {
            assert mUndoBehavior == UNDO_ABORT;
            mListener.onAccountSelectionCanceled();
        }
    }

    private void setUpConfirmButton() {
        setText(mPositiveButton, R.string.signin_accept);
        mPositiveButton.setOnClickListener(view -> {
            mListener.onAccountSelected(mSelectedAccountName, mIsDefaultAccountSelected, false);
            RecordUserAction.record("Signin_Signin_WithDefaultSyncSettings");

            // Record the fact that the user consented to the consent text by clicking
            // on |mPositiveButton|.
            recordConsent((TextView) view);
        });
        setUpMoreButtonVisible(true);
    }

    /*
    * mMoreButton is used to scroll mSigninConfirmationView down. It displays at the same position
    * as mPositiveButton.
    */
    private void setUpMoreButtonVisible(boolean enabled) {
        if (enabled) {
            mPositiveButton.setVisibility(View.GONE);
            mMoreButton.setVisibility(View.VISIBLE);
            mMoreButton.setOnClickListener(view -> {
                mSigninConfirmationView.smoothScrollBy(0, mSigninConfirmationView.getHeight());
                RecordUserAction.record("Signin_MoreButton_Shown");
            });
            mSigninConfirmationView.setObserver(() -> setUpMoreButtonVisible(false));
        } else {
            mPositiveButton.setVisibility(View.VISIBLE);
            mMoreButton.setVisibility(View.GONE);
            mSigninConfirmationView.setObserver(null);
        }
    }

    private void setNegativeButtonVisible(boolean enabled) {
        if (enabled) {
            mNegativeButton.setVisibility(View.VISIBLE);
            findViewById(R.id.positive_button_end_padding).setVisibility(View.GONE);
        } else {
            mNegativeButton.setVisibility(View.GONE);
            findViewById(R.id.positive_button_end_padding).setVisibility(View.INVISIBLE);
        }
    }

    private @StringRes int getSettingsControlDescription(boolean childAccount) {
        if (childAccount) {
            return R.string.signin_signed_in_settings_description_child_account;
        } else {
            return R.string.signin_signed_in_settings_description;
        }
    }

    /**
     * Applies a |transformation| on the string resource with given |id|, assigns the resulting
     * text to |view|, and caches the string resource |id| which will later be needed for consent
     * recording. Note that TextView instances used at the Sync consent screen MUST have their text
     * assigned using this method or {#link setTextNonRecordable}, which is verified
     * in {#link recordConsent()}.
     * @param view The TextView to which the text should be assigned.
     * @param id The id of the string resource with the text.
     * @param transformation The transformation to be applied on the text. Can be null to indicate
     *         no transformation (i.e. identity).
     */
    private void setText(
            TextView view, @StringRes int id, @Nullable TextTransformation transformation) {
        CharSequence text = getResources().getText(id);
        if (transformation != null) text = transformation.transform(text);
        view.setText(text);
        mTextViewToMetadataMap.put(view, new TextViewMetadata(text.toString(), id));
    }

    /**
     * Like {@link #setText(TextView, @StringRes int, TextTransformation)}, but with
     * no transformation applied on the assigned text.
     * @see #setText(TextView, @StringRes int, TextTransformation)
     * @param view The TextView to which the text should be assigned.
     * @param id The id of the string resource with the text.
     */
    private void setText(TextView view, @StringRes int id) {
        setText(view, id, null /* no text transformation */);
    }

    /**
     * Assigns a |text| to the given |view| and remembers that this text should be out of scope
     * for consent recording.
     * @see #setText(TextView, @StringRes int, TextTransformation)
     * @param view The TextView to which the text should be assigned.
     * @param text The text to be assigned.
     */
    private void setTextNonRecordable(TextView view, CharSequence text) {
        // TODO(crbug.com/821908): The selected account name, which is assigned to its |view| using
        // this method, can be null in rare circumstances.
        CharSequence textSanitized = text != null ? text : "";

        view.setText(textSanitized);
        mTextViewToMetadataMap.put(
                view, new TextViewMetadata(textSanitized.toString(), 0 /* no resource id */));
    }

    /**
     * Retrieves the string resource id from a given TextView while verifying that it corresponds
     * to the text of that TextView. This can be only done if the text was previously set by
     * {#link setText()} or {#link setTextNonRecordable()}.
     * @param view The TextView whose string resource id should be retrieved.
     * @return The string resource id of the |view|'s text. Can be 0 if this |view|'s text shouldn't
     *         be part of the consent record (Note: 0 is not a valid resource id).
     */
    private @StringRes int getConsentStringResource(TextView view) {
        TextViewMetadata metadata = mTextViewToMetadataMap.get(view);

        // Ensure that setText() was used to assign this text.
        assert metadata
                != null : "The text '" + view.getText().toString() + "' was not assigned "
                          + "by setText() or setTextNonRecordable().";

        // Ensure that the text hasn't changed since the assignment.
        assert view.getText().toString()
                == metadata.getString()
            : "The text '"
                        + view.getText().toString()
                        + "' has been modified after it was assigned by setText() "
                        + "or setTextNonRecordable().";
        return metadata.getId();
    }

    /**
     * @param view The root View where to start scanning.
     * @param outViews The output list to which |view| and all its transitive
     *         children, if visible, will be appended.
     */
    private void getAllVisibleViews(View view, ArrayList<View> outViews) {
        if (view.getVisibility() != View.VISIBLE) return;
        outViews.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); ++i) {
            getAllVisibleViews(group.getChildAt(i), outViews);
        }
    }

    /**
     * Records the Sync consent.
     * @param confirmationView The view that the user clicked when consenting.
     */
    private void recordConsent(TextView confirmationView) {
        int consentConfirmation = getConsentStringResource(confirmationView);

        ArrayList<Integer> consentDescription = new ArrayList<>();
        ArrayList<View> visibleViews = new ArrayList<>();
        getAllVisibleViews(findViewById(R.id.signin_confirmation_view), visibleViews);
        getAllVisibleViews(findViewById(R.id.button_bar), visibleViews);

        for (View view : visibleViews) {
            if (!(view instanceof TextView)) continue; // This element doesn't hold any text.
            @StringRes
            int id = getConsentStringResource((TextView) view);
            if (id == 0) continue; // This text is not relevant for consent recording.
            consentDescription.add(id);
        }

        ConsentAuditorBridge.getInstance().recordConsent(
                ConsentAuditorFeature.CHROME_SYNC, consentDescription, consentConfirmation);
    }
}
