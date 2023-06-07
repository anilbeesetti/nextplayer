package dev.anilbeesetti.nextplayer.core.model

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
    val dateModified: Long,
    val formattedDuration: String,
    val formattedFileSize: String
) {

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
            dateModified = 2347234789,
            formattedDuration = "29.36",
            formattedFileSize = "320KB"
        )
    }
}
