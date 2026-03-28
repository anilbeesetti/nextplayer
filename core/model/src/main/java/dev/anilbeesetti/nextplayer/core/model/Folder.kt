package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

data class Folder(
    val name: String,
    val path: String,
    val dateModified: Long,
    val parentPath: String? = null,
    val totalSize: Long = 0,
    val totalDuration: Long = 0,
    val videosCount: Int = 0,
    val foldersCount: Int = 0,
) : Serializable {
    // TODO
    val recentlyPlayedVideo: Video? = null
    // TODO
    val firstVideo: Video? = null

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
        )
    }
}
