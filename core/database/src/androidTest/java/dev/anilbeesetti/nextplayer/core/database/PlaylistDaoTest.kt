package dev.anilbeesetti.nextplayer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
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

        dao.removeMissingLocalItems(setOf("content://one", "content://two"))

        assertEquals(listOf("content://one"), dao.getItems(firstId).map { it.uri })
        assertEquals(listOf("content://two"), dao.getItems(secondId).map { it.uri })
        assertEquals(listOf(0), dao.getItems(firstId).map { it.position })
        assertEquals(listOf(0), dao.getItems(secondId).map { it.position })

        dao.deletePlaylist(firstId)
        assertEquals(emptyList<Any>(), dao.getItems(firstId))
    }

    @Test
    fun lastPlayedTimestampsRetainThePreviousItemAfterTheNewestIsRemoved() = runTest {
        val playlistId = dao.createPlaylist(
            "Movies",
            listOf("content://one", "content://two"),
        )

        dao.markItemPlayed(playlistId, "content://one", playedAt = 100)
        dao.markItemPlayed(playlistId, "content://two", playedAt = 200)
        assertEquals(
            listOf(100L, 200L),
            dao.getItems(playlistId).map { it.lastPlayedAt },
        )

        dao.removeItem(playlistId, "content://two")
        assertEquals(
            listOf("content://one" to 100L),
            dao.getItems(playlistId).map { it.uri to it.lastPlayedAt },
        )
    }

    @Test
    fun playlistItemTableStoresIdentityMetadataAndPlaybackTimestamp() {
        val columns = database.openHelper.readableDatabase
            .query("PRAGMA table_info(`playlist_item`)")
            .use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }

        assertEquals(
            setOf(
                "playlist_id",
                "uri",
                "position",
                "title",
                "tvg_logo",
                "duration",
                "group_title",
                "last_played_at",
            ),
            columns,
        )
    }

    @Test
    fun linkedRefreshReplacesMetadataAndPreservesMatchingPlaybackHistory() = runTest {
        val playlistId = dao.createM3UPlaylist(
            playlist = PlaylistEntity(
                name = "Channels",
                type = "M3U_URL",
                source = "https://example.com/list.m3u",
            ),
            items = listOf(
                PlaylistItemEntity(0, "https://example.com/one", 0, title = "Before"),
                PlaylistItemEntity(0, "https://example.com/removed", 1),
            ),
        )
        dao.markItemPlayed(playlistId, "https://example.com/one", playedAt = 123)

        dao.replaceM3UItems(
            playlistId = playlistId,
            items = listOf(
                PlaylistItemEntity(
                    playlistId = 0,
                    uri = "https://example.com/new",
                    position = 0,
                    title = "New",
                ),
                PlaylistItemEntity(
                    playlistId = 0,
                    uri = "https://example.com/one",
                    position = 1,
                    title = "After",
                    tvgLogo = "https://example.com/logo.png",
                    duration = 42,
                    groupTitle = "News",
                ),
            ),
            refreshedAt = 999,
        )

        val playlist = dao.getPlaylist(playlistId)
        assertEquals(999L, playlist?.playlist?.lastRefreshedAt)
        assertEquals(
            listOf("https://example.com/new", "https://example.com/one"),
            playlist?.items?.sortedBy { it.position }?.map { it.uri },
        )
        val retained = playlist?.items?.single { it.uri.endsWith("/one") }
        assertEquals("After", retained?.title)
        assertEquals("https://example.com/logo.png", retained?.tvgLogo)
        assertEquals(42, retained?.duration)
        assertEquals("News", retained?.groupTitle)
        assertEquals(123L, retained?.lastPlayedAt)
    }

    @Test
    fun missingMediaCleanupIgnoresLinkedPlaylists() = runTest {
        val linkedId = dao.createM3UPlaylist(
            playlist = PlaylistEntity(
                name = "Channels",
                type = "M3U_URL",
                source = "https://example.com/list.m3u",
            ),
            items = listOf(
                PlaylistItemEntity(0, "https://example.com/live", 0),
            ),
        )

        dao.removeMissingLocalItems(emptySet())

        assertEquals(
            listOf("https://example.com/live"),
            dao.getItems(linkedId).map { it.uri },
        )
    }

    @Test
    fun deletingPlaylistEntityCascadesThroughForeignKey() = runTest {
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Movies"))
        dao.addItems(playlistId, listOf("content://one"))

        dao.deletePlaylist(playlistId)

        assertEquals(emptyList<Any>(), dao.getItems(playlistId))
    }
}
