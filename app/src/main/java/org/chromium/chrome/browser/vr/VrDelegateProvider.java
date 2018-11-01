// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr;

/** Provides delegate interfaces that can be used to call into VR.  */
/* package */ interface VrDelegateProvider {
    VrDelegate getDelegate();
    VrIntentDelegate getIntentDelegate();
}
