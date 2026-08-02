package dev.anilbeesetti.nextplayer.core.domain

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import javax.inject.Inject

class SyncPlaylistsWithMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context,
) {

    suspend operator fun invoke(): Boolean {
        return synchronizePlaylistsWithMedia(
            hasStoragePermission = { context.hasStoragePermission() },
            fetchVideos = mediaRepository::fetchVideosOrThrow,
            removeMissingVideos = playlistRepository::removeMissingVideos,
        )
    }

    private fun Context.hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED
}

internal suspend fun synchronizePlaylistsWithMedia(
    hasStoragePermission: () -> Boolean,
    fetchVideos: suspend () -> List<dev.anilbeesetti.nextplayer.core.model.Video>,
    removeMissingVideos: suspend (Set<String>) -> Unit,
): Boolean {
    if (!hasStoragePermission()) return false
    val videos = runCatching { fetchVideos() }.getOrElse { return false }
    if (!hasStoragePermission()) return false

    removeMissingVideos(videos.mapTo(mutableSetOf()) { it.uriString })
    return true
}
