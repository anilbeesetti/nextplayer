package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenSubtitlesSearchResponse(
    @SerialName("data")
    val data: List<Data>,
    @SerialName("page")
    val page: Int,
    @SerialName("per_page")
    val perPage: Int,
    @SerialName("total_count")
    val totalCount: Int,
    @SerialName("total_pages")
    val totalPages: Int
)