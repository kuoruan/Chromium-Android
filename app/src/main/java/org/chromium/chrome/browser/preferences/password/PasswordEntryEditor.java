// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.PasswordUIView;
import org.chromium.chrome.browser.PasswordUIView.PasswordListObserver;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.components.sync.AndroidSyncSettings;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.widget.Toast;

/**
 * Password entry editor that allows to view and delete passwords stored in Chrome.
 */
public class PasswordEntryEditor extends Fragment {
    // Constants used to log UMA enum histogram, must stay in sync with
    // PasswordManagerAndroidPasswordEntryActions. Further actions can only be appended, existing
    // entries must not be overwritten.
    private static final int PASSWORD_ENTRY_ACTION_VIEWED = 0;
    private static final int PASSWORD_ENTRY_ACTION_DELETED = 1;
    private static final int PASSWORD_ENTRY_ACTION_CANCELLED = 2;
    private static final int PASSWORD_ENTRY_ACTION_BOUNDARY = 3;

    // Constants used to log UMA enum histogram, must stay in sync with
    // PasswordManagerAndroidWebsiteActions. Further actions can only be appended, existing
    // entries must not be overwritten.
    private static final int WEBSITE_ACTION_COPIED = 0;
    private static final int WEBSITE_ACTION_BOUNDARY = 1;

    // Constants used to log UMA enum histogram, must stay in sync with
    // PasswordManagerAndroidUsernameActions. Further actions can only be appended, existing
    // entries must not be overwritten.
    private static final int USERNAME_ACTION_COPIED = 0;
    private static final int USERNAME_ACTION_BOUNDARY = 1;

    // Constants used to log UMA enum histogram, must stay in sync with
    // PasswordManagerAndroidPasswordActions. Further actions can only be appended, existing
    // entries must not be overwritten.
    private static final int PASSWORD_ACTION_COPIED = 0;
    private static final int PASSWORD_ACTION_DISPLAYED = 1;
    private static final int PASSWORD_ACTION_HIDDEN = 2;
    private static final int PASSWORD_ACTION_BOUNDARY = 3;

    // ID of this name/password or exception.
    private int mID;

    // If true this is an exception site (never save here).
    // If false this represents a saved name/password.
    private boolean mException;

    @VisibleForTesting
    public static final String VIEW_PASSWORDS = "view-passwords";
    private static final int VALID_REAUTHENTICATION_TIME_INTERVAL_MILLIS = 60000;

