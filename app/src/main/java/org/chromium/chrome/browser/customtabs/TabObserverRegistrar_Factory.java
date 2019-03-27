package org.chromium.chrome.browser.customtabs;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TabObserverRegistrar_Factory implements Factory<TabObserverRegistrar> {
  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  public TabObserverRegistrar_Factory(
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider) {
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
  }

  @Override
  public TabObserverRegistrar get() {
    return provideInstance(lifecycleDispatcherProvider);
  }

  public static TabObserverRegistrar provideInstance(
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider) {
    return new TabObserverRegistrar(lifecycleDispatcherProvider.get());
  }

  public static TabObserverRegistrar_Factory create(
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider) {
    return new TabObserverRegistrar_Factory(lifecycleDispatcherProvider);
  }

  public static TabObserverRegistrar newTabObserverRegistrar(
      ActivityLifecycleDispatcher lifecycleDispatcher) {
    return new TabObserverRegistrar(lifecycleDispatcher);
  }
}
