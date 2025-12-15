
#include <android/log.h>
#include <jni.h>
#include <cstdlib>
#include <android/native_window_jni.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include "ffcommon.h"

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

# define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;

static jmethodID growOutputBufferMethod;

static inline uint16_t readLe16(const uint8_t *p) {
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static inline uint32_t readLe32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) |
           ((uint32_t)p[3] << 24);
}

static bool isWmaCodecId(enum AVCodecID codecId) {
    switch (codecId) {
        case AV_CODEC_ID_WMAV1:
        case AV_CODEC_ID_WMAV2:
        case AV_CODEC_ID_WMAPRO:
        case AV_CODEC_ID_WMALOSSLESS:
            return true;
        default:
            return false;
    }
}

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

struct GrowOutputBufferCallback {
    uint8_t *operator()(int requiredSize) const;

    JNIEnv *env;
    jobject thiz;
    jobject decoderOutputBuffer;
};

uint8_t *GrowOutputBufferCallback::operator()(int requiredSize) const {
    jobject newOutputData = env->CallObjectMethod(thiz, growOutputBufferMethod, decoderOutputBuffer, requiredSize);
    if (env->ExceptionCheck()) {
        LOGE("growOutputBuffer() failed");
        env->ExceptionDescribe();
        return nullptr;
    }
    return static_cast<uint8_t *>(env->GetDirectBufferAddress(newOutputData));
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
                (uint8_t *) av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
        if (!context->extradata) {
            LOGE("Failed to allocate extra data.");
            releaseContext(context);
            return nullptr;
        }
        env->GetByteArrayRegion(extraData, 0, size, (jbyte *) context->extradata);
    }

    // Some decoders (notably WMA) require these fields to be set on the AVCodecContext and do not
    // always derive them from extradata. Use the container-provided values from Media3's Format.
    if (rawSampleRate > 0) {
        context->sample_rate = rawSampleRate;
    }
    if (rawChannelCount > 0) {
        context->ch_layout.nb_channels = rawChannelCount;
        av_channel_layout_default(&context->ch_layout, rawChannelCount);
    }

    // For WMA codecs, init data is prefixed with [block_align u16][bit_rate u32], and the rest is
    // the real codec extradata expected by FFmpeg's decoder.
    if (isWmaCodecId(context->codec_id) && context->extradata && context->extradata_size >= 6) {
        const uint8_t *pref = context->extradata;
        uint16_t blockAlign = readLe16(pref);
        uint32_t bitRate = readLe32(pref + 2);

        if (context->block_align <= 0 && blockAlign > 0) {
            context->block_align = blockAlign;
        }
        if (context->bit_rate <= 0 && bitRate > 0) {
            context->bit_rate = bitRate;
        }

        // Strip the prefix so the decoder sees the expected extradata bytes.
        int newSize = context->extradata_size - 6;
        if (newSize > 0) {
            memmove(context->extradata, context->extradata + 6, newSize);
            memset(context->extradata + newSize, 0, AV_INPUT_BUFFER_PADDING_SIZE);
            context->extradata_size = newSize;
        } else {
            context->extradata_size = 0;
        }
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
                 uint8_t *outputBuffer, int outputSize, GrowOutputBufferCallback growBuffer) {
    int result;
    bool sentPacket = false;
    bool hadSendEagain = false;

    // Queue input data. If the decoder input queue is full, drain output and retry once.
    result = avcodec_send_packet(context, packet);
    if (result == AVERROR(EAGAIN)) {
        hadSendEagain = true;
    } else if (result) {
        logError("audio avcodec_send_packet", result);
        if (result == AVERROR_INVALIDDATA || result == AVERROR(EINVAL) || result == AVERROR(EPERM)) {
            avcodec_flush_buffers(context);
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }
        return transformError(result);
    } else {
        sentPacket = true;
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
                if (hadSendEagain && !sentPacket) {
                    // We've drained once, retry sending the packet now.
                    int sendRes = avcodec_send_packet(context, packet);
                    if (sendRes == 0) {
                        sentPacket = true;
                        hadSendEagain = false;
                        continue;
                    }
                    if (sendRes != AVERROR(EAGAIN)) {
                        logError("audio avcodec_send_packet", sendRes);
                        if (sendRes == AVERROR_INVALIDDATA || sendRes == AVERROR(EINVAL) || sendRes == AVERROR(EPERM)) {
                            avcodec_flush_buffers(context);
                            return AUDIO_DECODER_ERROR_INVALID_DATA;
                        }
                        return transformError(sendRes);
                    }
                }
                break;
            }
            logError("avcodec_receive_frame", result);
            return transformError(result);
        }

        // Resample output.
        AVSampleFormat sampleFormat = context->sample_fmt;
        int channelCount = context->ch_layout.nb_channels;
        int channelLayout = (int) context->ch_layout.u.mask;
        int sampleRate = context->sample_rate;
        int sampleCount = frame->nb_samples;
        int dataSize = av_samples_get_buffer_size(nullptr, channelCount, sampleCount,
                                                  sampleFormat, 1);
        SwrContext *resampleContext;
        if (context->opaque) {
            resampleContext = (SwrContext *) context->opaque;
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
            LOGD(
                    "Output buffer size (%d) too small for output data (%d), "
                    "reallocating buffer.",
                    outputSize, outSize + bufferOutSize);
            outputSize = outSize + bufferOutSize;
            outputBuffer = growBuffer(outputSize);
            if (!outputBuffer) {
                LOGE("Failed to reallocate output buffer.");
                av_frame_free(&frame);
                return AUDIO_DECODER_ERROR_OTHER;
            }
        }
        result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                             (const uint8_t **) frame->data, frame->nb_samples);
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

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffdecoder_FfmpegAudioDecoder_ffmpegInitialize(JNIEnv *env,
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
    jclass clazz = env->FindClass("io/github/anilbeesetti/nextlib/media3ext/ffdecoder/FfmpegAudioDecoder");
    growOutputBufferMethod = env->GetMethodID(clazz, "growOutputBuffer","(Landroidx/media3/decoder/SimpleDecoderOutputBuffer;I)Ljava/nio/ByteBuffer;");
    return (jlong) createContext(env, codec, extra_data, output_float, raw_sample_rate,
                                 raw_channel_count);
}

