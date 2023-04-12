package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.Folder
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import dev.anilbeesetti.nextplayer.core.media.mediasource.MediaSource
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class LocalVideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val mediaSource: MediaSource
) : VideoRepository {

    override fun getVideosFlow(folderPath: String?): Flow<List<Video>> {
        return mediaSource.getVideoItemsFlow().map { mediaVideos ->
            mediaVideos.filter {
                folderPath == null || it.data.substringBeforeLast("/") == folderPath
            }.map(MediaVideo::toVideo)
        }
    }

    override fun getFoldersFlow(): Flow<List<Folder>> {
        return mediaSource.getVideoItemsFlow().map { mediaVideos ->
            mediaVideos.groupBy { File(it.data).parentFile!! }
                .map { (file, videos) ->
                    Folder(
                        path = file.path,
                        name = file.name.takeIf { file.path != "/storage/emulated/0" }
                            ?: "Internal Storage",
                        mediaCount = videos.size,
                        mediaSize = videos.sumOf { it.size }
                    )
                }
        }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return videoDao.get(path)?.toVideoState()
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?
    ) {
        Timber.d("save state for [$path]: [$position, $audioTrackIndex, $subtitleTrackIndex]")
        videoDao.upsert(
            VideoEntity(
                path = path,
                playbackPosition = position,
                audioTrack = audioTrackIndex,
                subtitleTrack = subtitleTrackIndex
            )
        )
    }
}
