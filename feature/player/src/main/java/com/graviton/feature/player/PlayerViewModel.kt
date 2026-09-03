package com.graviton.feature.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.graviton.core.data.repository.MediaRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.domain.GetSortedPlaylistUseCase
import com.graviton.core.model.DecoderMode
import com.graviton.core.model.LoopMode
import com.graviton.core.model.MediaChapter
import com.graviton.core.model.MediaInfo
import com.graviton.core.model.PlayerPreferences
import com.graviton.core.model.ScreenOrientation
import com.graviton.core.model.Video
import com.graviton.core.model.VideoBookmark
import com.graviton.core.model.VideoContentScale
import com.graviton.feature.player.chapters.ChapterSource
import com.graviton.feature.player.state.SubtitleOptionsEvent
import com.graviton.feature.player.state.VideoZoomEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
) : ViewModel() {

    var playWhenReady: Boolean = true

    private val internalUiState = MutableStateFlow(
        PlayerUiState(
            playerPreferences = preferencesRepository.playerPreferences.value,
            bookmarks = preferencesRepository.applicationPreferences.value.videoBookmarks,
            isTutorialShown = preferencesRepository.applicationPreferences.value.playerTutorialShown,
        ),
    )
    val uiState = internalUiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                internalUiState.update { it.copy(playerPreferences = prefs) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                internalUiState.update {
                    it.copy(
                        bookmarks = prefs.videoBookmarks,
                        isTutorialShown = prefs.playerTutorialShown,
                    )
                }
            }
        }
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun updateVideoZoom(uri: String, zoom: Float) {
        viewModelScope.launch {
            mediaRepository.updateMediumZoom(uri, zoom)
        }
    }

    fun updatePlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    fun updateVideoContentScale(contentScale: VideoContentScale) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = contentScale) }
        }
    }

    fun setLoopMode(loopMode: LoopMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(loopMode = loopMode) }
        }
    }

    fun onVideoZoomEvent(event: VideoZoomEvent) {
        when (event) {
            is VideoZoomEvent.ContentScaleChanged -> {
                updateVideoContentScale(event.contentScale)
            }
            is VideoZoomEvent.ZoomChanged -> {
                updateVideoZoom(event.mediaItem.mediaId, event.zoom)
            }
        }
    }

    fun updateDecoderMode(decoderMode: DecoderMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(decoderMode = decoderMode)
            }
        }
    }

    fun updateAutoPip(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(autoPip = enabled) }
        }
    }

    fun updateScreenOrientation(orientation: ScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerScreenOrientation = orientation) }
        }
    }

    fun updateLongPressSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(longPressControlsSpeed = speed, useLongPressControls = true)
            }
        }
    }

    fun updatePanGesture(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(enablePanGesture = enabled) }
        }
    }

    fun updateControllerAutoHideTimeout(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(controllerAutoHideTimeout = seconds) }
        }
    }

    fun onSubtitleOptionEvent(event: SubtitleOptionsEvent) {
        when (event) {
            is SubtitleOptionsEvent.DelayChanged -> {
                updateSubtitleDelay(event.mediaItem.mediaId, event.delay)
            }
            is SubtitleOptionsEvent.SpeedChanged -> {
                updateSubtitleSpeed(event.mediaItem.mediaId, event.speed)
            }
        }
    }

    private fun updateSubtitleDelay(uri: String, delay: Long) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleDelay(uri, delay)
        }
    }

    private fun updateSubtitleSpeed(uri: String, speed: Float) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleSpeed(uri, speed)
        }
    }

    /**
     * Loads the chapter sidecar and container info for [mediaId].
     *
     * Both are read once per media item and cached in the UI state, so opening the chapter or
     * information sheet repeatedly does no extra work.
     */
    fun loadMediaDetails(mediaId: String) {
        if (internalUiState.value.detailsMediaId == mediaId) return
        internalUiState.update {
            it.copy(detailsMediaId = mediaId, mediaInfo = null, chapters = emptyList(), isLoadingDetails = true)
        }
        viewModelScope.launch {
            val chapters = runCatching { ChapterSource.chaptersFor(context, mediaId.toUri()) }
                .getOrDefault(emptyList())
            val mediaInfo = runCatching { mediaRepository.getMediaInfo(mediaId) }.getOrNull()
            internalUiState.update { current ->
                // A media item transition while loading must not overwrite the newer request.
                if (current.detailsMediaId != mediaId) return@update current
                current.copy(chapters = chapters, mediaInfo = mediaInfo, isLoadingDetails = false)
            }
        }
    }

    fun addBookmark(mediaId: String, positionMs: Long, label: String) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences ->
                val existing = preferences.videoBookmarks[mediaId].orEmpty()
                if (existing.size >= VideoBookmark.MAX_BOOKMARKS_PER_MEDIA) return@updateApplicationPreferences preferences
                val updated = (
                    existing + VideoBookmark(
                        positionMs = positionMs,
                        label = label.trim(),
                        createdAt = System.currentTimeMillis(),
                    )
                    ).sortedBy { it.positionMs }
                preferences.copy(videoBookmarks = preferences.videoBookmarks + (mediaId to updated))
            }
        }
    }

    fun deleteBookmark(mediaId: String, bookmark: VideoBookmark) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences ->
                val updated = preferences.videoBookmarks[mediaId].orEmpty().filterNot {
                    it.positionMs == bookmark.positionMs && it.createdAt == bookmark.createdAt
                }
                val bookmarks = if (updated.isEmpty()) {
                    preferences.videoBookmarks - mediaId
                } else {
                    preferences.videoBookmarks + (mediaId to updated)
                }
                preferences.copy(videoBookmarks = bookmarks)
            }
        }
    }

    fun setTutorialShown(shown: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { it.copy(playerTutorialShown = shown) }
        }
    }
}

@Stable
data class PlayerUiState(
    val playerPreferences: PlayerPreferences? = null,
    val bookmarks: Map<String, List<VideoBookmark>> = emptyMap(),
    val isTutorialShown: Boolean = false,
    val detailsMediaId: String? = null,
    val chapters: List<MediaChapter> = emptyList(),
    val mediaInfo: MediaInfo? = null,
    val isLoadingDetails: Boolean = false,
)
