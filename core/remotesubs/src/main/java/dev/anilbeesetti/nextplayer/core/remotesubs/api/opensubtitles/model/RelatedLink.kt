package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RelatedLink(
    @SerialName("img_url")
    val imgUrl: String,
    @SerialName("label")
    val label: String,
    @SerialName("url")
    val url: String
)