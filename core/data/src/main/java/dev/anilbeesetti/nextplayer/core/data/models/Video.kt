package dev.anilbeesetti.nextplayer.core.data.models

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.extensions.getSubtitles
import dev.anilbeesetti.nextplayer.core.common.extensions.getThumbnails
import java.io.File

/**
 * Defines Video item
 * @param id id of the video
 * @param duration duration of the video
 * @param uriString uri string of the video
 * @param displayName display name of the video
 * @param nameWithExtension name with extension of the video
 * @param width width of the video
 * @param height height of the video
 */
data class Video(
    val id: Long,
    val path: String,
    val duration: Long,
    val uriString: String,
    val displayName: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int,
    val size: Long
) {
    val uri: Uri
        get() = Uri.parse(uriString)
    val thumbs: List<File>
        get() = File(path).getThumbnails()
    val subtitleTracks: List<File>
        get() = File(path).getSubtitles()
    val subtitleExtensions: List<String>
        get() = subtitleTracks.map { it.extension }.distinct()
}
