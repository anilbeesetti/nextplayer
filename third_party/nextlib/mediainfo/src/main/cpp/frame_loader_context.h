#ifndef NEXTPLAYER_FRAME_LOADER_CONTEXT_H
#define NEXTPLAYER_FRAME_LOADER_CONTEXT_H


#include <jni.h>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
}

/**
 * A struct that is stored in a MediaInfo
 * Aggregates necessary pointers to FFmpeg structs.
 */
struct FrameLoaderContext {
    // Root FFmpeg object for the given media.
    AVFormatContext *avFormatContext;
    // Parameters of a video stream.
    AVCodecParameters *parameters;
    // Codec of a video stream.
    const AVCodec *avVideoCodec;
    // And index of a video stream in the avFormatContext.
    int videoStreamIndex;
};

/**
 * Function that converts a pointer to FrameLoaderContext from a int64_t handle.
 *
 * @param handle a pointer to actual FrameLoaderContext struct
 */
FrameLoaderContext *frame_loader_context_from_handle(int64_t handle);

/**
 * Converts a pointer to FrameLoaderContext struct to a long value to be stored in the JVM part.
 *
 * @param frameLoaderContext a pointer to convert
 * @return a converted pointer
 */
int64_t frame_loader_context_to_handle(FrameLoaderContext *frameLoaderContext);

/**
 * Frees the FrameLoaderContext struct.
 *
 * @param handle a pointer to a FrameLoaderContext struct to free
 */
void frame_loader_context_free(int64_t handle);

#endif //NEXTPLAYER_FRAME_LOADER_CONTEXT_H
