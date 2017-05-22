// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

/**
 * This is an interface which provides last animation interval.
 */
public interface AnimationIntervalProvider {
    /**
     * Returns last animation interval.
     */
    public long getLastAnimationFrameInterval();
}
