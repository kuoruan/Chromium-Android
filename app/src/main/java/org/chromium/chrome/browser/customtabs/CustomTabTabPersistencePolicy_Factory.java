package org.chromium.chrome.browser.customtabs;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabTabPersistencePolicy_Factory
    implements Factory<CustomTabTabPersistencePolicy> {
  private final Provider<ChromeActivity> activityProvider;

  public CustomTabTabPersistencePolicy_Factory(Provider<ChromeActivity> activityProvider) {
    this.activityProvider = activityProvider;
  }

  @Override
  public CustomTabTabPersistencePolicy get() {
    return provideInstance(activityProvider);
  }

  public static CustomTabTabPersistencePolicy provideInstance(
      Provider<ChromeActivity> activityProvider) {
    return new CustomTabTabPersistencePolicy(activityProvider.get());
  }

  public static CustomTabTabPersistencePolicy_Factory create(
      Provider<ChromeActivity> activityProvider) {
    return new CustomTabTabPersistencePolicy_Factory(activityProvider);
  }

  public static CustomTabTabPersistencePolicy newCustomTabTabPersistencePolicy(
      ChromeActivity activity) {
    return new CustomTabTabPersistencePolicy(activity);
  }
}
