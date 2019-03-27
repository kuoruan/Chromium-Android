package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.contextual_suggestions.EnabledStateMonitor;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeAppModule_ProvideEnabledStateMonitorFactory
    implements Factory<EnabledStateMonitor> {
  private final ChromeAppModule module;

  public ChromeAppModule_ProvideEnabledStateMonitorFactory(ChromeAppModule module) {
    this.module = module;
  }

  @Override
  public EnabledStateMonitor get() {
    return provideInstance(module);
  }

  public static EnabledStateMonitor provideInstance(ChromeAppModule module) {
    return proxyProvideEnabledStateMonitor(module);
  }

  public static ChromeAppModule_ProvideEnabledStateMonitorFactory create(ChromeAppModule module) {
    return new ChromeAppModule_ProvideEnabledStateMonitorFactory(module);
  }

  public static EnabledStateMonitor proxyProvideEnabledStateMonitor(ChromeAppModule instance) {
    return Preconditions.checkNotNull(
        instance.provideEnabledStateMonitor(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
