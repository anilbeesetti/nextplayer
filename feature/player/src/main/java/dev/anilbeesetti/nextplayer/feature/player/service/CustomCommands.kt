package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand

enum class CustomCommands(val customAction: String) {
    ADD_SUBTITLE_TRACK(customAction = "ADD_SUBTITLE_TRACK"),
    SWITCH_AUDIO_TRACK(customAction = "SWITCH_AUDIO_TRACK"),
    SWITCH_SUBTITLE_TRACK(customAction = "SWITCH_SUBTITLE_TRACK");

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