package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.ActivityTabProvider;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideActivityTabProviderFactory
    implements Factory<ActivityTabProvider> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideActivityTabProviderFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public ActivityTabProvider get() {
    return provideInstance(module);
  }

  public static ActivityTabProvider provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideActivityTabProvider(module);
  }

  public static ChromeActivityCommonsModule_ProvideActivityTabProviderFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideActivityTabProviderFactory(module);
  }

  public static ActivityTabProvider proxyProvideActivityTabProvider(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideActivityTabProvider(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
