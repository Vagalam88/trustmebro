# Add project specific ProGuard rules here.

# Bouncy Castle — keep all classes for PGP verification
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep crypto classes
-keep class com.trustmebro.crypto.** { *; }
-keep class com.trustmebro.utils.** { *; }

# Kotlin serialization / reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Keep X.509 cert classes
-keep class java.security.** { *; }
-keep class javax.security.** { *; }
-dontwarn java.security.**
-dontwarn javax.security.**
