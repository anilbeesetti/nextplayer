package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenSubDownloadLinks(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("link")
    val link: String,
    @SerialName("message")
    val message: String,
    @SerialName("remaining")
    val remaining: Int,
    @SerialName("requests")
    val requests: Int,
    @SerialName("reset_time")
    val resetTime: String,
    @SerialName("reset_time_utc")
    val resetTimeUtc: String,
)

@Serializable
data class OpenSubDownloadLinksError(
    @SerialName("message")
    override val message: String,
    @SerialName("remaining")
    val remaining: Int,
    @SerialName("requests")
    val requests: Int,
    @SerialName("reset_time")
    val resetTime: String,
    @SerialName("reset_time_utc")
    val resetTimeUtc: String,
) : Throwable(message = message)