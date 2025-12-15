#include <jni.h>
#include <stdio.h>
#include "utils.h"
#include "log.h"
#include "frame_loader_context.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/codec_desc.h>
#include <libavutil/display.h>
}

static char *get_string(AVDictionary *metadata, const char *key) {
    char *result = nullptr;
    AVDictionaryEntry *tag = av_dict_get(metadata, key, nullptr, 0);
    if (tag != nullptr) {
        result = tag->value;
    }
    return result;
}

static char *get_title(AVDictionary *metadata) {
    return get_string(metadata, "title");
}

static char *get_language(AVDictionary *metadata) {
    return get_string(metadata, "language");
}

static void onError(JNIEnv *env, jobject jMediaInfoBuilder) {
    utils_call_instance_method_void(env, jMediaInfoBuilder, fields.MediaInfoBuilder.onErrorID);
}

void onMediaInfoFound(JNIEnv *env, jobject jMediaInfoBuilder, AVFormatContext *avFormatContext) {
    const char *fileFormatName = avFormatContext->iformat->long_name;

    long duration_ms = (long) (avFormatContext->duration * av_q2d(AV_TIME_BASE_Q) * 1000.0);
    jstring jFileFormatName = env->NewStringUTF(fileFormatName);

    utils_call_instance_method_void(env,
                                    jMediaInfoBuilder,
                                    fields.MediaInfoBuilder.onMediaInfoFoundID,
                                    jFileFormatName,
                                    duration_ms);
}

void onVideoStreamFound(JNIEnv *env, jobject jMediaInfoBuilder, AVFormatContext *avFormatContext,
                        int index) {
    AVStream *stream = avFormatContext->streams[index];
    AVCodecParameters *parameters = stream->codecpar;

    auto codecDescriptor = avcodec_descriptor_get(parameters->codec_id);

    int64_t frameLoaderContextHandle = -1;
    auto *decoder = avcodec_find_decoder(parameters->codec_id);
    if (decoder != nullptr) {
        auto *frameLoaderContext = (FrameLoaderContext *) malloc(sizeof(FrameLoaderContext));;
        frameLoaderContext->avFormatContext = avFormatContext;
        frameLoaderContext->parameters = parameters;
        frameLoaderContext->avVideoCodec = decoder;
        frameLoaderContext->videoStreamIndex = index;
        frameLoaderContextHandle = frame_loader_context_to_handle(frameLoaderContext);
    }


    AVRational guessedFrameRate = av_guess_frame_rate(avFormatContext,
                                                      avFormatContext->streams[index],
                                                      nullptr);

    double resultFrameRate =
            guessedFrameRate.den == 0 ? 0.0 : guessedFrameRate.num / (double) guessedFrameRate.den;

    jstring jTitle = env->NewStringUTF(get_title(stream->metadata));
    jstring jCodecName;
    if (codecDescriptor != nullptr) {
        jCodecName = env->NewStringUTF(codecDescriptor->long_name);
    } else {
        jCodecName = env->NewStringUTF("Unknown Codec");
    }
    jstring jLanguage = env->NewStringUTF(get_language(stream->metadata));

    int rotation = 0;
    AVDictionaryEntry *rotateTag = av_dict_get(stream->metadata, "rotate", nullptr, 0);
    if (rotateTag && *rotateTag->value) {
        rotation = atoi(rotateTag->value);
        rotation %= 360;
        if (rotation < 0) rotation += 360;
    }
    uint8_t *displaymatrix = av_stream_get_side_data(stream,
                                                     AV_PKT_DATA_DISPLAYMATRIX,
                                                     nullptr);
    if (displaymatrix) {
        double theta = av_display_rotation_get((int32_t *) displaymatrix);
        rotation = (int) (-theta) % 360;
        if (rotation < 0) rotation += 360;
    }

    utils_call_instance_method_void(env,
                                    jMediaInfoBuilder,
                                    fields.MediaInfoBuilder.onVideoStreamFoundID,
                                    index,
                                    jTitle,
                                    jCodecName,
                                    jLanguage,
                                    stream->disposition,
                                    parameters->bit_rate,
                                    resultFrameRate,
                                    parameters->width,
                                    parameters->height,
                                    rotation,
                                    frameLoaderContextHandle);
}

