package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistSummaryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistWithItems
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
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

    override suspend fun create(name: String, videoUris: List<String>): Long =
        playlistDao.createPlaylist(
            name = name.validatedName(),
            uris = videoUris.distinct(),
        )

    override suspend fun rename(playlistId: Long, name: String) {
        playlistDao.renamePlaylist(playlistId, name.validatedName())
    }

    override suspend fun delete(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addVideos(playlistId: Long, videoUris: List<String>): Int =
        playlistDao.addItems(playlistId, videoUris.distinct())

    override suspend fun removeVideo(playlistId: Long, videoUri: String) {
        playlistDao.removeItem(playlistId, videoUri)
    }

    override suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>) {
        playlistDao.replaceOrder(playlistId, orderedUris)
    }

    override suspend fun markVideoPlayed(playlistId: Long, videoUri: String) {
        playlistDao.markItemPlayed(playlistId, videoUri)
    }

    override suspend fun removeMissingVideos(existingUris: Set<String>) {
        playlistDao.removeMissingItems(existingUris)
    }
}

private fun String.validatedName(): String =
    trim().also { require(it.isNotEmpty()) { "Playlist name cannot be blank" } }

private fun PlaylistSummaryEntity.toModel() = PlaylistSummary(
    id = id,
    name = name,
    itemCount = itemCount,
)

private fun PlaylistWithItems.toModel() = PlaylistRecord(
    id = playlist.id,
    name = playlist.name,
    orderedUris = items.sortedBy { it.position }.map { it.uri },
    lastPlayedUri = playlist.lastPlayedUri,
)
