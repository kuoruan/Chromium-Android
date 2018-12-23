// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper;

/**
 * Chrome host that runs custom modules.
 */
interface IModuleHost {

  /** Application context of Chrome. */
  IObjectWrapper /* Context */ getHostApplicationContext() = 0;

  /** Generated context of the module. */
  IObjectWrapper /* Context */ getModuleContext() = 1;

  int getHostVersion() = 2;

  int getMinimumModuleVersion() = 3;
}
