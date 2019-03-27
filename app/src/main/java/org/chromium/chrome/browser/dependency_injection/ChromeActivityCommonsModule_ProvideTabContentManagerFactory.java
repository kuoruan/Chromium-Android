package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideTabContentManagerFactory
    implements Factory<TabContentManager> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideTabContentManagerFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public TabContentManager get() {
    return provideInstance(module);
  }

  public static TabContentManager provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideTabContentManager(module);
  }

  public static ChromeActivityCommonsModule_ProvideTabContentManagerFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideTabContentManagerFactory(module);
  }

  public static TabContentManager proxyProvideTabContentManager(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideTabContentManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
