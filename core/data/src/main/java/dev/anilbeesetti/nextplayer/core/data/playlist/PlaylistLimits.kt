package dev.anilbeesetti.nextplayer.core.data.playlist

import java.io.IOException

internal object PlaylistLimits {
    const val MAX_SOURCE_BYTES = 4_194_304
    const val MAX_SOURCE_CHARS = 4_194_304
    const val MAX_ENTRIES = 20_000
}

sealed class PlaylistLimitExceededException protected constructor(message: String) : IOException(message)

class PlaylistSourceLimitExceededException internal constructor(
    val maxBytes: Int,
    val maxChars: Int,
) : PlaylistLimitExceededException(
    "Playlist source exceeds maximum size of $maxBytes bytes or $maxChars characters",
)

class PlaylistEntryLimitExceededException internal constructor(
    val maxEntries: Int,
) : PlaylistLimitExceededException("Playlist contains more than $maxEntries entries")
