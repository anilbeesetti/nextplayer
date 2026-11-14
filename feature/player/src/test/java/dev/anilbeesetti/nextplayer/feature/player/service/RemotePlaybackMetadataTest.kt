package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemotePlaybackMetadataTest {
    @Test
    fun addedCastSubtitleIsSelectedOnceWhenReceiverReportsItsTrack() {
        val exo = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        val subtitle = MediaItem.SubtitleConfiguration.Builder(
            android.net.Uri.parse("file:///added.srt"),
        )
            .setId("added").build()
        exo.setMediaItem(
            MediaItem.Builder().setMediaId("video").setUri("file:///video.mp4")
                .setSubtitleConfigurations(listOf(subtitle)).build(),
        )
        val group = TrackGroup(
            "item=1,track=1000000000",
            Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build(),
        )
        val audio = TrackGroup(
            "item=1,track=2",
            Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build(),
        )
        val tracks = Tracks(
            ImmutableList.of(
                Tracks.Group(
                    audio,
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(false),
                ),
                Tracks.Group(
                    group,
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(false),
                ),
            ),
        )
        val selections = mutableListOf<TrackSelectionParameters>()
        val player = object : ForwardingPlayer(exo) {
            override fun getDeviceInfo() =
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
            override fun getCurrentTracks() = tracks
            override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
                selections += parameters
                super.setTrackSelectionParameters(parameters)
            }
        }
        val session = MediaSession.Builder(
            RuntimeEnvironment.getApplication(),
            player,
        ).build()
        val service = PlayerService()
        fun field(name: String) = PlayerService::class.java.getDeclaredField(name).apply {
            isAccessible =
                true
        }
        field("mediaSession").set(
            service,
            session,
        )
        field("pendingCastSubtitleSelection").set(
            service,
            PendingCastSubtitleSelection(
                "video",
                "added",
                0,
            ),
        )
        val listener = field("playbackStateListener").get(service) as Player.Listener
        try {
            listener.onTracksChanged(Tracks.EMPTY)
            listener.onTracksChanged(Tracks(ImmutableList.of(tracks.groups.last())))
            assertEquals(
                0,
                selections.size,
            )
            listener.onTracksChanged(tracks)
            assertEquals(
                group,
                selections.last().overrides.values.single {
                    it.type == C.TRACK_TYPE_TEXT
                }.mediaTrackGroup,
            )
            assertEquals(
                audio,
                selections.last().overrides.values.single {
                    it.type ==
                        C.TRACK_TYPE_AUDIO
                }.mediaTrackGroup,
            )
            listener.onTracksChanged(tracks)
            assertEquals(
                2,
                selections.size,
            )
            assertNull(field("pendingCastSubtitleSelection").get(service))
        } finally {
            session.release()
            exo.release()
        }
    }

    @Test
    fun positionChangesIgnoreUnresolvedReceiverItems() {
        val recorded = mutableListOf<Pair<String, Long>>()
        startKoin {
            modules(
                module {
                    single<MediaRepository> {
                        object : MediaRepository by FakeMediaRepository() {
                            override suspend fun updateMediumPosition(uri: String, position: Long) {
                                recorded += uri to position
                            }
                        }
                    }
                },
            )
        }
        val context = RuntimeEnvironment.getApplication()
        val exo = ExoPlayer.Builder(context).build()
        var item = MediaItem.EMPTY
        val player = object : ForwardingPlayer(exo) {
            override fun getCurrentMediaItem() = item
            override fun getCurrentPosition() = 32_000L
        }
        val session = MediaSession.Builder(
            context,
            player,
        ).build()
        val service = PlayerService()
        PlayerService::class.java.getDeclaredField("mediaSession")
            .apply { isAccessible = true }.set(
                service,
                session,
            )
        val listener = PlayerService::class.java.getDeclaredField("playbackStateListener")
            .apply { isAccessible = true }.get(service) as Player.Listener
        try {
            listener.onIsPlayingChanged(false)
            item = MediaItem.Builder().setMediaId("content://media/42")
                .setUri("content://media/42").build()
            listener.onIsPlayingChanged(true)
            item = MediaItem.EMPTY
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(
                listOf("content://media/42" to 32_000L),
                recorded,
            )
        } finally {
            session.release()
            exo.release()
            stopKoin()
        }
    }

    @Test
    fun unresolvedHandoffDoesNotLoadArtworkOrRestoreStaleResumePosition() {
        val context = RuntimeEnvironment.getApplication()
        val exo = ExoPlayer.Builder(context).build()
        var seeks = 0
        val player = object : ForwardingPlayer(exo) {
            override fun getDeviceInfo() =
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
            override fun seekTo(positionMs: Long) {
                seeks++
            }
        }
        val session = MediaSession.Builder(
            context,
            player,
        ).build()
        val service = PlayerService()
        fun field(name: String) = PlayerService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
        field("mediaSession").set(
            service,
            session,
        )
        val item = MediaItem.fromUri("content://media/42").copy(positionMs = 99_000)
        field("lastPlaybackMediaId").set(
            service,
            item.mediaId,
        )
        field("lastPlaybackType").set(
            service,
            DeviceInfo.PLAYBACK_TYPE_LOCAL,
        )
        val listener = field("playbackStateListener").get(service) as Player.Listener
        try {
            listener.onMediaItemTransition(
                null,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            listener.onMediaItemTransition(
                MediaItem.EMPTY,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            assertNull(field("artworkLoadJob").get(service))
            // The resolved item may load the phone's thumbnail for the cast notification.
            listener.onMediaItemTransition(
                item,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            assertEquals(
                0,
                seeks,
            )
        } finally {
            session.release()
            exo.release()
        }
    }

    @Test
    fun resumePositionReachesThePhonePlayerBeforeTheSessionPlayerCanSeek() {
        startKoin {
            modules(
                module {
                    single<MediaRepository> { FakeMediaRepository() }
                    single<PreferencesRepository> { FakePreferencesRepository() }
                },
            )
        }
        val context = RuntimeEnvironment.getApplication()
        val decoders = DecoderManager()
        val exo = ExoPlayer.Builder(
            context,
            NextRenderersFactory(context).setDecoderManager(decoderManager = decoders),
        ).build()
        // Like CastPlayer, a SimpleBasePlayer refuses seeks until the item is prepared.
        val session = MediaSession.Builder(
            context,
            ForwardingSimpleBasePlayer(exo),
        ).setId("resume").build()
        try {
            decoders.attach(player = exo)
            val item = MediaItem.fromUri("content://media/42").copy(positionMs = 99_000)
            exo.setMediaItem(item)
            val service = PlayerService()
            fun field(name: String) = PlayerService::class.java.getDeclaredField(name)
                .apply { isAccessible = true }
            field("mediaSession").set(
                service,
                session,
            )
            field("localPlayer").set(
                service,
                exo,
            )
            field("decoderManager").set(
                service,
                decoders,
            )
            field("trackSelector").set(
                service,
                DefaultTrackSelector(context),
            )
            val listener = field("playbackStateListener").get(service) as Player.Listener
            listener.onMediaItemTransition(
                item,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            assertEquals(
                99_000,
                exo.currentPosition,
            )
        } finally {
            session.release()
            exo.release()
            stopKoin()
        }
    }
}
