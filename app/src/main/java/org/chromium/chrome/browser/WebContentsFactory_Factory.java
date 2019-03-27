package org.chromium.chrome.browser;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class WebContentsFactory_Factory implements Factory<WebContentsFactory> {
  private static final WebContentsFactory_Factory INSTANCE = new WebContentsFactory_Factory();

  @Override
  public WebContentsFactory get() {
    return provideInstance();
  }

  public static WebContentsFactory provideInstance() {
    return new WebContentsFactory();
  }

  public static WebContentsFactory_Factory create() {
    return INSTANCE;
  }

  public static WebContentsFactory newWebContentsFactory() {
    return new WebContentsFactory();
  }
}
