// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.androidoverlay.AndroidOverlayProviderImpl;
import org.chromium.content.browser.installedapp.InstalledAppProviderFactory;
import org.chromium.content.browser.shapedetection.FaceDetectionProviderImpl;
import org.chromium.content_public.browser.InterfaceRegistrar;
import org.chromium.content_public.browser.RenderFrameHost;
import org.chromium.content_public.browser.WebContents;
import org.chromium.device.nfc.mojom.Nfc;
import org.chromium.installedapp.mojom.InstalledAppProvider;
import org.chromium.media.mojom.AndroidOverlayProvider;
import org.chromium.mojo.system.impl.CoreImpl;
import org.chromium.services.service_manager.InterfaceRegistry;
import org.chromium.shape_detection.mojom.FaceDetectionProvider;

@JNINamespace("content")
class InterfaceRegistrarImpl {

    private static boolean sHasRegisteredRegistrars;

    @CalledByNative
    static void createInterfaceRegistryForContext(int nativeHandle, Context applicationContext) {
        ensureContentRegistrarsAreRegistered();

        InterfaceRegistry registry = InterfaceRegistry.create(
                CoreImpl.getInstance().acquireNativeHandle(nativeHandle).toMessagePipeHandle());
        InterfaceRegistrar.Registry.applyContextRegistrars(registry, applicationContext);
    }

    @CalledByNative
    static void createInterfaceRegistryForWebContents(int nativeHandle, WebContents webContents) {
        ensureContentRegistrarsAreRegistered();

        InterfaceRegistry registry = InterfaceRegistry.create(
                CoreImpl.getInstance().acquireNativeHandle(nativeHandle).toMessagePipeHandle());
        InterfaceRegistrar.Registry.applyWebContentsRegistrars(registry, webContents);
    }

    @CalledByNative
    static void createInterfaceRegistryForRenderFrameHost(
            int nativeHandle, RenderFrameHost renderFrameHost) {
        ensureContentRegistrarsAreRegistered();

        InterfaceRegistry registry = InterfaceRegistry.create(
                CoreImpl.getInstance().acquireNativeHandle(nativeHandle).toMessagePipeHandle());
        InterfaceRegistrar.Registry.applyRenderFrameHostRegistrars(registry, renderFrameHost);
    }

    private static void ensureContentRegistrarsAreRegistered() {
        if (sHasRegisteredRegistrars) return;
        sHasRegisteredRegistrars = true;
        InterfaceRegistrar.Registry.addContextRegistrar(new ContentContextInterfaceRegistrar());
        InterfaceRegistrar.Registry.addWebContentsRegistrar(
                new ContentWebContentsInterfaceRegistrar());
        InterfaceRegistrar.Registry.addRenderFrameHostRegistrar(
                new ContentRenderFrameHostInterfaceRegistrar());
    }

    private static class ContentContextInterfaceRegistrar implements InterfaceRegistrar<Context> {
        @Override
        public void registerInterfaces(
                InterfaceRegistry registry, final Context applicationContext) {
            registry.addInterface(FaceDetectionProvider.MANAGER,
                    new FaceDetectionProviderImpl.Factory(applicationContext));
            registry.addInterface(AndroidOverlayProvider.MANAGER,
                    new AndroidOverlayProviderImpl.Factory(applicationContext));
            // TODO(avayvod): Register the PresentationService implementation here.
        }
    }

    private static class ContentWebContentsInterfaceRegistrar
            implements InterfaceRegistrar<WebContents> {
        @Override
        public void registerInterfaces(InterfaceRegistry registry, final WebContents webContents) {
            registry.addInterface(Nfc.MANAGER, new NfcFactory(webContents));
        }
    }

    private static class ContentRenderFrameHostInterfaceRegistrar
            implements InterfaceRegistrar<RenderFrameHost> {
        @Override
        public void registerInterfaces(
                InterfaceRegistry registry, final RenderFrameHost renderFrameHost) {
            registry.addInterface(
                    InstalledAppProvider.MANAGER, new InstalledAppProviderFactory(renderFrameHost));
        }
    }
}
