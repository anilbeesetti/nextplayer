# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Fix for R8 compilation issue with XmlResourceParser and XmlPullParser
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
-keep interface org.xmlpull.v1.** { *; }

# Keep Android system classes that implement XmlPullParser
-keep class android.content.res.XmlResourceParser { *; }
-keep interface android.content.res.XmlResourceParser { *; }

# Ignore warnings about missing classes in XML parsing
-dontwarn android.content.res.XmlResourceParser
-dontwarn org.xmlpull.v1.XmlPullParser

# Keep WebDAV related classes if using external libraries
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep media/video related classes
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
