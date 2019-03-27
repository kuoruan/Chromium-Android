package org.chromium.chrome.browser.customtabs.dynamicmodule;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ActivityDelegate_Factory implements Factory<ActivityDelegate> {
  private final Provider<ChromeActivity> chromeActivityProvider;

  private final Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider;

  public ActivityDelegate_Factory(
      Provider<ChromeActivity> chromeActivityProvider,
      Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider) {
    this.chromeActivityProvider = chromeActivityProvider;
    this.activityLifecycleDispatcherProvider = activityLifecycleDispatcherProvider;
  }

  @Override
  public ActivityDelegate get() {
    return provideInstance(chromeActivityProvider, activityLifecycleDispatcherProvider);
  }

  public static ActivityDelegate provideInstance(
      Provider<ChromeActivity> chromeActivityProvider,
      Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider) {
    return new ActivityDelegate(
        chromeActivityProvider.get(), activityLifecycleDispatcherProvider.get());
  }

  public static ActivityDelegate_Factory create(
      Provider<ChromeActivity> chromeActivityProvider,
      Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider) {
    return new ActivityDelegate_Factory(
        chromeActivityProvider, activityLifecycleDispatcherProvider);
  }

  public static ActivityDelegate newActivityDelegate(
      ChromeActivity chromeActivity, ActivityLifecycleDispatcher activityLifecycleDispatcher) {
    return new ActivityDelegate(chromeActivity, activityLifecycleDispatcher);
  }
}
