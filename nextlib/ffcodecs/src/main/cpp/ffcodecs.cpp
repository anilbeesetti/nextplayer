
#include <android/log.h>
#include <jni.h>
#include <cstdlib>
#include <android/native_window_jni.h>
#include <algorithm>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <cstdint>
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
AVCodec *getCodecByName(JNIEnv *env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, jbyteArray extraData,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount);

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative AUDIO_DECODER_ERROR constant value in the case of an
 * error.
 */
int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize);

/**
 * Transforms ffmpeg AVERROR into a negative AUDIO_DECODER_ERROR constant value.
 */
int transformError(int errorNumber);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char *functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext *context);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

AVCodec *getCodecByName(JNIEnv *env, jstring codecName) {
    if (!codecName) {
        return nullptr;
    }
    const char *codecNameChars = env->GetStringUTFChars(codecName, nullptr);
    auto *codec = const_cast<AVCodec *>(avcodec_find_decoder_by_name(codecNameChars));
    env->ReleaseStringUTFChars(codecName, codecNameChars);
    return codec;
}

AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, jbyteArray extraData,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount) {
    AVCodecContext *context = avcodec_alloc_context3(codec);
    if (!context) {
        LOGE("Failed to allocate context.");
        return nullptr;
    }
    context->request_sample_fmt =
            outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
    if (extraData) {
        jsize size = env->GetArrayLength(extraData);
        context->extradata_size = size;
        context->extradata =
                (uint8_t *)av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
        if (!context->extradata) {
            LOGE("Failed to allocate extra data.");
            releaseContext(context);
            return nullptr;
        }
        env->GetByteArrayRegion(extraData, 0, size, (jbyte *)context->extradata);
    }
    if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
        context->codec_id == AV_CODEC_ID_PCM_ALAW) {
        context->sample_rate = rawSampleRate;
        context->ch_layout.nb_channels = rawChannelCount;
        av_channel_layout_default(&context->ch_layout, rawChannelCount);
    }
    context->err_recognition = AV_EF_IGNORE_ERR;
    int result = avcodec_open2(context, codec, nullptr);
    if (result < 0) {
        logError("avcodec_open2", result);
        releaseContext(context);
        return nullptr;
    }
    return context;
}

int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize) {
    int result = 0;
    // Queue input data.
    result = avcodec_send_packet(context, packet);
    if (result) {
        logError("avcodec_send_packet", result);
        return transformError(result);
    }

    // Dequeue output data until it runs out.
    int outSize = 0;
    while (true) {
        AVFrame *frame = av_frame_alloc();
        if (!frame) {
            LOGE("Failed to allocate output frame.");
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }
        result = avcodec_receive_frame(context, frame);
        if (result) {
            av_frame_free(&frame);
            if (result == AVERROR(EAGAIN)) {
                break;
            }
            logError("avcodec_receive_frame", result);
            return transformError(result);
        }

        // Resample output.
        AVSampleFormat sampleFormat = context->sample_fmt;
        int channelCount = context->ch_layout.nb_channels;
        int channelLayout = (int)context->ch_layout.u.mask;
        int sampleRate = context->sample_rate;
        int sampleCount = frame->nb_samples;
        int dataSize = av_samples_get_buffer_size(nullptr, channelCount, sampleCount,
                                                  sampleFormat, 1);
        SwrContext *resampleContext;
        if (context->opaque) {
            resampleContext = (SwrContext *)context->opaque;
        } else {
            resampleContext = swr_alloc();
            av_opt_set_int(resampleContext, "in_channel_layout", channelLayout, 0);
            av_opt_set_int(resampleContext, "out_channel_layout", channelLayout, 0);
            av_opt_set_int(resampleContext, "in_sample_rate", sampleRate, 0);
            av_opt_set_int(resampleContext, "out_sample_rate", sampleRate, 0);
            av_opt_set_int(resampleContext, "in_sample_fmt", sampleFormat, 0);
            // The output format is always the requested format.
            av_opt_set_int(resampleContext, "out_sample_fmt",
                           context->request_sample_fmt, 0);
            result = swr_init(resampleContext);
            if (result < 0) {
                logError("swr_init", result);
                av_frame_free(&frame);
                return transformError(result);
            }
            context->opaque = resampleContext;
        }
        int inSampleSize = av_get_bytes_per_sample(sampleFormat);
        int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
        int outSamples = swr_get_out_samples(resampleContext, sampleCount);
        int bufferOutSize = outSampleSize * channelCount * outSamples;
        if (outSize + bufferOutSize > outputSize) {
            LOGE("Output buffer size (%d) too small for output data (%d).",
                 outputSize, outSize + bufferOutSize);
            av_frame_free(&frame);
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }
        result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                             (const uint8_t **)frame->data, frame->nb_samples);
        av_frame_free(&frame);
        if (result < 0) {
            logError("swr_convert", result);
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }
        int available = swr_get_out_samples(resampleContext, 0);
        if (available != 0) {
            LOGE("Expected no samples remaining after resampling, but found %d.",
                 available);
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }
        outputBuffer += bufferOutSize;
        outSize += bufferOutSize;
    }
    return outSize;
}

