package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.extensions.getParcelableUriArray
import dev.anilbeesetti.nextplayer.feature.player.model.Subtitle

internal data class PlayerApiData(
    val hasPosition: Boolean = false,
    val position: Int? = null,
    val hasTitle: Boolean = false,
    val title: String? = null,
    val shouldReturnResult: Boolean = false,
    val subtitles: List<Subtitle> = emptyList(),
    val playlist: List<String> = emptyList(),
)

class PlayerApi internal constructor(
    private val currentDataProvider: () -> PlayerApiData?,
) {
    constructor(activity: PlayerActivity) : this(
        currentDataProvider = { activity.intent.toPlayerApiData() },
    )

    val isApiAccess: Boolean get() = snapshot() != null
    val hasPosition: Boolean get() = snapshot()?.hasPosition == true
    val hasTitle: Boolean get() = snapshot()?.hasTitle == true
    val shouldReturnResult: Boolean get() = snapshot()?.shouldReturnResult == true
    val position: Int? get() = snapshot()?.position
    val title: String? get() = snapshot()?.title

    fun getSubs(): List<Subtitle> = snapshot()?.subtitles.orEmpty()

    fun getPlaylist(): List<String> = snapshot()?.playlist.orEmpty()

    internal fun snapshot(): PlayerApiData? = currentDataProvider()

    fun getResult(isPlaybackFinished: Boolean, duration: Long, position: Long): Intent {
        return Intent(API_RESULT_INTENT).apply {
            if (isPlaybackFinished) {
                putExtra(API_END_BY, API_END_BY_COMPLETION)
            } else {
                putExtra(API_END_BY, API_END_BY_USER)
                if (duration != C.TIME_UNSET) putExtra(API_DURATION, duration.toInt())
                if (position != C.TIME_UNSET) putExtra(API_POSITION, position.toInt())
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

private fun Intent.toPlayerApiData(): PlayerApiData? {
    val extras = extras ?: return null
    val hasPosition = extras.containsKey(PlayerApi.API_POSITION)
    val hasTitle = extras.containsKey(PlayerApi.API_TITLE)
    val subtitleNames = extras.getStringArray(PlayerApi.API_SUBS_NAME)
    val defaultSubtitle = extras.getParcelableUriArray(PlayerApi.API_SUBS_ENABLE)
        ?.firstOrNull() as? Uri
    val subtitles = extras.getParcelableUriArray(PlayerApi.API_SUBS).orEmpty()
        .mapIndexed { index, parcelable ->
            val subtitleUri = parcelable as Uri
            Subtitle(
                name = subtitleNames?.getOrNull(index),
                uri = subtitleUri,
                isSelected = subtitleUri == defaultSubtitle,
            )
        }
    val playlist = extras.getParcelableUriArray(PlayerApi.API_PLAYLIST).orEmpty()
        .map { (it as Uri).toString() }

    return PlayerApiData(
        hasPosition = hasPosition,
        position = if (hasPosition) extras.getInt(PlayerApi.API_POSITION) else null,
        hasTitle = hasTitle,
        title = if (hasTitle) extras.getString(PlayerApi.API_TITLE) else null,
        shouldReturnResult = extras.containsKey(PlayerApi.API_RETURN_RESULT),
        subtitles = subtitles,
        playlist = playlist,
    )
}
