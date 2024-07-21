package dev.anilbeesetti.nextplayer.core.remotesubs.service

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.model.Video
import java.util.Locale

interface SubtitlesService {
    suspend fun search(video: Video, searchText: String?, languages: List<String>): Result<List<Subtitle>>
    suspend fun download(subtitle: Subtitle, name: String, fullName: String = "$name.${subtitle.language}.srt"): Result<SubtitleDownloadResponse>
}

data class Subtitle(
    val id: Int,
    val name: String,
    val language: String,
    val rating: String?,
) {
    val languageName: String = Locale.forLanguageTag(language).displayName
}

data class SubtitleDownloadResponse(
    val uri: Uri,
    val message: String? = null,
)
