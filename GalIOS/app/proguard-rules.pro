# zstd-jni: its native code calls back into these classes by name.
-keep class com.github.luben.zstd.** { *; }
# commons-compress names optional codecs GalIOS does not use.
-dontwarn org.brotli.**
-dontwarn org.tukaani.**
-dontwarn org.objectweb.asm.**
-dontwarn com.github.luben.zstd.**
-dontwarn javax.**
-dontwarn java.lang.management.**
-dontwarn org.osgi.**
