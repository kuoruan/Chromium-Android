// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.resources;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * A basic resource interface that all assets must use to be exposed to the CC layer as
 * UIResourceIds.
 */
public interface Resource {
    /**
     * This may be called more than once so if possible avoid doing redundant work.
     * @return A {@link Bitmap} representing the resource.
     */
    Bitmap getBitmap();

    /**
     * @return The size of the bitmap.
     */
    Rect getBitmapSize();

    /**
     * @return The padded content area of this resource.  For 9-patches this will represent the
     *         valid content of the 9-patch.  It can mean other things for other Resources though.
     */
    Rect getPadding();

    /**
     * @return The aperture of this resource.  For 9-patches this will represent the area of the
     *         {@link Bitmap} that should not be stretched.  It can mean other things for other
     *         Resources though.
     */
    Rect getAperture();
}