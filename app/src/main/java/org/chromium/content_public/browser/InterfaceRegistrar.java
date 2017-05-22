// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import android.content.Context;

import org.chromium.services.service_manager.InterfaceRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * A registrar for mojo interface implementations to provide to an InterfaceRegistry.
 *
 * @param <ParamType> the type of parameter to pass to the InterfaceRegistrar when adding its
 *     interfaces to an InterfaceRegistry
 */
public interface InterfaceRegistrar<ParamType> {
    /** Invoked to register interfaces on |registry|, parametrized by |paramValue|. */
    public void registerInterfaces(InterfaceRegistry registry, ParamType paramValue);

    /** A registry of InterfaceRegistrars. */
    public static class Registry<ParamType> {
        private static Registry<Context> sContextRegistry;
        private static Registry<WebContents> sWebContentsRegistry;

        private List<InterfaceRegistrar<ParamType>> mRegistrars =
                new ArrayList<InterfaceRegistrar<ParamType>>();

        public static void applyContextRegistrars(
                InterfaceRegistry interfaceRegistry, Context context) {
            if (sContextRegistry == null) {
                return;
            }
            sContextRegistry.applyRegistrars(interfaceRegistry, context);
        }

        public static void applyWebContentsRegistrars(
                InterfaceRegistry interfaceRegistry, WebContents webContents) {
            if (sWebContentsRegistry == null) {
                return;
            }
            sWebContentsRegistry.applyRegistrars(interfaceRegistry, webContents);
        }

        public static void addContextRegistrar(InterfaceRegistrar<Context> registrar) {
            if (sContextRegistry == null) {
                sContextRegistry = new Registry<Context>();
            }
            sContextRegistry.addRegistrar(registrar);
        }

        public static void addWebContentsRegistrar(InterfaceRegistrar<WebContents> registrar) {
            if (sWebContentsRegistry == null) {
                sWebContentsRegistry = new Registry<WebContents>();
            }
            sWebContentsRegistry.addRegistrar(registrar);
        }

        private Registry() {}

        private void addRegistrar(InterfaceRegistrar<ParamType> registrar) {
            mRegistrars.add(registrar);
        }

        private void applyRegistrars(InterfaceRegistry interfaceRegistry, ParamType param) {
            for (InterfaceRegistrar<ParamType> registrar : mRegistrars) {
                registrar.registerInterfaces(interfaceRegistry, param);
            }
        }
    }
}
