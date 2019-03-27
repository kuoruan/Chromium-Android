package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory
    implements Factory<ChromeFullscreenManager> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public ChromeFullscreenManager get() {
    return provideInstance(module);
  }

  public static ChromeFullscreenManager provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideChromeFullscreenManager(module);
  }

  public static ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory(module);
  }

  public static ChromeFullscreenManager proxyProvideChromeFullscreenManager(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideChromeFullscreenManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
