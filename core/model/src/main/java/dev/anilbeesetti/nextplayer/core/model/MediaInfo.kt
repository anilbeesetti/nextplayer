package dev.anilbeesetti.nextplayer.core.model

data class MediaInfo(
    val video: Video,
    val videoStream: VideoStreamInfo? = null,
    val audioStreams: List<AudioStreamInfo> = emptyList(),
    val subtitleStreams: List<SubtitleStreamInfo> = emptyList()
)
