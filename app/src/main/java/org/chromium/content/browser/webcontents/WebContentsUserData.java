// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.webcontents;

import org.chromium.content_public.browser.WebContents;

import java.util.Map;

/**
 * Holds an object to be stored in {@code userDataMap} in {@link WebContents} for those
 * classes that have the lifetime of {@link WebContents} without hanging directly onto it.
 * To create an object of a class {@code MyClass}, define a static method
 * {@code fromWebContents()} where you call:
 * <code>
 * WebContentsUserData.fromWebContents(webContents, MyClass.class, MyClass::new);
 * </code>
 *
 * {@code MyClass} should have a contstructor that accepts only one parameter:
 * <code>
 * public MyClass(WebContents webContents);
 * </code>
 */
public final class WebContentsUserData {
    /**
     * Factory interface passed to {@link #fromWebContents()} for instantiation of
     * class to be managed bu {@link WebContentsUserData}.
     *
     * Constructor method reference comes handy for class Foo to provide the factory.
     * Use lazy initialization to avoid having to generate too many anonymous reference.
     *
     * <code>
     * public class Foo {
     *     static final class FoofactoryLazyHolder {
     *         private static final UserDataFactory<Foo> INSTANCE = Foo::new;
     *     }
     *
     *     static Foo fromWebContents(WebContents webContents) {
     *         return WebContentsUserData.fromWebContents(
     *                 webContents, Foo.class, FooFactoryLazyHolder.INSTANCE);
     *     }
     *     ....
     * }
     * </code>
     *
     * @param <T> Class to instantiate.
     */
    public interface UserDataFactory<T> { T create(WebContents webContents); }

    private final Object mObject;

    private WebContentsUserData(Object object) {
        mObject = object;
    }

    /**
     * Looks up the generic object of the given web contents.
     *
     * @param webContents The web contents for which to lookup the object.
     * @param key Class instance of the object used as the key.
     * @param userDataFactory Factory that creates an object of the generic class. Create a new
     * instance if the object is not available and the factory is non-null.
     * @return The object (possibly null) of the given web contents.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromWebContents(
            WebContents webContents, Class<T> key, UserDataFactory<T> userDataFactory) {
        // Casting to WebContentsImpl is safe since it's the actual implementation.
        WebContentsImpl webContentsImpl = (WebContentsImpl) webContents;
        Map<Class, WebContentsUserData> userDataMap = webContentsImpl.getUserDataMap();

        // Map can be null after WebView gets gc'ed on it wasy to destruction.
        if (userDataMap == null) return null;

        WebContentsUserData data = userDataMap.get(key);
        if (data == null && userDataFactory != null) {
            T object = userDataFactory.create(webContents);
            assert object.getClass() == key;
            webContentsImpl.setUserData(key, new WebContentsUserData(object));
            // Retrieves from the map again to return null in case |setUserData| fails
            // to store the object.
            data = webContentsImpl.getUserData(key);
        }
        // Casting Object to T is safe since we make sure the object was of type T upon creation.
        return data != null ? (T) data.mObject : null;
    }
}
