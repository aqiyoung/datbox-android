# OpenList Android — R8 / ProGuard rules
# Release build (isMinifyEnabled = true) requires this file.
# Conservative, stack-proven rules for:
#   Jetpack Compose + Material3, Hilt, Retrofit2, OkHttp3,
#   kotlinx.serialization, Coil.

# ---- General attributes ----
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations

# ---- kotlinx.serialization ----
# Keep all @Serializable classes and their fields (names matter for JSON mapping).
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class kotlin.Metadata { *; }

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepnames class * extends androidx.navigation.NavDestination

# ---- Retrofit / OkHttp ----
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ---- Coil ----
-keep class coil.** { *; }
-dontwarn coil.**

# ---- App model / API layer (serialized DTOs) ----
-keep class com.threel.openlist.data.model.** { *; }
-keep class com.threel.openlist.data.api.** { *; }

# ---- Compose (keep @Composable, avoid stripping) ----
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# ---- Misc vendor warnings (non-fatal) ----
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.**
