package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["medium_path", "stream_index"],
    tableName = "audio_stream_info",
    foreignKeys = [
        ForeignKey(
            entity = MediumEntity::class,
            parentColumns = arrayOf("path"),
            childColumns = arrayOf("medium_path"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AudioStreamInfoEntity(
    @ColumnInfo(name = "stream_index") val index: Int,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "codec_name") val codecName: String,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "disposition") val disposition: Int,
    @ColumnInfo(name = "bit_rate") val bitRate: Long,
    @ColumnInfo(name = "sample_format") val sampleFormat: String?,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "channels") val channels: Int,
    @ColumnInfo(name = "channel_layout")  val channelLayout: String?,
    @ColumnInfo(name = "medium_path") val mediumPath: String
)