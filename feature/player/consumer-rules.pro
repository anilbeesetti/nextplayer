# libmpv's JNI resolves these client classes and callbacks by their binary names.
-keep class is.xyz.mpv.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
