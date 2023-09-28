package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["medium_path", "stream_index"],
    tableName = "video_stream_info",
    foreignKeys = [
        ForeignKey(
            entity = MediumEntity::class,
            parentColumns = arrayOf("path"),
            childColumns = arrayOf("medium_path"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VideoStreamInfoEntity(
    @ColumnInfo(name = "stream_index") val index: Int,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "codec_name") val codecName: String,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "disposition") val disposition: Int,
    @ColumnInfo(name = "bit_rate") val bitRate: Long,
    @ColumnInfo(name = "frame_rate") val frameRate: Double,
    @ColumnInfo(name = "width") val frameWidth: Int,
    @ColumnInfo(name = "height") val frameHeight: Int,
    @ColumnInfo(name = "medium_path") val mediumPath: String
)
