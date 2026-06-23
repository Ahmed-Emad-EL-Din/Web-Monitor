# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class org.jsoup.** { *; }
-keep class com.example.data.TrackingRule { *; }
-keep class com.example.data.TrackingHistory { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile
