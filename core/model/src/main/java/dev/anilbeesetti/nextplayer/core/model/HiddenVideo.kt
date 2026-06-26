package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

/**
 * Represents a video currently stored in the vault (hidden videos).
 *
 * [id] is the vault database row id, used to unhide or permanently delete the entry.
 * [video] is the regular [Video] representation, so existing video UI/playback code can be
 * reused as-is to display and play vault items.
 */
data class HiddenVideo(
    val id: Long,
    val vaultPath: String,
    val originalPath: String,
    val hiddenAt: Long,
    val video: Video,
) : Serializable
