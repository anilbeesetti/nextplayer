package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a video that has been hidden into the vault.
 *
 * When a video is hidden, its underlying file is moved from its original location into the
 * app's private storage (so it disappears from MediaStore and the rest of the system), and a
 * row is inserted here to remember where it came from and how to display it inside the vault.
 */
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
    /** Absolute path of the file inside the app's private vault directory. */
    @ColumnInfo(name = "vault_path")
    val vaultPath: String,
    /** Absolute path the file used to live at before it was hidden, used when unhiding. */
    @ColumnInfo(name = "original_path")
    val originalPath: String,
    /** Display name (with extension) shown for the video. */
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
    /** Time (epoch millis) the video was hidden. */
    @ColumnInfo(name = "hidden_at")
    val hiddenAt: Long = System.currentTimeMillis(),
)
