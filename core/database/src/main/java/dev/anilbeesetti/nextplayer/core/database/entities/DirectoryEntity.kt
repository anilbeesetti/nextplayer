package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "directories",
)
data class DirectoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "filename") val name: String,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "parent_path") val parentPath: String? = null,
)
