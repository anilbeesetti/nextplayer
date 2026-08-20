package com.graviton.core.data.repository.fake

import android.net.Uri
import com.graviton.core.data.models.VideoState
import com.graviton.core.data.repository.MediaRepository
import com.graviton.core.model.Folder
import com.graviton.core.model.MediaInfo
import com.graviton.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMediaRepository : MediaRepository {

    val videos = mutableListOf<Video>()
    val directories = mutableListOf<Folder>()
    private val updates = MutableStateFlow(0L)

    override fun observeFolders(folderPath: String?): Flow<List<Folder>> {
        return updates.map {
            directories.filter { folderPath == null || it.path.startsWith(folderPath) }
        }
    }

    override fun observeVideos(folderPath: String?): Flow<List<Video>> {
        return updates.map {
            videos.filter { folderPath == null || it.path.startsWith(folderPath) }
        }
    }

    override suspend fun fetchFolders(folderPath: String?): List<Folder> {
        return directories.filter { folderPath == null || it.path.startsWith(folderPath) }
    }

    override suspend fun fetchVideos(folderPath: String?): List<Video> {
        return videos.filter { folderPath == null || it.path.startsWith(folderPath) }
    }

    override suspend fun getVideoByUri(uri: String): Video? {
        return videos.find { it.uriString == uri }
    }

    fun notifyMediaChanged() {
        updates.value += 1
    }

    override suspend fun getVideoState(uri: String): VideoState? {
        return null
    }

    override suspend fun getMediaInfo(uri: String): MediaInfo? {
        return null
    }

    override suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long) {
    }

    override suspend fun updateMediumPosition(uri: String, position: Long) {
    }

    override suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
    }

    override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
    }

    override suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
    }

    override suspend fun updateMediumZoom(uri: String, zoom: Float) {
    }

    override suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
    }

    override suspend fun updateSubtitleDelay(uri: String, delay: Long) {
    }

    override suspend fun updateSubtitleSpeed(uri: String, speed: Float) {
    }
}
