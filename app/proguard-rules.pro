# ── Animal Match — release shrinking rules ───────────────────────────────

# Keep our public data classes (LevelCatalog, Level, etc.) since LevelRepository
# parses them from JSON and they're accessed reflectively in places.
-keep class com.datawaves.animalmatch.data.models.** { *; }

# Keep enums; their fields are read by reflection in some Android internals.
-keepclassmembers enum * { *; }

# Standard Android boilerplate
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# AppCompat / Material — already handled by their consumer rules, but be safe.
-keep class androidx.appcompat.widget.SwitchCompat { *; }
-keep class com.google.android.material.bottomsheet.** { *; }

# Kotlin reflection metadata (small footprint)
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Keep org.json — used by LevelRepository (it's actually built-in but be safe).
-dontwarn org.json.**

# Strip Kotlin null-checks for slightly smaller code on release.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

# Strip android.util.Log calls in release (saves a few KB and avoids leaking logs).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
