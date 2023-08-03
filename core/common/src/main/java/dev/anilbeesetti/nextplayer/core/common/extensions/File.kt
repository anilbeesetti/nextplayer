package dev.anilbeesetti.nextplayer.core.common.extensions

import android.os.Environment
import java.io.File

fun File.getSubtitles(): List<File> {
    val mediaName = this.nameWithoutExtension
    val subs = this.parentFile?.listFiles { file ->
        file.nameWithoutExtension.startsWith(mediaName) && file.isSubtitle()
    }?.toList() ?: emptyList()

    return subs
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
    return extension in subtitleExtensions
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != Environment.getExternalStorageDirectory()?.path } ?: "Internal Storage"
