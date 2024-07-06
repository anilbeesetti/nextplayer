package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable
import java.util.Date

data class Video(
    val id: Long,
    val path: String,
    val parentPath: String = "",
    val duration: Long,
    val uriString: String,
    val displayName: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val dateModified: Long = 0,
    val formattedDuration: String = "",
    val formattedFileSize: String = "",
    val format: String? = null,
    val thumbnailPath: String? = null,
    val lastPlayedAt: Date? = null,
    val videoStream: VideoStreamInfo? = null,
    val audioStreams: List<AudioStreamInfo> = emptyList(),
    val subtitleStreams: List<SubtitleStreamInfo> = emptyList(),
) : Serializable {

    companion object {
        val sample = Video(
            id = 8,
            path = "/storage/emulated/0/Download/Avengers Endgame (2019) BluRay x264.mp4",
            parentPath = "/storage/emulated/0/Download",
            uriString = "",
            nameWithExtension = "Avengers Endgame (2019) BluRay x264.mp4",
            duration = 1000,
            displayName = "Avengers Endgame (2019) BluRay x264",
            width = 1920,
            height = 1080,
            size = 1000,
            formattedDuration = "29.36",
            formattedFileSize = "320KB",
        )
    }
}
