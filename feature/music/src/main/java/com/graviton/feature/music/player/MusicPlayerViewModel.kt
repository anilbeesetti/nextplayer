package com.graviton.feature.music.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.graviton.core.data.repository.MusicRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.toggleMusicFavorite
import com.graviton.feature.music.lyrics.LyricsDocument
import com.graviton.feature.music.lyrics.LyricsParser
import com.graviton.feature.music.lyrics.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val lyricsInternal = MutableStateFlow(LyricsParser.parse(null))
    val lyrics = lyricsInternal.asStateFlow()
    val preferences = preferencesRepository.applicationPreferences

    /** Toggles the favourite flag for the item currently playing. */
    fun toggleFavorite(uriString: String?) {
        val uri = uriString?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { it.toggleMusicFavorite(uri) }
        }
    }

    fun loadLyrics(uriString: String?, title: String?) {
        viewModelScope.launch {
            if (!preferencesRepository.applicationPreferences.value.musicShowLyrics) {
                lyricsInternal.value = LyricsDocument(emptyList(), null)
                return@launch
            }
            val track = uriString?.let { musicRepository.getTrack(it) }
            lyricsInternal.value = lyricsRepository.load(
                com.graviton.feature.music.lyrics.LyricsRequest(
                    mediaUri = uriString.orEmpty(),
                    filePath = track?.path,
                    title = title.orEmpty().ifBlank { track?.displayTitle.orEmpty() },
                    artist = track?.displayArtist.orEmpty(),
                    album = track?.displayAlbum,
                    durationMs = track?.duration,
                ),
            )
            if (!lyricsInternal.value.isSynced && lyricsInternal.value.unsynced == null && !title.isNullOrBlank()) {
                lyricsInternal.value = LyricsDocument(emptyList(), null)
            }
        }
    }

}
