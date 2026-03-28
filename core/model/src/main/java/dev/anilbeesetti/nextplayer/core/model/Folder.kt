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
    companion object {
        val sample = Folder(
            name = "Folder 1",
            path = "/storage/emulated/0/DCIM/Camera/Live Photos",
            dateModified = 2000,
        )
    }
}

fun List<Folder>.findClosestFolder(videoPath: String): Folder? {
    val videoDirectory = videoPath.substringBeforeLast("/") + "/"

    return filter { folder ->
        videoDirectory.startsWith(folder.path)
    }.maxByOrNull { folder ->
        folder.path.length
    }
}
