package org.chromium.chrome.browser.customtabs;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabBottomBarDelegate_Factory
    implements Factory<CustomTabBottomBarDelegate> {
  private final Provider<ChromeActivity> activityProvider;

  private final Provider<CustomTabIntentDataProvider> dataProvider;

  private final Provider<ChromeFullscreenManager> fullscreenManagerProvider;

  public CustomTabBottomBarDelegate_Factory(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabIntentDataProvider> dataProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider) {
    this.activityProvider = activityProvider;
    this.dataProvider = dataProvider;
    this.fullscreenManagerProvider = fullscreenManagerProvider;
  }

  @Override
  public CustomTabBottomBarDelegate get() {
    return provideInstance(activityProvider, dataProvider, fullscreenManagerProvider);
  }

  public static CustomTabBottomBarDelegate provideInstance(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabIntentDataProvider> dataProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider) {
    return new CustomTabBottomBarDelegate(
        activityProvider.get(), dataProvider.get(), fullscreenManagerProvider.get());
  }

  public static CustomTabBottomBarDelegate_Factory create(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabIntentDataProvider> dataProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider) {
    return new CustomTabBottomBarDelegate_Factory(
        activityProvider, dataProvider, fullscreenManagerProvider);
  }

  public static CustomTabBottomBarDelegate newCustomTabBottomBarDelegate(
      ChromeActivity activity,
      CustomTabIntentDataProvider dataProvider,
      ChromeFullscreenManager fullscreenManager) {
    return new CustomTabBottomBarDelegate(activity, dataProvider, fullscreenManager);
  }
}
