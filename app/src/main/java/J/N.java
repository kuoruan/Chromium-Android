package J;

import java.lang.String;
import javax.annotation.Generated;

@Generated("org.chromium.jni_generator.JniProcessor")
public final class N {
  public static boolean TESTING_ENABLED;

  public static boolean REQUIRE_MOCK;

  /**
   * org.chromium.base.AnimationFrameTimeHistogram.saveHistogram
   * @param histogramName (java.lang.String)
   * @param frameTimesMs (long[])
   * @param count (int)
   * @return (void)
   */
  public static final native void M7xB0tc0(String histogramName, long[] frameTimesMs, int count);
}
