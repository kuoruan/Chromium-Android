package org.chromium.chrome.browser.browserservices.trustedwebactivityui;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityModel_Factory implements Factory<TrustedWebActivityModel> {
  private static final TrustedWebActivityModel_Factory INSTANCE =
      new TrustedWebActivityModel_Factory();

  @Override
  public TrustedWebActivityModel get() {
    return provideInstance();
  }

  public static TrustedWebActivityModel provideInstance() {
    return new TrustedWebActivityModel();
  }

  public static TrustedWebActivityModel_Factory create() {
    return INSTANCE;
  }

  public static TrustedWebActivityModel newTrustedWebActivityModel() {
    return new TrustedWebActivityModel();
  }
}
