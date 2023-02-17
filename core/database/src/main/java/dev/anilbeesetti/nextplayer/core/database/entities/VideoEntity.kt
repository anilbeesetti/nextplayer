package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class VideoEntity(
    @PrimaryKey val path: String,
    val playbackPosition: Long
)
