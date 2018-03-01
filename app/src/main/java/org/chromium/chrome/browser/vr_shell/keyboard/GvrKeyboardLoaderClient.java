// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell.keyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;

import com.google.vr.keyboard.IGvrKeyboardLoader;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/** Loads the GVR keyboard SDK dynamically using the Keyboard Service. */
@JNINamespace("vr_shell")
public class GvrKeyboardLoaderClient {
    private static final String TAG = "ChromeGvrKbClient";
    private static final boolean DEBUG_LOGS = false;

    private static final String KEYBOARD_PACKAGE = "com.google.android.vr.inputmethod";
    private static final String LOADER_NAME = "com.google.vr.keyboard.GvrKeyboardLoader";

    private static IGvrKeyboardLoader sLoader = null;
    private static ClassLoader sRemoteClassLoader = null;

    @CalledByNative
    public static long loadKeyboardSDK() {
        if (DEBUG_LOGS) Log.i(TAG, "loadKeyboardSDK");
        IGvrKeyboardLoader loader = getLoader();
        if (loader == null) {
            if (DEBUG_LOGS) Log.i(TAG, "Couldn't find GVR keyboard SDK.");
            return 0;
        }
        try {
            long handle = loader.loadGvrKeyboard(BuildConstants.API_VERSION);
            return handle;
        } catch (RemoteException e) {
            if (DEBUG_LOGS) Log.i(TAG, "Couldn't load GVR keyboard SDK.");
            return 0;
        }
    }

    @CalledByNative
    public static void closeKeyboardSDK(long handle) {
        if (DEBUG_LOGS) Log.i(TAG, "loadKeyboardSDK");
        IGvrKeyboardLoader loader = getLoader();
        if (loader != null) {
            try {
                loader.closeGvrKeyboard(handle);
            } catch (RemoteException e) {
                Log.e(TAG, "Couldn't close GVR keyboard library", e);
            }
        }
    }

    private static IGvrKeyboardLoader getLoader() {
        if (sLoader == null) {
            ClassLoader remoteClassLoader = (ClassLoader) getRemoteClassLoader();
            if (remoteClassLoader != null) {
                IBinder binder = newBinder(remoteClassLoader, LOADER_NAME);
                sLoader = IGvrKeyboardLoader.Stub.asInterface(binder);
            }
        }
        return sLoader;
    }

    private static Context getRemoteContext(Context context) {
        try {
            // The flags Context.CONTEXT_INCLUDE_CODE and Context.CONTEXT_IGNORE_SECURITY are
            // needed to be able to load classes via the classloader of the returned context.
            return context.createPackageContext(KEYBOARD_PACKAGE,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't find remote context", e);
        }
        return null;
    }

    @CalledByNative
    public static Context getContextWrapper() {
        Context context = ContextUtils.getApplicationContext();
        return new KeyboardContextWrapper(getRemoteContext(context), context);
    }

    @CalledByNative
    public static Object getRemoteClassLoader() {
        Context context = ContextUtils.getApplicationContext();
        if (sRemoteClassLoader == null) {
            Context remoteContext = getRemoteContext(context);
            if (remoteContext != null) {
                sRemoteClassLoader = remoteContext.getClassLoader();
            }
        }
        return sRemoteClassLoader;
    }

    private static IBinder newBinder(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            return (IBinder) clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to find dynamic class " + className);
        } catch (InstantiationException e) {
            throw new IllegalStateException("Unable to instantiate the remote class " + className);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Unable to call the default constructor of " + className);
        } catch (Exception e) {
            throw new IllegalStateException("Reflection error in " + className);
        }
    }

    private static class KeyboardContextWrapper extends ContextWrapper {
        private final Context mKeyboardContext;

        private KeyboardContextWrapper(Context keyboardContext, Context baseContext) {
            super(baseContext);
            this.mKeyboardContext = keyboardContext;
        }

        @Override
        public Object getSystemService(String name) {
            // As the LAYOUT_INFLATER_SERVICE uses assets from the Context, it should point to the
            // keyboard Context.
            if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                return mKeyboardContext.getSystemService(name);
            } else {
                return super.getSystemService(name);
            }
        }

        @Override
        public Resources getResources() {
            return mKeyboardContext.getResources();
        }

        @Override
        public AssetManager getAssets() {
            return mKeyboardContext.getAssets();
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public ClassLoader getClassLoader() {
            return mKeyboardContext.getClassLoader();
        }
    }
}
