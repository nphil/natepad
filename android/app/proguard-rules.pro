# Keep Bouncy Castle classes used via reflection
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep kotlinx.serialization
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class com.natepad.app.**$$serializer { *; }
-keepclassmembers class com.natepad.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.natepad.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Biometric
-keep class androidx.biometric.** { *; }

# Security crypto
-keep class androidx.security.crypto.** { *; }
