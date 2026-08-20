package com.graviton.core.domain

import com.graviton.core.data.repository.PlaylistRepository
import com.graviton.core.data.repository.fake.FakeMediaRepository
import com.graviton.core.model.PlaylistItemRecord
import com.graviton.core.model.PlaylistRecord
import com.graviton.core.model.PlaylistSummary
import com.graviton.core.model.Video
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObservePlaylistUseCaseTest {

    @Test
    fun resolvesCurrentMetadataInPersistedPlaylistOrderAndOmitsMissingVideos() = runTest {
        val playlistRepository = FakePlaylistRepository(
            PlaylistRecord(
                id = 7,
                name = "Movies",
                items = listOf(
                    PlaylistItemRecord(0, "content://two", lastPlayedAt = 200),
                    PlaylistItemRecord(1, "content://missing", lastPlayedAt = 300),
                    PlaylistItemRecord(2, "content://one", lastPlayedAt = 100),
                ),
            ),
        )
        val mediaRepository = FakeMediaRepository().apply {
            videos += video("content://one", "One.mp4", "/Movies")
            videos += video("content://two", "Two.mp4", "/Downloads")
        }

        val playlist = ObservePlaylistUseCase(playlistRepository, mediaRepository)(7).first()

        assertEquals(listOf("Two", "One"), playlist?.items?.map { it.video.displayName })
        assertEquals(listOf("/Downloads", "/Movies"), playlist?.items?.map { it.video.parentPath })
        assertEquals(listOf(0, 1), playlist?.items?.map { it.position })
        assertEquals(listOf(200L, 100L), playlist?.items?.map { it.lastPlayedAt })
        assertEquals("content://two", playlist?.lastPlayedVideo?.uriString)
    }

    @Test
    fun aMediaStoreRenameAndPlaybackUpdateAreReflectedWithoutChangingPlaylistRows() = runTest {
        val playlistRepository = FakePlaylistRepository(
            PlaylistRecord(
                id = 7,
                name = "Movies",
                items = listOf(PlaylistItemRecord(0, "content://one")),
            ),
        )
        val mediaRepository = FakeMediaRepository().apply {
            videos += video("content://one", "Before.mp4", "/Movies")
        }
        val useCase = ObservePlaylistUseCase(playlistRepository, mediaRepository)

        val before = useCase(7).first()
        mediaRepository.videos[0] = mediaRepository.videos[0].copy(
            nameWithExtension = "After.mp4",
            parentPath = "/Renamed",
            path = "/Renamed/After.mp4",
            playbackPosition = 500,
        )
        mediaRepository.notifyMediaChanged()
        val after = useCase(7).first()

        assertEquals("Before", before?.items?.single()?.video?.displayName)
        assertEquals("After", after?.items?.single()?.video?.displayName)
        assertEquals("/Renamed", after?.items?.single()?.video?.parentPath)
        assertEquals(500L, after?.items?.single()?.video?.playbackPosition)
        assertEquals(
            listOf("content://one"),
            playlistRepository.record.value?.items?.map { it.uri },
        )
    }
}

private class FakePlaylistRepository(
    initialRecord: PlaylistRecord?,
) : PlaylistRepository {
    val record = MutableStateFlow(initialRecord)

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        MutableStateFlow(emptyList())

    override fun observePlaylist(playlistId: Long): Flow<PlaylistRecord?> = record
    override suspend fun create(name: String, videoUris: List<String>): Long = error("Not used")
    override suspend fun rename(playlistId: Long, name: String) = error("Not used")
    override suspend fun delete(playlistId: Long) = error("Not used")
    override suspend fun addVideos(playlistId: Long, videoUris: List<String>): Int = error("Not used")
    override suspend fun removeVideo(playlistId: Long, videoUri: String) = error("Not used")
    override suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>) = error("Not used")
    override suspend fun markVideoPlayed(playlistId: Long, videoUri: String) = error("Not used")
}

private fun video(
    uri: String,
    name: String,
    parentPath: String,
) = Video(
    id = uri.hashCode().toLong(),
    path = "$parentPath/$name",
    parentPath = parentPath,
    duration = 1_000,
    uriString = uri,
    nameWithExtension = name,
    width = 1920,
    height = 1080,
    size = 1_000,
)
