package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideCompositorViewHolderFactory
    implements Factory<CompositorViewHolder> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideCompositorViewHolderFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public CompositorViewHolder get() {
    return provideInstance(module);
  }

  public static CompositorViewHolder provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideCompositorViewHolder(module);
  }

  public static ChromeActivityCommonsModule_ProvideCompositorViewHolderFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideCompositorViewHolderFactory(module);
  }

  public static CompositorViewHolder proxyProvideCompositorViewHolder(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideCompositorViewHolder(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
