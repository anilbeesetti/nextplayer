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

-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**

# SMBJ loads the SPNEGO authenticator factory through Class.forName().
-keep class com.hierynomus.smbj.auth.SpnegoAuthenticator$Factory { <init>(); }

# mbassador discovers SMBJ event handlers by their @Handler annotation.
-keepclassmembers class com.hierynomus.** {
    @net.engio.mbassy.listener.Handler <methods>;
}

# Bouncy Castle registers JCA implementations by class name. SFTP uses an
# app-specific provider name so Android's stripped platform BC is untouched.
-keep class org.bouncycastle.jcajce.provider.** { public <init>(...); }
-keep class org.bouncycastle.jce.provider.** { public <init>(...); }

# mbassador creates the default handler invocation through Class.getConstructor().
-keepclassmembers,allowobfuscation class net.engio.mbassy.dispatch.ReflectiveHandlerInvocation {
    public <init>(net.engio.mbassy.subscription.SubscriptionContext);
}

# Optional runtime dependencies of the network libraries (smbj, mbassador,
# okhttp/bouncycastle) that are not present on Android. Safe to ignore.
-dontwarn javax.el.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.ietf.jgss.**
-dontwarn org.openjsse.**
-dontwarn java.rmi.UnmarshalException
