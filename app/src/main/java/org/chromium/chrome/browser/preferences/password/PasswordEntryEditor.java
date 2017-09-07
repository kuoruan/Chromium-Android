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
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.PasswordUIView;
import org.chromium.chrome.browser.PasswordUIView.PasswordListObserver;
import org.chromium.ui.widget.Toast;

/**
 * Password entry editor that allows to view and delete passwords stored in Chrome.
 */
public class PasswordEntryEditor extends Fragment {

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
        if (shouldDisplayInteractivePasswordEntryEditor()) {
            if (!mException) {
                mView = inflater.inflate(
                        R.layout.password_entry_editor_interactive, container, false);
            } else {
                mView = inflater.inflate(R.layout.password_entry_exception, container, false);
            }
        } else {
            mView = inflater.inflate(R.layout.password_entry_editor, container, false);
        }
        getActivity().setTitle(R.string.password_entry_editor_title);
        mClipboard = (ClipboardManager) getActivity().getApplicationContext().getSystemService(
                Context.CLIPBOARD_SERVICE);
        TextView nameView = (TextView) mView.findViewById(R.id.password_entry_editor_name);
        if (!mException) {
            nameView.setText(name);
        } else {
            if (!shouldDisplayInteractivePasswordEntryEditor()) {
                nameView.setText(R.string.section_saved_passwords_exceptions);
            }
        }
        final String url = mExtras.getString(SavePasswordsPreferences.PASSWORD_LIST_URL);
        TextView urlView = (TextView) mView.findViewById(R.id.password_entry_editor_url);
        urlView.setText(url);
        if (shouldDisplayInteractivePasswordEntryEditor()) {
            mKeyguardManager =
                    (KeyguardManager) getActivity().getApplicationContext().getSystemService(
                            Context.KEYGUARD_SERVICE);
            if (!mException) {
                hidePassword();
                hookupPasswordButtons();
                hookupCopyUsernameButton();
            }
            hookupCopySiteButton();
        } else {
            hookupCancelDeleteButtons();
        }
        return mView;
    }

    public boolean shouldDisplayInteractivePasswordEntryEditor() {
        return ChromeFeatureList.isEnabled(VIEW_PASSWORDS)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
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
                    getActivity().finish();
                }
            }

            @Override
            public void passwordExceptionListAvailable(int count) {
                if (mException) {
                    passwordUIView.removeSavedPasswordException(mID);
                    passwordUIView.destroy();
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
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().finish();
            }
        });
    }

    private void hookupCopyUsernameButton() {
        final ImageButton copyUsernameButton =
                (ImageButton) mView.findViewById(R.id.password_entry_editor_copy_username);
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
            }
        });
    }

    private void hookupCopySiteButton() {
        final ImageButton copySiteButton =
                (ImageButton) mView.findViewById(R.id.password_entry_editor_copy_site);
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
            }
        });
    }

    private void changeHowPasswordIsDisplayed(int visibilityIcon, int inputType) {
        TextView passwordView = (TextView) mView.findViewById(R.id.password_entry_editor_password);
        ImageButton viewPasswordButton =
                (ImageButton) mView.findViewById(R.id.password_entry_editor_view_password);
        passwordView.setText(mExtras.getString(SavePasswordsPreferences.PASSWORD_LIST_PASSWORD));
        passwordView.setInputType(inputType);
        viewPasswordButton.setImageResource(visibilityIcon);
    }

    private void displayPassword() {
        changeHowPasswordIsDisplayed(
                R.drawable.ic_visibility_off, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    private void hidePassword() {
        changeHowPasswordIsDisplayed(R.drawable.ic_visibility,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private void copyPassword() {
        ClipData clip = ClipData.newPlainText("password",
                getArguments().getString(SavePasswordsPreferences.PASSWORD_LIST_PASSWORD));
        mClipboard.setPrimaryClip(clip);
        Toast.makeText(getActivity().getApplicationContext(),
                     R.string.password_entry_editor_password_copied_into_clipboard,
                     Toast.LENGTH_SHORT)
                .show();
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
                } else if (passwordView.getInputType()
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
