package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideLayoutManagerFactory
    implements Factory<LayoutManager> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideLayoutManagerFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public LayoutManager get() {
    return provideInstance(module);
  }

  public static LayoutManager provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideLayoutManager(module);
  }

  public static ChromeActivityCommonsModule_ProvideLayoutManagerFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideLayoutManagerFactory(module);
  }

  public static LayoutManager proxyProvideLayoutManager(ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideLayoutManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
