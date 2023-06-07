package dev.anilbeesetti.nextplayer.core.database.entities

import android.print.PrintAttributes.MediaSize
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(
    tableName = "directories",
)
data class DirectoryEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "filename") val name: String,
    @ColumnInfo(name = "media_count") val mediaCount: Int,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "size") val size: Long,
)