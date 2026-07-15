package dev.anilbeesetti.nextplayer.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import dev.anilbeesetti.nextplayer.core.data.playlist.M3uParseResult
import dev.anilbeesetti.nextplayer.core.data.playlist.M3uParser
import dev.anilbeesetti.nextplayer.core.data.playlist.PlaylistSourceReader
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistSummaryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistWithItems
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalPlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val parser: M3uParser,
    private val sourceReader: PlaylistSourceReader,
) : PlaylistRepository {
    private val refreshMutexes = ConcurrentHashMap<Long, Mutex>()

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        playlistDao.observeSummaries().map { summaries -> summaries.map(PlaylistSummaryEntity::toModel) }

    override fun observePlaylist(id: Long): Flow<Playlist?> =
        playlistDao.observePlaylist(id).map { it?.toModel() }

    override suspend fun createEditable(name: String): Long {
        val trimmedName = name.trim().also { require(it.isNotEmpty()) { "Playlist name cannot be blank" } }
        return mapNameConflict {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = trimmedName,
                    normalizedName = trimmedName.normalizedName(),
                    type = PlaylistType.EDITABLE.name,
                ),
            )
        }
    }

    override suspend fun createLinked(
        name: String,
        type: PlaylistType,
        source: String,
    ): PlaylistRefreshResult {
        require(type.isLinked) { "Linked playlist type must be M3U_URL or M3U_FILE" }
        val trimmedName = name.trim().also { require(it.isNotEmpty()) { "Playlist name cannot be blank" } }
        val parsed = readAndParse(type, source)
        val refreshedAt = System.currentTimeMillis()
        val playlistId = mapNameConflict {
            playlistDao.insertLinkedPlaylist(
                playlist = PlaylistEntity(
                    name = trimmedName,
                    normalizedName = trimmedName.normalizedName(),
                    type = type.name,
                    source = source,
                    createdAt = refreshedAt,
                    updatedAt = refreshedAt,
                    lastRefreshedAt = refreshedAt,
                ),
                items = parsed.entries.toEntities(),
            )
        }
        return parsed.toRefreshResult(playlistId)
    }

    override suspend fun addItems(id: Long, items: List<PlaylistItemInput>): Int {
        val playlist = playlistDao.getPlaylist(id)?.playlist
            ?: throw IllegalArgumentException("Playlist $id does not exist")
        playlist.requireEditable()
        return playlistDao.addItems(id, items.distinctBy { it.uriString }.toEntities(id))
    }

    override suspend fun moveItem(id: Long, uriString: String, toIndex: Int) {
        val playlist = playlistDao.getPlaylist(id)?.playlist
            ?: throw IllegalArgumentException("Playlist $id does not exist")
        playlist.requireEditable()
        playlistDao.moveItem(id, uriString, toIndex)
    }

    override suspend fun refresh(id: Long): PlaylistRefreshResult {
        val mutex = refreshMutexes.getOrPut(id) { Mutex() }
        return mutex.withLock {
            val playlist = playlistDao.getPlaylist(id)?.playlist
                ?: throw IllegalArgumentException("Playlist $id does not exist")
            val type = playlist.type.toPlaylistType()
            require(type.isLinked) { "Editable playlists do not have a linked source" }
            val source = requireNotNull(playlist.source) { "Linked playlist must have a source" }
            val parsed = readAndParse(type, source)
            playlistDao.replaceItems(
                playlistId = id,
                items = parsed.entries.toEntities(id),
                refreshedAt = System.currentTimeMillis(),
            )
            parsed.toRefreshResult(id)
        }
    }

    override suspend fun delete(id: Long) {
        playlistDao.deletePlaylist(id)
        refreshMutexes.remove(id)
    }

    private suspend fun readAndParse(type: PlaylistType, source: String): M3uParseResult = try {
        val content = sourceReader.read(type, source)
        parser.parse(content.text, content.resolveEntry)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw PlaylistSourceException(error)
    }

    private suspend fun <T> mapNameConflict(block: suspend () -> T): T = try {
        block()
    } catch (_: SQLiteConstraintException) {
        throw PlaylistNameConflictException()
    }

    private fun PlaylistEntity.requireEditable() {
        if (type.toPlaylistType() != PlaylistType.EDITABLE) throw LinkedPlaylistReadOnlyException()
    }
}

private val PlaylistType.isLinked: Boolean
    get() = this == PlaylistType.M3U_URL || this == PlaylistType.M3U_FILE

private fun String.normalizedName(): String = lowercase(Locale.ROOT)

private fun String.toPlaylistType(): PlaylistType = PlaylistType.valueOf(this)

private fun M3uParseResult.toRefreshResult(playlistId: Long) = PlaylistRefreshResult(
    playlistId = playlistId,
    itemCount = entries.size,
    skippedEntries = skippedEntries,
)

private fun List<PlaylistItemInput>.toEntities(playlistId: Long = 0): List<PlaylistItemEntity> =
    mapIndexed { position, item ->
        PlaylistItemEntity(
            playlistId = playlistId,
            uri = item.uriString,
            title = item.title,
            position = position,
        )
    }

private fun PlaylistSummaryEntity.toModel() = PlaylistSummary(
    id = id,
    name = name,
    type = type.toPlaylistType(),
    itemCount = itemCount,
    lastRefreshedAt = lastRefreshedAt,
)

private fun PlaylistWithItems.toModel() = Playlist(
    id = playlist.id,
    name = playlist.name,
    type = playlist.type.toPlaylistType(),
    source = playlist.source,
    items = items.map { item ->
        PlaylistItem(
            uriString = item.uri,
            title = item.title,
            position = item.position,
        )
    },
    lastRefreshedAt = playlist.lastRefreshedAt,
)
