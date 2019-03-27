package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.ui.base.ActivityWindowAndroid;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideActivityWindowAndroidFactory
    implements Factory<ActivityWindowAndroid> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideActivityWindowAndroidFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public ActivityWindowAndroid get() {
    return provideInstance(module);
  }

  public static ActivityWindowAndroid provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideActivityWindowAndroid(module);
  }

  public static ChromeActivityCommonsModule_ProvideActivityWindowAndroidFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideActivityWindowAndroidFactory(module);
  }

  public static ActivityWindowAndroid proxyProvideActivityWindowAndroid(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideActivityWindowAndroid(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
