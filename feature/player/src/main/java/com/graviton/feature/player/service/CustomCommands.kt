package com.graviton.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.graviton.feature.player.decoder.PlaybackDiagnosticsSnapshot
import kotlinx.coroutines.guava.await

enum class CustomCommands(val customAction: String) {
    ADD_SUBTITLE_TRACK(customAction = "ADD_SUBTITLE_TRACK"),
    SET_SKIP_SILENCE_ENABLED(customAction = "SET_SKIP_SILENCE_ENABLED"),
    GET_SKIP_SILENCE_ENABLED(customAction = "GET_SKIP_SILENCE_ENABLED"),
    SET_IS_SCRUBBING_MODE_ENABLED(customAction = "SET_IS_SCRUBBING_MODE_ENABLED"),
    GET_SUBTITLE_DELAY(customAction = "GET_SUBTITLE_DELAY"),
    SET_SUBTITLE_DELAY(customAction = "SET_SUBTITLE_DELAY"),
    GET_SUBTITLE_SPEED(customAction = "GET_SUBTITLE_SPEED"),
    SET_SUBTITLE_SPEED(customAction = "SET_SUBTITLE_SPEED"),
    STOP_PLAYER_SESSION(customAction = "STOP_PLAYER_SESSION"),
    IS_LOUDNESS_GAIN_SUPPORTED(customAction = "IS_LOUDNESS_GAIN_SUPPORTED"),
    SET_LOUDNESS_GAIN(customAction = "SET_LOUDNESS_GAIN"),
    GET_LOUDNESS_GAIN(customAction = "GET_LOUDNESS_GAIN"),
    GET_AUDIO_SESSION_ID(customAction = "GET_AUDIO_SESSION_ID"),
    START_SLEEP_TIMER(customAction = "START_SLEEP_TIMER"),
    CANCEL_SLEEP_TIMER(customAction = "CANCEL_SLEEP_TIMER"),
    GET_SLEEP_TIMER(customAction = "GET_SLEEP_TIMER"),
    GET_PLAYBACK_DIAGNOSTICS(customAction = "GET_PLAYBACK_DIAGNOSTICS"),
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
        const val SKIP_SILENCE_ENABLED_KEY = "skip_silence_enabled"
        const val IS_SCRUBBING_MODE_ENABLED_KEY = "is_scrubbing_mode_enabled"
        const val SUBTITLE_DELAY_KEY = "subtitle_delay"
        const val SUBTITLE_SPEED_KEY = "subtitle_speed"
        const val LOUDNESS_GAIN_KEY = "loudness_gain"
        const val IS_LOUDNESS_GAIN_SUPPORTED_KEY = "is_loudness_gain_supported"
        const val AUDIO_SESSION_ID_KEY = "audio_session_id"
        const val SLEEP_DURATION_MS_KEY = "sleep_duration_ms"
        const val SLEEP_FADE_MS_KEY = "sleep_fade_ms"
        const val SLEEP_END_OF_TRACK_KEY = "sleep_end_of_track"
        const val SLEEP_REMAINING_MS_KEY = "sleep_remaining_ms"
        const val VIDEO_DECODER_NAME_KEY = "video_decoder_name"
        const val VIDEO_DECODER_IS_HARDWARE_KEY = "video_decoder_is_hardware"
        const val VIDEO_DECODER_INIT_MS_KEY = "video_decoder_init_ms"
        const val AUDIO_DECODER_NAME_KEY = "audio_decoder_name"
        const val DROPPED_FRAMES_KEY = "dropped_frames"
        const val DECODER_INITIALISATIONS_KEY = "decoder_initialisations"
    }
}

fun MediaController.addSubtitleTrack(uri: Uri) {
    val args = Bundle().apply {
        putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, uri.toString())
    }
    sendCustomCommand(CustomCommands.ADD_SUBTITLE_TRACK.sessionCommand, args)
}

suspend fun MediaController.setSkipSilenceEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_SILENCE_ENABLED.sessionCommand, args).await()
}

fun MediaController.setMediaControllerIsScrubbingModeEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED.sessionCommand, args)
}

suspend fun MediaController.getSkipSilenceEnabled(): Boolean {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_SILENCE_ENABLED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, false)
}

fun MediaController.setSubtitleDelayMilliseconds(delayMillis: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.SUBTITLE_DELAY_KEY, delayMillis)
    }
    sendCustomCommand(CustomCommands.SET_SUBTITLE_DELAY.sessionCommand, args)
}

