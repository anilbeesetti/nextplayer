package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hidden_video",
    indices = [
        Index(value = ["vault_path"], unique = true),
    ],
)
data class HiddenVideoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "vault_path")
    val vaultPath: String,
    @ColumnInfo(name = "original_path")
    val originalPath: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "duration")
    val duration: Long = 0,
    @ColumnInfo(name = "size")
    val size: Long = 0,
    @ColumnInfo(name = "width")
    val width: Int = 0,
    @ColumnInfo(name = "height")
    val height: Int = 0,
    @ColumnInfo(name = "hidden_at")
    val hiddenAt: Long = System.currentTimeMillis(),
)
