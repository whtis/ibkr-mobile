# Keep kotlinx.serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.tis.ibkr.**$$serializer { *; }
-keepclassmembers class com.tis.ibkr.** {
    *** Companion;
}
-keepclasseswithmembers class com.tis.ibkr.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor + OkHttp engine: R8 full mode can strip the ServiceLoader-discovered
# engine and related classes, breaking all networking at runtime. Broad keep
# for the first release; can be narrowed once the release build is smoke-tested.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
