// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.favicon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content_public.browser.WebContents;

/**
 * This is a helper class to use favicon_service.cc's functionality.
 *
 * You can request a favicon image by web page URL. Note that an instance of
 * this class should be created & used & destroyed (by destroy()) in the same
 * thread due to the C++ base::CancelableTaskTracker class
 * requirement.
 */
public class FaviconHelper {

    private long mNativeFaviconHelper;

    /**
     * Callback interface for getting the result from getLocalFaviconImageForURL method.
     */
    public interface FaviconImageCallback {
        /**
         * This method will be called when the result favicon is ready.
         * @param image Favicon image.
         * @param iconUrl Favicon image's icon url.
         */
        @CalledByNative("FaviconImageCallback")
        public void onFaviconAvailable(Bitmap image, String iconUrl);
    }

    /**
     * Callback interface for the result of the ensureIconIsAvailable method.
     */
    public interface IconAvailabilityCallback {
        /**
         * This method will be called when the availability of the icon has been checked.
         * @param newlyAvailable true if the icon was downloaded and is now available, false if the
         *         favicon was already there or the download failed.
         */
        @CalledByNative("IconAvailabilityCallback")
        public void onIconAvailabilityChecked(boolean newlyAvailable);
    }

    /**
     * Helper for generating default favicons and sharing the same icon between multiple views.
     */
    public static class DefaultFaviconHelper {
        private Bitmap mChromeDarkBitmap;
        private Bitmap mChromeLightBitmap;
        private Bitmap mDefaultDarkBitmap;
        private Bitmap mDefaultLightBitmap;

        private int getResourceId(String url) {
            return NewTabPage.isNTPUrl(url) ? R.drawable.chromelogo16 : R.drawable.default_favicon;
        }

        private Bitmap createBitmap(Context context, String url, boolean useDarkIcon) {
            Bitmap origBitmap =
                    BitmapFactory.decodeResource(context.getResources(), getResourceId(url));
            Bitmap tintedBitmap = Bitmap.createBitmap(
                    origBitmap.getWidth(), origBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tintedBitmap);
            @ColorInt
            int tintColor = ApiCompatibilityUtils.getColor(context.getResources(),
                    useDarkIcon ? R.color.default_icon_color : R.color.default_icon_color_white);
            Paint p = new Paint();
            p.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
            c.drawBitmap(origBitmap, 0f, 0f, p);
            return tintedBitmap;
        }

        /**
         * Generate a default favicon bitmap for the given URL.
         * @param context The context used to fetch the default icons.
         * @param url The URL of the page whose icon is being generated.
         * @param useDarkIcon Whether a dark icon should be used.
         * @return The favicon.
         */
        public Bitmap getDefaultFaviconBitmap(Context context, String url, boolean useDarkIcon) {
            boolean isNtp = NewTabPage.isNTPUrl(url);
            Bitmap bitmap = isNtp ? (useDarkIcon ? mChromeDarkBitmap : mChromeLightBitmap)
                                  : (useDarkIcon ? mDefaultDarkBitmap : mDefaultLightBitmap);
            if (bitmap != null) return bitmap;
            bitmap = createBitmap(context, url, useDarkIcon);
            if (isNtp && useDarkIcon) {
                mChromeDarkBitmap = bitmap;
            } else if (isNtp) {
                mChromeLightBitmap = bitmap;
            } else if (useDarkIcon) {
                mDefaultDarkBitmap = bitmap;
            } else {
                mDefaultLightBitmap = bitmap;
            }
            return bitmap;
        }

        /**
         * Generate a default favicon drawable for the given URL.
         * @param context The context used to fetch the default icons.
         * @param url The URL of the page whose icon is being generated.
         * @param useDarkIcon Whether a dark icon should be used.
         * @return The favicon.
         */
        public Drawable getDefaultFaviconDrawable(
                Context context, String url, boolean useDarkIcon) {
            return new BitmapDrawable(
                    context.getResources(), getDefaultFaviconBitmap(context, url, useDarkIcon));
        }

        /** Clears any of the cached default drawables. */
        public void clearCache() {
            mChromeDarkBitmap = null;
            mChromeLightBitmap = null;
            mDefaultDarkBitmap = null;
            mDefaultLightBitmap = null;
        }
    }

