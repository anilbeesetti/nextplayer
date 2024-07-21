package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class File(
    @SerialName("cd_number")
    val cdNumber: Int,
    @SerialName("file_id")
    val fileId: Int,
    @SerialName("file_name")
    val fileName: String,
)
