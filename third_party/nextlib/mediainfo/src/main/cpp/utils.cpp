#include <jni.h>
#include "log.h"
#include "utils.h"

struct fields fields;
static JavaVM *javaVM;


JNIEnv *utils_get_env() {
    JNIEnv *env;
    if (javaVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return nullptr;
    }
    return env;
}

int utils_fields_init(JavaVM *vm) {
    javaVM = vm;

    JNIEnv *env = utils_get_env();
    if (env == nullptr) {
        return -1;
    }

#define GET_CLASS(clazz, str, b_global) do { \
        (clazz) = env->FindClass((str)); \
        if (!(clazz)) { \
            LOGE("FindClass(%s) failed", (str)); \
            return -1; \
        } \
        if (b_global) { \
            (clazz) = (jclass) env->NewGlobalRef((clazz)); \
            if (!(clazz)) { \
                LOGE("NewGlobalRef(%s) failed", (str)); \
                return -1; \
            } \
        } \
    } while (0)

#define GET_ID(get, id, clazz, str, args) do { \
        (id) = env->get((clazz), (str), (args)); \
        if (!(id)) { \
            LOGE(#get"(%s) failed", (str)); \
            return -1; \
        } \
    } while (0)

    GET_CLASS(fields.MediaInfoBuilder.clazz,
              "io/github/anilbeesetti/nextlib/mediainfo/MediaInfoBuilder", true);

    GET_ID(GetMethodID,
           fields.MediaInfoBuilder.onErrorID,
           fields.MediaInfoBuilder.clazz,
           "onError", "()V");

    GET_ID(GetMethodID,
           fields.MediaInfoBuilder.onMediaInfoFoundID,
           fields.MediaInfoBuilder.clazz,
           "onMediaInfoFound", "(Ljava/lang/String;J)V");

    GET_ID(GetMethodID,
           fields.MediaInfoBuilder.onVideoStreamFoundID,
           fields.MediaInfoBuilder.clazz,
           "onVideoStreamFound", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IJDIIIJ)V"
    );

    GET_ID(GetMethodID,
           fields.MediaInfoBuilder.onAudioStreamFoundID,
           fields.MediaInfoBuilder.clazz,
           "onAudioStreamFound",
           "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IJLjava/lang/String;IILjava/lang/String;)V"
    );

    GET_ID(GetMethodID,
           fields.MediaInfoBuilder.onSubtitleStreamFoundID,
           fields.MediaInfoBuilder.clazz,
           "onSubtitleStreamFound", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V"
    );

    GET_ID(GetMethodID,
           fields.MediaInfoBuilder.onChapterFoundID,
           fields.MediaInfoBuilder.clazz,
           "onChapterFound", "(ILjava/lang/String;JJ)V"
    );

    return 0;
}

void utils_fields_free(JavaVM *vm) {
    JNIEnv *env = utils_get_env();
    if (vm == nullptr) {
        return;
    }

    env->DeleteGlobalRef(fields.MediaInfoBuilder.clazz);

    javaVM = nullptr;
}

void utils_call_instance_method_void(JNIEnv *env, jobject instance, jmethodID methodID, ...) {
    va_list args;
    va_start(args, methodID);
    env->CallVoidMethodV(instance, methodID, args);
    va_end(args);
}