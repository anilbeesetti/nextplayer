package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class VideoEntity(
    @PrimaryKey val path: String,
    val playbackPosition: Long,
    val audioTrack: Int? = null,
    val subtitleTrack: Int? = null,
    @ColumnInfo(name = "playback_speed", defaultValue = "1.0")
    val playbackSpeed: Float = 1f
)