void onAudioStreamFound(JNIEnv *env, jobject jMediaInfoBuilder, AVFormatContext *avFormatContext,
                        int index) {
    AVStream *stream = avFormatContext->streams[index];
    AVCodecParameters *parameters = stream->codecpar;

    auto codecDescriptor = avcodec_descriptor_get(parameters->codec_id);

    auto avSampleFormat = static_cast<AVSampleFormat>(parameters->format);
    auto jSampleFormat = env->NewStringUTF(av_get_sample_fmt_name(avSampleFormat));
    char chLayoutDescription[128];
    av_channel_layout_describe(&parameters->ch_layout, chLayoutDescription,
                               sizeof(chLayoutDescription));

    jstring jTitle = env->NewStringUTF(get_title(stream->metadata));
    jstring jCodecName;
    if (codecDescriptor != nullptr) {
        jCodecName = env->NewStringUTF(codecDescriptor->long_name);
    } else {
        jCodecName = env->NewStringUTF("Unknown Codec");
    }
    jstring jLanguage = env->NewStringUTF(get_language(stream->metadata));
    jstring jChannelLayout = env->NewStringUTF(chLayoutDescription);

    utils_call_instance_method_void(env,
                                    jMediaInfoBuilder,
                                    fields.MediaInfoBuilder.onAudioStreamFoundID,
                                    index,
                                    jTitle,
                                    jCodecName,
                                    jLanguage,
                                    stream->disposition,
                                    parameters->bit_rate,
                                    jSampleFormat,
                                    parameters->sample_rate,
                                    parameters->ch_layout.nb_channels,
                                    jChannelLayout);
}

void onSubtitleStreamFound(JNIEnv *env, jobject jMediaInfoBuilder, AVFormatContext *avFormatContext,
                           int index) {
    AVStream *stream = avFormatContext->streams[index];
    AVCodecParameters *parameters = stream->codecpar;

    auto codecDescriptor = avcodec_descriptor_get(parameters->codec_id);

    jstring jTitle = env->NewStringUTF(get_title(stream->metadata));
    jstring jCodecName;
    if (codecDescriptor != nullptr) {
        jCodecName = env->NewStringUTF(codecDescriptor->long_name);
    } else {
        jCodecName = env->NewStringUTF("Unknown Codec");
    }
    jstring jLanguage = env->NewStringUTF(get_language(stream->metadata));

    utils_call_instance_method_void(env,
                                    jMediaInfoBuilder,
                                    fields.MediaInfoBuilder.onSubtitleStreamFoundID,
                                    index,
                                    jTitle,
                                    jCodecName,
                                    jLanguage,
                                    stream->disposition);
}

void onChapterFound(JNIEnv *env, jobject jMediaInfoBuilder, AVFormatContext *avFormatContext,
                    int index) {
    AVChapter *chapter = avFormatContext->chapters[index];

    jstring jTitle = env->NewStringUTF(get_title(chapter->metadata));
    double time_base = av_q2d(chapter->time_base);
    long start_ms = (long) (chapter->start * time_base * 1000.0);
    long end_ms = (long) (chapter->end * time_base * 1000.0);

    utils_call_instance_method_void(env,
                                    jMediaInfoBuilder,
                                    fields.MediaInfoBuilder.onChapterFoundID,
                                    index,
                                    jTitle,
                                    start_ms,
                                    end_ms);
}

void media_info_build(JNIEnv *env, jobject jMediaInfoBuilder, const char *uri) {
    AVFormatContext *avFormatContext = nullptr;
    if (int result = avformat_open_input(&avFormatContext, uri, nullptr, nullptr)) {
        LOGE("ERROR Could not open file %s - %s", uri, av_err2str(result));
        onError(env, jMediaInfoBuilder);
        return;
    }

    if (avformat_find_stream_info(avFormatContext, nullptr) < 0) {
        avformat_free_context(avFormatContext);
        LOGE("ERROR Could not get the stream info");
        onError(env, jMediaInfoBuilder);
        return;
    }

    onMediaInfoFound(env, jMediaInfoBuilder, avFormatContext);

    for (int pos = 0; pos < avFormatContext->nb_streams; pos++) {
        AVCodecParameters *parameters = avFormatContext->streams[pos]->codecpar;
        AVMediaType type = parameters->codec_type;
        switch (type) {
            case AVMEDIA_TYPE_VIDEO:
                onVideoStreamFound(env, jMediaInfoBuilder, avFormatContext, pos);
                break;
            case AVMEDIA_TYPE_AUDIO:
                onAudioStreamFound(env, jMediaInfoBuilder, avFormatContext, pos);
                break;
            case AVMEDIA_TYPE_SUBTITLE:
                onSubtitleStreamFound(env, jMediaInfoBuilder, avFormatContext, pos);
                break;
        }
    }

    for (int pos = 0; pos < avFormatContext->nb_chapters; pos++) {
        onChapterFound(env, jMediaInfoBuilder, avFormatContext, pos);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_anilbeesetti_nextlib_mediainfo_MediaInfoBuilder_nativeCreateFromFD(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jint file_descriptor) {
    // Use a seekable path whenever possible. `pipe:` is typically non-seekable and can break
    // frame extraction and duration probing for some formats.
    char fdPath[64];
    sprintf(fdPath, "/proc/self/fd/%d", file_descriptor);

    media_info_build(env, thiz, fdPath);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_anilbeesetti_nextlib_mediainfo_MediaInfoBuilder_nativeCreateFromPath(JNIEnv *env,
                                                                                    jobject thiz,
                                                                                    jstring jFilePath) {
    const char *cFilePath = env->GetStringUTFChars(jFilePath, nullptr);

    media_info_build(env, thiz, cFilePath);

    env->ReleaseStringUTFChars(jFilePath, cFilePath);
}
