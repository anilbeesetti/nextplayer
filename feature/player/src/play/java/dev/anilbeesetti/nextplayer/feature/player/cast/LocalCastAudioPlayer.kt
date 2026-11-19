package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.extensions.castSubtitleIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local containers often play on Cast without reporting any of their embedded audio tracks. */
@UnstableApi
internal class LocalCastAudioPlayer(
    private val context: Context,
    player: Player,
    private val audio: LocalCastAudio,
) : ForwardingSimpleBasePlayer(player) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loaded: Pair<Uri, List<LocalCastAudio.Track>>? = null
    private var inspecting: Uri? = null
    private var inspection: Job? = null
    private var switching: ListenableFuture<*>? = null
    private var pendingText: Pair<Set<String>, Int>? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        player.addListener(listener)
        refresh()
    }

    private fun refresh() {
        if (player.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            clear()
            return
        }
        val uri = player.currentMediaItem?.localConfiguration?.uri ?: return
        if (uri.scheme !in listOf(
                "file",
                "content",
            )
        ) {
            return
        }
        if (loaded?.first != uri) {
            pendingText = null
            switching?.cancel(true)
        }
        restoreTextSelection()
        if (loaded?.first == uri || inspecting == uri) return
        inspection?.cancel()
        inspecting = uri
        inspection = scope.launch {
            val tracks = try {
                withContext(Dispatchers.IO) { audio.inspect(uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            loaded = uri to tracks
            inspecting = null
            invalidateState()
        }
    }

    private fun fallbackTracks(state: State): List<LocalCastAudio.Track> {
        if (state.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE ||
            !state.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS) ||
            state.currentMediaItemIndex !in 0 until state.timeline.windowCount
        ) {
            return emptyList()
        }
        val uri =
            state.timeline.getWindow(
                state.currentMediaItemIndex,
                Timeline.Window(),
            ).mediaItem.localConfiguration?.uri
                ?: return emptyList()
        if (loaded?.first != uri) return emptyList()
        val tracks = loaded?.second.orEmpty()
        if (audio.patchFor(uri) == null &&
            state.currentTracks.groups.count { it.type == C.TRACK_TYPE_AUDIO } >= tracks.size
        ) {
            return emptyList()
        }
        return tracks.takeIf { it.size > 1 }.orEmpty()
    }

    private fun group(track: LocalCastAudio.Track) = TrackGroup(
        checkNotNull(track.format.id),
        track.format,
    )

    override fun getState(): State {
        val state = super.getState()
        val tracks = fallbackTracks(state)
        if (tracks.isEmpty()) return state
        val uri = checkNotNull(loaded).first
        val selected = audio.selectedIndex(
            uri,
            tracks,
        )
        val groups = state.currentTracks.groups.filter { it.type != C.TRACK_TYPE_AUDIO } +
            tracks.mapIndexed { index, track ->
                Tracks.Group(
                    group(track),
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(index == selected),
                )
            }
        return state.buildUpon()
            .setPlaylist(
                state.timeline,
                Tracks(ImmutableList.copyOf(groups)),
                state.currentMetadata,
            )
            .setTrackSelectionParameters(
                state.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(
                        C.TRACK_TYPE_AUDIO,
                        false,
                    )
                    .setOverrideForType(
                        TrackSelectionOverride(
                            group(tracks[selected]),
                            0,
                        ),
                    ).build(),
            )
            .build()
    }

    override fun getPlaceholderState(suggestedPlaceholderState: State): State {
        val selected = suggestedPlaceholderState.trackSelectionParameters.overrides.values
            .firstOrNull { it.mediaTrackGroup.id.startsWith("cast-local-audio:") }
            ?: return suggestedPlaceholderState
        val groups = suggestedPlaceholderState.currentTracks.groups.map { track ->
            if (track.type != C.TRACK_TYPE_AUDIO) {
                track
            } else {
                Tracks.Group(
                    track.mediaTrackGroup,
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(track.mediaTrackGroup == selected.mediaTrackGroup),
                )
            }
        }
        return suggestedPlaceholderState.buildUpon().setPlaylist(
            suggestedPlaceholderState.timeline,
            Tracks(ImmutableList.copyOf(groups)),
            suggestedPlaceholderState.currentMetadata,
        ).build()
    }

    override fun handleSetTrackSelectionParameters(
        parameters: TrackSelectionParameters,
    ): ListenableFuture<*> {
        val tracks = fallbackTracks(super.getState())
        val override = parameters.overrides.values.firstOrNull { choice ->
            tracks.any {
                group(it) ==
                    choice.mediaTrackGroup
            }
        }
        val index = tracks.indexOfFirst { group(it) == override?.mediaTrackGroup }
        val forwarded = if (tracks.isNotEmpty()) {
            parameters.buildUpon().clearOverridesOfType(
                C.TRACK_TYPE_AUDIO,
            ).build()
        } else {
            parameters
        }
        if (index < 0 || override?.trackIndices?.isEmpty() == true) {
            return super.handleSetTrackSelectionParameters(forwarded)
        }
        val uri = checkNotNull(loaded).first
        val previousIndex = audio.selectedIndex(
            uri,
            tracks,
        )
        if (index == previousIndex) {
            return super.handleSetTrackSelectionParameters(forwarded)
        }
        val previousPatch = audio.patchFor(uri)
        switching?.cancel(true)
        return scope.future {
            var selected = false
            try {
                val patch = withContext(Dispatchers.IO) {
                    audio.prepare(
                        uri,
                        tracks[index],
                    )
                }
                if (player.currentMediaItem?.localConfiguration?.uri != uri ||
                    player.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE ||
                    !player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                ) {
                    return@future
                }
                val playingIndex = player.currentMediaItemIndex
                val item = checkNotNull(player.currentMediaItem)
                val text = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                val selectedText =
                    text.firstOrNull { it.isSelected }?.mediaTrackGroup?.castSubtitleIndex ?: -1
                val updated = item.buildUpon().setSubtitleConfigurations(
                    item.localConfiguration?.subtitleConfigurations.orEmpty().mapIndexed {
                            subtitleIndex,
                            subtitle,
                        ->
                        subtitle.buildUpon().setSelectionFlags(
                            (subtitle.selectionFlags and C.SELECTION_FLAG_DEFAULT.inv()) or
                                if (subtitleIndex == selectedText) C.SELECTION_FLAG_DEFAULT else 0,
                        ).build()
                    },
                ).build()
                val items = (0 until player.mediaItemCount).map {
                    if (it ==
                        playingIndex
                    ) {
                        updated
                    } else {
                        player.getMediaItemAt(it)
                    }
                }
                check(items.all { it.localConfiguration != null })
                audio.select(
                    uri,
                    index,
                    patch,
                )
                selected = true
                pendingText =
                    if (updated.localConfiguration?.subtitleConfigurations?.isNotEmpty() == true) {
                        text.map { it.mediaTrackGroup.id }.toSet() to selectedText
                    } else {
                        null
                    }
                player.setMediaItems(
                    items,
                    playingIndex,
                    player.currentPosition,
                )
                invalidateState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (selected && player.currentMediaItem?.localConfiguration?.uri == uri &&
                    player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
                ) {
                    audio.select(
                        uri,
                        previousIndex,
                        previousPatch,
                    )
                    pendingText = null
                    invalidateState()
                }
                Toast.makeText(
                    context,
                    R.string.error_opening_audio,
                    Toast.LENGTH_LONG,
                ).show()
                throw error
            }
        }.also { switching = it }
    }

    private fun restoreTextSelection() {
        val pending = pendingText ?: return
        val tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (tracks.isEmpty() || tracks.any { it.mediaTrackGroup.id in pending.first }) return
        val selected = tracks.firstOrNull { it.mediaTrackGroup.castSubtitleIndex == pending.second }
        if (pending.second >= 0 && selected == null) return
        pendingText = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(
                C.TRACK_TYPE_TEXT,
                pending.second < 0,
            )
            .apply {
                selected?.let {
                    setOverrideForType(
                        TrackSelectionOverride(
                            it.mediaTrackGroup,
                            0,
                        ),
                    )
                }
            }
            .build()
    }

    fun clear() {
        scope.coroutineContext.cancelChildren()
        inspecting = null
        loaded = null
        pendingText = null
        audio.clear()
    }

    override fun handleRelease(): ListenableFuture<*> {
        player.removeListener(listener)
        clear()
        scope.cancel()
        return super.handleRelease()
    }
}
