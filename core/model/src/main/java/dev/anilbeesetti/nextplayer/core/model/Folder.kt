package dev.anilbeesetti.nextplayer.core.model

data class Folder(
    val name: String,
    val path: String,
    val mediaSize: Long,
    val mediaCount: Int,
    val dateModified: Long,
    val formattedMediaSize: String = ""
) {

    companion object {
        val sample = Folder(
            name = "Folder 1",
            path = "/storage/emulated/0/DCIM/Camera/Live Photos",
            mediaSize = 1024,
            mediaCount = 1,
            dateModified = 2000,
            formattedMediaSize = "1KB"
        )
    }
}
