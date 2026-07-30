package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistSummaryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistWithItems
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistFileGrantRepositoryTest {
    @Test
    fun dismissedPreparedGrantReleasesWhenUriIsUnreferenced() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(FakeGrantPlaylistDao(), access)
        val grant = repository.acquire(URI)

        repository.release(checkNotNull(grant))

        assertEquals(listOf(URI), access.releasedUris)
    }

    @Test
    fun replacementPickerReleasesOnlySupersededGrant() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(FakeGrantPlaylistDao(), access)
        val oldGrant = checkNotNull(repository.acquire(URI))
        val latestGrant = checkNotNull(repository.acquire(OTHER_URI))

        repository.release(oldGrant)

        assertEquals(listOf(URI), access.releasedUris)
        assertNotNull(latestGrant)
    }

    @Test
    fun releaseKeepsGrantWhileAnotherPreparationUsesSameUri() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(FakeGrantPlaylistDao(), access)
        val first = checkNotNull(repository.acquire(URI))
        val second = checkNotNull(repository.acquire(URI))

        repository.release(first)
        assertTrue(access.releasedUris.isEmpty())

        repository.release(second)
        assertEquals(listOf(URI), access.releasedUris)
    }

    @Test
    fun activeCreateReservationSurvivesPickerLifecycleCleanup() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(FakeGrantPlaylistDao(), access)
        val pickerGrant = checkNotNull(repository.acquire(URI))
        val createGrant = checkNotNull(repository.reserve(pickerGrant))

        repository.release(pickerGrant)
        assertTrue(access.releasedUris.isEmpty())

        repository.release(createGrant)
        assertEquals(listOf(URI), access.releasedUris)
    }

    @Test
    fun retainedSuccessfulGrantIgnoresLateDialogCleanup() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(FakeGrantPlaylistDao(), access)
        val grant = checkNotNull(repository.acquire(URI))

        repository.retain(grant)
        repository.release(grant)

        assertTrue(access.releasedUris.isEmpty())
    }

    @Test
    fun dismissedSelectionDoesNotReleaseOlderPlaylistsSharedUri() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(
            FakeGrantPlaylistDao(referenceCount = 1),
            access,
        )
        val grant = checkNotNull(repository.acquire(URI))

        repository.release(grant)

        assertTrue(access.releasedUris.isEmpty())
    }

    @Test
    fun deleteSharedReferenceRetainsGrantUntilLastReferenceIsGone() = runTest {
        val dao = FakeGrantPlaylistDao(referenceCount = 1)
        val access = FakePersistedPlaylistFileGrantAccess()
        val repository = LocalPlaylistFileGrantRepository(dao, access)

        repository.releaseIfUnused(URI)
        assertTrue(access.releasedUris.isEmpty())

        dao.referenceCount = 0
        repository.releaseIfUnused(URI)
        assertEquals(listOf(URI), access.releasedUris)
    }

    @Test
    fun securityFailuresAreIdempotentAndExistingGrantCanBePrepared() = runTest {
        val access = FakePersistedPlaylistFileGrantAccess().apply {
            takeFailure = SecurityException("already granted")
            hasReadPermission = true
            releaseFailure = SecurityException("already released")
        }
        val repository = LocalPlaylistFileGrantRepository(FakeGrantPlaylistDao(), access)

        val grant = checkNotNull(repository.acquire(URI))
        repository.release(grant)
        repository.release(grant)

        assertEquals(1, access.releaseAttempts)
    }

    private companion object {
        const val URI = "content://documents/news.m3u"
        const val OTHER_URI = "content://documents/sports.m3u"
    }
}

private class FakePersistedPlaylistFileGrantAccess : PersistedPlaylistFileGrantAccess {
    var takeFailure: Throwable? = null
    var releaseFailure: Throwable? = null
    var hasReadPermission = false
    var releaseAttempts = 0
    val releasedUris = mutableListOf<String>()

    override fun takeReadPermission(uri: String) {
        takeFailure?.let { throw it }
    }

    override fun hasReadPermission(uri: String): Boolean = hasReadPermission

    override fun releaseReadPermission(uri: String) {
        releaseAttempts++
        releaseFailure?.let { throw it }
        releasedUris += uri
    }
}

private class FakeGrantPlaylistDao(
    var referenceCount: Int = 0,
) : PlaylistDao {
    override fun observeSummaries(): Flow<List<PlaylistSummaryEntity>> = emptyFlow()
    override fun observePlaylist(id: Long): Flow<PlaylistWithItems?> = emptyFlow()
    override suspend fun getPlaylist(id: Long): PlaylistWithItems? = null
    override suspend fun getItems(playlistId: Long): List<PlaylistItemEntity> = emptyList()
    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long = error("Not used")
    override suspend fun insertItemsIgnore(items: List<PlaylistItemEntity>): List<Long> = error("Not used")
    override suspend fun deleteItems(playlistId: Long) = error("Not used")
    override suspend fun updatePlaylist(playlist: PlaylistEntity) = error("Not used")
    override suspend fun deletePlaylist(playlistId: Long) = error("Not used")
    override suspend fun updateItems(items: List<PlaylistItemEntity>) = error("Not used")
    override suspend fun shiftItemPositions(playlistId: Long, offset: Int) = error("Not used")
    override suspend fun countPlaylistsByTypeAndSource(type: String, source: String): Int {
        assertEquals(PlaylistType.M3U_FILE.name, type)
        return referenceCount
    }
}
