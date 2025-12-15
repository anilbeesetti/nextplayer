#ifndef NEXTPLAYER_LOG_H
#define NEXTPLAYER_LOG_H

#define LOG_TAG  "NextPlayerJNI"

#ifndef NDEBUG

#include <android/log.h>

# define LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
# define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
# define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
# define LOGW(...)  __android_log_print(ANDROID_LOG_WARNING, LOG_TAG, __VA_ARGS__)
# define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
# define LOGV(...)  (void)0
# define LOGD(...)  (void)0
# define LOGI(...)  (void)0
# define LOGW(...)  (void)0
# define LOGE(...)  (void)0
#endif


#endif //NEXTPLAYER_LOG_H
