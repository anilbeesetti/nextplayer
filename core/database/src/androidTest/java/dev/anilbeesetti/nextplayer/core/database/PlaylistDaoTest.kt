package dev.anilbeesetti.nextplayer.core.database

import android.database.sqlite.SQLiteConstraintException
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
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var db: MediaDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun duplicateNormalizedNameIsRejected() = runTest {
        dao.insertPlaylist(PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"))

        assertFailsWith<SQLiteConstraintException> {
            dao.insertPlaylist(
                PlaylistEntity(name = " movies ", normalizedName = "movies", type = "EDITABLE"),
            )
        }
    }

    @Test
    fun addAndMoveKeepUniqueContiguousOrder() = runTest {
        val id = dao.insertPlaylist(
            PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"),
        )

        dao.addItems(
            id,
            listOf(item(id, "content://1", 0), item(id, "content://2", 1), item(id, "content://1", 2)),
        )
        dao.moveItem(id, "content://2", 0)

        assertEquals(listOf("content://2", "content://1"), dao.getItems(id).map { it.uri })
        assertEquals(listOf(0, 1), dao.getItems(id).map { it.position })
    }

    @Test
    fun itemMetadataRoundTrips() = runTest {
        val id = dao.insertPlaylist(
            PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"),
        )
        dao.addItems(
            id,
            listOf(
                PlaylistItemEntity(
                    playlistId = id,
                    uri = "content://video/1",
                    title = "First",
                    position = 0,
                    imageUrl = "https://images.example/first.png",
                    displayPath = "/storage/emulated/0/Movies",
                ),
            ),
        )

        val item = dao.getItems(id).single()
        assertEquals("https://images.example/first.png", item.imageUrl)
        assertEquals("/storage/emulated/0/Movies", item.displayPath)
    }

    @Test
    fun deletingPlaylistCascadesToItems() = runTest {
        val id = dao.insertPlaylist(
            PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"),
        )
        dao.addItems(id, listOf(item(id, "content://1", 0)))

        dao.deletePlaylist(id)

        assertEquals(emptyList<PlaylistItemEntity>(), dao.getItems(id))
    }

    @Test
    fun getPlaylistOrdersItemsByPersistedPosition() = runTest {
        val id = insertPlaylistWithItemsInNonPositionOrder()

        assertEquals(
            listOf("content://first", "content://second", "content://third"),
            dao.getPlaylist(id)?.items?.map { it.uri },
        )
    }

    @Test
    fun observePlaylistOrdersItemsByPersistedPosition() = runTest {
        val id = insertPlaylistWithItemsInNonPositionOrder()

        assertEquals(
            listOf("content://first", "content://second", "content://third"),
            dao.observePlaylist(id).first()?.items?.map { it.uri },
        )
    }

    private suspend fun insertPlaylistWithItemsInNonPositionOrder(): Long {
        val id = dao.insertPlaylist(
            PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"),
        )
        dao.insertItemsIgnore(
            listOf(
                item(id, "content://third", 2),
                item(id, "content://first", 0),
                item(id, "content://second", 1),
            ),
        )
        return id
    }

    private fun item(playlistId: Long, uri: String, position: Int) = PlaylistItemEntity(
        playlistId = playlistId,
        uri = uri,
        title = null,
        position = position,
    )
}
