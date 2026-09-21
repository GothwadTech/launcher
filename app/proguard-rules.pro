# ---------------------------------------------------------------------------
# R8 / kotlinx.serialization
#
# gradle.properties enables `android.enableR8.fullMode=true` and the release
# build is minified, so the serializers need explicit protection: without these
# rules `Json.decodeFromString<LauncherConfig>()` can silently fall back to
# defaults (or crash) after obfuscation - which for a launcher means a device
# that boots into a reset home screen.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Serializers generated for our own @Serializable classes.
-keep,includedescriptorclasses class com.gothwad.launcher.**$$serializer { *; }
-keepclassmembers class com.gothwad.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.gothwad.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the @Serializable models themselves: the generated descriptor re-creates
# instances reflectively and R8 full mode otherwise strips the synthetic
# constructor / companion.
-keep @kotlinx.serialization.Serializable class com.gothwad.launcher.** { *; }

# kotlinx.serialization runtime entry points (Json, JsonBuilder, SerializersModule).
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepnames class kotlinx.serialization.** { *; }
