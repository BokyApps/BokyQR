# Release builds carry no logging at all: strip every android.util.Log call.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static boolean isLoggable(...);
}

# Compile-time-only annotations that androidx.security:security-crypto's Tink dependency
# references in its public API signatures but never ships. R8 reports each as a missing class and
# fails the release build. They are annotations: nothing is called, nothing is loaded at runtime,
# and the JVM discards them anyway.
#
# Scoped to the exact classes R8 named rather than to `-dontwarn com.google.errorprone.**` or
# `-dontwarn javax.annotation.**`. A blanket rule here would silently hide a genuinely absent
# class from Tink or from security-crypto later, which is the failure this file is guarding.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
