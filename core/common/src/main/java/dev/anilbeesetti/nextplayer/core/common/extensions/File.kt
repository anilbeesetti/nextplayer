package dev.anilbeesetti.nextplayer.core.common.extensions

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun File.getSubtitles(): List<File> = withContext(Dispatchers.IO) {
    val mediaName = this@getSubtitles.nameWithoutExtension
    val parentDir = this@getSubtitles.parentFile
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")

    subtitleExtensions.mapNotNull { extension ->
        val file = File(parentDir, "$mediaName.$extension")
        file.takeIf { it.exists() }
    }
}


fun String.getThumbnail(): File? {
    val filePathWithoutExtension = this.substringBeforeLast(".")
    val imageExtensions = listOf("png", "jpg", "jpeg")
    for (imageExtension in imageExtensions) {
        val file = File("$filePathWithoutExtension.$imageExtension")
        if (file.exists()) return file
    }
    return null
}

fun File.isSubtitle(): Boolean {
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")
    return extension.lowercase() in subtitleExtensions
}

fun File.deleteFiles() {
    try {
        listFiles()?.onEach {
            it.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != Environment.getExternalStorageDirectory()?.path } ?: "Internal Storage"