int transformError(int errorNumber) {
    return errorNumber == AVERROR_INVALIDDATA ? AUDIO_DECODER_ERROR_INVALID_DATA
                                              : AUDIO_DECODER_ERROR_OTHER;
}

void logError(const char *functionName, int errorNumber) {
    char *buffer = (char *)malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
    av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
    LOGE("Error in %s: %s", functionName, buffer);
    free(buffer);
}

void releaseContext(AVCodecContext *context) {
    if (!context) {
        return;
    }
    SwrContext *swrContext;
    if ((swrContext = (SwrContext *)context->opaque)) {
        swr_free(&swrContext);
        context->opaque = nullptr;
    }
    avcodec_free_context(&context);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegAudioDecoder_ffmpegInitialize(JNIEnv *env,
                                                                                    jobject thiz,
                                                                                    jstring codec_name,
                                                                                    jbyteArray extra_data,
                                                                                    jboolean output_float,
                                                                                    jint raw_sample_rate,
                                                                                    jint raw_channel_count) {
    AVCodec *codec = getCodecByName(env, codec_name);
    if (!codec) {
        LOGE("Codec not found.");
        return 0L;
    }
    return (jlong)createContext(env, codec, extra_data, output_float, raw_sample_rate,
                                raw_channel_count);
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegAudioDecoder_ffmpegDecode(JNIEnv *env,
                                                                                jobject thiz,
                                                                                jlong context,
                                                                                jobject input_data,
                                                                                jint input_size,
                                                                                jobject output_data,
                                                                                jint output_size) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    if (!input_data || !output_data) {
        LOGE("Input and output buffers must be non-NULL.");
        return -1;
    }
    if (input_size < 0) {
        LOGE("Invalid input buffer size: %d.", input_size);
        return -1;
    }
    if (output_size < 0) {
        LOGE("Invalid output buffer length: %d", output_size);
        return -1;
    }
    auto *inputBuffer = (uint8_t *)env->GetDirectBufferAddress(input_data);
    auto *outputBuffer = (uint8_t *)env->GetDirectBufferAddress(output_data);
    AVPacket *packet;
    packet = av_packet_alloc();

    if (packet == nullptr) {
        LOGE("audio_decoder_decode_frame: av_packet_alloc failed");
        return -1;
    }

    packet->data = inputBuffer;
    packet->size = input_size;
    int decodedPacket = decodePacket((AVCodecContext *)context, packet, outputBuffer,
                        output_size);
    av_packet_free(&packet);
    return decodedPacket;
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegAudioDecoder_ffmpegGetChannelCount(
        JNIEnv *env, jobject thiz, jlong context) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    return ((AVCodecContext *)context)->ch_layout.nb_channels;
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegAudioDecoder_ffmpegGetSampleRate(JNIEnv *env,
                                                                                       jobject thiz,
                                                                                       jlong context) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    return ((AVCodecContext *)context)->sample_rate;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegAudioDecoder_ffmpegReset(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong jContext,
                                                                               jbyteArray extra_data) {
    auto *context = (AVCodecContext *)jContext;
    if (!context) {
        LOGE("Tried to reset without a context.");
        return 0L;
    }

    AVCodecID codecId = context->codec_id;
    if (codecId == AV_CODEC_ID_TRUEHD) {
        // Release and recreate the context if the codec is TrueHD.
        // TODO: Figure out why flushing doesn't work for this codec.
        releaseContext(context);
        auto *codec = const_cast<AVCodec *>(avcodec_find_decoder(codecId));
        if (!codec) {
            LOGE("Unexpected error finding codec %d.", codecId);
            return 0L;
        }
        auto outputFloat =
                (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
        return (jlong)createContext(env, codec, extra_data, outputFloat,
                /* rawSampleRate= */ -1,
                /* rawChannelCount= */ -1);
    }

    avcodec_flush_buffers(context);
    return (jlong)context;
}
extern "C"
JNIEXPORT void JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegAudioDecoder_ffmpegRelease(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jlong context) {
    if (context) {
        releaseContext((AVCodecContext *)context);
    }
}
extern "C"
JNIEXPORT jstring JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegLibrary_ffmpegGetVersion(JNIEnv *env,
                                                                               jclass clazz) {
    return env->NewStringUTF(LIBAVCODEC_IDENT);
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegLibrary_ffmpegGetInputBufferPaddingSize(
        JNIEnv *env, jclass clazz) {
    return (jint)AV_INPUT_BUFFER_PADDING_SIZE;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegLibrary_ffmpegHasDecoder(JNIEnv *env,
                                                                               jclass clazz,
                                                                               jstring codec_name) {
    return getCodecByName(env, codec_name) != nullptr;
}



static const int VIDEO_DECODER_SUCCESS = 0;
static const int VIDEO_DECODER_ERROR_INVALID_DATA = -1;
static const int VIDEO_DECODER_ERROR_OTHER = -2;
static const int VIDEO_DECODER_ERROR_READ_FRAME = -3;


// YUV plane indices.
const int kPlaneY = 0;
const int kPlaneU = 1;
const int kPlaneV = 2;
const int kMaxPlanes = 3;

// Android YUV format. See:
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12.
const int kImageFormatYV12 = 0x32315659;

struct JniContext {
    ~JniContext() {
        if (native_window) {
            ANativeWindow_release(native_window);
        }
    }

    bool MaybeAcquireNativeWindow(JNIEnv *env, jobject new_surface) {
        if (surface == new_surface) {
            return true;
        }
        if (native_window) {
            ANativeWindow_release(native_window);
        }
        native_window_width = 0;
        native_window_height = 0;
        native_window = ANativeWindow_fromSurface(env, new_surface);
        if (native_window == nullptr) {
            LOGE("kJniStatusANativeWindowError");
            surface = nullptr;
            return false;
        }
        surface = new_surface;
        return true;
    }

    jfieldID data_field{};
    jfieldID yuvPlanes_field{};
    jfieldID yuvStrides_field{};
    jmethodID init_for_private_frame_method{};
    jmethodID init_for_yuv_frame_method{};
    jmethodID init_method{};

    AVCodecContext *codecContext{};

    ANativeWindow *native_window = nullptr;
    jobject surface = nullptr;
    int native_window_width = 0;
    int native_window_height = 0;
};

void CopyPlane(const uint8_t *source, int source_stride, uint8_t *destination,
               int destination_stride, int width, int height) {
    while (height--) {
        memcpy(destination, source, width);
        source += source_stride;
        destination += destination_stride;
    }
}

constexpr int AlignTo16(int value) { return (value + 15) & (~15); }

JniContext *createVideoContext(JNIEnv *env,
                               AVCodec *codec,
                               jbyteArray extraData,
                               jint threads) {
    auto *jniContext = new JniContext();

    AVCodecContext *codecContext = avcodec_alloc_context3(codec);
    if (!codecContext) {
        LOGE("Failed to allocate context.");
        return nullptr;
    }

    if (extraData) {
        jsize size = env->GetArrayLength(extraData);
        codecContext->extradata_size = size;
        codecContext->extradata =
                (uint8_t *) av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
        if (!codecContext->extradata) {
            LOGE("Failed to allocate extradata.");
            releaseContext(codecContext);
            return nullptr;
        }
        env->GetByteArrayRegion(extraData, 0, size, (jbyte *) codecContext->extradata);
    }

    codecContext->thread_count = threads;
    codecContext->err_recognition = AV_EF_IGNORE_ERR;
    int result = avcodec_open2(codecContext, codec, nullptr);
    if (result < 0) {
        logError("avcodec_open2", result);
        releaseContext(codecContext);
        return nullptr;
    }

    jniContext->codecContext = codecContext;

    // Populate JNI References.
    jclass outputBufferClass = env->FindClass(
            "androidx/media3/decoder/VideoDecoderOutputBuffer");
    jniContext->data_field = env->GetFieldID(outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
    jniContext->yuvPlanes_field =
            env->GetFieldID(outputBufferClass, "yuvPlanes", "[Ljava/nio/ByteBuffer;");
    jniContext->yuvStrides_field = env->GetFieldID(outputBufferClass, "yuvStrides", "[I");
    jniContext->init_for_private_frame_method =
            env->GetMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
    jniContext->init_for_yuv_frame_method =
            env->GetMethodID(outputBufferClass, "initForYuvFrame", "(IIIII)Z");
    jniContext->init_method =
            env->GetMethodID(outputBufferClass, "init", "(JILjava/nio/ByteBuffer;)V");

    return jniContext;
}


extern "C"
JNIEXPORT jlong JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegVideoDecoder_ffmpegInitialize(JNIEnv *env, jobject thiz,
                                                                        jstring codec_name,
                                                                        jbyteArray extra_data,
                                                                        jint threads) {
    AVCodec *codec = getCodecByName(env, codec_name);
    if (!codec) {
        LOGE("Codec not found.");
        return 0L;
    }

    return (jlong) createVideoContext(env, codec, extra_data, threads);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegVideoDecoder_ffmpegReset(JNIEnv *env, jobject thiz,
                                                                   jlong jContext) {
    auto *const jniContext = reinterpret_cast<JniContext *>(jContext);
    AVCodecContext *context = jniContext->codecContext;
    if (!context) {
        LOGE("Tried to reset without a context.");
        return 0L;
    }

    avcodec_flush_buffers(context);
    return (jlong) jniContext;
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegVideoDecoder_ffmpegRelease(JNIEnv *env, jobject thiz,
                                                                     jlong jContext) {
    auto *const jniContext = reinterpret_cast<JniContext *>(jContext);
    AVCodecContext *context = jniContext->codecContext;
    if (context) {
        releaseContext(context);
    }
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegVideoDecoder_ffmpegRenderFrame(JNIEnv *env, jobject thiz,
                                                                         jlong jContext,
                                                                         jobject surface,
                                                                         jobject output_buffer,
                                                                         jint displayed_width,
                                                                         jint displayed_height) {
    auto *const jniContext = reinterpret_cast<JniContext *>(jContext);
    if (!jniContext->MaybeAcquireNativeWindow(env, surface)) {
        return VIDEO_DECODER_ERROR_OTHER;
    }

    if (jniContext->native_window_width != displayed_width ||
        jniContext->native_window_height != displayed_height) {
        if (ANativeWindow_setBuffersGeometry(
                jniContext->native_window,
                displayed_width,
                displayed_height,
                kImageFormatYV12)) {
            LOGE("kJniStatusANativeWindowError");
            return VIDEO_DECODER_ERROR_OTHER;
        }
        jniContext->native_window_width = displayed_width;
        jniContext->native_window_height = displayed_height;
    }

    ANativeWindow_Buffer native_window_buffer;
    int result = ANativeWindow_lock(jniContext->native_window, &native_window_buffer, nullptr);
    if (result == -19) {
        // Surface: dequeueBuffer failed (No such device)
        jniContext->surface = nullptr;
        return VIDEO_DECODER_SUCCESS;
    } else if (result || native_window_buffer.bits == nullptr) {
        LOGE("kJniStatusANativeWindowError");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    jobject yuvPlanes_object = env->GetObjectField(output_buffer, jniContext->yuvPlanes_field);
    auto yuvPlanes_array = jobjectArray(yuvPlanes_object);
    jobject yuvPlanesY = env->GetObjectArrayElement(yuvPlanes_array, kPlaneY);
    jobject yuvPlanesU = env->GetObjectArrayElement(yuvPlanes_array, kPlaneU);
    jobject yuvPlanesV = env->GetObjectArrayElement(yuvPlanes_array, kPlaneV);
    auto *planeY = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(yuvPlanesY));
    auto *planeU = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(yuvPlanesU));
    auto *planeV = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(yuvPlanesV));

    jobject yuvStrides_object = env->GetObjectField(output_buffer, jniContext->yuvStrides_field);
    auto *yuvStrides_array = reinterpret_cast<jintArray *>(&yuvStrides_object);

    int *yuvStrides = env->GetIntArrayElements(*yuvStrides_array, nullptr);
    int strideY = yuvStrides[kPlaneY];
    int strideU = yuvStrides[kPlaneU];
    int strideV = yuvStrides[kPlaneV];

    // Y plane
    CopyPlane(reinterpret_cast<const uint8_t *>(planeY),
              strideY,
              reinterpret_cast<uint8_t *>(native_window_buffer.bits),
              native_window_buffer.stride,
              displayed_width,
              displayed_height);

    const int y_plane_size = native_window_buffer.stride * native_window_buffer.height;
    const int32_t native_window_buffer_uv_height = (native_window_buffer.height + 1) / 2;
    const int native_window_buffer_uv_stride = AlignTo16(native_window_buffer.stride / 2);

    // TODO(b/140606738): Handle monochrome videos.

    // V plane
    // Since the format for ANativeWindow is YV12, V plane is being processed
    // before U plane.
    const int v_plane_height = std::min(native_window_buffer_uv_height,
                                        displayed_height);
    CopyPlane(
            reinterpret_cast<const uint8_t *>(planeV),
            strideV,
            reinterpret_cast<uint8_t *>(native_window_buffer.bits) + y_plane_size,
            native_window_buffer_uv_stride, displayed_width,
            v_plane_height);

    const int v_plane_size = v_plane_height * native_window_buffer_uv_stride;

    // U plane
    CopyPlane(
            reinterpret_cast<const uint8_t *>(planeU),
            strideU,
            reinterpret_cast<uint8_t *>(native_window_buffer.bits) +
            y_plane_size + v_plane_size,
            native_window_buffer_uv_stride, displayed_width,
            std::min(native_window_buffer_uv_height,
                     displayed_height));


    env->ReleaseIntArrayElements(*yuvStrides_array, yuvStrides, 0);

    if (ANativeWindow_unlockAndPost(jniContext->native_window)) {
        LOGE("kJniStatusANativeWindowError");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    return VIDEO_DECODER_SUCCESS;
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegVideoDecoder_ffmpegSendPacket(JNIEnv *env, jobject thiz,
                                                                        jlong jContext,
                                                                        jobject encoded_data,
                                                                        jint length,
                                                                        jlong input_time) {
    auto *const jniContext = reinterpret_cast<JniContext *>(jContext);
    AVCodecContext *avContext = jniContext->codecContext;

    auto *inputBuffer = (uint8_t *) env->GetDirectBufferAddress(encoded_data);
    AVPacket packet = *av_packet_alloc();
    packet.data = inputBuffer;
    packet.size = length;
    packet.pts = input_time;

    int result = 0;
    // Queue input data.
    result = avcodec_send_packet(avContext, &packet);
    if (result) {
        logError("avcodec_send_packet", result);
        if (result == AVERROR_INVALIDDATA) {
            // need more data
            return VIDEO_DECODER_ERROR_INVALID_DATA;
        } else if (result == AVERROR(EAGAIN)) {
            // need read frame
            return VIDEO_DECODER_ERROR_READ_FRAME;
        } else {
            return VIDEO_DECODER_ERROR_OTHER;
        }
    }
    return result;
}
extern "C"
JNIEXPORT jint JNICALL
Java_dev_anilbeesetti_libs_ffcodecs_FfmpegVideoDecoder_ffmpegReceiveFrame(JNIEnv *env, jobject thiz,
                                                                          jlong jContext,
                                                                          jint output_mode,
                                                                          jobject output_buffer,
                                                                          jboolean decode_only) {
    auto *const jniContext = reinterpret_cast<JniContext *>(jContext);
    AVCodecContext *avContext = jniContext->codecContext;
    int result = 0;

    AVFrame *frame = av_frame_alloc();
    if (!frame) {
        LOGE("Failed to allocate output frame.");
        return VIDEO_DECODER_ERROR_OTHER;
    }
    result = avcodec_receive_frame(avContext, frame);

    // fail
    if (decode_only || result == AVERROR(EAGAIN)) {
        // This is not an error. The input data was decode-only or no displayable
        // frames are available.
        av_frame_free(&frame);
        return VIDEO_DECODER_ERROR_INVALID_DATA;
    }
    if (result) {
        av_frame_free(&frame);
        logError("avcodec_receive_frame", result);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    // success
    // init time and mode
    env->CallVoidMethod(output_buffer, jniContext->init_method, frame->pts, output_mode, nullptr);

    // init data
    const jboolean init_result = env->CallBooleanMethod(
            output_buffer, jniContext->init_for_yuv_frame_method,
            frame->width,
            frame->height,
            frame->linesize[0], frame->linesize[1],
            0);
    if (env->ExceptionCheck()) {
        // Exception is thrown in Java when returning from the native call.
        return VIDEO_DECODER_ERROR_OTHER;
    }
    if (!init_result) {
        return VIDEO_DECODER_ERROR_OTHER;
    }

    jobject data_object = env->GetObjectField(output_buffer, jniContext->data_field);
    auto *data = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(data_object));
    const int32_t uvHeight = (frame->height + 1) / 2;
    const uint64_t yLength = frame->linesize[0] * frame->height;
    const uint64_t uvLength = frame->linesize[1] * uvHeight;

    // TODO: Support rotate YUV data

    memcpy(data, frame->data[0], yLength);
    memcpy(data + yLength, frame->data[1], uvLength);
    memcpy(data + yLength + uvLength, frame->data[2], uvLength);

    av_frame_free(&frame);

    return result;
}