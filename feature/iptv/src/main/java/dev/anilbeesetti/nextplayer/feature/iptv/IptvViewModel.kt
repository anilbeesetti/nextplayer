package dev.anilbeesetti.nextplayer.feature.iptv

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.ImportResult
import dev.anilbeesetti.nextplayer.core.data.repository.IptvRepository
import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.core.model.IptvPlaylist
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class IptvViewModel @Inject constructor(
    private val iptvRepository: IptvRepository,
) : ViewModel() {

    private val selectedPlaylistId = MutableStateFlow<Long?>(null)

    private val uiStateInternal = MutableStateFlow(IptvUiState())

    val uiState = combine(
        iptvRepository.observePlaylists(),
        iptvRepository.observeAllChannels(),
        selectedPlaylistId,
        uiStateInternal,
    ) { playlists, channels, selectedId, internal ->
        val visibleChannels = if (selectedId == null) {
            channels
        } else {
            channels.filter { it.playlistId == selectedId }
        }
        internal.copy(
            playlists = playlists,
            selectedPlaylistId = selectedId,
            channels = visibleChannels.toChannelListItems(),
            isEmpty = playlists.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IptvUiState(),
    )

    private val eventsInternal = Channel<IptvEvent>()
    val events = eventsInternal.receiveAsFlow()

    fun onAction(action: IptvAction) {
        when (action) {
            is IptvAction.ImportUrl -> importUrl(action.url, action.name)
            is IptvAction.ImportContent -> importContent(action.content, action.name, action.source)
            is IptvAction.SelectPlaylist -> selectedPlaylistId.update { action.playlistId }
            is IptvAction.DeletePlaylist -> deletePlaylist(action.playlistId)
            is IptvAction.RefreshPlaylist -> refreshPlaylist(action.playlist)
            is IptvAction.PlayChannel -> playChannel(action.channel)
        }
    }

    private fun importUrl(url: String, name: String?) {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isImporting = true) }
            handleResult(iptvRepository.importFromUrl(url, name))
        }
    }

    private fun importContent(content: String, name: String, source: String) {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isImporting = true) }
            handleResult(iptvRepository.importFromContent(content, name, source))
        }
    }

    private fun refreshPlaylist(playlist: IptvPlaylist) {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isImporting = true) }
            handleResult(iptvRepository.refreshPlaylist(playlist))
        }
    }

    private fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (selectedPlaylistId.value == playlistId) selectedPlaylistId.update { null }
            iptvRepository.deletePlaylist(playlistId)
        }
    }

    private fun playChannel(channel: IptvChannel) {
        viewModelScope.launch {
            eventsInternal.send(IptvEvent.PlayChannel(channel))
        }
    }

    private suspend fun handleResult(result: ImportResult) {
        uiStateInternal.update { it.copy(isImporting = false) }
        when (result) {
            is ImportResult.Success -> eventsInternal.send(
                IptvEvent.ImportSucceeded(result.channelCount),
            )
            is ImportResult.Error -> eventsInternal.send(IptvEvent.ImportFailed(result.message))
        }
    }
}

@Stable
data class IptvUiState(
    val playlists: List<IptvPlaylist> = emptyList(),
    val channels: List<ChannelListItem> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val isImporting: Boolean = false,
    val isEmpty: Boolean = false,
)

sealed interface IptvAction {
    data class ImportUrl(val url: String, val name: String?) : IptvAction
    data class ImportContent(val content: String, val name: String, val source: String) : IptvAction
    data class SelectPlaylist(val playlistId: Long?) : IptvAction
    data class DeletePlaylist(val playlistId: Long) : IptvAction
    data class RefreshPlaylist(val playlist: IptvPlaylist) : IptvAction
    data class PlayChannel(val channel: IptvChannel) : IptvAction
}

sealed interface IptvEvent {
    data class PlayChannel(val channel: IptvChannel) : IptvEvent
    data class ImportSucceeded(val channelCount: Int) : IptvEvent
    data class ImportFailed(val message: String) : IptvEvent
}
