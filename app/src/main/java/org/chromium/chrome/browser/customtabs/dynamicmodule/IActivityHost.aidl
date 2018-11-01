// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import android.net.Uri;
import org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper;

interface IActivityHost {
  IObjectWrapper /* Context */ getActivityContext() = 0;

  void setBottomBarView(in IObjectWrapper /* View */ bottomBarView) = 1;

  void setOverlayView(in IObjectWrapper /* View */ overlayView) = 2;

  void setBottomBarHeight(int height) = 3;

  /**
   * Loads a URI in the existing CCT activity. This is used by features that
   * want to show web content (e.g. saves when reopening a saved page).
   *
   * Introduced in API version 3.
   */
  void loadUri(in Uri uri) = 4;
}
