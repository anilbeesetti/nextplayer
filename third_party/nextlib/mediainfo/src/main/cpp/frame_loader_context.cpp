#include "frame_loader_context.h"

FrameLoaderContext *frame_loader_context_from_handle(int64_t handle) {
    return reinterpret_cast<FrameLoaderContext *>(handle);
}

int64_t frame_loader_context_to_handle(FrameLoaderContext *frameLoaderContext) {
    return reinterpret_cast<int64_t>(frameLoaderContext);
}

void frame_loader_context_free(int64_t handle) {
    auto *frameLoaderContext = frame_loader_context_from_handle(handle);
    auto *avFormatContext = frameLoaderContext->avFormatContext;

    avformat_close_input(&avFormatContext);
    free(frameLoaderContext);
}