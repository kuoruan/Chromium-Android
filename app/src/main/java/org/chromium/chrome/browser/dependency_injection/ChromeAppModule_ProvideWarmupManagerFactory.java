package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.WarmupManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeAppModule_ProvideWarmupManagerFactory implements Factory<WarmupManager> {
  private final ChromeAppModule module;

  public ChromeAppModule_ProvideWarmupManagerFactory(ChromeAppModule module) {
    this.module = module;
  }

  @Override
  public WarmupManager get() {
    return provideInstance(module);
  }

  public static WarmupManager provideInstance(ChromeAppModule module) {
    return proxyProvideWarmupManager(module);
  }

  public static ChromeAppModule_ProvideWarmupManagerFactory create(ChromeAppModule module) {
    return new ChromeAppModule_ProvideWarmupManagerFactory(module);
  }

  public static WarmupManager proxyProvideWarmupManager(ChromeAppModule instance) {
    return Preconditions.checkNotNull(
        instance.provideWarmupManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
