#include <jni.h>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/codec_desc.h>
#include <libavutil/avutil.h>
#include <libavutil/dict.h>
#include <libavutil/opt.h>
}

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>
#include <cstring>

namespace {

static constexpr int64_t kNoPtsValue = AV_NOPTS_VALUE;
static constexpr int64_t kUsPerSecond = 1000000;
static constexpr AVRational kTimeBaseUs = {1, (int)kUsPerSecond};

// Mirrors androidx.media3.common.C constants we need.
static constexpr int kTrackTypeAudio = 1; // C.TRACK_TYPE_AUDIO
static constexpr int kTrackTypeVideo = 2; // C.TRACK_TYPE_VIDEO

static constexpr int kBufferFlagKeyFrame = 1; // C.BUFFER_FLAG_KEY_FRAME

static void writeLe16(uint8_t *dst, uint16_t v) {
    dst[0] = (uint8_t)(v & 0xFF);
    dst[1] = (uint8_t)((v >> 8) & 0xFF);
}

static void writeLe32(uint8_t *dst, uint32_t v) {
    dst[0] = (uint8_t)(v & 0xFF);
    dst[1] = (uint8_t)((v >> 8) & 0xFF);
    dst[2] = (uint8_t)((v >> 16) & 0xFF);
    dst[3] = (uint8_t)((v >> 24) & 0xFF);
}

static bool isWmaCodec(AVCodecID codecId) {
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

static std::vector<uint8_t> buildWmaInitDataPrefix(const AVCodecParameters *par) {
    // FFmpeg's WMAV1/2 decoder expects AVCodecContext.extradata to be only the codec-specific bytes
    // (the tail of WAVEFORMATEX), and requires block_align and bit_rate to be set on AVCodecContext.
    //
    // Media3's FFmpeg decoder JNI init doesn't have access to codecpar, so we prefix those values
    // into initializationData and strip it back out in the decoder init.
    //
    // Prefix layout (little-endian):
    //  WORD block_align
    //  DWORD bit_rate
    uint16_t blockAlign = (uint16_t)(par->block_align > 0 ? par->block_align : 0);
    uint32_t bitRate = (uint32_t)(par->bit_rate > 0 ? par->bit_rate : 0);
    std::vector<uint8_t> prefix(6);
    writeLe16(&prefix[0], blockAlign);
    writeLe32(&prefix[2], bitRate);
    return prefix;
}

struct DemuxerContext {
    AVFormatContext *formatCtx = nullptr;
    std::vector<int> streamIndices;
    std::unordered_map<int, int> streamIndexToTrackIndex;
};

static int64_t rescaleToUs(int64_t ts, const AVRational &timeBase) {
    return av_rescale_q(ts, timeBase, kTimeBaseUs);
}

static std::string codecIdToSampleMimeType(AVCodecID codecId, AVMediaType mediaType) {
    if (mediaType == AVMEDIA_TYPE_VIDEO) {
        switch (codecId) {
            case AV_CODEC_ID_VC1:
                return "video/wvc1";
            case AV_CODEC_ID_WMV3:
                return "video/x-ms-wmv3";
            case AV_CODEC_ID_WMV2:
                return "video/x-ms-wmv2";
            case AV_CODEC_ID_MSMPEG4V3:
                return "video/x-ms-msmpeg4v3";
            default:
                return "video/x-unknown";
        }
    }

    if (mediaType == AVMEDIA_TYPE_AUDIO) {
        switch (codecId) {
            case AV_CODEC_ID_WMAV1:
                return "audio/x-ms-wmav1";
            case AV_CODEC_ID_WMAV2:
                return "audio/x-ms-wmav2";
            case AV_CODEC_ID_WMAPRO:
                return "audio/x-ms-wmapro";
            case AV_CODEC_ID_WMALOSSLESS:
                return "audio/x-ms-wmalossless";
            default:
                return "audio/x-unknown";
        }
    }

    return "application/octet-stream";
}

static jobject makeTrackInfo(JNIEnv *env, const AVStream *stream, int trackType) {
    const AVCodecParameters *par = stream->codecpar;
    const AVCodecDescriptor *desc = avcodec_descriptor_get(par->codec_id);
    const char *codecName = desc ? desc->name : avcodec_get_name(par->codec_id);

    std::string mime = codecIdToSampleMimeType(par->codec_id, (AVMediaType)par->codec_type);

    jclass cls = env->FindClass("io/github/anilbeesetti/nextlib/media3ext/ffextractor/FfmpegTrackInfo");
    jmethodID ctor = env->GetMethodID(
            cls,
            "<init>",
            "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIIII[B)V");

    jstring jMime = env->NewStringUTF(mime.c_str());
    jstring jCodecs = codecName ? env->NewStringUTF(codecName) : nullptr;

    jstring jLang = nullptr;
    if (stream->metadata != nullptr) {
        AVDictionaryEntry *lang = av_dict_get(stream->metadata, "language", nullptr, 0);
        if (lang && lang->value) {
            jLang = env->NewStringUTF(lang->value);
        }
    }

    int width = 0;
    int height = 0;
    int channelCount = 0;
    int sampleRate = 0;
    int averageBitrate = (int)par->bit_rate;
    int rotationDegrees = 0;

    if (trackType == kTrackTypeVideo) {
        width = par->width;
        height = par->height;
        // rotation may be stored in side_data, ignore for now (0).
    } else if (trackType == kTrackTypeAudio) {
        channelCount = par->ch_layout.nb_channels;
        sampleRate = par->sample_rate;
    }

    jbyteArray jExtra = nullptr;
    if (trackType == kTrackTypeAudio && isWmaCodec(par->codec_id)) {
        std::vector<uint8_t> prefix = buildWmaInitDataPrefix(par);
        int codecExtraSize = (par->extradata != nullptr && par->extradata_size > 0) ? par->extradata_size : 0;
        std::vector<uint8_t> initData(prefix.size() + codecExtraSize);
        memcpy(initData.data(), prefix.data(), prefix.size());
        if (codecExtraSize > 0) {
            memcpy(initData.data() + prefix.size(), par->extradata, codecExtraSize);
        }
        jExtra = env->NewByteArray((jsize)initData.size());
        env->SetByteArrayRegion(
                jExtra,
                0,
                (jsize)initData.size(),
                reinterpret_cast<const jbyte *>(initData.data()));
    } else if (par->extradata != nullptr && par->extradata_size > 0) {
        jExtra = env->NewByteArray(par->extradata_size);
        env->SetByteArrayRegion(
                jExtra,
                0,
                par->extradata_size,
                reinterpret_cast<const jbyte *>(par->extradata));
    }

    jobject obj = env->NewObject(
            cls,
            ctor,
            trackType,
            jMime,
            jCodecs,
            jLang,
            width,
            height,
            channelCount,
            sampleRate,
            averageBitrate,
            rotationDegrees,
            jExtra);

    return obj;
}

static jobject makePacket(JNIEnv *env, int trackIndex, const AVStream *stream, const AVPacket *pkt) {
    jclass cls = env->FindClass("io/github/anilbeesetti/nextlib/media3ext/ffextractor/FfmpegPacket");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(IJI[B)V");

    int64_t pts = pkt->pts != kNoPtsValue ? pkt->pts : pkt->dts;
    int64_t timeUs = (pts != kNoPtsValue) ? rescaleToUs(pts, stream->time_base) : 0;

    int flags = 0;
    if ((pkt->flags & AV_PKT_FLAG_KEY) != 0) {
        flags |= kBufferFlagKeyFrame;
    }

    jbyteArray data = nullptr;
    if (pkt->data != nullptr && pkt->size > 0) {
        data = env->NewByteArray(pkt->size);
        env->SetByteArrayRegion(data, 0, pkt->size, reinterpret_cast<const jbyte *>(pkt->data));
    }

    return env->NewObject(cls, ctor, trackIndex, (jlong)timeUs, (jint)flags, data);
}

static DemuxerContext *fromHandle(jlong handle) {
    return reinterpret_cast<DemuxerContext *>(handle);
}

static jlong toHandle(DemuxerContext *ctx) {
    return reinterpret_cast<jlong>(ctx);
}

static void closeContext(DemuxerContext *ctx) {
    if (!ctx) return;
    if (ctx->formatCtx) {
        AVFormatContext *fmt = ctx->formatCtx;
        avformat_close_input(&fmt);
        ctx->formatCtx = nullptr;
    }
    delete ctx;
}

static jlong openWithUrl(const char *url) {
    auto *ctx = new DemuxerContext();
    avformat_network_init();

    AVFormatContext *fmt = nullptr;
    if (avformat_open_input(&fmt, url, nullptr, nullptr) < 0) {
        delete ctx;
        return 0;
    }
    if (avformat_find_stream_info(fmt, nullptr) < 0) {
        avformat_close_input(&fmt);
        delete ctx;
        return 0;
    }

    ctx->formatCtx = fmt;

    for (unsigned int i = 0; i < fmt->nb_streams; i++) {
        AVStream *stream = fmt->streams[i];
        AVCodecParameters *par = stream->codecpar;
        if (par->codec_type != AVMEDIA_TYPE_AUDIO && par->codec_type != AVMEDIA_TYPE_VIDEO) {
            continue;
        }
        int trackIndex = (int)ctx->streamIndices.size();
        ctx->streamIndices.push_back((int)i);
        ctx->streamIndexToTrackIndex[(int)i] = trackIndex;
    }

    return toHandle(ctx);
}

static std::string procFdPath(int fd) {
    return "/proc/self/fd/" + std::to_string(fd);
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeOpenFromUrl(
        JNIEnv *env, jobject thiz, jstring jUrl) {
    const char *url = env->GetStringUTFChars(jUrl, nullptr);
    jlong handle = openWithUrl(url);
    env->ReleaseStringUTFChars(jUrl, url);
    return handle;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeOpenFromPath(
        JNIEnv *env, jobject thiz, jstring jPath) {
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    jlong handle = openWithUrl(path);
    env->ReleaseStringUTFChars(jPath, path);
    return handle;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeOpenFromFd(
        JNIEnv *env, jobject thiz, jint fd) {
    std::string path = procFdPath(fd);
    return openWithUrl(path.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeGetTrackCount(
        JNIEnv *env, jobject thiz, jlong handle) {
    DemuxerContext *ctx = fromHandle(handle);
    if (!ctx || !ctx->formatCtx) return 0;
    return (jint)ctx->streamIndices.size();
}

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeGetDurationUs(
        JNIEnv *env, jobject thiz, jlong handle) {
    DemuxerContext *ctx = fromHandle(handle);
    if (!ctx || !ctx->formatCtx) return (jlong)-9223372036854775807LL; // C.TIME_UNSET
    if (ctx->formatCtx->duration == kNoPtsValue) return (jlong)-9223372036854775807LL;
    return (jlong)rescaleToUs(ctx->formatCtx->duration, AV_TIME_BASE_Q);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeGetTrackInfo(
        JNIEnv *env, jobject thiz, jlong handle, jint trackIndex) {
    DemuxerContext *ctx = fromHandle(handle);
    if (!ctx || !ctx->formatCtx) return nullptr;
    if (trackIndex < 0 || trackIndex >= (jint)ctx->streamIndices.size()) return nullptr;

    int streamIndex = ctx->streamIndices[trackIndex];
    AVStream *stream = ctx->formatCtx->streams[streamIndex];

    int trackType = (stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) ? kTrackTypeVideo : kTrackTypeAudio;
    return makeTrackInfo(env, stream, trackType);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeReadPacket(
        JNIEnv *env, jobject thiz, jlong handle) {
    DemuxerContext *ctx = fromHandle(handle);
    if (!ctx || !ctx->formatCtx) return nullptr;

    AVPacket *pkt = av_packet_alloc();
    if (!pkt) return nullptr;

    while (true) {
        int result = av_read_frame(ctx->formatCtx, pkt);
        if (result < 0) {
            av_packet_free(&pkt);
            return nullptr;
        }
        auto it = ctx->streamIndexToTrackIndex.find(pkt->stream_index);
        if (it == ctx->streamIndexToTrackIndex.end()) {
            av_packet_unref(pkt);
            continue;
        }

        int trackIndex = it->second;
        AVStream *stream = ctx->formatCtx->streams[pkt->stream_index];
        jobject packetObj = makePacket(env, trackIndex, stream, pkt);
        av_packet_free(&pkt);
        return packetObj;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeSeekToUs(
        JNIEnv *env, jobject thiz, jlong handle, jlong timeUs) {
    DemuxerContext *ctx = fromHandle(handle);
    if (!ctx || !ctx->formatCtx) return;

    // When stream_index == -1, timestamp is in AV_TIME_BASE units.
    av_seek_frame(ctx->formatCtx, -1, (int64_t)timeUs, AVSEEK_FLAG_BACKWARD);
    avformat_flush(ctx->formatCtx);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_anilbeesetti_nextlib_media3ext_ffextractor_FfmpegAsfExtractor_nativeRelease(
        JNIEnv *env, jobject thiz, jlong handle) {
    DemuxerContext *ctx = fromHandle(handle);
    closeContext(ctx);
}
