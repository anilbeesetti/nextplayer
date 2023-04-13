package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.domain.GetSortedFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.feature.videopicker.MediaState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedVideosUseCase: GetSortedVideosUseCase,
    getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val mediaState = preferencesRepository.appPreferencesFlow.flatMapLatest { appPreferences ->
        if (appPreferences.groupVideosByFolder) {
            getSortedFoldersUseCase.invoke()
                .map { MediaState.Success(it) }
        } else {
            getSortedVideosUseCase.invoke()
                .map { MediaState.Success(it) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MediaState.Loading
    )

    val preferences = preferencesRepository.appPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    fun updateMenu(sortBy: SortBy, sortOrder: SortOrder, groupVideosByFolder: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSortBy(sortBy)
            preferencesRepository.setSortOrder(sortOrder)
            preferencesRepository.setGroupVideosByFolder(groupVideosByFolder)
        }
    }
}
