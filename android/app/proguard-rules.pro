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
