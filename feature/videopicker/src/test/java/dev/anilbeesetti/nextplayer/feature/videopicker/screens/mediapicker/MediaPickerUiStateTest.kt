package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPickerUiStateTest {
    private val folder = Folder.sample.copy(path = "/storage/Movies")
    private val video = Video.sample.copy(path = "/storage/Movies/sample.mp4")
    private val media = DataState.Success(MediaHolder(videos = emptyList(), folders = listOf(folder)))

    @Test
    fun `recent folder is resolved regardless of which source emits first`() {
        val initial = MediaPickerUiState(folderName = null)
        val videoFirst = initial.copy(recentlyPlayedVideo = video)
        assertNull(videoFirst.recentlyPlayedFolder)
        assertEquals(folder, videoFirst.copy(mediaDataState = media).recentlyPlayedFolder)

        val mediaFirst = initial.copy(mediaDataState = media)
        assertNull(mediaFirst.recentlyPlayedFolder)
        assertEquals(folder, mediaFirst.copy(recentlyPlayedVideo = video).recentlyPlayedFolder)
    }

    @Test
    fun `recent folder tracks changes to either source`() {
        val state = MediaPickerUiState(folderName = null, recentlyPlayedVideo = video, mediaDataState = media)
        assertNull(state.copy(recentlyPlayedVideo = null).recentlyPlayedFolder)
        assertNull(state.copy(recentlyPlayedVideo = video.copy(path = "/storage/Elsewhere/sample.mp4")).recentlyPlayedFolder)
        assertNull(state.copy(mediaDataState = DataState.Success(null)).recentlyPlayedFolder)
        assertNull(state.copy(mediaDataState = DataState.Success(MediaHolder(emptyList(), emptyList()))).recentlyPlayedFolder)
    }
}
