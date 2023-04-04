package dev.anilbeesetti.nextplayer.feature.player.extensions

import java.io.File

fun File.getSubtitles(): List<File> {
    val subtitleExtensions = listOf("srt")
    val mediaName = this.nameWithoutExtension
    val subs = this.parentFile?.listFiles { file ->
        file.extension in subtitleExtensions && file.nameWithoutExtension == mediaName
    }?.toList() ?: emptyList()

    return subs
}