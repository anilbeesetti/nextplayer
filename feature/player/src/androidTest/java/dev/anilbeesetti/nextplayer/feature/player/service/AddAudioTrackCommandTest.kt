package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.session.SessionResult
import androidx.media3.test.utils.FakePlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.feature.player.extensions.externalAudioTrackUris
import dev.anilbeesetti.nextplayer.feature.player.extensions.getManuallySelectedTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddAudioTrackCommandTest {

    @Test
    fun externalControllerIsNotOfferedAddAudioTrackWhileOtherCommandsRemainAvailable() {
        val commands = commandsForController(
            controllerPackageName = "com.example.external",
            applicationPackageName = "dev.anilbeesetti.nextplayer",
        )

        assertFalse(CustomCommands.ADD_AUDIO_TRACK.sessionCommand in commands)
        assertTrue(
            commands.containsAll(
                CustomCommands.entries
                    .filterNot { it == CustomCommands.ADD_AUDIO_TRACK }
                    .map { it.sessionCommand },
            ),
        )
    }

    @Test
    fun applicationControllerIsOfferedAddAudioTrack() {
        val applicationPackageName = "dev.anilbeesetti.nextplayer"

        val commands = commandsForController(
            controllerPackageName = applicationPackageName,
            applicationPackageName = applicationPackageName,
        )

        assertTrue(CustomCommands.ADD_AUDIO_TRACK.sessionCommand in commands)
        assertNull(
            commandPermissionError(
                controllerPackageName = applicationPackageName,
                applicationPackageName = applicationPackageName,
                command = CustomCommands.ADD_AUDIO_TRACK,
            ),
        )
    }

    @Test
    fun externalControllerAddAudioTrackIsDeniedBeforeCommandHandling() = runOnMainThread {
        val repository = RecordingMediaRepository()
        val permissionError = commandPermissionError(
            controllerPackageName = "com.example.external",
            applicationPackageName = "dev.anilbeesetti.nextplayer",
            command = CustomCommands.ADD_AUDIO_TRACK,
        )

        val result = permissionError ?: runBlocking {
            handleAddAudioTrackCommand(
                audioTrackArgs("content://audio/external"),
                player = null,
                repository,
            )
        }

        assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED, result.resultCode)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun externalControllerOtherCustomCommandsRemainAuthorized() {
        assertNull(
            commandPermissionError(
                controllerPackageName = "com.example.external",
                applicationPackageName = "dev.anilbeesetti.nextplayer",
                command = CustomCommands.ADD_SUBTITLE_TRACK,
            ),
        )
    }

    @Test
    fun controllerCommandUsesAddAudioTrackActionAndUriArgument() {
        val audioUri = Uri.parse("content://audio/controller")

        assertEquals("ADD_AUDIO_TRACK", CustomCommands.ADD_AUDIO_TRACK.sessionCommand.customAction)
        assertEquals(
            audioUri.toString(),
            audioTrackArguments(audioUri).getString(CustomCommands.AUDIO_TRACK_URI_KEY),
        )
    }

    @Test
    fun missingUriReturnsBadValueWithoutRepositoryCalls() = runOnMainThread {
        val repository = RecordingMediaRepository()

        val result = runBlocking {
            handleAddAudioTrackCommand(Bundle.EMPTY, player = null, repository)
        }

        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result.resultCode)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun blankUriReturnsBadValueWithoutRepositoryCalls() = runOnMainThread {
        val repository = RecordingMediaRepository()
        val args = audioTrackArgs("  ")

        val result = runBlocking {
            handleAddAudioTrackCommand(args, player = null, repository)
        }

        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result.resultCode)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun validUriPersistsStateAndAttachesCurrentItem() = runOnMainThread {
        val repository = RecordingMediaRepository()
        val audioUri = Uri.parse("content://audio/new")
        val player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItem(mediaItem("content://video/current"))
        player.seekTo(8_765L)

        try {
            val result = runBlocking {
                handleAddAudioTrackCommand(audioTrackArgs(audioUri.toString()), player, repository)
            }

            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            assertEquals(
                listOf(
                    RepositoryCall.Position("content://video/current", 8_765L),
                    RepositoryCall.AudioTrack("content://video/current", 0),
                    RepositoryCall.ExternalAudio("content://video/current", audioUri),
                ),
                repository.calls,
            )
            assertEquals(listOf(audioUri), player.currentMediaItem!!.mediaMetadata.externalAudioTrackUris)
        } finally {
            player.release()
        }
    }

    @Test
    fun duplicateUriReturnsSuccessWithoutChangingItemOrRepository() = runOnMainThread {
        val repository = RecordingMediaRepository()
        val audioUri = Uri.parse("content://audio/existing")
        val currentItem = mediaItem("content://video/current", listOf(audioUri))
        val player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItem(currentItem)

        try {
            val result = runBlocking {
                handleAddAudioTrackCommand(audioTrackArgs(audioUri.toString()), player, repository)
            }

            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            assertSame(currentItem, player.currentMediaItem)
            assertTrue(repository.calls.isEmpty())
        } finally {
            player.release()
        }
    }

    @Test
    fun validUriWithoutPlayerReturnsInvalidState() = runOnMainThread {
        val repository = RecordingMediaRepository()

        val result = runBlocking {
            handleAddAudioTrackCommand(audioTrackArgs("content://audio/new"), player = null, repository)
        }

        assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, result.resultCode)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun validUriWithoutCurrentItemReturnsInvalidState() = runOnMainThread {
        val repository = RecordingMediaRepository()
        val player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()

        try {
            val result = runBlocking {
                handleAddAudioTrackCommand(audioTrackArgs("content://audio/new"), player, repository)
            }

            assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, result.resultCode)
            assertTrue(repository.calls.isEmpty())
        } finally {
            player.release()
        }
    }

    @Test
    fun supportedTrackIndexSkipsUnsupportedGroups() = runOnMainThread {
        val unsupported = audioGroup("unsupported", C.FORMAT_UNSUPPORTED_TYPE)
        val existing = audioGroup("existing", C.FORMAT_HANDLED)
        val appended = audioGroup("appended", C.FORMAT_HANDLED)
        val item = mediaItem("content://video/current")
        val itemData = MediaItemData.Builder("current")
            .setMediaItem(item)
            .setTracks(Tracks(listOf(unsupported, existing, appended)))
            .build()
        val player = FakePlayer(Player.STATE_READY, false, listOf(itemData), 1f, 0L)

        try {
            player.switchTrack(C.TRACK_TYPE_AUDIO, 1)

            val selectedGroup = player.trackSelectionParameters.overrides.values.single().mediaTrackGroup
            assertEquals(appended.mediaTrackGroup, selectedGroup)
            assertEquals(1, player.getManuallySelectedTrackIndex(C.TRACK_TYPE_AUDIO))
        } finally {
            player.release()
        }
    }

    private fun audioTrackArgs(value: String) = Bundle().apply {
        putString(CustomCommands.AUDIO_TRACK_URI_KEY, value)
    }

    private fun mediaItem(mediaId: String, externalAudioTrackUris: List<Uri> = emptyList()): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(externalAudioTrackUris = externalAudioTrackUris)
                    .build(),
            )
            .build()
    }

    private fun audioGroup(id: String, support: Int): Tracks.Group {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        return Tracks.Group(
            TrackGroup(id, format),
            false,
            intArrayOf(support),
            booleanArrayOf(false),
        )
    }

    private fun runOnMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class RecordingMediaRepository : MediaRepository by FakeMediaRepository() {
        val calls = mutableListOf<RepositoryCall>()

        override suspend fun updateMediumPosition(uri: String, position: Long) {
            calls += RepositoryCall.Position(uri, position)
        }

        override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
            calls += RepositoryCall.AudioTrack(uri, audioTrackIndex)
        }

        override suspend fun addExternalAudioTrackToMedium(uri: String, audioUri: Uri) {
            calls += RepositoryCall.ExternalAudio(uri, audioUri)
        }
    }

    private sealed interface RepositoryCall {
        data class Position(val uri: String, val position: Long) : RepositoryCall
        data class AudioTrack(val uri: String, val index: Int) : RepositoryCall
        data class ExternalAudio(val uri: String, val audioUri: Uri) : RepositoryCall
    }
}
