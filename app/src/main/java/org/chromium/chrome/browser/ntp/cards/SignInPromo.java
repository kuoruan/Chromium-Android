// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.signin.AccountSigninActivity;
import org.chromium.chrome.browser.signin.SigninAccessPoint;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInAllowedObserver;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.suggestions.DestructionObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsRecyclerView;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

/**
 * Shows a card prompting the user to sign in. This item is also an {@link OptionalLeaf}, and sign
 * in state changes control its visibility.
 */
public class SignInPromo extends OptionalLeaf
        implements StatusCardViewHolder.DataSource, ImpressionTracker.Listener {

    /**
     * Whether the user has seen the promo and dismissed it at some point. When this is set,
     * the promo will never be shown.
     */
    private boolean mDismissed;

    /**
     * Whether the signin status means that the user has the possibility to sign in.
     */
    private boolean mCanSignIn;

    /**
     * Whether personalized suggestions can be shown. If it's not the case, we have no reason to
     * offer the user to sign in.
     */
    private boolean mCanShowPersonalizedSuggestions;

    private final ImpressionTracker mImpressionTracker = new ImpressionTracker(null, this);

    @Nullable
    private final SigninObserver mSigninObserver;

    public SignInPromo(SuggestionsUiDelegate uiDelegate) {
        mDismissed = ChromePreferenceManager.getInstance().getNewTabPageSigninPromoDismissed();

        SuggestionsSource suggestionsSource = uiDelegate.getSuggestionsSource();
        SigninManager signinManager = SigninManager.get(ContextUtils.getApplicationContext());
        if (mDismissed) {
            mSigninObserver = null;
        } else {
            mSigninObserver = new SigninObserver(signinManager, suggestionsSource);
            uiDelegate.addDestructionObserver(mSigninObserver);
        }

        mCanSignIn = signinManager.isSignInAllowed() && !signinManager.isSignedInOnNative();
        mCanShowPersonalizedSuggestions = suggestionsSource.areRemoteSuggestionsEnabled();
        updateVisibility();
    }

    @Override
    @ItemViewType
    protected int getItemViewType() {
        return ItemViewType.PROMO;
    }

    /**
     * @return a {@link DestructionObserver} observer that updates the visibility of the signin
     * promo and unregisters itself when the New Tab Page is destroyed.
     */
    @Nullable
    public DestructionObserver getObserver() {
        return mSigninObserver;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof StatusCardViewHolder;
        ((StatusCardViewHolder) holder).onBindViewHolder(this);
        mImpressionTracker.reset(mImpressionTracker.wasTriggered() ? null : holder.itemView);
    }

    @Override
    protected void visitOptionalItem(NodeVisitor visitor) {
        visitor.visitSignInPromo();
    }

    @Override
    @StringRes
    public int getHeader() {
        return R.string.snippets_disabled_generic_prompt;
    }

    @Override
    public String getDescription() {
        return ContextUtils.getApplicationContext().getString(
                R.string.snippets_disabled_signed_out_instructions);
    }

    @Override
    @StringRes
    public int getActionLabel() {
        return R.string.sign_in_button;
    }

    @Override
    public void performAction(Context context) {
        AccountSigninActivity.startIfAllowed(context, SigninAccessPoint.NTP_CONTENT_SUGGESTIONS);
    }

    @Override
    public void onImpression() {
        RecordUserAction.record("Signin_Impression_FromNTPContentSuggestions");
        mImpressionTracker.reset(null);
    }

    private void updateVisibility() {
        setVisibilityInternal(!mDismissed && mCanSignIn && mCanShowPersonalizedSuggestions);
    }

    @Override
    protected boolean canBeDismissed() {
        return true;
    }

    /** Hides the sign in promo and sets a preference to make sure it is not shown again. */
    @Override
    public void dismiss(Callback<String> itemRemovedCallback) {
        assert mSigninObserver != null;
        mDismissed = true;
        updateVisibility();

        ChromePreferenceManager.getInstance().setNewTabPageSigninPromoDismissed(true);
        mSigninObserver.unregister();
        itemRemovedCallback.onResult(ContextUtils.getApplicationContext().getString(getHeader()));
    }

    @VisibleForTesting
    class SigninObserver extends SuggestionsSource.EmptyObserver
            implements SignInStateObserver, SignInAllowedObserver, DestructionObserver {
        private final SigninManager mSigninManager;
        private final SuggestionsSource mSuggestionsSource;

        /** Guards {@link #unregister()}, which can be called multiple times. */
        private boolean mUnregistered;

        private SigninObserver(SigninManager signinManager, SuggestionsSource suggestionsSource) {
            mSigninManager = signinManager;
            mSigninManager.addSignInAllowedObserver(this);
            mSigninManager.addSignInStateObserver(this);

            mSuggestionsSource = suggestionsSource;
            mSuggestionsSource.addObserver(this);
        }

        private void unregister() {
            if (mUnregistered) return;
            mUnregistered = true;

            mSigninManager.removeSignInAllowedObserver(this);
            mSigninManager.removeSignInStateObserver(this);

            mSuggestionsSource.removeObserver(this);
        }

        @Override
        public void onDestroy() {
            unregister();
        }

        @Override
        public void onSignInAllowedChanged() {
            // Listening to onSignInAllowedChanged is important for the FRE. Sign in is not allowed
            // until it is completed, but the NTP is initialised before the FRE is even shown. By
            // implementing this we can show the promo if the user did not sign in during the FRE.
            mCanSignIn = mSigninManager.isSignInAllowed();
            updateVisibility();
        }

        @Override
        public void onSignedIn() {
            mCanSignIn = false;
            updateVisibility();
        }

        @Override
        public void onSignedOut() {
            mCanSignIn = mSigninManager.isSignInAllowed();
            updateVisibility();
        }

        @Override
        public void onCategoryStatusChanged(
                @CategoryInt int category, @CategoryStatus int newStatus) {
            if (!SnippetsBridge.isCategoryRemote(category)) return;

            // Checks whether the category is enabled first to avoid unnecessary calls across JNI.
            mCanShowPersonalizedSuggestions = SnippetsBridge.isCategoryEnabled(category)
                    || mSuggestionsSource.areRemoteSuggestionsEnabled();
            updateVisibility();
        }
    }

    /**
     * View Holder for {@link SignInPromo}.
     */
    public static class ViewHolder extends StatusCardViewHolder {
        public ViewHolder(SuggestionsRecyclerView parent, ContextMenuManager contextMenuManager,
                UiConfig config) {
            super(parent, contextMenuManager, config);
            getParams().topMargin = parent.getResources().getDimensionPixelSize(
                    R.dimen.ntp_sign_in_promo_margin_top);
        }

        @DrawableRes
        @Override
        protected int selectBackground(boolean hasCardAbove, boolean hasCardBelow) {
            return R.drawable.ntp_signin_promo_card_single;
        }
    }
}
