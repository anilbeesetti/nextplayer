package com.graviton.feature.music.lyrics

import com.graviton.core.model.LyricsSourceKind

data class LyricsRequest(
    val mediaUri: String,
    val filePath: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
)

data class LyricsCandidate(
    val id: String,
    val provider: LyricsSourceKind,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val rawLyrics: String,
    val confidence: Float = 1f,
)

/** Providers are deliberately independent from playback and can be reordered or replaced. */
interface LyricsProvider {
    val kind: LyricsSourceKind
    suspend fun find(request: LyricsRequest): List<LyricsCandidate>
    suspend fun search(request: LyricsRequest): List<LyricsCandidate> = find(request)
}
