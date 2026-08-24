package dev.anilbeesetti.nextplayer.feature.playlist

import android.net.Uri
import androidx.activity.ComponentActivity
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylist
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal data class CreateM3UCall(
    val type: PlaylistType,
    val source: String,
    val playlist: M3UPlaylist,
)

internal class FakePlaylistRepository : PlaylistRepository {
    val playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlist = MutableStateFlow<PlaylistRecord?>(null)
    val createM3UCalls = mutableListOf<CreateM3UCall>()
    val replacementCalls = mutableListOf<Pair<Long, List<M3UPlaylistItem>>>()
    var createM3UFailure: Throwable? = null
    var replaceFailure: Throwable? = null
    var createdId: Long = 42

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlists

    override fun observePlaylist(playlistId: Long): Flow<PlaylistRecord?> = playlist

    override suspend fun getPlaylist(playlistId: Long): PlaylistRecord? = playlist.value

    override suspend fun create(name: String, videoUris: List<String>): Long = createdId

    override suspend fun createM3U(
        type: PlaylistType,
        source: String,
        playlist: M3UPlaylist,
    ): Long {
        createM3UCalls += CreateM3UCall(type, source, playlist)
        createM3UFailure?.let { throw it }
        return createdId
    }

    override suspend fun replaceM3UItems(
        playlistId: Long,
        items: List<M3UPlaylistItem>,
    ) {
        replacementCalls += playlistId to items
        replaceFailure?.let { throw it }
    }

    override suspend fun rename(playlistId: Long, name: String) = Unit

    override suspend fun delete(playlistId: Long) = Unit

    override suspend fun addVideos(playlistId: Long, videoUris: List<String>): Int = 0

    override suspend fun removeVideo(playlistId: Long, videoUri: String) = Unit

    override suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>) = Unit

    override suspend fun markVideoPlayed(playlistId: Long, videoUri: String) = Unit

    override suspend fun countFilePlaylistsBySource(source: String): Int = 0
}

internal class FakeSystemService : SystemService {
    val toasts = mutableListOf<String>()

    override fun initialize(activity: ComponentActivity) = Unit

    override suspend fun pickFolder(): Uri? = null

    override fun getString(stringResId: Int): String = "string-$stringResId"

    override fun getQuantityString(
        pluralsResId: Int,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = "plurals-$pluralsResId-$quantity"

    override fun showToast(text: String, duration: Int) {
        toasts += text
    }
}
