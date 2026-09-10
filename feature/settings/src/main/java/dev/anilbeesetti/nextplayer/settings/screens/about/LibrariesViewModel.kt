package dev.anilbeesetti.nextplayer.settings.screens.about

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel(assistedFactory = LibrariesViewModel.Factory::class)
class LibrariesViewModel @AssistedInject constructor(
    @ApplicationContext context: Context,
    @Assisted internal var output: Output,
) : MviViewModel<LibrariesUiState, LibrariesAction>() {

    data class Output(val navigateUp: () -> Unit)

    @AssistedFactory
    interface Factory {
        fun create(output: Output): LibrariesViewModel
    }

    private val stateInternal = MutableStateFlow(
        LibrariesUiState(libraries = Libs.Builder().withContext(context).build().libraries),
    )
    override val state: StateFlow<LibrariesUiState> = stateInternal.asStateFlow()

    override fun onAction(action: LibrariesAction) {
        when (action) {
            is LibrariesAction.NavigateUp -> output.navigateUp()
        }
    }
}

data class LibrariesUiState(val libraries: List<Library>)

sealed interface LibrariesAction {
    data object NavigateUp : LibrariesAction
}
