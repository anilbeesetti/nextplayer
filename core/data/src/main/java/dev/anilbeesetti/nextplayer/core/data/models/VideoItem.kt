package dev.anilbeesetti.nextplayer.core.data.models

import android.net.Uri

/**
 * Defines Video item
 * @param id id of the video
 * @param duration duration of the video
 * @param contentUri content uri of the video
 * @param displayName display name of the video
 * @param nameWithExtension name with extension of the video
 * @param width width of the video
 * @param height height of the video
 */
data class VideoItem(
    val id: Long,
    val duration: Int,
    val contentUri: Uri,
    val displayName: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int
)