package org.chromium.chrome.browser.browserservices;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityUmaRecorder_Factory
    implements Factory<TrustedWebActivityUmaRecorder> {
  private static final TrustedWebActivityUmaRecorder_Factory INSTANCE =
      new TrustedWebActivityUmaRecorder_Factory();

  @Override
  public TrustedWebActivityUmaRecorder get() {
    return provideInstance();
  }

  public static TrustedWebActivityUmaRecorder provideInstance() {
    return new TrustedWebActivityUmaRecorder();
  }

  public static TrustedWebActivityUmaRecorder_Factory create() {
    return INSTANCE;
  }

  public static TrustedWebActivityUmaRecorder newTrustedWebActivityUmaRecorder() {
    return new TrustedWebActivityUmaRecorder();
  }
}
