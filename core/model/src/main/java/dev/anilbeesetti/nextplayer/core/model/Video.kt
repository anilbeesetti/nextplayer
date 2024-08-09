package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable
import java.util.Date

data class Video(
    val id: Long,
    val path: String,
    val parentPath: String = "",
    val duration: Long,
    val uriString: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val playbackPosition: Long = 200,
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

    val displayName: String = nameWithExtension.substringBeforeLast(".")
    val playedPercentage: Float = playbackPosition.toFloat() / duration.toFloat()

    companion object {
        val sample = Video(
            id = 8,
            path = "/storage/emulated/0/Download/Avengers Endgame (2019) BluRay x264.mp4",
            parentPath = "/storage/emulated/0/Download",
            uriString = "",
            nameWithExtension = "Avengers Endgame (2019) BluRay x264.mp4",
            duration = 1000,
            width = 1920,
            height = 1080,
            size = 1000,
            formattedDuration = "29.36",
            formattedFileSize = "320KB",
            playbackPosition = 200,
        )
    }
}

fun List<Video>.recentPlayed(): Video? =
    filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt?.time }.firstOrNull()
