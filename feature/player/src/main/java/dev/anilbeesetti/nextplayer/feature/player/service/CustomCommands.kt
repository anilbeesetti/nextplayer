package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import kotlinx.coroutines.guava.await

enum class CustomCommands(val customAction: String) {
    ADD_SUBTITLE_TRACK(customAction = "ADD_SUBTITLE_TRACK"),
    SWITCH_AUDIO_TRACK(customAction = "SWITCH_AUDIO_TRACK"),
    SWITCH_SUBTITLE_TRACK(customAction = "SWITCH_SUBTITLE_TRACK"),
    SET_SKIP_SILENCE_ENABLED(customAction = "SET_SKIP_SILENCE_ENABLED"),
    GET_SKIP_SILENCE_ENABLED(customAction = "GET_SKIP_SILENCE_ENABLED"),
    SET_PLAYBACK_SPEED(customAction = "SET_PLAYBACK_SPEED"),
    GET_AUDIO_SESSION_ID(customAction = "GET_AUDIO_SESSION_ID"),
    STOP_PLAYER_SESSION(customAction = "STOP_PLAYER_SESSION"),
    ;

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? {
            return entries.find { it.customAction == sessionCommand.customAction }
        }

        fun asSessionCommands(): List<SessionCommand> {
            return entries.map { it.sessionCommand }
        }

        const val SUBTITLE_TRACK_URI_KEY = "subtitle_track_uri"
        const val AUDIO_TRACK_INDEX_KEY = "audio_track_index"
        const val SUBTITLE_TRACK_INDEX_KEY = "subtitle_track_index"
        const val SKIP_SILENCE_ENABLED_KEY = "skip_silence_enabled"
        const val PLAYBACK_SPEED_KEY = "playback_speed"
        const val AUDIO_SESSION_ID_KEY = "audio_session_id"
    }
}

fun MediaController.addSubtitleTrack(uri: Uri) {
    val args = Bundle().apply {
        putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, uri.toString())
    }
    sendCustomCommand(CustomCommands.ADD_SUBTITLE_TRACK.sessionCommand, args)
}

fun MediaController.switchAudioTrack(trackIndex: Int) {
    val args = Bundle().apply {
        putInt(CustomCommands.AUDIO_TRACK_INDEX_KEY, trackIndex)
    }
    sendCustomCommand(CustomCommands.SWITCH_AUDIO_TRACK.sessionCommand, args)
}

fun MediaController.switchSubtitleTrack(trackIndex: Int) {
    val args = Bundle().apply {
        putInt(CustomCommands.SUBTITLE_TRACK_INDEX_KEY, trackIndex)
    }
    sendCustomCommand(CustomCommands.SWITCH_SUBTITLE_TRACK.sessionCommand, args)
}

fun MediaController.setSkipSilenceEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_SILENCE_ENABLED.sessionCommand, args)
}

suspend fun MediaController.getSkipSilenceEnabled(): Boolean {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_SILENCE_ENABLED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, false)
}

fun MediaController.setSpeed(speed: Float) {
    val args = Bundle().apply {
        putFloat(CustomCommands.PLAYBACK_SPEED_KEY, speed)
    }
    sendCustomCommand(CustomCommands.SET_PLAYBACK_SPEED.sessionCommand, args)
}

@OptIn(UnstableApi::class)
suspend fun MediaController.getAudioSessionId(): Int {
    val result = sendCustomCommand(CustomCommands.GET_AUDIO_SESSION_ID.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.AUDIO_SESSION_ID_KEY, C.AUDIO_SESSION_ID_UNSET)
}
