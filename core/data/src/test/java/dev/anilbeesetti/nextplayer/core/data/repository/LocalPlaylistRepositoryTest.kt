package dev.anilbeesetti.nextplayer.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import dev.anilbeesetti.nextplayer.core.data.playlist.M3uParser
import dev.anilbeesetti.nextplayer.core.data.playlist.PlaylistEntryLimitExceededException
import dev.anilbeesetti.nextplayer.core.data.playlist.PlaylistSourceContent
import dev.anilbeesetti.nextplayer.core.data.playlist.PlaylistSourceReader
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistSummaryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistWithItems
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaylistRepositoryTest {
    private lateinit var dao: FakePlaylistDao
    private lateinit var sourceReader: FakePlaylistSourceReader
    private lateinit var fileGrantRepository: FakePlaylistFileGrantRepository
    private lateinit var repository: LocalPlaylistRepository

    @Before
    fun setUp() {
        dao = FakePlaylistDao()
        sourceReader = FakePlaylistSourceReader()
        fileGrantRepository = FakePlaylistFileGrantRepository()
        repository = LocalPlaylistRepository(dao, M3uParser(), sourceReader, fileGrantRepository)
    }

    @Test
    fun createEditableTrimsNameAndMapsCaseFoldedConflict() = runTest {
        val id = repository.createEditable("  Movies  ")

        assertEquals("Movies", repository.observePlaylist(id).first()?.name)
        assertFailsWith<PlaylistNameConflictException> {
            repository.createEditable(" movies ")
        }
    }

    @Test
    fun addItemsKeepsFirstSeenUniqueUris() = runTest {
        val id = repository.createEditable("Movies")

        val added = repository.addItems(
            id,
            listOf(
                PlaylistItemInput("content://video/1", "First"),
                PlaylistItemInput("content://video/2", "Second"),
                PlaylistItemInput("content://video/1", "Duplicate"),
            ),
        )

        assertEquals(2, added)
        assertEquals(
            listOf("content://video/1", "content://video/2"),
            repository.observePlaylist(id).first()?.items?.map { it.uriString },
        )
    }

    @Test
    fun editableItemMetadataRoundTrips() = runTest {
        val id = repository.createEditable("Movies")

        repository.addItems(
            id,
            listOf(
                PlaylistItemInput(
                    uriString = "content://video/1",
                    title = "First",
                    imageUrl = "https://images.example/first.png",
                    displayPath = "/storage/emulated/0/Movies",
                ),
            ),
        )

        val item = repository.observePlaylist(id).first()!!.items.single()
        assertEquals("https://images.example/first.png", item.imageUrl)
        assertEquals("/storage/emulated/0/Movies", item.displayPath)
    }

    @Test
    fun editablePlaylistCanMoveItems() = runTest {
        val id = repository.createEditable("Movies")
        repository.addItems(
            id,
            listOf(PlaylistItemInput("content://1"), PlaylistItemInput("content://2")),
        )

        repository.moveItem(id, "content://2", 0)

        assertEquals(
            listOf("content://2", "content://1"),
            repository.observePlaylist(id).first()?.items?.map { it.uriString },
        )
    }

    @Test
    fun linkedPlaylistRejectsManualMutation() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId

        assertFailsWith<LinkedPlaylistReadOnlyException> {
            repository.addItems(id, listOf(PlaylistItemInput("content://video/1")))
        }
        assertFailsWith<LinkedPlaylistReadOnlyException> {
            repository.moveItem(id, "content://video/1", 0)
        }
    }

    @Test
    fun linkedCreationRetainsTypeSourceAndParsedItems() = runTest {
        sourceReader.content = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://images.example/first.png",First
            https://example.test/one.mp4
            https://example.test/two.mp4
            https://example.test/one.mp4
        """.trimIndent()

        val result = repository.createLinked(" News ", PlaylistType.M3U_URL, SOURCE)
        val playlist = repository.observePlaylist(result.playlistId).first()

        assertEquals(2, result.itemCount)
        assertEquals("News", playlist?.name)
        assertEquals(PlaylistType.M3U_URL, playlist?.type)
        assertEquals(SOURCE, playlist?.source)
        assertEquals(
            listOf("https://example.test/one.mp4", "https://example.test/two.mp4"),
            playlist?.items?.map { it.uriString },
        )
        assertEquals("https://images.example/first.png", playlist?.items?.first()?.imageUrl)
    }

    @Test
    fun failedLinkedCreationLeavesNoPlaylist() = runTest {
        sourceReader.failure = IOException("offline")

        assertFailsWith<PlaylistSourceException> {
            repository.createLinked("News", PlaylistType.M3U_URL, SOURCE)
        }

        assertTrue(repository.observePlaylists().first().isEmpty())
    }

    @Test
    fun linkedCreationMapsNormalizedNameConstraint() = runTest {
        dao.linkedInsertFailure = FakeSQLiteConstraintException(
            "UNIQUE constraint failed: playlist.normalized_name",
        )

        assertFailsWith<PlaylistNameConflictException> {
            repository.createLinked("News", PlaylistType.M3U_URL, SOURCE)
        }
    }

    @Test
    fun linkedCreationRethrowsNonNameConstraint() = runTest {
        val databaseFailure = FakeSQLiteConstraintException(
            "UNIQUE constraint failed: playlist_item.playlist_id, playlist_item.position",
        )
        dao.linkedInsertFailure = databaseFailure

        val failure = assertFailsWith<SQLiteConstraintException> {
            repository.createLinked("News", PlaylistType.M3U_URL, SOURCE)
        }

        assertSame(databaseFailure, failure)
    }

    @Test
    fun refreshReplacesCachedItemsAndReportsSkippedEntries() = runTest {
        sourceReader.content = """
            #EXTINF:-1 tvg-logo="https://images.example/original.png",Original
            https://example.test/one.mp4
        """.trimIndent()
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        assertEquals(
            "https://images.example/original.png",
            repository.observePlaylist(id).first()?.items?.single()?.imageUrl,
        )
        sourceReader.content = """
            #EXTINF:-1 tvg-logo="https://images.example/replacement.png",Replacement
            https://example.test/two.mp4
            unsupported-entry
            https://example.test/two.mp4
        """.trimIndent()
        sourceReader.resolve = { raw -> raw.takeIf { it.startsWith("https://") } }

        val result = repository.refresh(id)

        assertEquals(1, result.itemCount)
        assertEquals(1, result.skippedEntries)
        assertEquals(
            listOf("https://example.test/two.mp4"),
            repository.observePlaylist(id).first()?.items?.map { it.uriString },
        )
        assertEquals(
            "https://images.example/replacement.png",
            repository.observePlaylist(id).first()?.items?.single()?.imageUrl,
        )
    }

    @Test
    fun failedRefreshKeepsCachedItems() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        val cachedItem = repository.observePlaylist(id).first()!!.items.single()
        sourceReader.failure = IOException("offline")

        val failure = assertFailsWith<PlaylistSourceException> { repository.refresh(id) }

        assertSame(sourceReader.failure, failure.cause)
        assertEquals(cachedItem, repository.observePlaylist(id).first()!!.items.single())
    }

    @Test
    fun parseFailureKeepsCachedItems() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        sourceReader.resolve = { error("resolver failed") }

        assertFailsWith<PlaylistSourceException> { repository.refresh(id) }

        assertEquals(
            listOf("https://example.test/one.mp4"),
            dao.getItems(id).map { it.uri },
        )
    }

    @Test
    fun entryLimitFailureKeepsCachedItems() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        sourceReader.content = buildString {
            repeat(20_001) { index -> append("https://example.test/$index.mp4\n") }
        }

        val failure = assertFailsWith<PlaylistSourceException> { repository.refresh(id) }

        assertTrue(failure.cause is PlaylistEntryLimitExceededException)
        assertEquals("Playlist contains more than 20000 entries", failure.cause?.message)
        assertEquals(
            listOf("https://example.test/one.mp4"),
            dao.getItems(id).map { it.uri },
        )
    }

    @Test
    fun databaseFailureDuringRefreshKeepsCachedItems() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        sourceReader.content = "https://example.test/two.mp4"
        val databaseFailure = IOException("database write failed")
        dao.replaceFailure = databaseFailure

        val failure = assertFailsWith<IOException> { repository.refresh(id) }

        assertSame(databaseFailure, failure)
        assertEquals(
            listOf("https://example.test/one.mp4"),
            dao.getItems(id).map { it.uri },
        )
    }

    @Test
    fun missingPlaylistRefreshesValidateBeforeSerializationAndSourceRead() = runTest {
        val firstLookup = dao.pauseNextPlaylistLookup()
        val firstRefresh = async {
            assertFailsWith<IllegalArgumentException> { repository.refresh(404) }
        }
        firstLookup.started.await()

        val secondRefresh = async {
            assertFailsWith<IllegalArgumentException> { repository.refresh(404) }
        }
        runCurrent()

        try {
            assertEquals(2, dao.maxConcurrentPlaylistLookups)
            assertEquals(0, sourceReader.readCount)
        } finally {
            firstLookup.release.complete(Unit)
        }
        firstRefresh.await()
        secondRefresh.await()
    }

    @Test
    fun editablePlaylistRefreshesValidateBeforeSerializationAndSourceRead() = runTest {
        val id = repository.createEditable("Movies")
        val firstLookup = dao.pauseNextPlaylistLookup()
        val firstRefresh = async {
            assertFailsWith<IllegalArgumentException> { repository.refresh(id) }
        }
        firstLookup.started.await()

        val secondRefresh = async {
            assertFailsWith<IllegalArgumentException> { repository.refresh(id) }
        }
        runCurrent()

        try {
            assertEquals(2, dao.maxConcurrentPlaylistLookups)
            assertEquals(0, sourceReader.readCount)
        } finally {
            firstLookup.release.complete(Unit)
        }
        firstRefresh.await()
        secondRefresh.await()
    }

    @Test
    fun samePlaylistRefreshesAreSerialized() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        val firstRead = sourceReader.pauseNextRead("https://example.test/two.mp4")
        val firstRefresh = async { repository.refresh(id) }
        firstRead.started.await()

        val secondRefresh = async { repository.refresh(id) }
        runCurrent()

        assertEquals(1, sourceReader.maxConcurrentReads)

        firstRead.release.complete(Unit)
        firstRefresh.await()
        secondRefresh.await()

        assertEquals(1, sourceReader.maxConcurrentReads)
        assertEquals(
            listOf("https://example.test/one.mp4"),
            repository.observePlaylist(id).first()?.items?.map { it.uriString },
        )
    }

    @Test
    fun differentPlaylistRefreshesMayRunConcurrently() = runTest {
        val firstId = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        val secondId = repository.createLinked("Sports", PlaylistType.M3U_URL, "$SOURCE?two").playlistId
        val firstRead = sourceReader.pauseNextRead("https://example.test/two.mp4")
        val refreshOne = async { repository.refresh(firstId) }
        firstRead.started.await()

        val refreshTwo = async { repository.refresh(secondId) }
        runCurrent()

        assertEquals(2, sourceReader.maxConcurrentReads)
        firstRead.release.complete(Unit)
        refreshOne.await()
        refreshTwo.await()
    }

    @Test
    fun waitingRefreshRevalidatesPlaylistBeforeSourceRead() = runTest {
        val id = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId
        val firstRead = sourceReader.pauseNextRead("https://example.test/two.mp4")
        val firstRefresh = async { repository.refresh(id) }
        firstRead.started.await()

        val waitingRefresh = async {
            assertFailsWith<IllegalArgumentException> { repository.refresh(id) }
        }
        runCurrent()
        assertEquals(3, dao.playlistLookupCount)

        repository.delete(id)
        firstRead.release.complete(Unit)
        firstRefresh.await()
        waitingRefresh.await()

        assertEquals(2, sourceReader.readCount)
    }

    @Test
    fun deleteRemovesPlaylist() = runTest {
        val id = repository.createEditable("Movies")

        repository.delete(id)

        assertNull(repository.observePlaylist(id).first())
    }

    @Test
    fun deletingM3uFileChecksGrantAfterDatabaseDeletion() = runTest {
        val id = repository.createLinked("File", PlaylistType.M3U_FILE, "content://documents/news.m3u").playlistId
        fileGrantRepository.onReleaseIfUnused = { uri ->
            assertEquals("content://documents/news.m3u", uri)
            assertNull(dao.getPlaylist(id))
        }

        repository.delete(id)

        assertEquals(listOf("content://documents/news.m3u"), fileGrantRepository.releaseIfUnusedUris)
    }

    @Test
    fun deletingUrlAndEditablePlaylistsDoesNotTouchFileGrants() = runTest {
        val editableId = repository.createEditable("Movies")
        val urlId = repository.createLinked("News", PlaylistType.M3U_URL, SOURCE).playlistId

        repository.delete(editableId)
        repository.delete(urlId)

        assertTrue(fileGrantRepository.releaseIfUnusedUris.isEmpty())
    }

    private companion object {
        const val SOURCE = "https://example.test/list.m3u"
    }
}

private class FakePlaylistFileGrantRepository : PlaylistFileGrantRepository {
    val releaseIfUnusedUris = mutableListOf<String>()
    var onReleaseIfUnused: suspend (String) -> Unit = {}

    override suspend fun acquire(uri: String): PlaylistFileGrant? = error("Not used")
    override suspend fun reserve(grant: PlaylistFileGrant): PlaylistFileGrant? = error("Not used")
    override suspend fun retain(grant: PlaylistFileGrant) = error("Not used")
    override suspend fun release(grant: PlaylistFileGrant) = error("Not used")
    override suspend fun releaseIfUnused(uri: String) {
        releaseIfUnusedUris += uri
        onReleaseIfUnused(uri)
    }
}

private class FakePlaylistSourceReader : PlaylistSourceReader {
    var content = "https://example.test/one.mp4"
    var resolve: (String) -> String? = { it }
    var failure: Throwable? = null
    var maxConcurrentReads = 0
        private set
    var readCount = 0
        private set
    private var activeReads = 0
    private var pausedRead: PausedRead? = null

    fun pauseNextRead(content: String): PausedRead = PausedRead(content).also { pausedRead = it }

    override suspend fun read(type: PlaylistType, source: String): PlaylistSourceContent {
        readCount++
        failure?.let { throw it }
        activeReads++
        maxConcurrentReads = maxOf(maxConcurrentReads, activeReads)
        return try {
            val pause = pausedRead.also { pausedRead = null }
            if (pause != null) {
                pause.started.complete(Unit)
                pause.release.await()
                PlaylistSourceContent(pause.content, resolve)
            } else {
                PlaylistSourceContent(content, resolve)
            }
        } finally {
            activeReads--
        }
    }

    data class PausedRead(
        val content: String,
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val release: CompletableDeferred<Unit> = CompletableDeferred(),
    )
}

private class FakePlaylistDao : PlaylistDao {
    private val playlists = linkedMapOf<Long, PlaylistEntity>()
    private val items = linkedMapOf<Long, MutableList<PlaylistItemEntity>>()
    private val summaries = MutableStateFlow(emptyList<PlaylistSummaryEntity>())
    private val details = mutableMapOf<Long, MutableStateFlow<PlaylistWithItems?>>()
    private var nextId = 1L
    private var activePlaylistLookups = 0
    private var pausedPlaylistLookup: PausedPlaylistLookup? = null

    var replaceFailure: Throwable? = null
    var linkedInsertFailure: Throwable? = null
    var maxConcurrentPlaylistLookups = 0
        private set
    var playlistLookupCount = 0
        private set

    fun pauseNextPlaylistLookup(): PausedPlaylistLookup =
        PausedPlaylistLookup().also { pausedPlaylistLookup = it }

    override fun observeSummaries(): Flow<List<PlaylistSummaryEntity>> = summaries

    override fun observePlaylist(id: Long): Flow<PlaylistWithItems?> =
        details.getOrPut(id) { MutableStateFlow(snapshot(id)) }

    override suspend fun getPlaylist(id: Long): PlaylistWithItems? {
        playlistLookupCount++
        activePlaylistLookups++
        maxConcurrentPlaylistLookups = maxOf(maxConcurrentPlaylistLookups, activePlaylistLookups)
        return try {
            val pause = pausedPlaylistLookup.also { pausedPlaylistLookup = null }
            if (pause != null) {
                pause.started.complete(Unit)
                pause.release.await()
            }
            snapshot(id)
        } finally {
            activePlaylistLookups--
        }
    }

    override suspend fun getItems(playlistId: Long): List<PlaylistItemEntity> =
        items[playlistId].orEmpty().sortedBy { it.position }

    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        if (playlists.values.any { it.normalizedName == playlist.normalizedName }) {
            throw FakeSQLiteConstraintException("UNIQUE constraint failed: playlist.normalized_name")
        }
        val id = nextId++
        playlists[id] = playlist.copy(id = id)
        items[id] = mutableListOf()
        emit(id)
        return id
    }

    override suspend fun insertItemsIgnore(items: List<PlaylistItemEntity>): List<Long> {
        return items.map { item ->
            val stored = this.items.getOrPut(item.playlistId) { mutableListOf() }
            if (stored.any { it.uri == item.uri }) {
                -1L
            } else {
                stored += item
                emit(item.playlistId)
                stored.size.toLong()
            }
        }
    }

    override suspend fun deleteItems(playlistId: Long) {
        items[playlistId]?.clear()
        emit(playlistId)
    }

    override suspend fun updatePlaylist(playlist: PlaylistEntity) {
        playlists[playlist.id] = playlist
        emit(playlist.id)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlists.remove(playlistId)
        items.remove(playlistId)
        emit(playlistId)
    }

    override suspend fun countPlaylistsByTypeAndSource(type: String, source: String): Int =
        playlists.values.count { it.type == type && it.source == source }

    override suspend fun updateItems(items: List<PlaylistItemEntity>) {
        items.groupBy { it.playlistId }.forEach { (playlistId, updated) ->
            this.items[playlistId] = updated.toMutableList()
            emit(playlistId)
        }
    }

    override suspend fun shiftItemPositions(playlistId: Long, offset: Int) {
        items[playlistId] = getItems(playlistId)
            .map { it.copy(position = it.position + offset) }
            .toMutableList()
        emit(playlistId)
    }

    override suspend fun insertLinkedPlaylist(
        playlist: PlaylistEntity,
        items: List<PlaylistItemEntity>,
    ): Long {
        linkedInsertFailure?.let { throw it }
        val id = insertPlaylist(playlist)
        addItems(id, items)
        return id
    }

    override suspend fun replaceItems(
        playlistId: Long,
        items: List<PlaylistItemEntity>,
        refreshedAt: Long,
    ) {
        replaceFailure?.let { throw it }
        val playlist = playlists[playlistId] ?: return
        this.items[playlistId] = items.mapIndexed { index, item ->
            item.copy(playlistId = playlistId, position = index)
        }.toMutableList()
        playlists[playlistId] = playlist.copy(updatedAt = refreshedAt, lastRefreshedAt = refreshedAt)
        emit(playlistId)
    }

    override suspend fun addItems(playlistId: Long, items: List<PlaylistItemEntity>): Int {
        val current = getItems(playlistId).toMutableList()
        val initialSize = current.size
        val known = current.mapTo(mutableSetOf()) { it.uri }
        items.forEach { item ->
            if (known.add(item.uri)) current += item.copy(playlistId = playlistId, position = current.size)
        }
        this.items[playlistId] = current
        emit(playlistId)
        return current.size - initialSize
    }

    override suspend fun moveItem(playlistId: Long, uri: String, toIndex: Int) {
        val current = getItems(playlistId).toMutableList()
        val from = current.indexOfFirst { it.uri == uri }
        if (from < 0 || current.size < 2) return
        current.add(toIndex.coerceIn(0, current.lastIndex), current.removeAt(from))
        items[playlistId] = current.mapIndexed { index, item -> item.copy(position = index) }.toMutableList()
        emit(playlistId)
    }

    private fun snapshot(id: Long): PlaylistWithItems? = playlists[id]?.let { playlist ->
        PlaylistWithItems(playlist, items[id].orEmpty().sortedBy { it.position })
    }

    private fun emit(changedId: Long) {
        summaries.value = playlists.values.map { playlist ->
            PlaylistSummaryEntity(
                id = playlist.id,
                name = playlist.name,
                type = playlist.type,
                itemCount = items[playlist.id].orEmpty().size,
                lastRefreshedAt = playlist.lastRefreshedAt,
            )
        }
        details.getOrPut(changedId) { MutableStateFlow(null) }.value = snapshot(changedId)
    }

    data class PausedPlaylistLookup(
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val release: CompletableDeferred<Unit> = CompletableDeferred(),
    )
}

private class FakeSQLiteConstraintException(
    private val constraintMessage: String,
) : SQLiteConstraintException() {
    override val message: String
        get() = constraintMessage
}
