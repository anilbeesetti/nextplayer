package io.github.anilbeesetti.nextlib.mediainfo

import android.graphics.Bitmap

class FrameLoader internal constructor(private var frameLoaderContextHandle: Long) {

    fun loadFrameInto(bitmap: Bitmap, durationMillis: Long): Boolean {
        require(frameLoaderContextHandle != -1L)
        return nativeLoadFrame(frameLoaderContextHandle, durationMillis, bitmap)
    }

    fun getFrame(durationMillis: Long): Bitmap? {
        require(frameLoaderContextHandle != -1L)
        return nativeGetFrame(frameLoaderContextHandle, durationMillis)
    }

    fun release() {
        nativeRelease(frameLoaderContextHandle)
        frameLoaderContextHandle = -1
    }

    companion object {
        @JvmStatic
        private external fun nativeRelease(handle: Long)

        @JvmStatic
        private external fun nativeLoadFrame(handle: Long, durationMillis: Long, bitmap: Bitmap): Boolean

        @JvmStatic
        private external fun nativeGetFrame(handle: Long, durationMillis: Long): Bitmap?
    }
}