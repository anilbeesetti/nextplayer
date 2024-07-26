package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Attributes(
    @SerialName("download_count")
    val downloadCount: Int,
    @SerialName("file_hashes")
    val fileHashes: List<String>,
    @SerialName("files")
    val files: List<File>,
    @SerialName("foreign_parts_only")
    val foreignPartsOnly: Boolean,
    @SerialName("fps")
    val fps: Double,
    @SerialName("from_trusted")
    val fromTrusted: Boolean,
    @SerialName("hd")
    val hd: Boolean,
    @SerialName("hearing_impaired")
    val hearingImpaired: Boolean,
    @SerialName("language")
    val language: String,
    @SerialName("machine_translated")
    val machineTranslated: Boolean,
    @SerialName("nb_cd")
    val nbCd: Int,
    @SerialName("new_download_count")
    val newDownloadCount: Int,
    @SerialName("ratings")
    val ratings: Double,
    @SerialName("release")
    val release: String,
    @SerialName("subtitle_id")
    val subtitleId: String,
    @SerialName("upload_date")
    val uploadDate: String,
    @SerialName("url")
    val url: String,
    @SerialName("votes")
    val votes: Int,
)
