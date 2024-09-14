package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["path"], unique = true),
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

    // Medium playback state
    @ColumnInfo(name = "playback_position") val playbackPosition: Long = 0,
    @ColumnInfo(name = "audio_track_index") val audioTrackIndex: Int? = null,
    @ColumnInfo(name = "subtitle_track_index") val subtitleTrackIndex: Int? = null,
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Float? = null,
    @ColumnInfo(name = "last_played_time") val lastPlayedTime: Long? = null,
    @ColumnInfo(name = "external_subs") val externalSubs: String = "",
    @ColumnInfo(name = "video_scale") val videoScale: Float = 1f,
)
