# NEXUS IPTV R8 / ProGuard Production Rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database & FTS4
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp & Media3 ExoPlayer
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class androidx.media3.** { *; }
