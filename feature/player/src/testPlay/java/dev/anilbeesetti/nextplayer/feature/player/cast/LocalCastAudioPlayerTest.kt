package dev.anilbeesetti.nextplayer.feature.player.cast

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
import dev.anilbeesetti.nextplayer.feature.player.MiniPlayerTestPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
class LocalCastAudioPlayerTest {
    @Test
    fun localEmbeddedAudioIsExposedWithoutLosingReceiverSubtitles() {
        withPlayer { media3, player, tracks ->
            val text = TrackGroup(
                "captions",
                Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build(),
            )
            media3.update {
                setPlaylist(
                    listOf(
                        media3.item(0).buildUpon().setTracks(
                            Tracks(
                                ImmutableList.of(
                                    Tracks.Group(
                                        text,
                                        false,
                                        intArrayOf(C.FORMAT_HANDLED),
                                        booleanArrayOf(true),
                                    ),
                                ),
                            ),
                        ).build(),
                    ),
                )
            }
            ShadowLooper.idleMainLooper()
            assertEquals(
                listOf(
                    "en",
                    "ja",
                    "en",
                ),
                player.currentTracks.groups.filter {
                    it.type ==
                        C.TRACK_TYPE_AUDIO
                }.map { it.getTrackFormat(0).language },
            )
            assertEquals(
                text,
                player.currentTracks.groups.first().mediaTrackGroup,
            )
            assertEquals(
                tracks[0].format,
                player.currentTracks.groups.first {
                    it.type ==
                        C.TRACK_TYPE_AUDIO &&
                        it.isSelected
                }.getTrackFormat(0),
            )
            assertEquals(
                "0",
                player.currentMediaItem?.mediaId,
            )
        }
    }

    @Test
    fun subtitleChoiceIsRestoredAfterAudioReloadWithoutForwardingSyntheticAudio() {
        withPlayer { media3, player, _ ->
            ReflectionHelpers.setField(
                player,
                "pendingText",
                setOf("item=1,track=1000000000") to 0,
            )
            val text = TrackGroup(
                "item=2,track=1000000000",
                Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build(),
            )
            media3.update {
                setPlaylist(
                    listOf(
                        media3.item(0).buildUpon().setTracks(
                            Tracks(
                                ImmutableList.of(
                                    Tracks.Group(
                                        text,
                                        false,
                                        intArrayOf(C.FORMAT_HANDLED),
                                        booleanArrayOf(false),
                                    ),
                                ),
                            ),
                        ).build(),
                    ),
                )
            }
            ShadowLooper.idleMainLooper()
            assertEquals(
                text,
                media3.selectedTracks?.overrides?.values?.single()?.mediaTrackGroup,
            )
            assertEquals(
                null,
                ReflectionHelpers.getField<Any?>(
                    player,
                    "pendingText",
                ),
            )
        }
    }

    @Test
    fun changingVideoCancelsTheOldAudioPreparation() {
        withPlayer { media3, player, _ ->
            val pending = SettableFuture.create<Void>()
            ReflectionHelpers.setField(
                player,
                "switching",
                pending,
            )
            media3.update { setPlaylist(listOf(media3.item(1))) }
            ShadowLooper.idleMainLooper()
            assertTrue(pending.isCancelled)
        }
    }

    @Test
    fun requestedAudioIsShownWhileTheCopyIsPreparing() {
        withPlayer { _, player, _ ->
            val state = ReflectionHelpers.callInstanceMethod<SimpleBasePlayer.State>(
                player,
                "getState",
            )
            val japanese = state.currentTracks.groups.filter {
                it.type == C.TRACK_TYPE_AUDIO
            }[1].mediaTrackGroup
            // The fake's PlaylistTimeline would otherwise restore its original empty tracks.
            val suggested = state.buildUpon().setPlaylist(
                state.timeline,
                state.currentTracks,
                state.currentMetadata,
            ).setTrackSelectionParameters(
                state.trackSelectionParameters.buildUpon().setOverrideForType(
                    TrackSelectionOverride(
                        japanese,
                        0,
                    ),
                ).build(),
            ).build()
            val pending = ReflectionHelpers.callInstanceMethod<SimpleBasePlayer.State>(
                player,
                "getPlaceholderState",
                ClassParameter.from(
                    SimpleBasePlayer.State::class.java,
                    suggested,
                ),
            )
            assertEquals(
                japanese,
                pending.currentTracks.groups.single {
                    it.type ==
                        C.TRACK_TYPE_AUDIO &&
                        it.isSelected
                }.mediaTrackGroup,
            )
        }
    }

    @Test
    fun receiversThatExposeAllAudioKeepNativeTrackSelection() {
        withPlayer { media3, player, tracks ->
            val native = tracks.map { track ->
                Tracks.Group(
                    TrackGroup(
                        "receiver:${track.sourceIndex}",
                        track.format,
                    ),
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(track.sourceIndex == 1),
                )
            }
            media3.update {
                setPlaylist(
                    listOf(
                        media3.item(
                            0,
                        ).buildUpon().setTracks(Tracks(ImmutableList.copyOf(native))).build(),
                    ),
                )
            }
            ShadowLooper.idleMainLooper()
            assertEquals(
                native,
                player.currentTracks.groups,
            )
        }
    }

    private fun withPlayer(
        check: (MiniPlayerTestPlayer, LocalCastAudioPlayer, List<LocalCastAudio.Track>) -> Unit,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val media3 = MiniPlayerTestPlayer()
        val player = LocalCastAudioPlayer(
            context,
            media3,
            LocalCastAudio(context),
        )
        val tracks = listOf(
            "eng",
            "jpn",
            "eng",
        ).mapIndexed { index, language ->
            LocalCastAudio.Track(
                index + 1,
                Format.Builder().setId(
                    "cast-local-audio:$index",
                ).setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage(language).build(),
                index == 0,
            )
        }
        try {
            media3.update {
                setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            }
            ShadowLooper.idleMainLooper()
            ReflectionHelpers.setField(
                player,
                "loaded",
                Uri.parse("file:///0.mp4") to tracks,
            )
            media3.remote(true)
            ShadowLooper.idleMainLooper()
            check(
                media3,
                player,
                tracks,
            )
        } finally {
            player.release()
        }
    }
}
