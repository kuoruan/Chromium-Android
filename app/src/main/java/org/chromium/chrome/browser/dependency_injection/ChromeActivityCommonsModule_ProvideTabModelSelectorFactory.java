package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideTabModelSelectorFactory
    implements Factory<TabModelSelector> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideTabModelSelectorFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public TabModelSelector get() {
    return provideInstance(module);
  }

  public static TabModelSelector provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideTabModelSelector(module);
  }

  public static ChromeActivityCommonsModule_ProvideTabModelSelectorFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideTabModelSelectorFactory(module);
  }

  public static TabModelSelector proxyProvideTabModelSelector(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideTabModelSelector(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
