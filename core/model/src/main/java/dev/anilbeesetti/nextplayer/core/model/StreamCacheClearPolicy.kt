package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class StreamCacheClearPolicy {
    DO_NOT_CLEAR,
    CLEAR_ON_APP_EXIT,
    CLEAR_ON_PLAYBACK_SESSION_EXIT,
}
