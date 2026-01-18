package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteFiles
import dev.anilbeesetti.nextplayer.core.common.extensions.thumbnailCacheDir
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.VideoStreamInfoEntity
import io.github.anilbeesetti.nextlib.mediainfo.AudioStream
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import io.github.anilbeesetti.nextlib.mediainfo.SubtitleStream
import io.github.anilbeesetti.nextlib.mediainfo.VideoStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.text.substringBeforeLast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalMediaInfoSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val imageLoader: ImageLoader,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val dispatcher: CoroutineDispatcher,
) : MediaInfoSynchronizer {

    private val activeSyncJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    override fun sync(uri: Uri) {
        applicationScope.launch(dispatcher) {
            val uriString = uri.toString()

            mutex.withLock {
                activeSyncJobs[uriString]
            }?.join()

            val job = applicationScope.launch(dispatcher) {
                try {
                    performSync(uri)
                } finally {
                    mutex.withLock {
                        activeSyncJobs.remove(uriString)
                    }
                }
            }

            mutex.withLock {
                activeSyncJobs[uriString] = job
            }
        }
    }

    override suspend fun clearThumbnailsCache() {
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }

    private suspend fun performSync(uri: Uri) {
        val medium = mediumDao.getWithInfo(uri.toString()) ?: return
        if (medium.videoStreamInfo != null) return

        val mediaInfo = runCatching {
            MediaInfoBuilder().from(context = context, uri = uri).build() ?: throw NullPointerException()
        }.onFailure { e ->
            e.printStackTrace()
            Log.d(TAG, "sync: MediaInfoBuilder exception", e)
        }.getOrNull() ?: return
        mediaInfo.release()

        val videoStreamInfo = mediaInfo.videoStream?.toVideoStreamInfoEntity(medium.mediumEntity.uriString)
        val audioStreamsInfo = mediaInfo.audioStreams.map {
            it.toAudioStreamInfoEntity(medium.mediumEntity.uriString)
        }
        val subtitleStreamsInfo = mediaInfo.subtitleStreams.map {
            it.toSubtitleStreamInfoEntity(medium.mediumEntity.uriString)
        }

        mediumDao.upsert(medium.mediumEntity.copy(format = mediaInfo.format))
        videoStreamInfo?.let { mediumDao.upsertVideoStreamInfo(it) }
        audioStreamsInfo.onEach { mediumDao.upsertAudioStreamInfo(it) }
        subtitleStreamsInfo.onEach { mediumDao.upsertSubtitleStreamInfo(it) }
    }

    companion object {
        private const val TAG = "MediaInfoSynchronizer"
    }
}

private fun VideoStream.toVideoStreamInfoEntity(mediumUri: String) = VideoStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    frameRate = frameRate,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    mediumUri = mediumUri,
)

private fun AudioStream.toAudioStreamInfoEntity(mediumUri: String) = AudioStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    sampleFormat = sampleFormat,
    sampleRate = sampleRate,
    channels = channels,
    channelLayout = channelLayout,
    mediumUri = mediumUri,
)

private fun SubtitleStream.toSubtitleStreamInfoEntity(mediumUri: String) = SubtitleStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    mediumUri = mediumUri,
)
