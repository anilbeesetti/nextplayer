package com.graviton.core.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.graviton.core.common.di.ApplicationScope
import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/**
 * Queries audio through MediaStore and observes the collection for rescans/imports. Query work is
 * deliberately kept here rather than in the composable so all music tabs use one consistent data
 * set and the UI has real loading/error behaviour.
 */
class LocalMusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
) : MusicRepository {
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val audioChanges: Flow<Unit> = merge(
        callbackFlow {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            context.contentResolver.registerContentObserver(AUDIO_URI, true, observer)
            context.contentResolver.registerContentObserver(PLAYLIST_URI, true, observer)
            trySend(Unit)
            awaitClose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        },
        manualRefresh,
    ).shareIn(
        scope = applicationScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        replay = 1,
    )

    override fun observeTracks(): Flow<List<AudioTrack>> = audioChanges
        .map { queryTracks() }
        .flowOn(Dispatchers.IO)

    override fun observePlaylists(): Flow<List<MusicPlaylist>> = audioChanges
        .map { queryPlaylists() }
        .flowOn(Dispatchers.IO)

    override fun refresh() {
        manualRefresh.tryEmit(Unit)
    }

    override suspend fun getTrack(uriString: String): AudioTrack? = withContext(Dispatchers.IO) {
        queryTracks().firstOrNull { it.uriString == uriString }
    }

    override suspend fun getPlaylistTrackIds(playlistId: Long): List<Long> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Long>()
        val membersUri = MediaStore.Audio.Playlists.Members.getContentUri(EXTERNAL_VOLUME, playlistId)
        context.contentResolver.query(
            membersUri,
            arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID, MediaStore.Audio.Playlists.Members.PLAY_ORDER),
            null,
            null,
            "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            if (idColumn >= 0) {
                while (cursor.moveToNext()) result += cursor.getLong(idColumn)
            }
        }
        result
    }

    override suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>): Boolean =
        withContext(Dispatchers.IO) {
            if (trackIds.isEmpty()) return@withContext true
            val membersUri = MediaStore.Audio.Playlists.Members.getContentUri(EXTERNAL_VOLUME, playlistId)
            // PLAY_ORDER is 1-based and must continue after the existing members.
            val existing = context.contentResolver.query(
                membersUri,
                arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                null,
                null,
                null,
            )?.use { it.count } ?: 0
            val values = trackIds.mapIndexed { offset, trackId ->
                ContentValues().apply {
                    put(MediaStore.Audio.Playlists.Members.AUDIO_ID, trackId)
                    put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, existing + offset + 1)
                }
            }.toTypedArray()
            val inserted = runCatching { context.contentResolver.bulkInsert(membersUri, values) }
                .getOrDefault(0)
            if (inserted > 0) manualRefresh.tryEmit(Unit)
            inserted > 0
        }

    override suspend fun createPlaylist(name: String): Long? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Playlists.NAME, trimmed)
            put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000)
        }
        val uri = runCatching { context.contentResolver.insert(PLAYLIST_URI, values) }.getOrNull()
        val id = uri?.lastPathSegment?.toLongOrNull()
        if (id != null) manualRefresh.tryEmit(Unit)
        id
    }

    private fun queryTracks(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        try {
            context.contentResolver.query(
                AUDIO_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE ?",
                arrayOf("audio/%"),
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val id = cursor.index(MediaStore.Audio.Media._ID)
                val title = cursor.index(MediaStore.Audio.Media.TITLE)
                val artist = cursor.index(MediaStore.Audio.Media.ARTIST)
                val album = cursor.index(MediaStore.Audio.Media.ALBUM)
                val albumId = cursor.index(MediaStore.Audio.Media.ALBUM_ID)
                val duration = cursor.index(MediaStore.Audio.Media.DURATION)
                val size = cursor.index(MediaStore.Audio.Media.SIZE)
                val data = cursor.index(MediaStore.Audio.Media.DATA)
                val displayName = cursor.index(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateAdded = cursor.index(MediaStore.Audio.Media.DATE_ADDED)
                if (id < 0) return@use

                while (cursor.moveToNext()) {
                    val trackId = cursor.getLong(id)
                    val path = cursor.string(data).orEmpty().ifBlank {
                        cursor.string(displayName).orEmpty()
                    }
                    val albumKey = cursor.longOrDefault(albumId, 0L)
                    tracks += AudioTrack(
                        id = trackId,
                        uriString = ContentUris.withAppendedId(AUDIO_URI, trackId).toString(),
                        title = cursor.string(title).orEmpty(),
                        artist = cursor.string(artist).orEmpty(),
                        album = cursor.string(album).orEmpty(),
                        albumId = albumKey,
                        duration = cursor.longOrDefault(duration, 0L),
                        size = cursor.longOrDefault(size, 0L),
                        path = path,
                        dateAdded = cursor.longOrDefault(dateAdded, 0L),
                        artworkUriString = albumArtworkUri(albumKey).toString().takeIf { albumKey > 0 },
                    )
                }
            }
        } catch (_: SecurityException) {
            // The music permission is requested by the music screen. An empty result lets the
            // screen render a useful permission/empty state without crashing the app.
        } catch (_: IllegalArgumentException) {
            // A vendor MediaStore may omit an optional column. Treat it as an unavailable scan.
        }
        return tracks
    }

    private fun queryPlaylists(): List<MusicPlaylist> {
        val playlists = mutableListOf<MusicPlaylist>()
        val projection = arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME)
        try {
            context.contentResolver.query(PLAYLIST_URI, projection, null, null, "${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC")
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID)
                    val nameColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME)
                    if (idColumn < 0 || nameColumn < 0) return@use
                    while (cursor.moveToNext()) {
                        val playlistId = cursor.getLong(idColumn)
                        val membersUri = MediaStore.Audio.Playlists.Members.getContentUri(EXTERNAL_VOLUME, playlistId)
                        val count = context.contentResolver.query(
                            membersUri,
                            arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                            null,
                            null,
                            null,
                        )?.use { members -> members.count } ?: 0
                        playlists += MusicPlaylist(
                            id = playlistId,
                            name = cursor.getString(nameColumn).orEmpty(),
                            trackCount = count,
                        )
                    }
                }
        } catch (_: SecurityException) {
            // See queryTracks().
        } catch (_: IllegalArgumentException) {
            // Some OEMs do not expose playlist tables.
        }
        return playlists
    }

    private fun albumArtworkUri(albumId: Long): Uri = "content://media/external/audio/albumart/$albumId".toUri()

    private fun android.database.Cursor.index(column: String): Int = getColumnIndex(column)

    private fun android.database.Cursor.string(column: Int): String? =
        column.takeIf { it >= 0 }?.let { getString(it) }

    private fun android.database.Cursor.longOrDefault(column: Int, default: Long): Long =
        column.takeIf { it >= 0 }?.let { getLong(it) } ?: default

    private companion object {
        const val EXTERNAL_VOLUME = "external"
        val AUDIO_URI: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val PLAYLIST_URI: Uri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
    }
}
