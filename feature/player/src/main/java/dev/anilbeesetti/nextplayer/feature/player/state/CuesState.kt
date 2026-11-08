package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayerStateObserver
import androidx.media3.ui.compose.state.observeState

@UnstableApi
@Composable
fun rememberCuesState(player: Player?): CuesState {
    val cuesState = remember(player) { CuesState(player = player) }
    LaunchedEffect(key1 = player) { cuesState.observe() }
    return cuesState
}

@UnstableApi
class CuesState(private val player: Player?) {
    var currentCues: CueGroup? by mutableStateOf(value = null)
        private set

    private var selectedTextFormat: Format? by mutableStateOf(null)

    val canReposition: Boolean
        get() = canRepositionSubtitles(
            selectedTextFormat,
            currentCues?.cues.orEmpty(),
        )

    private fun update() {
        currentCues = player?.currentCues
        selectedTextFormat = player?.currentTracks?.groups?.firstNotNullOfOrNull { group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@firstNotNullOfOrNull null
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) }
        }
    }

    private val playerStateObserver: PlayerStateObserver? = player?.observeState(
        Player.EVENT_CUES,
        Player.EVENT_TRACKS_CHANGED,
        Player.EVENT_MEDIA_ITEM_TRANSITION,
    ) {
        update()
    }

    suspend fun observe() {
        update()
        playerStateObserver?.observe()
    }
}

@UnstableApi
internal fun canRepositionSubtitles(format: Format?, cues: List<Cue>): Boolean {
    // Media3 preserves the original subtitle MIME in codecs when transcoding to cues.
    val mimeType = if (format?.sampleMimeType ==
        MimeTypes.APPLICATION_MEDIA3_CUES
    ) {
        format.codecs
    } else {
        format?.sampleMimeType
    }
    return mimeType != MimeTypes.TEXT_SSA && cues.none { it.bitmap != null }
}
