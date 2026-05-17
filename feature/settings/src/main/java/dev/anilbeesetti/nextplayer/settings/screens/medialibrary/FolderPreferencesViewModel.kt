package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FolderPreferencesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        FolderPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState: StateFlow<FolderPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                mediaRepository.getFoldersFlow(),
                preferencesRepository.applicationPreferences,
            ) { folders, preferences ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        foldersDataState = DataState.Success(folders),
                        preferences = preferences,
                        manualFolders = preferences.manuallySelectedFolders.map { uri ->
                            ManualFolder(uriString = uri, displayPath = uri.toTreeDisplayPath())
                        },
                    )
                }
            }.collect {}
        }
    }

    fun onEvent(event: FolderPreferencesUiEvent) {
        when (event) {
            is FolderPreferencesUiEvent.UpdateExcludeList -> updateExcludeList(event.path)
            is FolderPreferencesUiEvent.AddFolder -> addFolder(event.uri)
            is FolderPreferencesUiEvent.RemoveFolder -> removeFolder(event.uriString)
        }
    }

    private fun updateExcludeList(path: String) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    excludeFolders = if (path in it.excludeFolders) {
                        it.excludeFolders - path
                    } else {
                        it.excludeFolders + path
                    },
                )
            }
        }
    }

    private fun addFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            preferencesRepository.updateApplicationPreferences {
                if (uri.toString() in it.manuallySelectedFolders) {
                    it
                } else {
                    it.copy(manuallySelectedFolders = it.manuallySelectedFolders + uri.toString())
                }
            }
        }
    }

    private fun removeFolder(uriString: String) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            preferencesRepository.updateApplicationPreferences {
                it.copy(manuallySelectedFolders = it.manuallySelectedFolders - uriString)
            }
        }
    }
}

// Turns a SAF tree URI into a readable "/Movies/.hidden" style path.
private fun String.toTreeDisplayPath(): String = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(Uri.parse(this))
    val relative = docId.substringAfter(':', "")
    if (relative.isEmpty()) docId else "/$relative"
}.getOrDefault(this)

data class ManualFolder(
    val uriString: String,
    val displayPath: String,
)

data class FolderPreferencesUiState(
    val foldersDataState: DataState<List<Folder>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val manualFolders: List<ManualFolder> = emptyList(),
)

sealed interface FolderPreferencesUiEvent {
    data class UpdateExcludeList(val path: String) : FolderPreferencesUiEvent
    data class AddFolder(val uri: Uri) : FolderPreferencesUiEvent
    data class RemoveFolder(val uriString: String) : FolderPreferencesUiEvent
}
