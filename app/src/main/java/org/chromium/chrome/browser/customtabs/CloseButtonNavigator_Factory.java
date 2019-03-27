package org.chromium.chrome.browser.customtabs;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CloseButtonNavigator_Factory implements Factory<CloseButtonNavigator> {
  private static final CloseButtonNavigator_Factory INSTANCE = new CloseButtonNavigator_Factory();

  @Override
  public CloseButtonNavigator get() {
    return provideInstance();
  }

  public static CloseButtonNavigator provideInstance() {
    return new CloseButtonNavigator();
  }

  public static CloseButtonNavigator_Factory create() {
    return INSTANCE;
  }

  public static CloseButtonNavigator newCloseButtonNavigator() {
    return new CloseButtonNavigator();
  }
}
