// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.resources;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.SparseArray;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.MainDex;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.display.DisplayAndroid;
import org.chromium.ui.resources.ResourceLoader.ResourceLoaderCallback;
import org.chromium.ui.resources.dynamics.DynamicResource;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;
import org.chromium.ui.resources.sprites.CrushedSpriteResource;
import org.chromium.ui.resources.sprites.CrushedSpriteResourceLoader;
import org.chromium.ui.resources.statics.StaticResourceLoader;
import org.chromium.ui.resources.system.SystemResourceLoader;

/**
 * The Java component of a manager for all static resources to be loaded and used by CC layers.
 * This class does not hold any resource state, but passes it directly to native as they are loaded.
 */
@JNINamespace("ui")
@MainDex
public class ResourceManager implements ResourceLoaderCallback {
    private final SparseArray<ResourceLoader> mResourceLoaders = new SparseArray<ResourceLoader>();
    private final SparseArray<SparseArray<LayoutResource>> mLoadedResources =
            new SparseArray<SparseArray<LayoutResource>>();
    private final CrushedSpriteResourceLoader mCrushedSpriteResourceLoader;

    private final float mPxToDp;

    private long mNativeResourceManagerPtr;

    private ResourceManager(
            Resources resources, int minScreenSideLength, long staticResourceManagerPtr) {
        mPxToDp = 1.f / resources.getDisplayMetrics().density;

        registerResourceLoader(new StaticResourceLoader(
                AndroidResourceType.STATIC, this, resources));
        registerResourceLoader(new DynamicResourceLoader(
                AndroidResourceType.DYNAMIC, this));
        registerResourceLoader(new DynamicResourceLoader(
                AndroidResourceType.DYNAMIC_BITMAP, this));
        registerResourceLoader(
                new SystemResourceLoader(AndroidResourceType.SYSTEM, this, minScreenSideLength));
        mCrushedSpriteResourceLoader = new CrushedSpriteResourceLoader(this, resources);

        mNativeResourceManagerPtr = staticResourceManagerPtr;
    }

    /**
     * Creates an instance of a {@link ResourceManager}.
     * @param WindowAndroid            A {@link WindowAndroid} instance to fetch a {@link Context}
     * and thus grab {@link Resources} from.
     * @param staticResourceManagerPtr A pointer to the native component of this class.
     * @return                         A new instance of a {@link ResourceManager}.
     */
    @CalledByNative
    private static ResourceManager create(
            WindowAndroid windowAndroid, long staticResourceManagerPtr) {
        Context context = windowAndroid.getContext().get();
        // This call should happen early enough (i.e. during construction) that this context should
        // not yet have been released.
        if (context == null) {
            throw new IllegalStateException("Context should not be null during initialization.");
        }

        DisplayAndroid displayAndroid = windowAndroid.getDisplay();
        int screenWidth = displayAndroid.getPhysicalDisplayWidth() != 0
                ? displayAndroid.getPhysicalDisplayWidth()
                : displayAndroid.getDisplayWidth();
        int screenHeight = displayAndroid.getPhysicalDisplayHeight() != 0
                ? displayAndroid.getPhysicalDisplayHeight()
                : displayAndroid.getDisplayHeight();
        int minScreenSideLength = Math.min(screenWidth, screenHeight);

        Resources resources = context.getResources();
        return new ResourceManager(resources, minScreenSideLength, staticResourceManagerPtr);
    }

    /**
     * @return A reference to the {@link DynamicResourceLoader} that provides
     *         {@link DynamicResource} objects to this class.
     */
    public DynamicResourceLoader getDynamicResourceLoader() {
        return (DynamicResourceLoader) mResourceLoaders.get(
                AndroidResourceType.DYNAMIC);
    }

    /**
     * @return A reference to the {@link DynamicResourceLoader} for bitmaps that provides
     *         {@link BitmapDynamicResource} objects to this class.
     */
    public DynamicResourceLoader getBitmapDynamicResourceLoader() {
        return (DynamicResourceLoader) mResourceLoaders.get(
                AndroidResourceType.DYNAMIC_BITMAP);
    }

    /**
     * Automatically loads any synchronous resources specified in |syncIds| and will start
     * asynchronous reads for any asynchronous resources specified in |asyncIds|.
     * @param type AndroidResourceType which will be loaded.
     * @param syncIds Resource ids which will be loaded synchronously.
     * @param asyncIds Resource ids which will be loaded asynchronously.
     */
    public void preloadResources(int type, int[] syncIds, int[] asyncIds) {
        ResourceLoader loader = mResourceLoaders.get(type);
        if (asyncIds != null) {
            for (Integer resId : asyncIds) {
                loader.preloadResource(resId);
            }
        }

        if (syncIds != null) {
            for (Integer resId : syncIds) {
                loader.loadResource(resId);
            }
        }
    }

