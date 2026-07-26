package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.extensions.getParcelableUriArray
import dev.anilbeesetti.nextplayer.feature.player.model.Subtitle

class PlayerApi(val activity: PlayerActivity) {

    private val extras = activity.intent.extras
    val isApiAccess: Boolean get() = extras != null
    val hasPosition: Boolean get() = position != null
    val hasTitle: Boolean get() = extras?.containsKey(API_TITLE) == true
    val shouldReturnResult: Boolean
        get() = activity.callingActivity != null || activity.callingPackage != null || extras?.containsKey(API_RETURN_RESULT) == true
    val position: Long?
        get() {
            if (extras == null) return null
            val msRaw = extras.get(API_POSITION)
                ?: extras.get("extra_position")
                ?: extras.get("position_extra")
                ?: extras.get("resume_position")
                ?: extras.get("position_ms")
            if (msRaw != null) {
                return when (msRaw) {
                    is Number -> msRaw.toLong()
                    is String -> msRaw.toLongOrNull()
                    else -> null
                }
            }
            val secRaw = extras.get("start") ?: extras.get("position_sec") ?: return null
            val num = when (secRaw) {
                is Number -> secRaw.toLong()
                is String -> secRaw.toLongOrNull()
                else -> null
            } ?: return null
            return if (num in 1..86400) num * 1000L else num
        }
    val title: String? get() = if (hasTitle) extras?.getString(API_TITLE) else null

    fun getSubs(): List<Subtitle> {
        if (extras == null) return emptyList()
        if (!extras.containsKey(API_SUBS)) return emptyList()

        val subs = extras.getParcelableUriArray(API_SUBS) ?: return emptyList()
        val subsName = extras.getStringArray(API_SUBS_NAME)

        val subsEnable = extras.getParcelableUriArray(API_SUBS_ENABLE)
        val defaultSub = if (!subsEnable.isNullOrEmpty()) subsEnable[0] as Uri else null

        return subs.mapIndexed { index, parcelable ->
            val subtitleUri = parcelable as Uri
            val subtitleName = subsName?.let { if (it.size > index) it[index] else null }
            Subtitle(
                name = subtitleName,
                uri = subtitleUri,
                isSelected = subtitleUri == defaultSub,
            )
        }
    }

    fun getPlaylist(): List<String> {
        if (extras == null) return emptyList()
        if (!extras.containsKey(API_PLAYLIST)) return emptyList()
        val playlist = extras.getParcelableUriArray(API_PLAYLIST) ?: return emptyList()
        return playlist.map { (it as Uri).toString() }
    }

    fun getResult(isPlaybackFinished: Boolean, duration: Long, position: Long): Intent {
        return Intent(API_RESULT_INTENT).apply {
            data = activity.intent.data
            val endBy = if (isPlaybackFinished) API_END_BY_COMPLETION else API_END_BY_USER
            putExtra(API_END_BY, endBy)
            putExtra("is_finished", isPlaybackFinished)
            putExtra("playback_completion", isPlaybackFinished)
            putExtra(API_RETURN_RESULT, true)

            val targetDuration = if (duration != C.TIME_UNSET) duration else -1L
            if (targetDuration >= 0) {
                putExtra(API_DURATION, targetDuration.toInt())
                putExtra("duration_long", targetDuration)
                putExtra("extra_duration", targetDuration)
                putExtra("extra_duration_int", targetDuration.toInt())
                putExtra("duration_ms", targetDuration)
                putExtra("duration_sec", (targetDuration / 1000).toInt())
            }

            val targetPosition = if (isPlaybackFinished && targetDuration >= 0) {
                targetDuration
            } else if (position != C.TIME_UNSET) {
                position
            } else {
                -1L
            }

            if (targetPosition >= 0) {
                putExtra(API_POSITION, targetPosition.toInt())
                putExtra("position_long", targetPosition)
                putExtra("position_extra", targetPosition.toInt())
                putExtra("extra_position", targetPosition)
                putExtra("extra_position_int", targetPosition.toInt())
                putExtra("position_ms", targetPosition)
                putExtra("position_sec", (targetPosition / 1000).toInt())
            }
        }
    }

    companion object {
        const val API_TITLE = "title"
        const val API_POSITION = "position"
        const val API_DURATION = "duration"
        const val API_RETURN_RESULT = "return_result"
        const val API_END_BY = "end_by"
        const val API_SUBS = "subs"
        const val API_SUBS_ENABLE = "subs.enable"
        const val API_SUBS_NAME = "subs.name"
        const val API_PLAYLIST = "video_list"

        const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"

        private const val API_END_BY_USER = "user"
        private const val API_END_BY_COMPLETION = "playback_completion"
    }
}
