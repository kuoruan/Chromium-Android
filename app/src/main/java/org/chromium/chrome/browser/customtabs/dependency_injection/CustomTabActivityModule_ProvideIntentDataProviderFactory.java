package org.chromium.chrome.browser.customtabs.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabActivityModule_ProvideIntentDataProviderFactory
    implements Factory<CustomTabIntentDataProvider> {
  private final CustomTabActivityModule module;

  public CustomTabActivityModule_ProvideIntentDataProviderFactory(CustomTabActivityModule module) {
    this.module = module;
  }

  @Override
  public CustomTabIntentDataProvider get() {
    return provideInstance(module);
  }

  public static CustomTabIntentDataProvider provideInstance(CustomTabActivityModule module) {
    return proxyProvideIntentDataProvider(module);
  }

  public static CustomTabActivityModule_ProvideIntentDataProviderFactory create(
      CustomTabActivityModule module) {
    return new CustomTabActivityModule_ProvideIntentDataProviderFactory(module);
  }

  public static CustomTabIntentDataProvider proxyProvideIntentDataProvider(
      CustomTabActivityModule instance) {
    return Preconditions.checkNotNull(
        instance.provideIntentDataProvider(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
