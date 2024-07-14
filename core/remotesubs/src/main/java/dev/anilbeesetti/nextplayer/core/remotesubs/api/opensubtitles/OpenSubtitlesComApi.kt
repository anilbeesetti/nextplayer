package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles

import dev.anilbeesetti.nextplayer.core.remotesubs.BuildConfig
import dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model.OpenSubDownloadLinks
import dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model.OpenSubtitlesSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.userAgent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class DownloadRequest(
    @SerialName("file_id")
    val fileId: Int,
)

@Singleton
class OpenSubtitlesComApi @Inject constructor(
    private val client: HttpClient,
) {
    companion object {
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
        private const val API_KEY = BuildConfig.OPEN_SUBTITLES_API_KEY
    }

    suspend fun search(
        fileHash: String,
        searchText: String?,
        languages: List<String>,
    ): Result<OpenSubtitlesSearchResponse> {
        return try {
            val response = client.get(BASE_URL) {
                url {
                    appendPathSegments("subtitles")
                    parameter("languages", languages.joinToString())
                    parameter("moviehash", fileHash)
                    parameter("order_by", "from_trusted,ratings,download_count")
                    if (!searchText.isNullOrBlank()) {
                        parameter("query", searchText)
                    }
                }
                userAgent("nextplayer v0.11.1")
                header("Api-Key", API_KEY)
            }
            return when (response.status) {
                HttpStatusCode.OK -> {
                    runCatching { response.body<OpenSubtitlesSearchResponse>() }
                }

                else -> {
                    Result.failure(RuntimeException("Failed to search subtitles"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(fileId: Int): Result<OpenSubDownloadLinks> {
        return try {
            val response = client.post(BASE_URL) {
                url {
                    appendPathSegments("download")
                }
                setBody(DownloadRequest(fileId))
                contentType(ContentType.Application.Json)
                userAgent("nextplayer v0.11.1")
                header("Api-Key", API_KEY)
            }
            return when (response.status) {
                HttpStatusCode.OK -> {
                    runCatching { response.body<OpenSubDownloadLinks>() }
                }

                else -> {
                    Result.failure(RuntimeException("Failed to search subtitles"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}