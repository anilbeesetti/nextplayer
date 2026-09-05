package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderServiceState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
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
    SET_VIDEO_DECODER_MODE(customAction = "SET_VIDEO_DECODER_MODE"),
    SET_AUDIO_DECODER_MODE(customAction = "SET_AUDIO_DECODER_MODE"),
    TRY_DECODER_FALLBACK(customAction = "TRY_DECODER_FALLBACK"),
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
        const val VIDEO_DECODER_MODE_KEY = "video_decoder_mode"
        const val AUDIO_DECODER_MODE_KEY = "audio_decoder_mode"
        const val DECODER_RECOVERY_STATUS_KEY = "decoder_recovery_status"
        const val DECODER_RECOVERY_TRACK_TYPE_KEY = "decoder_recovery_track_type"
        const val UNSUPPORTED_DECODER_MODE_KEY = "unsupported_decoder_mode"
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

suspend fun MediaController.setVideoDecoderMode(mode: DecoderMode): Boolean {
    val args = Bundle().apply {
        putString(CustomCommands.VIDEO_DECODER_MODE_KEY, mode.name)
    }
    val result = sendCustomCommand(CustomCommands.SET_VIDEO_DECODER_MODE.sessionCommand, args).await()
    return result.resultCode == SessionResult.RESULT_SUCCESS
}

suspend fun MediaController.setAudioDecoderMode(mode: DecoderMode): Boolean {
    val args = Bundle().apply {
        putString(CustomCommands.AUDIO_DECODER_MODE_KEY, mode.name)
    }
    val result = sendCustomCommand(CustomCommands.SET_AUDIO_DECODER_MODE.sessionCommand, args).await()
    return result.resultCode == SessionResult.RESULT_SUCCESS
}

internal fun Bundle.decoderServiceState(): DecoderServiceState {
    val videoMode = decoderMode(CustomCommands.VIDEO_DECODER_MODE_KEY)
    val audioMode = decoderMode(CustomCommands.AUDIO_DECODER_MODE_KEY)
    val status = getString(CustomCommands.DECODER_RECOVERY_STATUS_KEY)
        ?.let { value -> DecoderRecoveryStatus.entries.find { it.name == value } }
        ?: DecoderRecoveryStatus.NONE
    val trackType = getString(CustomCommands.DECODER_RECOVERY_TRACK_TYPE_KEY)
        ?.let { value -> DecoderTrackType.entries.find { it.name == value } }
    return DecoderServiceState(
        videoMode = videoMode,
        audioMode = audioMode,
        recoveryState = DecoderRecoveryState(
            status = status,
            trackType = trackType,
            unsupportedMode = decoderMode(CustomCommands.UNSUPPORTED_DECODER_MODE_KEY),
        ),
    )
}

suspend fun MediaController.tryDecoderFallback(): Boolean {
    val result = sendCustomCommand(CustomCommands.TRY_DECODER_FALLBACK.sessionCommand, Bundle.EMPTY).await()
    return result.resultCode == SessionResult.RESULT_SUCCESS
}

internal fun Bundle.decoderMode(key: String): DecoderMode? {
    val value = getString(key) ?: return null
    return DecoderMode.entries.find { it.name == value }
}
