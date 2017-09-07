// Copyright 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.signin;

/**
 * Will be removed after migrating downstream code. Use {@link AccountManagerFacade} instead.
 */
@Deprecated
public class AccountManagerHelper {
    public static AccountManagerFacade get() {
        return AccountManagerFacade.get();
    }
}
