// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

/**
 * An empty base implementation of the {@link BottomSheetObserver} interface.
 */
public class EmptyBottomSheetObserver implements BottomSheetObserver {
    @Override
    public void onSheetOpened() {}

    @Override
    public void onSheetClosed() {}

    @Override
    public void onLoadUrl(String url) {}

    @Override
    public void onSheetOffsetChanged(float heightFraction) {}

    @Override
    public void onTransitionPeekToHalf(float transitionFraction) {}
}
