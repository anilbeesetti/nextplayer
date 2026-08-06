package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.database.converter.UriListConverter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAudioPersistenceTest {

    private lateinit var database: MediaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MediaDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addExternalAudioTrackPreservesOrderAndDeduplicates() = runBlocking {
        val videoUri = "content://video/1"
        database.mediumStateDao().addExternalAudioTrack(videoUri, Uri.parse("content://audio/one"))
        database.mediumStateDao().addExternalAudioTrack(videoUri, Uri.parse("content://audio/two"))
        database.mediumStateDao().addExternalAudioTrack(videoUri, Uri.parse("content://audio/one"))

        assertEquals(
            listOf(Uri.parse("content://audio/one"), Uri.parse("content://audio/two")),
            UriListConverter.fromStringToList(database.mediumStateDao().get(videoUri)!!.externalAudioTracks),
        )
    }
}
