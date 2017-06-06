// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.support.design.widget.TabLayout;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.infobar.translate.TranslateTabLayout;

/**
 * Java version of the compcat translate infobar
 */
class TranslateCompactInfoBar extends InfoBar implements TabLayout.OnTabSelectedListener {
    private final TranslateOptions mOptions;

    private long mNativeTranslateInfoBarPtr;
    private TranslateTabLayout mTabLayout;

    @CalledByNative
    private static InfoBar create(String sourceLanguageCode, String targetLanguageCode,
            String[] languages, String[] codes) {
        return new TranslateCompactInfoBar(
                sourceLanguageCode, targetLanguageCode, languages, codes);
    }

    TranslateCompactInfoBar(String sourceLanguageCode, String targetLanguageCode,
            String[] languages, String[] codes) {
        super(R.drawable.infobar_translate, null, null);
        // TODO(googleo): Set correct values for the last 2.
        mOptions = TranslateOptions.create(
                sourceLanguageCode, targetLanguageCode, languages, codes, false, false);
    }

    @Override
    protected boolean usesCompactLayout() {
        return true;
    }

    @Override
    protected void createCompactLayoutContent(InfoBarCompactLayout parent) {
        LinearLayout content =
                (LinearLayout) LayoutInflater.from(getContext())
                        .inflate(R.layout.infobar_translate_compact_content, parent, false);

        mTabLayout = (TranslateTabLayout) content.findViewById(R.id.translate_infobar_tabs);
        mTabLayout.addTabs(mOptions.sourceLanguageName(), mOptions.targetLanguageName());
        mTabLayout.addOnTabSelectedListener(this);

        parent.addContent(content, 1.0f);
    }

    @CalledByNative
    private void onPageTranslated(int errorType) {
        if (mTabLayout != null) {
            // Success
            if (errorType == 0) {
                mTabLayout.hideProgressBar();
            } else {
                mTabLayout.stopProgressBarAndRevertBack();
            }
        }
    }

    @CalledByNative
    private void setNativePtr(long nativePtr) {
        mNativeTranslateInfoBarPtr = nativePtr;
    }

    @Override
    protected void onNativeDestroyed() {
        mNativeTranslateInfoBarPtr = 0;
        super.onNativeDestroyed();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        if (tab.getPosition() == 0) {
            onButtonClicked(ActionType.TRANSLATE_SHOW_ORIGINAL);
        } else {
            mTabLayout.showProgressBarOnTab(tab.getPosition());
            onButtonClicked(ActionType.TRANSLATE);
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public void onTabReselected(TabLayout.Tab tab) {}

    private native void nativeApplyTranslateOptions(long nativeTranslateCompactInfoBar);
}
