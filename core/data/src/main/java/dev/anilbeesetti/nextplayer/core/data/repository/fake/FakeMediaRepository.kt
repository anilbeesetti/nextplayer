package dev.anilbeesetti.nextplayer.core.data.repository.fake

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMediaRepository : MediaRepository {

    val videos = mutableListOf<Video>()
    val directories = mutableListOf<Folder>()
    private val updates = MutableStateFlow(value = 0L)

    override fun observeFolders(folderPath: String?): Flow<List<Folder>> = updates.map {
        directories.filter { folderPath == null || it.path.startsWith(prefix = folderPath) }
    }

    override fun observeVideos(folderPath: String?): Flow<List<Video>> = updates.map {
        videos.filter { folderPath == null || it.path.startsWith(prefix = folderPath) }
    }

    override suspend fun fetchFolders(folderPath: String?): List<Folder> = directories.filter {
        folderPath == null || it.path.startsWith(prefix = folderPath)
    }

    override suspend fun fetchVideos(folderPath: String?): List<Video> = videos.filter {
        folderPath == null || it.path.startsWith(prefix = folderPath)
    }

    override fun observePlaybackHistory(): Flow<List<Video>> = updates.map {
        videos.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt?.time }
    }

    override fun observeTrashVideos(): Flow<List<Video>> = updates.map { emptyList() }

    override suspend fun getVideoByUri(uri: String): Video? = videos.find { it.uriString == uri }

    fun notifyMediaChanged() {
        updates.value += 1
    }

    override suspend fun getVideoState(uri: String): VideoState? = null

    override suspend fun getMediaInfo(uri: String): MediaInfo? = null

    override suspend fun clearPlaybackHistory() {
    }

    override suspend fun updateMediumLastPlayedTime(
        uri: String,
        lastPlayedTime: Long,
        duration: Long?,
    ) {
    }

    override suspend fun updateMediumPosition(uri: String, position: Long) {
    }

    override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
    }

    override suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
    }

    override suspend fun updateMediumZoom(uri: String, zoom: Float) {
    }

    override suspend fun addExternalAudioToMedium(uri: String, audioUri: Uri) {
    }

    override suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
    }

    override suspend fun updateSubtitleDelay(uri: String, delay: Long) {
    }

    override suspend fun updateSubtitleSpeed(uri: String, speed: Float) {
    }
}
