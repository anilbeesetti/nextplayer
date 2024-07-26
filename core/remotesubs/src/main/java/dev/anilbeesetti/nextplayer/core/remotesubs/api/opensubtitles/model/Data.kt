package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Data(
    @SerialName("attributes")
    val attributes: Attributes,
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
)
