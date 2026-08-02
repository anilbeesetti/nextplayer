package dev.anilbeesetti.nextplayer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var database: MediaDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createAllowsDuplicateNamesAndDeduplicatesVideos() = runTest {
        val firstId = dao.createPlaylist("Movies", listOf("content://one", "content://one", "content://two"))
        dao.createPlaylist("Movies", emptyList())

        val summaries = dao.observeSummaries().first()

        assertEquals(listOf("Movies", "Movies"), summaries.map { it.name })
        assertEquals(2, summaries.first { it.id == firstId }.itemCount)
        assertEquals(
            listOf("content://one", "content://two"),
            dao.getItems(firstId).map { it.uri },
        )
    }

    @Test
    fun renameRemoveAndReorderKeepContiguousPositions() = runTest {
        val playlistId = dao.createPlaylist(
            "Before",
            listOf("content://one", "content://two", "content://three"),
        )

        dao.renamePlaylist(playlistId, "After")
        dao.removeItem(playlistId, "content://two")
        dao.replaceOrder(playlistId, listOf("content://three", "content://one"))

        val playlist = dao.observePlaylist(playlistId).first()
        assertEquals("After", playlist?.playlist?.name)
        assertEquals(
            listOf("content://three", "content://one"),
            dao.getItems(playlistId).map { it.uri },
        )
        assertEquals(listOf(0, 1), dao.getItems(playlistId).map { it.position })
    }

    @Test
    fun cleanupRemovesMissingVideosAndDeleteCascades() = runTest {
        val firstId = dao.createPlaylist("First", listOf("content://one", "content://missing"))
        val secondId = dao.createPlaylist("Second", listOf("content://missing", "content://two"))

        dao.removeMissingItems(setOf("content://one", "content://two"))

        assertEquals(listOf("content://one"), dao.getItems(firstId).map { it.uri })
        assertEquals(listOf("content://two"), dao.getItems(secondId).map { it.uri })
        assertEquals(listOf(0), dao.getItems(firstId).map { it.position })
        assertEquals(listOf(0), dao.getItems(secondId).map { it.position })

        dao.deletePlaylist(firstId)
        assertEquals(emptyList<Any>(), dao.getItems(firstId))
    }

    @Test
    fun lastPlayedUriIsStoredAndClearedWhenItsItemIsRemoved() = runTest {
        val playlistId = dao.createPlaylist(
            "Movies",
            listOf("content://one", "content://two"),
        )

        dao.markItemPlayed(playlistId, "content://two")
        assertEquals(
            "content://two",
            dao.observePlaylist(playlistId).first()?.playlist?.lastPlayedUri,
        )

        dao.removeItem(playlistId, "content://two")
        assertEquals(null, dao.observePlaylist(playlistId).first()?.playlist?.lastPlayedUri)
    }

    @Test
    fun playlistItemTableStoresOnlyIdentityAndOrder() {
        val columns = database.openHelper.readableDatabase
            .query("PRAGMA table_info(`playlist_item`)")
            .use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }

        assertEquals(setOf("playlist_id", "uri", "position"), columns)
    }

    @Test
    fun deletingPlaylistEntityCascadesThroughForeignKey() = runTest {
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Movies"))
        dao.addItems(playlistId, listOf("content://one"))

        dao.deletePlaylist(playlistId)

        assertEquals(emptyList<Any>(), dao.getItems(playlistId))
    }
}
