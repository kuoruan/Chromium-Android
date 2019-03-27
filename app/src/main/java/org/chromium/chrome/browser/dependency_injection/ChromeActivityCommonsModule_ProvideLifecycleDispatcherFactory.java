package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory
    implements Factory<ActivityLifecycleDispatcher> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public ActivityLifecycleDispatcher get() {
    return provideInstance(module);
  }

  public static ActivityLifecycleDispatcher provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideLifecycleDispatcher(module);
  }

  public static ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory(module);
  }

  public static ActivityLifecycleDispatcher proxyProvideLifecycleDispatcher(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideLifecycleDispatcher(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