    private ClipboardManager mClipboard;
    private KeyguardManager mKeyguardManager;
    private Bundle mExtras;
    private View mView;
    private boolean mViewButtonPressed;
    private boolean mCopyButtonPressed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ChromeFeatureList.isEnabled(VIEW_PASSWORDS)) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Extras are set on this intent in class {@link SavePasswordsPreferences}.
        mExtras = getArguments();
        assert mExtras != null;
        mID = mExtras.getInt(SavePasswordsPreferences.PASSWORD_LIST_ID);
        final String name = mExtras.containsKey(SavePasswordsPreferences.PASSWORD_LIST_NAME)
                ? mExtras.getString(SavePasswordsPreferences.PASSWORD_LIST_NAME)
                : null;

        mException = (name == null);
        final String url = mExtras.getString(SavePasswordsPreferences.PASSWORD_LIST_URL);
        getActivity().setTitle(R.string.password_entry_editor_title);
        mClipboard = (ClipboardManager) getActivity().getApplicationContext().getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (shouldDisplayInteractivePasswordEntryEditor()) {
            mView = inflater.inflate(mException ? R.layout.password_entry_exception
                                                : R.layout.password_entry_editor_interactive,
                    container, false);
            getActivity().setTitle(R.string.password_entry_editor_title);
            mClipboard = (ClipboardManager) getActivity().getApplicationContext().getSystemService(
                    Context.CLIPBOARD_SERVICE);
            View urlRowsView = mView.findViewById(R.id.url_row);
            TextView dataView = urlRowsView.findViewById(R.id.password_entry_editor_row_data);
            dataView.setText(url);

            hookupCopySiteButton(urlRowsView);
            if (!mException) {
                View usernameView = mView.findViewById(R.id.username_row);
                TextView usernameDataView =
                        usernameView.findViewById(R.id.password_entry_editor_row_data);
                usernameDataView.setText(name);
                hookupCopyUsernameButton(usernameView);
                mKeyguardManager =
                        (KeyguardManager) getActivity().getApplicationContext().getSystemService(
                                Context.KEYGUARD_SERVICE);
                if (isReauthenticationAvailable()) {
                    hidePassword();
                    hookupPasswordButtons();
                } else {
                    mView.findViewById(R.id.password_data).setVisibility(View.GONE);
                    if (isPasswordSyncingUser()) {
                        ForegroundColorSpan colorSpan =
                                new ForegroundColorSpan(ApiCompatibilityUtils.getColor(
                                        getResources(), R.color.pref_accent_color));
                        SpannableString passwordLink =
                                SpanApplier.applySpans(getString(R.string.manage_passwords_text),
                                        new SpanApplier.SpanInfo("<link>", "</link>", colorSpan));
                        ClickableSpan clickableLink = new ClickableSpan() {
                            @Override
                            public void onClick(View textView) {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(PasswordUIView.getAccountDashboardURL()));
                                intent.setPackage(getActivity().getPackageName());
                                getActivity().startActivity(intent);
                            }

                            @Override
                            public void updateDrawState(TextPaint ds) {}
                        };
                        passwordLink.setSpan(clickableLink, 0, passwordLink.length(),
                                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                        TextView passwordsLinkTextView = mView.findViewById(R.id.passwords_link);
                        passwordsLinkTextView.setVisibility(View.VISIBLE);
                        passwordsLinkTextView.setText(passwordLink);
                        passwordsLinkTextView.setMovementMethod(LinkMovementMethod.getInstance());
                    } else {
                        mView.findViewById(R.id.password_title).setVisibility(View.GONE);
                    }
                }
            }
        } else {
            mView = inflater.inflate(R.layout.password_entry_editor, container, false);
            TextView nameView = (TextView) mView.findViewById(R.id.password_entry_editor_name);
            if (!mException) {
                nameView.setText(name);
            } else {
                nameView.setText(R.string.section_saved_passwords_exceptions);
            }
            TextView urlView = (TextView) mView.findViewById(R.id.password_entry_editor_url);
            urlView.setText(url);
            hookupCancelDeleteButtons();
        }
        // NOTE: This is deliberately not simplified so that the histogram strings can be found via
        // code search and pre-submit scripts can catch errors. Also applies to similar spots below.
        if (mException) {
            RecordHistogram.recordEnumeratedHistogram(
                    "PasswordManager.Android.PasswordExceptionEntry", PASSWORD_ENTRY_ACTION_VIEWED,
                    PASSWORD_ENTRY_ACTION_BOUNDARY);
        } else {
            RecordHistogram.recordEnumeratedHistogram(
                    "PasswordManager.Android.PasswordCredentialEntry", PASSWORD_ENTRY_ACTION_VIEWED,
                    PASSWORD_ENTRY_ACTION_BOUNDARY);
        }
        return mView;
    }

    public boolean shouldDisplayInteractivePasswordEntryEditor() {
        return ChromeFeatureList.isEnabled(VIEW_PASSWORDS);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (authenticationStillValid()) {
            if (mViewButtonPressed) displayPassword();

            if (mCopyButtonPressed) copyPassword();
        }
    }

    /**
     * Verifies if authentication is still valid (the user authenticated less than 60 seconds ago
     * and the startTime is not equal to 0.
     */
    private boolean authenticationStillValid() {
        return SavePasswordsPreferences.getLastReauthTimeMillis() != 0
                && System.currentTimeMillis() - SavePasswordsPreferences.getLastReauthTimeMillis()
                < VALID_REAUTHENTICATION_TIME_INTERVAL_MILLIS;
    }

    private boolean isReauthenticationAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private boolean isPasswordSyncingUser() {
        ProfileSyncService syncService = ProfileSyncService.get();
        return (AndroidSyncSettings.isSyncEnabled(getActivity().getApplicationContext())
                && syncService.isEngineInitialized() && !syncService.isUsingSecondaryPassphrase());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.password_entry_editor_action_bar_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_delete_saved_password) {
            removeItem();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Delete was clicked.
    private void removeItem() {
        final PasswordUIView passwordUIView = new PasswordUIView();
        final PasswordListObserver passwordDeleter = new PasswordListObserver() {
            @Override
            public void passwordListAvailable(int count) {
                if (!mException) {
                    passwordUIView.removeSavedPasswordEntry(mID);
                    passwordUIView.destroy();
                    Toast.makeText(getActivity().getApplicationContext(), R.string.deleted,
                                 Toast.LENGTH_SHORT)
                            .show();
                    getActivity().finish();
                }
            }

            @Override
            public void passwordExceptionListAvailable(int count) {
                if (mException) {
                    passwordUIView.removeSavedPasswordException(mID);
                    passwordUIView.destroy();
                    Toast.makeText(getActivity().getApplicationContext(), R.string.deleted,
                                 Toast.LENGTH_SHORT)
                            .show();
                    getActivity().finish();
                }
            }
        };

        passwordUIView.addObserver(passwordDeleter);
        passwordUIView.updatePasswordLists();
    }

    private void hookupCancelDeleteButtons() {
        final Button deleteButton = (Button) mView.findViewById(R.id.password_entry_editor_delete);
        final Button cancelButton = (Button) mView.findViewById(R.id.password_entry_editor_cancel);

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeItem();
                deleteButton.setEnabled(false);
                cancelButton.setEnabled(false);
                if (mException) {
                    RecordHistogram.recordEnumeratedHistogram(
                            "PasswordManager.Android.PasswordExceptionEntry",
                            PASSWORD_ENTRY_ACTION_DELETED, PASSWORD_ENTRY_ACTION_BOUNDARY);
                } else {
                    RecordHistogram.recordEnumeratedHistogram(
                            "PasswordManager.Android.PasswordCredentialEntry",
                            PASSWORD_ENTRY_ACTION_DELETED, PASSWORD_ENTRY_ACTION_BOUNDARY);
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().finish();
                if (mException) {
                    RecordHistogram.recordEnumeratedHistogram(
                            "PasswordManager.Android.PasswordExceptionEntry",
                            PASSWORD_ENTRY_ACTION_CANCELLED, PASSWORD_ENTRY_ACTION_BOUNDARY);
                } else {
                    RecordHistogram.recordEnumeratedHistogram(
                            "PasswordManager.Android.PasswordCredentialEntry",
                            PASSWORD_ENTRY_ACTION_CANCELLED, PASSWORD_ENTRY_ACTION_BOUNDARY);
                }
            }
        });
    }

    private void hookupCopyUsernameButton(View usernameView) {
        final ImageButton copyUsernameButton =
                (ImageButton) usernameView.findViewById(R.id.password_entry_editor_copy);
        copyUsernameButton.setContentDescription(
                getActivity().getString(R.string.password_entry_editor_copy_stored_username));
        copyUsernameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipData clip = ClipData.newPlainText("username",
                        getArguments().getString(SavePasswordsPreferences.PASSWORD_LIST_NAME));
                mClipboard.setPrimaryClip(clip);
                Toast.makeText(getActivity().getApplicationContext(),
                             R.string.password_entry_editor_username_copied_into_clipboard,
                             Toast.LENGTH_SHORT)
                        .show();
                RecordHistogram.recordEnumeratedHistogram(
                        "PasswordManager.Android.PasswordCredentialEntry.Username",
                        USERNAME_ACTION_COPIED, USERNAME_ACTION_BOUNDARY);
            }
        });
    }

    private void hookupCopySiteButton(View siteView) {
        final ImageButton copySiteButton =
                (ImageButton) siteView.findViewById(R.id.password_entry_editor_copy);
        copySiteButton.setContentDescription(
                getActivity().getString(R.string.password_entry_editor_copy_stored_site));
        copySiteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipData clip = ClipData.newPlainText("site",
                        getArguments().getString(SavePasswordsPreferences.PASSWORD_LIST_URL));
                mClipboard.setPrimaryClip(clip);
                Toast.makeText(getActivity().getApplicationContext(),
                             R.string.password_entry_editor_site_copied_into_clipboard,
                             Toast.LENGTH_SHORT)
                        .show();
                if (mException) {
                    RecordHistogram.recordEnumeratedHistogram(
                            "PasswordManager.Android.PasswordExceptionEntry.Website",
                            WEBSITE_ACTION_COPIED, WEBSITE_ACTION_BOUNDARY);
                } else {
                    RecordHistogram.recordEnumeratedHistogram(
                            "PasswordManager.Android.PasswordCredentialEntry.Website",
                            WEBSITE_ACTION_COPIED, WEBSITE_ACTION_BOUNDARY);
                }
            }
        });
    }

    private void changeHowPasswordIsDisplayed(
            int visibilityIcon, int inputType, @StringRes int annotation) {
        TextView passwordView = (TextView) mView.findViewById(R.id.password_entry_editor_password);
        ImageButton viewPasswordButton =
                (ImageButton) mView.findViewById(R.id.password_entry_editor_view_password);
        passwordView.setText(mExtras.getString(SavePasswordsPreferences.PASSWORD_LIST_PASSWORD));
        passwordView.setInputType(inputType);
        viewPasswordButton.setImageResource(visibilityIcon);
        viewPasswordButton.setContentDescription(getActivity().getString(annotation));
    }

    private void displayPassword() {
        getActivity().getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);

        changeHowPasswordIsDisplayed(R.drawable.ic_visibility_off,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                R.string.password_entry_editor_hide_stored_password);
        RecordHistogram.recordEnumeratedHistogram(
                "PasswordManager.Android.PasswordCredentialEntry.Password",
                PASSWORD_ACTION_DISPLAYED, PASSWORD_ACTION_BOUNDARY);
    }

    private void hidePassword() {
        changeHowPasswordIsDisplayed(R.drawable.ic_visibility,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                R.string.password_entry_editor_view_stored_password);
        RecordHistogram.recordEnumeratedHistogram(
                "PasswordManager.Android.PasswordCredentialEntry.Password", PASSWORD_ACTION_HIDDEN,
                PASSWORD_ACTION_BOUNDARY);
    }

    private void copyPassword() {
        ClipData clip = ClipData.newPlainText("password",
                getArguments().getString(SavePasswordsPreferences.PASSWORD_LIST_PASSWORD));
        mClipboard.setPrimaryClip(clip);
        Toast.makeText(getActivity().getApplicationContext(),
                     R.string.password_entry_editor_password_copied_into_clipboard,
                     Toast.LENGTH_SHORT)
                .show();
        RecordHistogram.recordEnumeratedHistogram(
                "PasswordManager.Android.PasswordCredentialEntry.Password", PASSWORD_ACTION_COPIED,
                PASSWORD_ACTION_BOUNDARY);
    }

    private void displayReauthenticationFragment() {
        Fragment passwordReauthentication = new PasswordReauthenticationFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(
                R.id.password_entry_editor_interactive, passwordReauthentication);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    private void hookupPasswordButtons() {
        final ImageButton copyPasswordButton =
                (ImageButton) mView.findViewById(R.id.password_entry_editor_copy_password);
        final ImageButton viewPasswordButton =
                (ImageButton) mView.findViewById(R.id.password_entry_editor_view_password);
        copyPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mKeyguardManager.isKeyguardSecure()) {
                    Toast.makeText(getActivity().getApplicationContext(),
                                 R.string.password_entry_editor_set_lock_screen, Toast.LENGTH_LONG)
                            .show();
                } else if (authenticationStillValid()) {
                    copyPassword();
                } else {
                    mCopyButtonPressed = true;
                    displayReauthenticationFragment();
                }
            }
        });
        viewPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView passwordView =
                        (TextView) mView.findViewById(R.id.password_entry_editor_password);
                if (!mKeyguardManager.isKeyguardSecure()) {
                    Toast.makeText(getActivity().getApplicationContext(),
                                 R.string.password_entry_editor_set_lock_screen, Toast.LENGTH_LONG)
                            .show();
                } else if ((passwordView.getInputType()
                                   & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                        == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    hidePassword();
                } else if (authenticationStillValid()) {
                    displayPassword();
                } else {
                    mViewButtonPressed = true;
                    displayReauthenticationFragment();
                }
            }
        });
    }

}
