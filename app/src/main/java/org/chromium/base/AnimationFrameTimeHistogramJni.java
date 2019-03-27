package org.chromium.base;

import J.N;
import java.lang.Override;
import java.lang.String;
import javax.annotation.Generated;

@Generated("org.chromium.jni_generator.JniProcessor")
final class AnimationFrameTimeHistogramJni implements AnimationFrameTimeHistogram.Natives {
  private static AnimationFrameTimeHistogram.Natives testInstance;

  public static final JniStaticTestMocker<AnimationFrameTimeHistogram.Natives> TEST_HOOKS = new org.chromium.base.JniStaticTestMocker<org.chromium.base.AnimationFrameTimeHistogram.Natives>() {
    @java.lang.Override
    public void setInstanceForTesting(
        org.chromium.base.AnimationFrameTimeHistogram.Natives instance) {
      testInstance = instance;
    }
  };

  @Override
  public void saveHistogram(String histogramName, long[] frameTimesMs, int count) {
    N.M7xB0tc0(histogramName, frameTimesMs, count);
  }

  public static AnimationFrameTimeHistogram.Natives get() {
    if (N.TESTING_ENABLED) {
      if (testInstance != null) {
        return testInstance;
      }
      if (N.REQUIRE_MOCK) {
        throw new UnsupportedOperationException("No mock found for the native implementation for org.chromium.base.AnimationFrameTimeHistogram.Natives. The current configuration requires all native implementations to have a mock instance.");
      }
    }
    return new AnimationFrameTimeHistogramJni();
  }
}
