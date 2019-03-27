package org.chromium.base;
public class BuildConfig {
    public static boolean IS_MULTIDEX_ENABLED ;
    public static String FIREBASE_APP_ID = "";
    public static boolean DCHECK_IS_ON ;
    public static boolean IS_UBSAN ;
    public static String[] COMPRESSED_LOCALES = {
            // "am", "ar", "bg", "ca", "cs", "da", "de", "el", "en-GB", "en-US", "es", "es-419",
            // "fa", "fi", "fr", "he", "hi", "hr", "hu", "id", "it", "ja", "ko", "lt",
            // "lv", "nb", "nl", "pl", "pt-BR", "pt-PT", "ro", "ru", "sk", "sl", "sr",
            // "sv", "sw", "th", "tr", "uk", "vi", "zh-CN", "zh-TW"
            "en-US","zh-CN", "zh-TW"
    };
    public static String[] UNCOMPRESSED_LOCALES = {
            // "en-US","zh-CN", "zh-TW"
    };
    public static int R_STRING_PRODUCT_VERSION ;
}
