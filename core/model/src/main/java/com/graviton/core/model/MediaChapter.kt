package com.graviton.core.model

/**
 * One chapter of a media file.
 *
 * Chapters are only ever produced from a real chapter description that ships next to the media
 * file. Nothing here is inferred or generated, so a file without chapter information yields an
 * empty list rather than invented entries.
 */
data class MediaChapter(
    val startMs: Long,
    val title: String,
)
