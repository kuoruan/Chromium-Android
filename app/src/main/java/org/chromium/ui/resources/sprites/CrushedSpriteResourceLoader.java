// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.resources.sprites;

import android.content.res.Resources;
import android.graphics.Bitmap;

import org.chromium.ui.resources.AndroidResourceType;
import org.chromium.ui.resources.Resource;
import org.chromium.ui.resources.ResourceLoader.ResourceLoaderCallback;

/**
 * Handles loading {@link CrushedSpriteResource}s.
 */
public class CrushedSpriteResourceLoader {

    private final ResourceLoaderCallback mCallback;
    private final Resources mResources;

    /**
     * Creates an instance of {@link CrushedSpriteResourceLoader}.
     * @param callback The {@link ResourceLoaderCallback} to notify when a {@link Resource} is
     *                 loaded.
     * @param resources A {@link Resources} instance to load assets from.
     */
    public CrushedSpriteResourceLoader(ResourceLoaderCallback callback, Resources resources) {
        mCallback = callback;
        mResources = resources;
    }

    /**
     * Loads the {@link Bitmap} for {@link CrushedSpriteResource} specified by {@code bitmapResId}
     * and metadata specified by {@code metadataResId} and notifies the
     * {@link ResourceLoaderCallback}.
     * @param bitmapResId The id of the bitmap resource.
     * @param metadataResId The id of the raw resource containing JSON metadata.
     */
    public void loadResource(int bitmapResId, int metadataResId) {
        CrushedSpriteResource resource = new CrushedSpriteResource(
                bitmapResId, metadataResId, mResources);
        mCallback.onResourceLoaded(AndroidResourceType.CRUSHED_SPRITE, bitmapResId, resource);
    }

    public Bitmap reloadResource(int bitmapResId) {
        return CrushedSpriteResource.loadBitmap(bitmapResId, mResources);
    }

}
