
#include "ffcommon.h"

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define ERROR_STRING_BUFFER_LENGTH 256


/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext *context) {
    if (!context) {
        return;
    }
    SwrContext *swrContext;
    if ((swrContext = (SwrContext *)context->opaque)) {
        swr_free(&swrContext);
        context->opaque = nullptr;
    }
    av_freep(&context->extradata);
    avcodec_free_context(&context);
}

/**
* Returns the AVCodec with the specified name, or NULL if it is not available.
*/
AVCodec *getCodecByName(JNIEnv *env, jstring codecName) {
    if (!codecName) {
        return nullptr;
    }
    const char *codecNameChars = env->GetStringUTFChars(codecName, nullptr);
    auto *codec = const_cast<AVCodec *>(avcodec_find_decoder_by_name(codecNameChars));
    env->ReleaseStringUTFChars(codecName, codecNameChars);
    return codec;
}


/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char *functionName, int errorNumber) {
    char *buffer = (char *)malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
    av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
    LOGE("Error in %s: %s", functionName, buffer);
    free(buffer);
}