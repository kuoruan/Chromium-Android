// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Intent-specific interface to call into VR. */
public interface VrIntentDelegate {
    boolean isVrIntent(Intent intent);
    boolean isLaunchingIntoVr(Activity activity, Intent intent);
    Intent setupVrFreIntent(Context context, Intent freIntent);
    Bundle getVrIntentOptions(Context context);
}
