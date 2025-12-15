package io.github.anilbeesetti.nextlib.mediainfo

import android.graphics.Bitmap

data class MediaInfo(
    val format: String,
    val duration: Long,
    val videoStream: VideoStream?,
    val audioStreams: List<AudioStream>,
    val subtitleStreams: List<SubtitleStream>,
    val chapters: List<Chapter>,
    private val frameLoaderContext: Long?
) {

    private var frameLoader = frameLoaderContext?.let { FrameLoader(frameLoaderContext) }

    val supportsFrameLoading: Boolean = frameLoader != null

    /**
     * Retrieves a video frame as a Bitmap at a specific duration in milliseconds from the video stream.
     *
     * @param durationMillis The timestamp in milliseconds at which to retrieve the video frame.
     *                       If set to -1, the frame will be retrieved at one-third of the video's duration.
     * @return A Bitmap containing the video frame if retrieval is successful, or null if an error occurs.
     */
    fun getFrame(durationMillis: Long = -1): Bitmap? {
        if (videoStream == null) return null
        val bitmap = Bitmap.createBitmap(videoStream.frameWidth.takeIf { it > 0 } ?: 1920, videoStream.frameHeight.takeIf { it > 0 } ?: 1080, Bitmap.Config.ARGB_8888)
        val result = frameLoader?.loadFrameInto(bitmap, durationMillis)
        return if (result == true) bitmap.rotate(videoStream.rotation) else null
    }

    /**
     * Retrieves a video frame as a Bitmap at a specific duration in milliseconds from the video stream.
     *
     * @param durationMillis The timestamp in milliseconds at which to retrieve the video frame.
     *                       If set to -1, the frame will be retrieved at one-third of the video's duration.
     * @return A Bitmap containing the video frame if retrieval is successful, or null if an error occurs.
     */
    fun getFrameAt(durationMillis: Long = -1): Bitmap? {
        if (videoStream == null) return null
        return frameLoader?.getFrame(durationMillis)?.rotate(videoStream.rotation)
    }

    fun release() {
        frameLoader?.release()
        frameLoader = null
    }
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    val matrix = android.graphics.Matrix()
    matrix.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}