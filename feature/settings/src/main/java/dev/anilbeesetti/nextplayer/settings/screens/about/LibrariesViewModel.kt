package dev.anilbeesetti.nextplayer.settings.screens.about

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class LibrariesViewModel(
    context: Context,
    @InjectedParam internal var output: Output,
) : MviViewModel<LibrariesUiState, LibrariesAction>() {

    data class Output(val navigateUp: () -> Unit)

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
