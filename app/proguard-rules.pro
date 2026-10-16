# ElementCtrl R8 rules.

# Keep file and line numbers so crash reports point at real code (decode names with mapping.txt from the CI run).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The Rust SDK is reached through JNA and generated UniFFI bindings, which look classes and fields up by name.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }
-keep class org.matrix.rustsdk.** { *; }
-keep, includedescriptorclasses class org.rustls.platformverifier.** { *; }
-dontwarn java.awt.**

# Background sync worker and notification receivers are created by the system from their class names.
-keep class com.vknxworks.elementctrl.notify.** { *; }

-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
