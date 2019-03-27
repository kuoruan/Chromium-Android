package org.chromium.chrome.browser;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class AppHooksModule_ProvideExternalAuthUtilsFactory
    implements Factory<ExternalAuthUtils> {
  private final AppHooksModule module;

  public AppHooksModule_ProvideExternalAuthUtilsFactory(AppHooksModule module) {
    this.module = module;
  }

  @Override
  public ExternalAuthUtils get() {
    return provideInstance(module);
  }

  public static ExternalAuthUtils provideInstance(AppHooksModule module) {
    return proxyProvideExternalAuthUtils(module);
  }

  public static AppHooksModule_ProvideExternalAuthUtilsFactory create(AppHooksModule module) {
    return new AppHooksModule_ProvideExternalAuthUtilsFactory(module);
  }

  public static ExternalAuthUtils proxyProvideExternalAuthUtils(AppHooksModule instance) {
    return Preconditions.checkNotNull(
        instance.provideExternalAuthUtils(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
