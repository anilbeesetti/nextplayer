#include <jni.h>

#include "utils.h"

// This function is called when the native library is loaded.
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    if (utils_fields_init(vm) != 0) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

// This function is called when the native library is unloaded.
void JNI_OnUnload(JavaVM *vm, void *reserved) {
    utils_fields_free(vm);
}