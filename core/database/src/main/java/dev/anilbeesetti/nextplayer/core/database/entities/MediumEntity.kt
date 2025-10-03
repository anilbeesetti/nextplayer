package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["path"]),
    ],
)
data class MediumEntity(
    @PrimaryKey
    @ColumnInfo(name = "uri")
    val uriString: String,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "filename") val name: String,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,

    // Medium info
    @ColumnInfo(name = "format") val format: String? = null,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
)
