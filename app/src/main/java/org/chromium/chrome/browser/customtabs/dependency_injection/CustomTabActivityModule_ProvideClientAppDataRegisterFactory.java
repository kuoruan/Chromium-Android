package org.chromium.chrome.browser.customtabs.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.browserservices.ClientAppDataRegister;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabActivityModule_ProvideClientAppDataRegisterFactory
    implements Factory<ClientAppDataRegister> {
  private final CustomTabActivityModule module;

  public CustomTabActivityModule_ProvideClientAppDataRegisterFactory(
      CustomTabActivityModule module) {
    this.module = module;
  }

  @Override
  public ClientAppDataRegister get() {
    return provideInstance(module);
  }

  public static ClientAppDataRegister provideInstance(CustomTabActivityModule module) {
    return proxyProvideClientAppDataRegister(module);
  }

  public static CustomTabActivityModule_ProvideClientAppDataRegisterFactory create(
      CustomTabActivityModule module) {
    return new CustomTabActivityModule_ProvideClientAppDataRegisterFactory(module);
  }

  public static ClientAppDataRegister proxyProvideClientAppDataRegister(
      CustomTabActivityModule instance) {
    return Preconditions.checkNotNull(
        instance.provideClientAppDataRegister(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
