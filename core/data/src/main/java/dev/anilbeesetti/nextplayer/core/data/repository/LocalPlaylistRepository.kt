package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistSummaryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistWithItems
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylist
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalPlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        playlistDao.observeSummaries().map { summaries ->
            summaries.map(PlaylistSummaryEntity::toModel)
        }

    override fun observePlaylist(playlistId: Long): Flow<PlaylistRecord?> =
        playlistDao.observePlaylist(playlistId).map { it?.toModel() }

    override suspend fun getPlaylist(playlistId: Long): PlaylistRecord? =
        playlistDao.getPlaylist(playlistId)?.toModel()

    override suspend fun create(name: String, videoUris: List<String>): Long =
        playlistDao.createPlaylist(
            name = name.validatedName(),
            uris = videoUris.distinct(),
        )

    override suspend fun createM3U(
        type: PlaylistType,
        source: String,
        playlist: M3UPlaylist,
    ): Long {
        require(type == PlaylistType.M3U_URL || type == PlaylistType.M3U_FILE)
        val refreshedAt = System.currentTimeMillis()
        return playlistDao.createM3UPlaylist(
            playlist = PlaylistEntity(
                name = playlist.playlistName.validatedName(),
                type = type.name,
                source = source,
                lastRefreshedAt = refreshedAt,
            ),
            items = playlist.items.toEntities(),
        )
    }

    override suspend fun replaceM3UItems(
        playlistId: Long,
        items: List<M3UPlaylistItem>,
    ) {
        playlistDao.replaceM3UItems(
            playlistId = playlistId,
            items = items.toEntities(playlistId),
            refreshedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun rename(playlistId: Long, name: String) {
        playlistDao.renamePlaylist(playlistId, name.validatedName())
    }

    override suspend fun delete(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addVideos(playlistId: Long, videoUris: List<String>): Int {
        playlistDao.requireLocalPlaylist(playlistId)
        return playlistDao.addItems(playlistId, videoUris.distinct())
    }

    override suspend fun removeVideo(playlistId: Long, videoUri: String) {
        playlistDao.requireLocalPlaylist(playlistId)
        playlistDao.removeItem(playlistId, videoUri)
    }

    override suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>) {
        playlistDao.requireLocalPlaylist(playlistId)
        playlistDao.replaceOrder(playlistId, orderedUris)
    }

    override suspend fun markVideoPlayed(playlistId: Long, videoUri: String) {
        playlistDao.markItemPlayed(
            playlistId = playlistId,
            uri = videoUri,
            playedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun countFilePlaylistsBySource(source: String): Int =
        playlistDao.countPlaylistsByTypeAndSource(PlaylistType.M3U_FILE.name, source)
}

private fun String.validatedName(): String =
    trim().also { require(it.isNotEmpty()) { "Playlist name cannot be blank" } }

private fun PlaylistSummaryEntity.toModel() = PlaylistSummary(
    id = id,
    name = name,
    type = PlaylistType.valueOf(type),
    itemCount = itemCount,
    lastRefreshedAt = lastRefreshedAt,
)

private fun PlaylistWithItems.toModel() = PlaylistRecord(
    id = playlist.id,
    name = playlist.name,
    type = PlaylistType.valueOf(playlist.type),
    source = playlist.source,
    items = items.sortedBy { it.position }.map { item ->
        PlaylistItemRecord(
            position = item.position,
            uri = item.uri,
            title = item.title,
            tvgLogo = item.tvgLogo,
            duration = item.duration,
            groupTitle = item.groupTitle,
            lastPlayedAt = item.lastPlayedAt,
        )
    },
    lastRefreshedAt = playlist.lastRefreshedAt,
)

private fun List<M3UPlaylistItem>.toEntities(playlistId: Long = 0): List<PlaylistItemEntity> =
    mapIndexed { position, item ->
        PlaylistItemEntity(
            playlistId = playlistId,
            uri = item.uri,
            position = position,
            title = item.title,
            tvgLogo = item.tvgLogo,
            duration = item.duration,
            groupTitle = item.groupTitle,
        )
    }
