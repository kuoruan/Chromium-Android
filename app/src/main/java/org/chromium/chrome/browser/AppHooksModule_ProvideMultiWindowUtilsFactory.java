package org.chromium.chrome.browser;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class AppHooksModule_ProvideMultiWindowUtilsFactory
    implements Factory<MultiWindowUtils> {
  private final AppHooksModule module;

  public AppHooksModule_ProvideMultiWindowUtilsFactory(AppHooksModule module) {
    this.module = module;
  }

  @Override
  public MultiWindowUtils get() {
    return provideInstance(module);
  }

  public static MultiWindowUtils provideInstance(AppHooksModule module) {
    return proxyProvideMultiWindowUtils(module);
  }

  public static AppHooksModule_ProvideMultiWindowUtilsFactory create(AppHooksModule module) {
    return new AppHooksModule_ProvideMultiWindowUtilsFactory(module);
  }

  public static MultiWindowUtils proxyProvideMultiWindowUtils(AppHooksModule instance) {
    return Preconditions.checkNotNull(
        instance.provideMultiWindowUtils(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
