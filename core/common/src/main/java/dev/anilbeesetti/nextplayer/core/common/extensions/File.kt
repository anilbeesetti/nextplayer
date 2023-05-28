package dev.anilbeesetti.nextplayer.core.common.extensions

import java.io.File

fun File.getSubtitles(): List<File> {
    val mediaName = this.nameWithoutExtension
    val subs = this.parentFile?.listFiles { file ->
        file.nameWithoutExtension.startsWith(mediaName) && file.isSubtitle()
    }?.toList() ?: emptyList()

    return subs
}

fun File.getThumbnails(): List<File> {
    val mediaName = this.nameWithoutExtension
    val thumbs = this.parentFile?.listFiles { file ->
        file.nameWithoutExtension == mediaName && file.isImage()
    }?.toList() ?: emptyList()

    return thumbs
}

fun File.isSubtitle(): Boolean {
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")
    return extension in subtitleExtensions
}

fun File.isImage(): Boolean {
    val imageExtensions = listOf("png", "jpg", "jpeg")
    return extension in imageExtensions
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != "/storage/emulated/0" } ?: "Internal Storage"
