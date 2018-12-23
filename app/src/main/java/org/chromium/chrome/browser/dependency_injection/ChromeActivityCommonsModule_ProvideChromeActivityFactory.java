package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.ChromeActivity;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideChromeActivityFactory
    implements Factory<ChromeActivity> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideChromeActivityFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public ChromeActivity get() {
    return provideInstance(module);
  }

  public static ChromeActivity provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideChromeActivity(module);
  }

  public static ChromeActivityCommonsModule_ProvideChromeActivityFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideChromeActivityFactory(module);
  }

  public static ChromeActivity proxyProvideChromeActivity(ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideChromeActivity(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
