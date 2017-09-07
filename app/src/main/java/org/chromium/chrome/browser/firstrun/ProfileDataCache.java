// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.MainThread;
import android.support.v7.content.res.AppCompatResources;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.profiles.ProfileDownloader;

import java.util.HashMap;
import java.util.List;

/**
 * Fetches and caches Google Account profile images and full names for the accounts on the device.
 * ProfileDataCache doesn't observe account list changes by itself, so account list
 * should be provided by calling {@link #update(List)}
 */
@MainThread
public class ProfileDataCache implements ProfileDownloader.Observer {
    /**
     * Observer to get notifications about changes in profile data.
     */
    public interface Observer {
        /**
         * Notifies that an account's profile data has been updated.
         * @param accountId An account ID.
         */
        void onProfileDataUpdated(String accountId);
    }

    private static class CacheEntry {
        public CacheEntry(Drawable picture, String fullName, String givenName) {
            this.picture = picture;
            this.fullName = fullName;
            this.givenName = givenName;
        }

        public Drawable picture;
        public String fullName;
        public String givenName;
    }

    private final HashMap<String, CacheEntry> mCacheEntries = new HashMap<>();

    private final Drawable mPlaceholderImage;
    private final ObserverList<Observer> mObservers = new ObserverList<>();

    private final Context mContext;
    private Profile mProfile;

    public ProfileDataCache(Context context, Profile profile) {
        mContext = context;
        mProfile = profile;

        mPlaceholderImage =
                AppCompatResources.getDrawable(context, R.drawable.logo_avatar_anonymous);
    }

    /**
     * Initiate fetching the user accounts data (images and the full name). Fetched data will be
     * sent to observers of ProfileDownloader. The instance must have at least one observer (see
     * {@link #addObserver}) when this method is called.
     */
    public void update(List<String> accounts) {
        ThreadUtils.assertOnUiThread();
        assert !mObservers.isEmpty();

        int imageSizePx =
                mContext.getResources().getDimensionPixelSize(R.dimen.signin_account_image_size);
        for (int i = 0; i < accounts.size(); i++) {
            if (mCacheEntries.get(accounts.get(i)) == null) {
                ProfileDownloader.startFetchingAccountInfoFor(
                        mContext, mProfile, accounts.get(i), imageSizePx, true);
            }
        }
    }

    /**
     * @param accountId Google account ID for the image that is requested.
     * @return Returns the profile image for a given Google account ID if it's in
     *         the cache, otherwise returns a placeholder image.
     */
    public Drawable getImage(String accountId) {
        CacheEntry cacheEntry = mCacheEntries.get(accountId);
        if (cacheEntry == null) return mPlaceholderImage;
        return cacheEntry.picture;
    }

    /**
     * @param accountId Google account ID for the full name that is requested.
     * @return Returns the full name for a given Google account ID if it is
     *         the cache, otherwise returns null.
     */
    public String getFullName(String accountId) {
        CacheEntry cacheEntry = mCacheEntries.get(accountId);
        if (cacheEntry == null) return null;
        return cacheEntry.fullName;
    }

    /**
     * @param accountId Google account ID for the full name that is requested.
     * @return Returns the given name for a given Google account ID if it is in the cache, otherwise
     * returns null.
     */
    public String getGivenName(String accountId) {
        CacheEntry cacheEntry = mCacheEntries.get(accountId);
        if (cacheEntry == null) return null;
        return cacheEntry.givenName;
    }

    /**
     * @param observer Observer that should be notified when new profile images are available.
     */
    public void addObserver(Observer observer) {
        ThreadUtils.assertOnUiThread();
        if (mObservers.isEmpty()) {
            ProfileDownloader.addObserver(this);
        }
        mObservers.addObserver(observer);
    }

    /**
     * @param observer Observer that was added by {@link #addObserver} and should be removed.
     */
    public void removeObserver(Observer observer) {
        ThreadUtils.assertOnUiThread();
        mObservers.removeObserver(observer);
        if (mObservers.isEmpty()) {
            ProfileDownloader.removeObserver(this);
        }
    }

    @Override
    public void onProfileDownloaded(String accountId, String fullName, String givenName,
            Bitmap bitmap) {
        ThreadUtils.assertOnUiThread();
        Drawable drawable = bitmap != null ? getCroppedAvatar(bitmap) : mPlaceholderImage;
        mCacheEntries.put(accountId, new CacheEntry(drawable, fullName, givenName));
        for (Observer observer : mObservers) {
            observer.onProfileDataUpdated(accountId);
        }
    }

    private Drawable getCroppedAvatar(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(
                bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.WHITE);

        canvas.drawCircle(
                bitmap.getWidth() / 2f, bitmap.getHeight() / 2f, bitmap.getWidth() / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return new BitmapDrawable(mContext.getResources(), output);
    }
}
