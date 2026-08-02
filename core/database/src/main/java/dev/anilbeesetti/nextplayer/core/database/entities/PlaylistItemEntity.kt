package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_item",
    primaryKeys = ["playlist_id", "uri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlist_id"),
        Index(value = ["playlist_id", "position"], unique = true),
    ],
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    val uri: String,
    val position: Int,
)