extern "C"
JNIEXPORT jint JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffdecoder_FfmpegAudioDecoder_ffmpegDecode(JNIEnv *env,
                                                                    jobject thiz,
                                                                    jlong context,
                                                                    jobject input_data,
                                                                    jint input_size,
                                                                    jobject decoderOutputBuffer,
                                                                    jobject output_data,
                                                                    jint output_size) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    if (!input_data || !decoderOutputBuffer || !output_data) {
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
    auto *inputBuffer = (uint8_t *) env->GetDirectBufferAddress(input_data);
    auto *outputBuffer = (uint8_t *) env->GetDirectBufferAddress(output_data);
    AVPacket *packet;
    packet = av_packet_alloc();

    if (packet == nullptr) {
        LOGE("audio_decoder_decode_frame: av_packet_alloc failed");
        return -1;
    }

    if (input_size > 0) {
        // Create a refcounted packet (with padding) so codecs can keep references as needed.
        int allocResult = av_new_packet(packet, input_size);
        if (allocResult < 0) {
            logError("av_new_packet", allocResult);
            av_packet_free(&packet);
            return -1;
        }
        memcpy(packet->data, inputBuffer, input_size);
        packet->size = input_size;
    } else {
        packet->data = nullptr;
        packet->size = 0;
    }
    packet->pts = AV_NOPTS_VALUE;
    packet->dts = AV_NOPTS_VALUE;
    packet->pos = -1;
    int decodedPacket = decodePacket((AVCodecContext *) context, packet, outputBuffer,
                                     output_size, GrowOutputBufferCallback{env, thiz, decoderOutputBuffer});
    av_packet_free(&packet);
    return decodedPacket;
}

extern "C"
JNIEXPORT jint JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffdecoder_FfmpegAudioDecoder_ffmpegGetChannelCount(
        JNIEnv *env, jobject thiz, jlong context) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    return ((AVCodecContext *) context)->ch_layout.nb_channels;
}

extern "C"
JNIEXPORT jint JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffdecoder_FfmpegAudioDecoder_ffmpegGetSampleRate(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jlong context) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    return ((AVCodecContext *) context)->sample_rate;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffdecoder_FfmpegAudioDecoder_ffmpegReset(JNIEnv *env,
                                                                   jobject thiz,
                                                                   jlong jContext,
                                                                   jbyteArray extra_data) {
    auto *context = (AVCodecContext *) jContext;
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
                (jboolean) (context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
        return (jlong) createContext(env, codec, extra_data, outputFloat,
                /* rawSampleRate= */ -1,
                /* rawChannelCount= */ -1);
    }

    avcodec_flush_buffers(context);
    return (jlong) context;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffdecoder_FfmpegAudioDecoder_ffmpegRelease(JNIEnv *env,
                                                                     jobject thiz,
                                                                     jlong context) {
    if (context) {
        releaseContext((AVCodecContext *) context);
    }
}
