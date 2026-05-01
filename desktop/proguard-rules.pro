# Compose Desktop specific rules
-keep class network.columba.app.desktop.** { *; }
-keep class androidx.compose.** { *; }

# Kotlin
-keepclassmembers class ** {
    public <methods>;
    <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    <fields>;
}
-keepclassmembernames class kotlinx.coroutines.** {
    <methods>;
}

# R8/ProGuard
-dontwarn kotlinx.internal.**
-dontwarn java.lang.invoke.**
