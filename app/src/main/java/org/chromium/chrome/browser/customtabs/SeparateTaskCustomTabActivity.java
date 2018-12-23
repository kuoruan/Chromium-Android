// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;
/**
 * Wrapper around the CustomTabActivity for Android version pre L to be used when launching
 * each CustomTab in a separate task. This class is copied 10 times as
 * SeparateTaskCustomTabActivity${i} to "emulate" having multi task being supported.
 *
 * TODO(arthursonzogni, tedchoc): Remove this after M74.
 */
public class SeparateTaskCustomTabActivity extends CustomTabActivity {}
