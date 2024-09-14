package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

data class Folder(
    val name: String,
    val path: String,
    val dateModified: Long,
    val parentPath: String? = null,
    val formattedMediaSize: String = "",
    val mediaList: List<Video> = emptyList(),
    val folderList: List<Folder> = emptyList(),
) : Serializable {

    val mediaSize: Long = mediaList.sumOf { it.size } + folderList.sumOf { it.mediaSize }
    val allMediaList: List<Video> = mediaList + folderList.flatMap { it.allMediaList }
    val recentlyPlayedVideo: Video? = allMediaList.recentPlayed()
    val firstVideo: Video? = allMediaList.firstOrNull()

    fun isRecentlyPlayedVideo(video: Video?): Boolean {
        if (recentlyPlayedVideo == null) return false
        if (video == null) return false
        return video.path == recentlyPlayedVideo.path
    }

    companion object {
        val rootFolder = Folder(
            name = "Root",
            path = "/",
            dateModified = System.currentTimeMillis(),
        )

        val sample = Folder(
            name = "Folder 1",
            path = "/storage/emulated/0/DCIM/Camera/Live Photos",
            dateModified = 2000,
            formattedMediaSize = "1KB",
        )
    }
}