suspend fun MediaController.getSubtitleDelayMilliseconds(): Long {
    val result = sendCustomCommand(CustomCommands.GET_SUBTITLE_DELAY.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getLong(CustomCommands.SUBTITLE_DELAY_KEY, 0L)
}

fun MediaController.setSubtitleSpeed(speed: Float) {
    val args = Bundle().apply {
        putFloat(CustomCommands.SUBTITLE_SPEED_KEY, speed)
    }
    sendCustomCommand(CustomCommands.SET_SUBTITLE_SPEED.sessionCommand, args)
}

suspend fun MediaController.getSubtitleSpeed(): Float {
    val result = sendCustomCommand(CustomCommands.GET_SUBTITLE_SPEED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getFloat(CustomCommands.SUBTITLE_SPEED_KEY, 1f)
}

fun MediaController.stopPlayerSession() {
    sendCustomCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
}

fun MediaController.setLoudnessGain(gain: Int) {
    val args = Bundle().apply {
        putInt(CustomCommands.LOUDNESS_GAIN_KEY, gain)
    }
    sendCustomCommand(CustomCommands.SET_LOUDNESS_GAIN.sessionCommand, args)
}

suspend fun MediaController.getLoudnessGain(): Int {
    val result = sendCustomCommand(CustomCommands.GET_LOUDNESS_GAIN.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
}

suspend fun MediaController.getIsLoudnessGainSupported(): Boolean {
    val result = sendCustomCommand(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, false)
}

suspend fun MediaController.getAudioSessionId(): Int {
    val result = sendCustomCommand(CustomCommands.GET_AUDIO_SESSION_ID.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.AUDIO_SESSION_ID_KEY, C.AUDIO_SESSION_ID_UNSET)
}

suspend fun MediaController.startSleepTimer(
    durationMs: Long,
    fadeMs: Long = 0L,
    endOfTrack: Boolean = false,
) {
    val args = Bundle().apply {
        putLong(CustomCommands.SLEEP_DURATION_MS_KEY, durationMs.coerceAtLeast(0L))
        putLong(CustomCommands.SLEEP_FADE_MS_KEY, fadeMs.coerceAtLeast(0L))
        putBoolean(CustomCommands.SLEEP_END_OF_TRACK_KEY, endOfTrack)
    }
    sendCustomCommand(CustomCommands.START_SLEEP_TIMER.sessionCommand, args).await()
}

suspend fun MediaController.cancelSleepTimer() {
    sendCustomCommand(CustomCommands.CANCEL_SLEEP_TIMER.sessionCommand, Bundle.EMPTY).await()
}

suspend fun MediaController.getSleepTimerRemainingMs(): Long {
    val result = sendCustomCommand(CustomCommands.GET_SLEEP_TIMER.sessionCommand, Bundle.EMPTY).await()
    return result.extras.getLong(CustomCommands.SLEEP_REMAINING_MS_KEY, 0L)
}

/**
 * Reads the decoder facts the service's [com.graviton.feature.player.decoder.PlaybackDiagnostics]
 * has observed, for the Video information sheet.
 *
 * A missing key means the renderer has not reported that value yet, which the UI shows as
 * "Unknown".
 */
suspend fun MediaController.getPlaybackDiagnostics(): PlaybackDiagnosticsSnapshot {
    val extras = sendCustomCommand(CustomCommands.GET_PLAYBACK_DIAGNOSTICS.sessionCommand, Bundle.EMPTY)
        .await()
        .extras
    return PlaybackDiagnosticsSnapshot(
        videoDecoderName = extras.getString(CustomCommands.VIDEO_DECODER_NAME_KEY),
        isVideoDecoderHardware = if (extras.containsKey(CustomCommands.VIDEO_DECODER_IS_HARDWARE_KEY)) {
            extras.getBoolean(CustomCommands.VIDEO_DECODER_IS_HARDWARE_KEY)
        } else {
            null
        },
        videoDecoderInitMs = extras.getLong(
            CustomCommands.VIDEO_DECODER_INIT_MS_KEY,
            PlaybackDiagnosticsSnapshot.UNKNOWN_LONG,
        ),
        audioDecoderName = extras.getString(CustomCommands.AUDIO_DECODER_NAME_KEY),
        droppedFrames = extras.getInt(CustomCommands.DROPPED_FRAMES_KEY, 0),
        decoderInitialisations = extras.getInt(CustomCommands.DECODER_INITIALISATIONS_KEY, 0),
    )
}