    /**
     * Allocate and initialize the C++ side of this class.
     */
    public FaviconHelper() {
        mNativeFaviconHelper = nativeInit();
    }

    /**
     * Clean up the C++ side of this class. After the call, this class instance shouldn't be used.
     */
    public void destroy() {
        assert mNativeFaviconHelper != 0;
        nativeDestroy(mNativeFaviconHelper);
        mNativeFaviconHelper = 0;
    }

    /**
     * Get Favicon bitmap for the requested arguments. Retrieves favicons only for pages the user
     * has visited on the current device.
     * @param profile Profile used for the FaviconService construction.
     * @param pageUrl The target Page URL to get the favicon.
     * @param desiredSizeInPixel The size of the favicon in pixel we want to get.
     * @param faviconImageCallback A method to be called back when the result is available. Note
     *         that this callback is not called if this method returns false.
     * @return True if GetLocalFaviconImageForURL is successfully called.
     */
    public boolean getLocalFaviconImageForURL(
            Profile profile, String pageUrl, int desiredSizeInPixel,
            FaviconImageCallback faviconImageCallback) {
        assert mNativeFaviconHelper != 0;
        return nativeGetLocalFaviconImageForURL(mNativeFaviconHelper, profile, pageUrl,
                desiredSizeInPixel, faviconImageCallback);
    }

    /**
     * Get 16x16 Favicon bitmap for the requested arguments. Only retrives favicons in synced
     * session storage. (e.g. favicons synced from other devices).
     * TODO(apiccion): provide a way to obtain higher resolution favicons.
     * @param profile Profile used for the FaviconService construction.
     * @param pageUrl The target Page URL to get the favicon.
     * @return 16x16 favicon Bitmap corresponding to the pageUrl.
     */
    public Bitmap getSyncedFaviconImageForURL(Profile profile, String pageUrl) {
        assert mNativeFaviconHelper != 0;
        return nativeGetSyncedFaviconImageForURL(mNativeFaviconHelper, profile, pageUrl);
    }

    // TODO(jkrcal): Remove these two methods when FaviconHelper is not used any more by
    // org.chromium.chrome.browser.suggestions.ImageFetcher. https://crbug.com/751628
    /**
     * Tries to make sure that the specified icon is available in the cache of the provided profile.
     * The icon will we cached as an on-demand favicon.
     * @param profile Profile used for the FaviconService construction.
     * @param webContents The object used to download the icon.
     * @param pageUrl The target Page URL to get the favicon for.
     * @param iconUrl The URL of the icon to retrieve.
     * @param isLargeIcon Specifies whether the type is kTouchIcon (true) or kFavicon (false).
     * @param callback Called when completed (download not needed, finished or failed).
     */
    public void ensureIconIsAvailable(Profile profile, WebContents webContents, String pageUrl,
            String iconUrl, boolean isLargeIcon, IconAvailabilityCallback callback) {
        nativeEnsureIconIsAvailable(mNativeFaviconHelper, profile, webContents, pageUrl, iconUrl,
                isLargeIcon, callback);
    }

    /**
     * Mark that the specified on-demand favicon was requested now. This postpones the automatic
     * eviction of the favicon from the database.
     * @param profile Profile used for the FaviconService construction.
     * @param iconUrl The URL of the icon to touch.
     */
    public void touchOnDemandFavicon(Profile profile, String iconUrl) {
        nativeTouchOnDemandFavicon(mNativeFaviconHelper, profile, iconUrl);
    }

    private static native long nativeInit();
    private static native void nativeDestroy(long nativeFaviconHelper);
    private static native boolean nativeGetLocalFaviconImageForURL(long nativeFaviconHelper,
            Profile profile, String pageUrl, int desiredSizeInDip,
            FaviconImageCallback faviconImageCallback);
    private static native Bitmap nativeGetSyncedFaviconImageForURL(long nativeFaviconHelper,
            Profile profile, String pageUrl);
    private static native void nativeEnsureIconIsAvailable(long nativeFaviconHelper,
            Profile profile, WebContents webContents, String pageUrl, String iconUrl,
            boolean isLargeIcon, IconAvailabilityCallback callback);
    private static native void nativeTouchOnDemandFavicon(
            long nativeFaviconHelper, Profile profile, String iconUrl);
}
