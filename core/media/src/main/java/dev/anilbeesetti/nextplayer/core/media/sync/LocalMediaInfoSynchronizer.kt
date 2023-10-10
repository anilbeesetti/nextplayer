package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.VideoStreamInfoEntity
import io.github.anilbeesetti.nextlib.mediainfo.AudioStream
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import io.github.anilbeesetti.nextlib.mediainfo.SubtitleStream
import io.github.anilbeesetti.nextlib.mediainfo.VideoStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class LocalMediaInfoSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val dispatcher: CoroutineDispatcher
) : MediaInfoSynchronizer {

    private val media = MutableSharedFlow<Uri>()

    override suspend fun addMedia(uri: Uri) = media.emit(uri)

    private fun sync() {
        applicationScope.launch(dispatcher) {
            media.collect { mediumUri ->
                Log.d(TAG, "sync: $mediumUri")
                val path = context.getPath(mediumUri) ?: return@collect
                val medium = mediumDao.getWithInfo(path) ?: return@collect
                if (medium.videoStreamInfo != null) return@collect

                val mediaInfo = MediaInfoBuilder(context).from(mediumUri).build() ?: run {
                    Log.d(TAG, "sync: MediaInfoBuilder returned null")
                    return@collect
                }
                val thumbnail = mediaInfo.getFrame()
                mediaInfo.release()

                val videoStreamInfo = mediaInfo.videoStream?.toVideoStreamInfoEntity(medium.mediumEntity.path)
                val audioStreamsInfo = mediaInfo.audioStreams.map { it.toAudioStreamInfoEntity(medium.mediumEntity.path) }
                val subtitleStreamsInfo = mediaInfo.subtitleStreams.map { it.toSubtitleStreamInfoEntity(medium.mediumEntity.path) }
                val thumbnailPath = thumbnail?.saveTo(storageDir = context.filesDir, quality = 50)

                mediumDao.upsert(medium.mediumEntity.copy(format = mediaInfo.format, thumbnailPath = thumbnailPath))
                videoStreamInfo?.let { mediumDao.upsertVideoStreamInfo(it) }
                audioStreamsInfo.onEach { mediumDao.upsertAudioStreamInfo(it) }
                subtitleStreamsInfo.onEach { mediumDao.upsertSubtitleStreamInfo(it) }
            }
        }
    }

    init {
        sync()
    }

    companion object {
        private const val TAG = "MediaInfoSynchronizer"
    }
}

fun VideoStream.toVideoStreamInfoEntity(mediumPath: String) = VideoStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    frameRate = frameRate,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    mediumPath = mediumPath
)

fun AudioStream.toAudioStreamInfoEntity(mediumPath: String) = AudioStreamInfoEntity(
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
    mediumPath = mediumPath
)

fun SubtitleStream.toSubtitleStreamInfoEntity(mediumPath: String) = SubtitleStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    mediumPath = mediumPath
)


suspend fun Bitmap.saveTo(storageDir: File, quality: Int = 100): String? = withContext(Dispatchers.IO) {
    val thumbnailFileName = "thumbnail-${System.currentTimeMillis()}"
    val thumbFile = File(storageDir, thumbnailFileName)
    try {
        FileOutputStream(thumbFile).use { fos ->
            compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext if (thumbFile.exists()) thumbFile.path else null
}