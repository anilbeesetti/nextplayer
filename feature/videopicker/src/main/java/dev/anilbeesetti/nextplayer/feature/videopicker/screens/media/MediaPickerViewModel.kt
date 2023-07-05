package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedDirectoriesUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedVideosUseCase: GetSortedVideosUseCase,
    getSortedDirectoriesUseCase: GetSortedDirectoriesUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val videosState = getSortedVideosUseCase.invoke()
        .map { VideosState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideosState.Loading
        )

    val foldersState = getSortedDirectoriesUseCase.invoke()
        .map { FoldersState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FoldersState.Loading
        )

    val preferences = preferencesRepository.applicationPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ApplicationPreferences()
        )

    fun updateMenu(sortBy: SortBy, sortOrder: SortOrder, groupVideosByFolder: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    groupVideosByFolder = groupVideosByFolder
                )
            }
        }
    }
}
