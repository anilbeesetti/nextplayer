package dev.anilbeesetti.nextplayer.core.common.extensions

import java.io.File

fun File.getSubtitles(): List<File> {
    val mediaName = this.nameWithoutExtension
    val subs = this.parentFile?.listFiles { file ->
        file.isSubtitle() && file.nameWithoutExtension.startsWith(mediaName)
    }?.toList() ?: emptyList()

    return subs
}

fun File.isSubtitle(): Boolean {
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")
    return extension in subtitleExtensions
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != "/storage/emulated/0" } ?: "Internal Storage"