    /**
     * @param resType The type of the Android resource.
     * @param resId   The id of the Android resource.
     * @return The corresponding {@link LayoutResource}.
     */
    public LayoutResource getResource(int resType, int resId) {
        SparseArray<LayoutResource> bucket = mLoadedResources.get(resType);
        return bucket != null ? bucket.get(resId) : null;
    }

    @SuppressWarnings("cast")
    @Override
    public void onResourceLoaded(int resType, int resId, Resource resource) {
        if (resource == null || resource.getBitmap() == null) return;

        if (resType != AndroidResourceType.CRUSHED_SPRITE) {
            saveMetadataForLoadedResource(resType, resId, resource);
        }

        if (mNativeResourceManagerPtr == 0) return;
        if (resType == AndroidResourceType.CRUSHED_SPRITE) {
            if (resource.getBitmap() != null) {
                CrushedSpriteResource crushedResource = (CrushedSpriteResource) resource;
                nativeOnCrushedSpriteResourceReady(mNativeResourceManagerPtr, resId,
                        crushedResource.getBitmap(), crushedResource.getFrameRectangles(),
                        crushedResource.getUnscaledSpriteWidth(),
                        crushedResource.getUnscaledSpriteHeight(),
                        crushedResource.getScaledSpriteWidth(),
                        crushedResource.getScaledSpriteHeight());
            }
            return;
        }

        Rect padding = resource.getPadding();
        Rect aperture = resource.getAperture();

        nativeOnResourceReady(mNativeResourceManagerPtr, resType, resId, resource.getBitmap(),
                padding.left, padding.top, padding.right, padding.bottom,
                aperture.left, aperture.top, aperture.right, aperture.bottom);
    }

    @Override
    public void onResourceUnregistered(int resType, int resId) {
        // Only remove dynamic bitmaps that were unregistered.
        if (resType != AndroidResourceType.DYNAMIC_BITMAP) return;

        nativeRemoveResource(mNativeResourceManagerPtr, resType, resId);
    }

    /**
     * Clear the cache of tinted assets that the native manager holds.
     */
    public void clearTintedResourceCache() {
        if (mNativeResourceManagerPtr == 0) return;
        nativeClearTintedResourceCache(mNativeResourceManagerPtr);
    }

    private void saveMetadataForLoadedResource(int resType, int resId, Resource resource) {
        SparseArray<LayoutResource> bucket = mLoadedResources.get(resType);
        if (bucket == null) {
            bucket = new SparseArray<LayoutResource>();
            mLoadedResources.put(resType, bucket);
        }
        bucket.put(resId, new LayoutResource(mPxToDp, resource));
    }

    @CalledByNative
    private void destroy() {
        assert mNativeResourceManagerPtr != 0;
        mNativeResourceManagerPtr = 0;
    }

    @CalledByNative
    private void resourceRequested(int resType, int resId) {
        ResourceLoader loader = mResourceLoaders.get(resType);
        if (loader != null) loader.loadResource(resId);
    }

    @CalledByNative
    private void preloadResource(int resType, int resId) {
        ResourceLoader loader = mResourceLoaders.get(resType);
        if (loader != null) loader.preloadResource(resId);
    }

    @CalledByNative
    private void crushedSpriteResourceRequested(int bitmapResId, int metatadataResId,
            boolean reloading) {
        if (reloading) {
            Bitmap bitmap = mCrushedSpriteResourceLoader.reloadResource(bitmapResId);
            if (bitmap != null) {
                nativeOnCrushedSpriteResourceReloaded(mNativeResourceManagerPtr, bitmapResId,
                        bitmap);
            }
        } else {
            mCrushedSpriteResourceLoader.loadResource(bitmapResId, metatadataResId);
        }
    }

    @CalledByNative
    private long getNativePtr() {
        return mNativeResourceManagerPtr;
    }

    private void registerResourceLoader(ResourceLoader loader) {
        mResourceLoaders.put(loader.getResourceType(), loader);
    }

    private native void nativeOnResourceReady(long nativeResourceManagerImpl, int resType,
            int resId, Bitmap bitmap, int paddingLeft, int paddingTop, int paddingRight,
            int paddingBottom, int apertureLeft, int apertureTop, int apertureRight,
            int apertureBottom);
    private native void nativeOnCrushedSpriteResourceReady(long nativeResourceManagerImpl,
            int bitmapResId, Bitmap bitmap, int[][] frameRects, int unscaledSpriteWidth,
            int unscaledSpriteHeight, float scaledSpriteWidth, float scaledSpriteHeight);
    private native void nativeOnCrushedSpriteResourceReloaded(long nativeResourceManagerImpl,
            int bitmapResId, Bitmap bitmap);
    private native void nativeRemoveResource(long nativeResourceManagerImpl, int resType,
            int resId);
    private native void nativeClearTintedResourceCache(long nativeResourceManagerImpl);

}
