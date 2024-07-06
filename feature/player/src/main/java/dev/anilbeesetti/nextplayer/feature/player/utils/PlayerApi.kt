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
    val hasPosition: Boolean get() = extras?.containsKey(API_POSITION) == true
    val hasTitle: Boolean get() = extras?.containsKey(API_TITLE) == true
    val shouldReturnResult: Boolean get() = extras?.containsKey(API_RETURN_RESULT) == true
    val position: Int? get() = if (hasPosition) extras?.getInt(API_POSITION) else null
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
        const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"

        private const val API_END_BY_USER = "user"
        private const val API_END_BY_COMPLETION = "playback_completion"
    }
}
