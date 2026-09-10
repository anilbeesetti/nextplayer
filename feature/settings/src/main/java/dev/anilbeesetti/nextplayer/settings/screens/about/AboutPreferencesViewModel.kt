package dev.anilbeesetti.nextplayer.settings.screens.about

import android.content.Context
import android.os.Build
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel(assistedFactory = AboutPreferencesViewModel.Factory::class)
class AboutPreferencesViewModel @AssistedInject constructor(
    @ApplicationContext context: Context,
    @Assisted internal var output: Output,
) : MviViewModel<AboutPreferencesUiState, AboutPreferencesAction>() {

    data class Output(
        val navigateUp: () -> Unit,
        val openLibraries: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): AboutPreferencesViewModel
    }

    private val stateInternal = MutableStateFlow(AboutPreferencesUiState(appVersion = context.appVersion()))
    override val state: StateFlow<AboutPreferencesUiState> = stateInternal.asStateFlow()

    override fun onAction(action: AboutPreferencesAction) {
        when (action) {
            is AboutPreferencesAction.NavigateUp -> output.navigateUp()
            is AboutPreferencesAction.OpenLibraries -> output.openLibraries()
        }
    }
}

data class AboutPreferencesUiState(val appVersion: String)

sealed interface AboutPreferencesAction {
    data object NavigateUp : AboutPreferencesAction
    data object OpenLibraries : AboutPreferencesAction
}

private fun Context.appVersion(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)

    @Suppress("DEPRECATION")
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode
    }

    return "${packageInfo.versionName} ($versionCode)"
}
