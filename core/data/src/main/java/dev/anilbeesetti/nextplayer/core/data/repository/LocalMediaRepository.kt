package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.converter.UriListConverter
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.mapAsync
import dev.anilbeesetti.nextplayer.core.media.services.MediaFolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import dev.anilbeesetti.nextplayer.core.model.AudioStreamInfo
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.SubtitleStreamInfo
import dev.anilbeesetti.nextplayer.core.model.VideoStreamInfo
import io.github.anilbeesetti.nextlib.mediainfo.AudioStream
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import io.github.anilbeesetti.nextlib.mediainfo.SubtitleStream
import io.github.anilbeesetti.nextlib.mediainfo.VideoStream
import java.util.Date

class LocalMediaRepository @Inject constructor(
    private val mediumStateDao: MediumStateDao,
    private val mediaService: MediaService,
    @ApplicationContext private val context: Context,
) : MediaRepository {

    override fun observeFolders(filter: FolderFilter): Flow<List<Folder>> {
        return mediaService.observeFolders(filter).map { mediaFolders ->
            coroutineScope {
                mediaFolders.map { mediaFolder ->
                    mediaFolder.toFolder()
                }
            }
        }
    }

    override fun observeVideos(filter: FolderFilter): Flow<List<Video>> {
        return combine(mediaService.observeVideos(filter), mediumStateDao.getAll()) { mediaVideos, mediumStates ->
            val statesMap = mediumStates.associateBy { it.uriString }
            coroutineScope {
                mediaVideos.map { mediaVideo ->
                    val uriString = mediaVideo.uri.toString()
                    val mediaState = statesMap[uriString]
                    mediaVideo.toVideo(mediaState)
                }
            }
        }
    }

    override suspend fun fetchFolders(filter: FolderFilter): List<Folder> {
        return mediaService.fetchFolders(filter).map { it.toFolder() }
    }

    override suspend fun fetchVideos(filter: FolderFilter): List<Video> {
        return mediaService.fetchVideos(filter).mapAsync { mediaVideo ->
            val mediaState = mediumStateDao.get(mediaVideo.uri.toString())
            mediaVideo.toVideo(mediaState)
        }
    }

    override suspend fun getVideoByUri(uri: String): Video? = coroutineScope {
        val mediaVideoDeferred = async { mediaService.findVideo(uri.toUri()) }
        val mediaStateDeferred = async { mediumStateDao.get(uri) }

        val mediaVideo = mediaVideoDeferred.await() ?: return@coroutineScope null
        val mediaState = mediaStateDeferred.await()

        return@coroutineScope mediaVideo.toVideo(mediaState)
    }

    override suspend fun getVideoState(uri: String): VideoState? {
        return mediumStateDao.get(uri)?.toVideoState()
    }

    override suspend fun getMediaInfo(uri: String): MediaInfo? {
        val video = getVideoByUri(uri) ?: return null
        val mediaInfo = runCatching { MediaInfoBuilder().from(context = context, uri = uri.toUri()).build() }.getOrNull()
        mediaInfo?.release()
        return MediaInfo(
            video = video,
            videoStream = mediaInfo?.videoStream?.toVideoStreamInfo(),
            audioStreams = mediaInfo?.audioStreams?.map { it.toAudioStreamInfo() } ?: emptyList(),
            subtitleStreams = mediaInfo?.subtitleStreams?.map { it.toSubtitleStreamInfo() } ?: emptyList(),
        )
    }

    override suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                lastPlayedTime = lastPlayedTime,
            ),
        )
    }

    override suspend fun updateMediumPosition(uri: String, position: Long) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                playbackPosition = position,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                playbackSpeed = playbackSpeed,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                audioTrackIndex = audioTrackIndex,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleTrackIndex = subtitleTrackIndex,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumZoom(uri: String, zoom: Float) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                videoScale = zoom,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)
        val currentExternalSubs = UriListConverter.fromStringToList(stateEntity.externalSubs)

        if (currentExternalSubs.contains(subtitleUri)) return
        val newExternalSubs = UriListConverter.fromListToString(urlList = currentExternalSubs + subtitleUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                externalSubs = newExternalSubs,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSubtitleDelay(uri: String, delay: Long) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleDelayMilliseconds = delay,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSubtitleSpeed(uri: String, speed: Float) {
        val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleSpeed = speed,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    private fun MediaVideo.toVideo(mediaState: MediumStateEntity?) = Video(
        id = this.id,
        uriString = this.uri.toString(),
        duration = this.duration,
        height = this.height,
        width = this.width,
        path = this.path,
        size = this.size,
        nameWithExtension = this.title,
        parentPath = this.parentPath,
        formattedDuration = Utils.formatDurationMillis(this.duration),
        formattedFileSize = Utils.formatFileSize(this.size),
        playbackPosition = mediaState?.playbackPosition,
        lastPlayedAt = mediaState?.lastPlayedTime?.let { Date(it) }
    )
    
    private fun MediaFolder.toFolder() = Folder(
        name = this.name,
        path = this.path,
        dateModified = this.dateModified,
        totalSize = this.totalSize,
        totalDuration = this.totalDuration,
        videosCount = this.videosCount,
        foldersCount = this.foldersCount,
    )

    private fun VideoStream.toVideoStreamInfo() = VideoStreamInfo(
        index = index,
        title = title,
        codecName = codecName,
        language = language,
        disposition = disposition,
        bitRate = bitRate,
        frameRate = frameRate,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
    )

    private fun AudioStream.toAudioStreamInfo() = AudioStreamInfo(
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
    )

    private fun SubtitleStream.toSubtitleStreamInfo() = SubtitleStreamInfo(
        index = index,
        title = title,
        codecName = codecName,
        language = language,
        disposition = disposition,
    )
}
