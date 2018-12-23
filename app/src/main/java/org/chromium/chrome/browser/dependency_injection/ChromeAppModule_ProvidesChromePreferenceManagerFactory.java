package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeAppModule_ProvidesChromePreferenceManagerFactory
    implements Factory<ChromePreferenceManager> {
  private final ChromeAppModule module;

  public ChromeAppModule_ProvidesChromePreferenceManagerFactory(ChromeAppModule module) {
    this.module = module;
  }

  @Override
  public ChromePreferenceManager get() {
    return provideInstance(module);
  }

  public static ChromePreferenceManager provideInstance(ChromeAppModule module) {
    return proxyProvidesChromePreferenceManager(module);
  }

  public static ChromeAppModule_ProvidesChromePreferenceManagerFactory create(
      ChromeAppModule module) {
    return new ChromeAppModule_ProvidesChromePreferenceManagerFactory(module);
  }

  public static ChromePreferenceManager proxyProvidesChromePreferenceManager(
      ChromeAppModule instance) {
    return Preconditions.checkNotNull(
        instance.providesChromePreferenceManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
