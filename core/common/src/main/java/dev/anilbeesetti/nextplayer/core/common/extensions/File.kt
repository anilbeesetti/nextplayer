package dev.anilbeesetti.nextplayer.core.common.extensions

import java.io.File

fun File.getSubtitles(): List<File> {
    val subtitleExtensions = listOf("srt")
    val mediaName = this.nameWithoutExtension
    val subs = this.parentFile?.listFiles { file ->
        file.extension in subtitleExtensions && file.nameWithoutExtension == mediaName
    }?.toList() ?: emptyList()

    return subs
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != "/storage/emulated/0" } ?: "Internal Storage"
