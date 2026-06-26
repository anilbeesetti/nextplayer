package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File
import kotlinx.coroutines.flow.Flow

interface VaultRepository {

    /**
     * Observes all videos currently hidden in the vault, newest hidden first.
     */
    fun observeHiddenVideos(): Flow<List<Video>>

    /**
     * Whether the vault currently has at least one hidden video.
     */
    suspend fun hasHiddenVideos(): Boolean

    fun getStagingDir(): File

    suspend fun hideVideo(originalUri: String, movedFile: File? = null, originalPath: String? = null): HideResult?


    suspend fun confirmStagedHides(confirmedIds: List<Long>, rolledBackIds: List<Long>)

    /**
     * Moves every video back out of the vault to its original location.
     *
     * @return the ids of the vault entries that failed to restore, empty if all succeeded.
     */
    suspend fun unhideVideos(ids: List<Long>): List<Long>

    /**
     * Permanently deletes the given hidden videos from the vault.
     */
    suspend fun deleteHiddenVideos(ids: List<Long>)

    /**
     * Reads extended media info (codec, resolution, etc) for a hidden video.
     */
    suspend fun getHiddenVideoInfo(id: Long): MediaInfo?
}

/**
 * Result of a single [VaultRepository.hideVideo] call.
 */
sealed interface HideResult {
    /** The video that's now in the vault (whether already fully hidden, or still staged). */
    val video: Video

    /**
     * The video was moved directly into the vault. Nothing left to clean up - the original
     * file no longer exists anywhere outside the vault.
     */
    data class Hidden(override val video: Video) : HideResult

    data class Staged(override val video: Video, val leftoverUri: Uri) : HideResult
}
