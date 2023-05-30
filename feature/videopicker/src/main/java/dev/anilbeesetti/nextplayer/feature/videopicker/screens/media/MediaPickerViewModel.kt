package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.AppPrefs
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedVideosUseCase: GetSortedVideosUseCase,
    getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val videosState = getSortedVideosUseCase.invoke()
        .map { VideosState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideosState.Loading
        )

    val foldersState = getSortedFoldersUseCase.invoke()
        .map { FoldersState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FoldersState.Loading
        )

    val preferences = preferencesRepository.appPrefsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPrefs.default()
        )

    fun updateMenu(sortBy: SortBy, sortOrder: SortOrder, groupVideosByFolder: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSortBy(sortBy)
            preferencesRepository.setSortOrder(sortOrder)
            preferencesRepository.setGroupVideosByFolder(groupVideosByFolder)
        }
    }
}
