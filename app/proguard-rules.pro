# ---------------------------------------------------------------------------
# JARVIS proguard rules
# ---------------------------------------------------------------------------

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jarvis.**$$serializer { *; }
-keep class com.jarvis.data.remote.gemini.** { *; }
-keep class com.jarvis.domain.model.** { *; }
-keep class com.jarvis.domain.orb.** { *; }
-keep class com.jarvis.domain.setup.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Play Core / Integrity (reflection inside the SDK)
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# Credential Manager / Google ID
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep line numbers so crash reports stay readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
