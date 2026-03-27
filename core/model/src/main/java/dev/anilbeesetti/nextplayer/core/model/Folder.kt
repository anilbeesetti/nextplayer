package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

data class Folder(
    val name: String,
    val path: String,
    val dateModified: Long,
    val parentPath: String? = null,
    val formattedMediaSize: String = "",
    val totalSize: Long = 0,
    val totalDuration: Long = 0,
    val videosCount: Int = 0,
    val foldersCount: Int = 0,
    val mediaList: List<Video> = emptyList(),
    val folderList: List<Folder> = emptyList(),
) : Serializable {
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
