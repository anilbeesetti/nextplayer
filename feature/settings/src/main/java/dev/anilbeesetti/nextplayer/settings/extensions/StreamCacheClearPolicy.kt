package dev.anilbeesetti.nextplayer.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.model.StreamCacheClearPolicy
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun StreamCacheClearPolicy.name(): String {
    val stringRes = when (this) {
        StreamCacheClearPolicy.DO_NOT_CLEAR -> R.string.stream_cache_clear_never
        StreamCacheClearPolicy.CLEAR_ON_APP_EXIT -> R.string.stream_cache_clear_on_app_exit
        StreamCacheClearPolicy.CLEAR_ON_PLAYBACK_SESSION_EXIT -> R.string.stream_cache_clear_on_playback_exit
    }
    return stringResource(id = stringRes)
}
