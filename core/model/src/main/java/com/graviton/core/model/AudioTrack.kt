package com.graviton.core.model

import java.io.Serializable

/**
 * A track read from Android's MediaStore.  The URI is stable across the music UI and the
 * Media3 session, so the list, mini-player and notification all describe the same item.
 */
data class AudioTrack(
    val id: Long,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val size: Long,
    val path: String,
    val dateAdded: Long,
    val artworkUriString: String?,
) : Serializable {
    val displayTitle: String
        get() = title.ifBlank { path.substringAfterLast('/').substringBeforeLast('.') }

    val displayArtist: String
        get() = artist.ifBlank { "Unknown artist" }

    val displayAlbum: String
        get() = album.ifBlank { "Unknown album" }
}

data class MusicPlaylist(
    val id: Long,
    val name: String,
    val trackCount: Int,
) : Serializable
