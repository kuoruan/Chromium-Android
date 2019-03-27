package org.chromium.chrome.browser.customtabs.dynamicmodule;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ActivityTabProvider;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class DynamicModulePageLoadObserver_Factory
    implements Factory<DynamicModulePageLoadObserver> {
  private final Provider<ActivityTabProvider> activityTabProvider;

  public DynamicModulePageLoadObserver_Factory(Provider<ActivityTabProvider> activityTabProvider) {
    this.activityTabProvider = activityTabProvider;
  }

  @Override
  public DynamicModulePageLoadObserver get() {
    return provideInstance(activityTabProvider);
  }

  public static DynamicModulePageLoadObserver provideInstance(
      Provider<ActivityTabProvider> activityTabProvider) {
    return new DynamicModulePageLoadObserver(activityTabProvider.get());
  }

  public static DynamicModulePageLoadObserver_Factory create(
      Provider<ActivityTabProvider> activityTabProvider) {
    return new DynamicModulePageLoadObserver_Factory(activityTabProvider);
  }

  public static DynamicModulePageLoadObserver newDynamicModulePageLoadObserver(
      ActivityTabProvider activityTabProvider) {
    return new DynamicModulePageLoadObserver(activityTabProvider);
  }
}
