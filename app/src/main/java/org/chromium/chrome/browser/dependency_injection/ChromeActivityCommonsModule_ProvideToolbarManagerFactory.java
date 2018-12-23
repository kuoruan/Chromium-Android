package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.toolbar.ToolbarManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideToolbarManagerFactory
    implements Factory<ToolbarManager> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideToolbarManagerFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public ToolbarManager get() {
    return provideInstance(module);
  }

  public static ToolbarManager provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideToolbarManager(module);
  }

  public static ChromeActivityCommonsModule_ProvideToolbarManagerFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideToolbarManagerFactory(module);
  }

  public static ToolbarManager proxyProvideToolbarManager(ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideToolbarManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
