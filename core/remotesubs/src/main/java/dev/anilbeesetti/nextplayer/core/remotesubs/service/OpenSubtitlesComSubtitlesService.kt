package dev.anilbeesetti.nextplayer.core.remotesubs.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.hasWriteStoragePermissionBelowQ
import dev.anilbeesetti.nextplayer.core.common.extensions.scanFilePath
import dev.anilbeesetti.nextplayer.core.common.services.SystemService
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.remotesubs.OpenSubtitlesHasher
import dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.OpenSubtitlesComApi
import dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model.OpenSubDownloadLinksError
import dev.anilbeesetti.nextplayer.core.ui.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenSubtitlesComSubtitlesService @Inject constructor(
    private val openSubtitlesComApi: OpenSubtitlesComApi,
    private val openSubtitlesHasher: OpenSubtitlesHasher,
    private val systemService: SystemService,
    @ApplicationContext private val context: Context,
) : SubtitlesService {

    override suspend fun search(video: Video, searchText: String?, languages: List<String>): Result<List<Subtitle>> {
        val hash = openSubtitlesHasher.computeHash(Uri.parse(video.uriString), video.size)
        return openSubtitlesComApi.search(
            fileHash = hash,
            searchText = searchText,
            languages = languages,
        ).map { response ->
            response.data.map { dto ->
                Subtitle(
                    id = dto.attributes.files.first().fileId,
                    name = dto.attributes.files.first().fileName,
                    language = dto.attributes.language,
                    rating = "${dto.attributes.ratings}/10".takeIf { dto.attributes.ratings > 0 },
                )
            }
        }
    }

    override suspend fun download(
        subtitle: Subtitle,
        name: String,
        fullName: String,
    ): Result<SubtitleDownloadResponse> = withContext(Dispatchers.IO) {
        val folder = if (context.hasWriteStoragePermissionBelowQ()) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        } else {
            context.filesDir
        }

        val destinationUri = File(folder, fullName).toUri()
        openSubtitlesComApi.download(subtitle.id).onSuccess { response ->
            try {
                val url = URL(response.link)
                val urlConnection = url.openConnection() as HttpURLConnection
                val inputStream = urlConnection.inputStream

                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                } ?: return@withContext Result.failure(
                    IllegalStateException("Failed to open output stream for uri: $destinationUri"),
                )
                inputStream.close()
                destinationUri.path?.let { context.scanFilePath(it, "application/x-subrip") }
                return@withContext Result.success(
                    SubtitleDownloadResponse(
                        uri = destinationUri,
                        message = systemService.getString(R.string.downloads_remaining, "${response.remaining}/${response.requests + response.remaining}"),
                    ),
                )
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }.onFailure {
            if (it is OpenSubDownloadLinksError && it.remaining <= 0) {
                val errorMessage = systemService.getString(R.string.download_quota_reached, it.resetTime)
                return@withContext Result.failure(Throwable(errorMessage))
            }
            return@withContext Result.failure(it)
        }
        return@withContext Result.failure(UnknownError())
    }
}
