// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.usage_stats;

import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.SynchronousInitializationActivity;
import org.chromium.chrome.browser.widget.PromoDialog;

/**
 * Activity that prompts the user for consent to share browsing activity with Digital Wellbeing.
 */
public class UsageStatsConsentActivity extends SynchronousInitializationActivity {
    private static final String DIGITAL_WELLBEING_PACKAGE_NAME =
            "com.google.android.apps.wellbeing";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO(pnoland): handle revocation as well as authorization.
        ComponentName caller = getCallingActivity();
        if (caller == null
                || !TextUtils.equals(DIGITAL_WELLBEING_PACKAGE_NAME, caller.getPackageName())) {
            finish();
            return;
        }

        PromoDialog promoScreen = makePromoScreen();
        promoScreen.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private PromoDialog makePromoScreen() {
        return new PromoDialog(this) {
            @Override
            protected DialogParams getDialogParams() {
                PromoDialog.DialogParams params = new PromoDialog.DialogParams();
                params.headerStringResource = R.string.usage_stats_consent_title;
                params.subheaderStringResource = R.string.usage_stats_consent_prompt;
                params.primaryButtonStringResource = R.string.ok;
                params.secondaryButtonStringResource = R.string.cancel;
                return params;
            }

            @Override
            public void onDismiss(DialogInterface dialog) {
                UsageStatsConsentActivity.this.finish();
            }

            @Override
            public void onClick(View v) {
                int id = v.getId();
                UsageStatsService service = UsageStatsService.getInstance();

                if (id == R.id.button_primary) {
                    service.setOptInState(true);
                    UsageStatsConsentActivity.this.setResult(Activity.RESULT_OK);
                    UsageStatsConsentActivity.this.finish();
                } else if (id == R.id.button_secondary) {
                    service.setOptInState(false);
                    UsageStatsConsentActivity.this.setResult(Activity.RESULT_CANCELED);
                    UsageStatsConsentActivity.this.finish();
                } else {
                    assert false : "Unhandled onClick event";
                }
            }
        };
    }
}