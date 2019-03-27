package org.chromium.chrome.browser;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class AppHooksModule_ProvideCustomTabsConnectionFactory
    implements Factory<CustomTabsConnection> {
  private static final AppHooksModule_ProvideCustomTabsConnectionFactory INSTANCE =
      new AppHooksModule_ProvideCustomTabsConnectionFactory();

  @Override
  public CustomTabsConnection get() {
    return provideInstance();
  }

  public static CustomTabsConnection provideInstance() {
    return proxyProvideCustomTabsConnection();
  }

  public static AppHooksModule_ProvideCustomTabsConnectionFactory create() {
    return INSTANCE;
  }

  public static CustomTabsConnection proxyProvideCustomTabsConnection() {
    return Preconditions.checkNotNull(
        AppHooksModule.provideCustomTabsConnection(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
