package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "media"
)
data class MediumEntity(
    @PrimaryKey
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "filename") val name: String,
    @ColumnInfo(name = "uri") val uriString: String,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "playback_position") val playbackPosition: Long = 0,
    @ColumnInfo(name = "audio_track_index") val audioTrackIndex: Int? = null,
    @ColumnInfo(name = "subtitle_track_index") val subtitleTrackIndex: Int? = null,
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Float? = null,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "external_subs", defaultValue = "") val externalSubs: String
)
