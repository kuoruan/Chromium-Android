// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import android.os.Bundle;
import org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate;
import org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost;
import org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost;
import org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper;

/**
 * Entry point for a dynamic module.
 */
interface IModuleEntryPoint {

  void init(in IModuleHost moduleHost) = 0;

  int getModuleVersion() = 1;

  int getMinimumHostVersion() = 2;

  /**
   * Called when an enhanced activity is started.
   *
   * @throws IllegalStateException if the hosted application is not created.
   */
  IActivityDelegate createActivityDelegate(in IActivityHost activityHost) = 3;

  void onDestroy() = 4;
}
