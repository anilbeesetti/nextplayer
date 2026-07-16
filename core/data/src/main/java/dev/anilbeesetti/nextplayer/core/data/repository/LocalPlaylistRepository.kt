package dev.anilbeesetti.nextplayer.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalPlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val parser: M3uParser,
    private val sourceReader: PlaylistSourceReader,
    private val fileGrantRepository: PlaylistFileGrantRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) : PlaylistRepository {
    private val refreshMutexes = ConcurrentHashMap<Long, Mutex>()

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        playlistDao.observeSummaries().map { summaries -> summaries.map(PlaylistSummaryEntity::toModel) }

    override fun observePlaylist(id: Long): Flow<Playlist?> =
        playlistDao.observePlaylist(id)
            .map { it?.toModel() }
            .flowOn(defaultDispatcher)

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
        val entities = parsed.toEntitiesOffMain()
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
                items = entities,
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
        requireLinkedPlaylist(id)
        val mutex = refreshMutexes.getOrPut(id) { Mutex() }
        return mutex.withLock {
            val playlist = requireLinkedPlaylist(id)
            val type = playlist.type.toPlaylistType()
            val source = requireNotNull(playlist.source) { "Linked playlist must have a source" }
            val parsed = readAndParse(type, source)
            val entities = parsed.toEntitiesOffMain(id)
            playlistDao.replaceItems(
                playlistId = id,
                items = entities,
                refreshedAt = System.currentTimeMillis(),
            )
            parsed.toRefreshResult(id)
        }
    }

    private suspend fun requireLinkedPlaylist(id: Long): PlaylistEntity {
        val playlist = playlistDao.getPlaylist(id)?.playlist
            ?: throw IllegalArgumentException("Playlist $id does not exist")
        require(playlist.type.toPlaylistType().isLinked) { "Editable playlists do not have a linked source" }
        return playlist
    }

    override suspend fun delete(id: Long) {
        val playlist = playlistDao.getPlaylist(id)?.playlist
        withContext(NonCancellable) {
            playlistDao.deletePlaylist(id)
            refreshMutexes.remove(id)
            if (playlist?.type == PlaylistType.M3U_FILE.name) {
                playlist.source?.let { fileGrantRepository.releaseIfUnused(it) }
            }
        }
    }

    private suspend fun readAndParse(type: PlaylistType, source: String): M3uParseResult = try {
        withContext(defaultDispatcher) {
            val content = sourceReader.read(type, source)
            parser.parse(content.text, content.resolveEntry)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw PlaylistSourceException(error)
    }

    private suspend fun M3uParseResult.toEntitiesOffMain(playlistId: Long = 0): List<PlaylistItemEntity> =
        withContext(defaultDispatcher) { entries.toEntities(playlistId) }

    private suspend fun <T> mapNameConflict(block: suspend () -> T): T = try {
        block()
    } catch (error: SQLiteConstraintException) {
        if (error.isPlaylistNameConflict()) throw PlaylistNameConflictException()
        throw error
    }

    private fun PlaylistEntity.requireEditable() {
        if (type.toPlaylistType() != PlaylistType.EDITABLE) throw LinkedPlaylistReadOnlyException()
    }
}

private val PlaylistType.isLinked: Boolean
    get() = this == PlaylistType.M3U_URL || this == PlaylistType.M3U_FILE

private fun String.normalizedName(): String = lowercase(Locale.ROOT)

private fun SQLiteConstraintException.isPlaylistNameConflict(): Boolean {
    val uniqueConstraint = message.orEmpty()
        .substringAfter("UNIQUE constraint failed: ", missingDelimiterValue = "")
        .substringBefore(" (code ")
    return uniqueConstraint == "playlist.normalized_name" ||
        uniqueConstraint == "index_playlist_normalized_name"
}

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
            imageUrl = item.imageUrl,
            displayPath = item.displayPath,
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
            imageUrl = item.imageUrl,
            displayPath = item.displayPath,
        )
    },
    lastRefreshedAt = playlist.lastRefreshedAt,
)
