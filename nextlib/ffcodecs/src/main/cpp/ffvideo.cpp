
#include <android/log.h>
#include <jni.h>
#include <cstdlib>
#include <android/native_window_jni.h>
#include <algorithm>
#include "ffcommon.h"

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
#include <libswscale/swscale.h>
}

#define ALIGN(x, a) (((x) + ((a) - 1)) & ~((a) - 1))

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
    SwsContext *swsContext{};

    ANativeWindow *native_window = nullptr;
    jobject surface = nullptr;
    int native_window_width = 0;
    int native_window_height = 0;
};

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
        sws_freeContext(jniContext->swsContext);
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


        // Initializing swsContext with AV_PIX_FMT_YUV420P, which is equivalent to YV12.
        // The only difference is the order of the u and v planes.
        SwsContext *swsContext = sws_getContext(displayed_width, displayed_height,
                                                jniContext->codecContext->pix_fmt,
                                                displayed_width, displayed_height,
                                                AV_PIX_FMT_YUV420P,
                                                SWS_BILINEAR, nullptr, nullptr, nullptr);

        if (!swsContext) {
            LOGE("Failed to allocate swsContext.");
            return VIDEO_DECODER_ERROR_OTHER;
        }
        jniContext->swsContext = swsContext;
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

    // src planes from VideoDecoderOutputBuffer
    jobject yuvPlanes_object = env->GetObjectField(output_buffer, jniContext->yuvPlanes_field);
    auto yuvPlanes_array = jobjectArray(yuvPlanes_object);
    jobject yuvPlanesY = env->GetObjectArrayElement(yuvPlanes_array, kPlaneY);
    jobject yuvPlanesU = env->GetObjectArrayElement(yuvPlanes_array, kPlaneU);
    jobject yuvPlanesV = env->GetObjectArrayElement(yuvPlanes_array, kPlaneV);
    auto *planeY = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(yuvPlanesY));
    auto *planeU = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(yuvPlanesU));
    auto *planeV = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(yuvPlanesV));

    // src strides from VideoDecoderOutputBuffer
    jobject yuvStrides_object = env->GetObjectField(output_buffer, jniContext->yuvStrides_field);
    auto *yuvStrides_array = reinterpret_cast<jintArray *>(&yuvStrides_object);
    int *yuvStrides = env->GetIntArrayElements(*yuvStrides_array, nullptr);
    int strideY = yuvStrides[kPlaneY];
    int strideU = yuvStrides[kPlaneU];
    int strideV = yuvStrides[kPlaneV];


    const int y_plane_size = native_window_buffer.stride * native_window_buffer.height;
    const int32_t native_window_buffer_uv_height = (native_window_buffer.height + 1) / 2;
    const int native_window_buffer_uv_stride = ALIGN(native_window_buffer.stride / 2, 16);
    const int v_plane_height = std::min(native_window_buffer_uv_height, displayed_height);
    const int v_plane_size = v_plane_height * native_window_buffer_uv_stride;


    // src data with swapped u and v planes
    uint8_t *src[3] = {planeY, planeV, planeU};
    int src_stride[3] = {strideY, strideV, strideU};


    // dest data
    uint8_t *dest[3] = {reinterpret_cast<uint8_t *>(native_window_buffer.bits),
                        reinterpret_cast<uint8_t *>(native_window_buffer.bits) + y_plane_size,
                        reinterpret_cast<uint8_t *>(native_window_buffer.bits) + y_plane_size +
                        v_plane_size};
    int dest_stride[3] = {native_window_buffer.stride, native_window_buffer_uv_stride,
                          native_window_buffer_uv_stride};


    //Perform color space conversion using sws_scale.
    //Convert the source data (src) with specified strides (src_stride) and displayed height,
    //and store the result in the destination data (dest) with corresponding strides (dest_stride).
    sws_scale(jniContext->swsContext, src, src_stride,
              0, displayed_height,
              dest, dest_stride);

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