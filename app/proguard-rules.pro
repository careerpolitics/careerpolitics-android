# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================================================
# WebView JavaScript Interface - CRITICAL for app functionality
# ============================================================================
# Keep all JavaScript interface methods (exposed to WebView)
-keepclassmembers class com.murari.careerpolitics.util.AndroidWebViewBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all classes with JavascriptInterface annotations
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView related classes
-keep public class android.webkit.WebView
-keepclassmembers class android.webkit.WebView {
    <methods>;
}

# Keep EventBus classes
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep WebView related classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep custom WebView clients
-keep class com.murari.careerpolitics.webclients.** { *; }

# Keep audio service
-keep class com.murari.careerpolitics.media.AudioService { *; }

# Keep network utilities
-keep class com.murari.careerpolitics.util.network.** { *; }

# Keep event classes
-keep class com.murari.careerpolitics.events.** { *; }

# ============================================================================
# Logging - Completely strip debug logs in release builds
# ============================================================================
# This removes ALL debug/verbose/info logging code at compile time (zero overhead)
# Warning and error logs are preserved for crash reporting
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Strip custom Logger debug/info/verbose logs
-assumenosideeffects class com.murari.careerpolitics.util.Logger {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================================================
# R8 Optimization Configuration
# ============================================================================
# Enable full mode for maximum optimization
# (R8 full mode is enabled by default in AGP 8.0+)
-allowaccessmodification
-repackageclasses ''

# Optimization passes (R8 handles this automatically)
-optimizationpasses 5

# Don't optimize away useful code
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ============================================================================
# Debugging - Keep line numbers and source file for crash reporting
# ============================================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signature information for reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Remove unused code
-dontwarn android.support.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# ============================================================================
# Kotlin Specific
# ============================================================================
# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================================
# Gson (JSON parsing)
# ============================================================================
# Keep generic signatures for Gson
-keepattributes Signature

# Keep all model classes used with Gson
# Add specific model classes here if you have them:
# -keep class com.murari.careerpolitics.model.** { *; }

# ============================================================================
# Configuration Object - Keep for BuildConfig access
# ============================================================================
-keep class com.murari.careerpolitics.BuildConfig { *; }
-keep class com.murari.careerpolitics.config.AppConfig { *; }
-keep class com.murari.careerpolitics.config.Environment { *; }

# ============================================================================
# Pusher Push Notifications
# ============================================================================
-keep class com.pusher.pushnotifications.** { *; }
-dontwarn com.pusher.pushnotifications.**

# ============================================================================
# Compose (if issues arise)
# ============================================================================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ============================================================================
# Additional Warnings to Ignore
# ============================================================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
