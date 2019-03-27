package org.chromium.base.library_loader;
public class NativeLibraries {
    public static final int CPU_FAMILY_UNKNOWN = 0;
    public static final int CPU_FAMILY_ARM = 1;
    public static final int CPU_FAMILY_MIPS = 2;
    public static final int CPU_FAMILY_X86 = 3;
    public static boolean sUseLinker;
    public static boolean sUseLibraryInZipFile;
    public static boolean sEnableLinkerTests;
    public static final String[] LIBRARIES =
      {"chrome"};
    public static String sVersionNumber =
      "73.0.3683.93";
    public static int sCpuFamily =
        CPU_FAMILY_UNKNOWN;
}
