package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "LOCAL",
    val source: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_refreshed_at")
    val lastRefreshedAt: Long? = null,
)

data class PlaylistSummaryEntity(
    val id: Long,
    val name: String,
    val type: String,
    @ColumnInfo(name = "item_count")
    val itemCount: Int,
    @ColumnInfo(name = "last_refreshed_at")
    val lastRefreshedAt: Long?,
)

data class PlaylistWithItems(
    @Embedded
    val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    val items: List<PlaylistItemEntity>,
)
