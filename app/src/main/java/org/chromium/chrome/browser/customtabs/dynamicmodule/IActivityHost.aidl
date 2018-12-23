// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

import android.net.Uri;
import org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper;

/**
 * API to customize the Chrome activity.
 */
interface IActivityHost {

  /** Activity context of Chrome. */
  IObjectWrapper /* Context */ getActivityContext() = 0;

  void setBottomBarView(in IObjectWrapper /* View */ bottomBarView) = 1;

  void setOverlayView(in IObjectWrapper /* View */ overlayView) = 2;

  void setBottomBarHeight(int heightInPx) = 3;

  /**
   * Loads a URI in the existing CCT activity. This is used by features that
   * want to show web content (e.g. saves when reopening a saved page).
   *
   * Introduced in API version 3.
   */
  void loadUri(in Uri uri) = 4;

  /**
   * Sets the top bar view in CCT. This will not attempt to hide or remove
   * the CCT header. It should only be called once in the lifecycle of an
   * activity.
   *
   * Experimental API.
   * Introduced in API version 4.
   */
  void setTopBarView(in IObjectWrapper /* View */ topBarView) = 5;

  /**
   * Sets the height of the top bar. This is needed for CCT to calculate the
   * web content area.
   *
   * Experimental API.
   * Introduced in API version 4.
   */
  void setTopBarHeight(int heightInPx) = 6;
}
