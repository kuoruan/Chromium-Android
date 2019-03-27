package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import android.content.Context;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.ClientAppDataRegister;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ClientAppDataRecorder_Factory implements Factory<ClientAppDataRecorder> {
  private final Provider<Context> contextProvider;

  private final Provider<ClientAppDataRegister> clientAppDataRegisterProvider;

  public ClientAppDataRecorder_Factory(
      Provider<Context> contextProvider,
      Provider<ClientAppDataRegister> clientAppDataRegisterProvider) {
    this.contextProvider = contextProvider;
    this.clientAppDataRegisterProvider = clientAppDataRegisterProvider;
  }

  @Override
  public ClientAppDataRecorder get() {
    return provideInstance(contextProvider, clientAppDataRegisterProvider);
  }

  public static ClientAppDataRecorder provideInstance(
      Provider<Context> contextProvider,
      Provider<ClientAppDataRegister> clientAppDataRegisterProvider) {
    return new ClientAppDataRecorder(contextProvider.get(), clientAppDataRegisterProvider.get());
  }

  public static ClientAppDataRecorder_Factory create(
      Provider<Context> contextProvider,
      Provider<ClientAppDataRegister> clientAppDataRegisterProvider) {
    return new ClientAppDataRecorder_Factory(contextProvider, clientAppDataRegisterProvider);
  }

  public static ClientAppDataRecorder newClientAppDataRecorder(
      Context context, ClientAppDataRegister clientAppDataRegister) {
    return new ClientAppDataRecorder(context, clientAppDataRegister);
  }
}
