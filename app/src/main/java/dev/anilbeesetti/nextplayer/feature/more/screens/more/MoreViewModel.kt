package dev.anilbeesetti.nextplayer.feature.more.screens.more

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor() : ViewModel() {
    fun onAction(action: MoreAction, output: Output) {
        when (action) {
            is MoreAction.PlayVideo -> output.playVideo(action.uri)
            MoreAction.OpenSettings -> output.openSettings()
            MoreAction.OpenVault -> output.openVault()
        }
    }

    data class Output(
        val playVideo: (String) -> Unit,
        val openSettings: () -> Unit,
        val openVault: () -> Unit,
    )
}

sealed interface MoreAction {
    data class PlayVideo(val uri: String) : MoreAction
    data object OpenSettings : MoreAction
    data object OpenVault : MoreAction
}
