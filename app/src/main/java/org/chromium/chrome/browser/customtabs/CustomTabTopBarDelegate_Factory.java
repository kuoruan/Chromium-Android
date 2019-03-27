package org.chromium.chrome.browser.customtabs;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabTopBarDelegate_Factory implements Factory<CustomTabTopBarDelegate> {
  private final Provider<ChromeActivity> activityProvider;

  public CustomTabTopBarDelegate_Factory(Provider<ChromeActivity> activityProvider) {
    this.activityProvider = activityProvider;
  }

  @Override
  public CustomTabTopBarDelegate get() {
    return provideInstance(activityProvider);
  }

  public static CustomTabTopBarDelegate provideInstance(Provider<ChromeActivity> activityProvider) {
    return new CustomTabTopBarDelegate(activityProvider.get());
  }

  public static CustomTabTopBarDelegate_Factory create(Provider<ChromeActivity> activityProvider) {
    return new CustomTabTopBarDelegate_Factory(activityProvider);
  }

  public static CustomTabTopBarDelegate newCustomTabTopBarDelegate(ChromeActivity activity) {
    return new CustomTabTopBarDelegate(activity);
  }
}
