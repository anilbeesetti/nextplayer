package com.graviton.core.media.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import com.graviton.core.common.di.ApplicationScope
import com.graviton.core.common.extensions.getStorageVolumes
import com.graviton.core.common.extensions.scanPaths
import com.graviton.core.common.extensions.scanStorage
import com.graviton.core.common.storagePermission
import com.graviton.core.database.converter.UriListConverter
import com.graviton.core.database.dao.MediumStateDao
import com.graviton.core.database.dao.PlaylistDao
import com.graviton.core.media.services.MediaService
import com.graviton.core.media.services.MediaVideo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalMediaSynchronizer @Inject constructor(
    private val mediumStateDao: MediumStateDao,
    private val playlistDao: PlaylistDao,
    private val imageLoader: ImageLoader,
    private val mediaService: MediaService,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

    override suspend fun refresh(path: String?): Boolean {
        return path?.let { context.scanPaths(listOf(path)) }
            ?: context.getStorageVolumes().all { context.scanStorage(it.path) }
    }

    override fun startSync() {
        if (mediaSyncingJob?.isActive == true) return
        mediaSyncingJob = mediaService.observeVideos()
            .onEach { media -> updateMedia(media) }
            .catch {
                // A failed MediaStore query is not a complete snapshot. Leave stored state alone;
                // startSync can retry after storage permission or the provider becomes available.
            }
            .launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
    }

    private fun Context.hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        if (!context.hasStoragePermission()) return@withContext

        val currentMediaUris = media.mapTo(mutableSetOf()) { it.uri.toString() }

        playlistDao.removeMissingItems(currentMediaUris)

        val (wantedMediaStates, unwantedMediaStates) = mediumStateDao.getAll().first().partition {
            it.uriString in currentMediaUris || !ContentResolver.SCHEME_CONTENT.equals(it.uriString.toUri().scheme, ignoreCase = true)
        }

        mediumStateDao.delete(unwantedMediaStates.map { it.uriString })

        // Delete unwanted thumbnails
        unwantedMediaStates.forEach { mediaState ->
            try {
                imageLoader.diskCache?.remove(mediaState.uriString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Release external subtitle uri permission if not used by any other media
        launch {
            val currentMediaExternalSubs = wantedMediaStates.flatMap {
                UriListConverter.fromStringToList(it.externalSubs)
            }.toSet()

            unwantedMediaStates.onEach { mediaState ->
                for (sub in UriListConverter.fromStringToList(mediaState.externalSubs)) {
                    if (sub !in currentMediaExternalSubs) {
                        try {
                            context.contentResolver.releasePersistableUriPermission(sub, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
}
