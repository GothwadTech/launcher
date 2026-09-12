# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.gothwad.launcher.**$$serializer { *; }
-keepclassmembers class com.gothwad.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.gothwad.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
