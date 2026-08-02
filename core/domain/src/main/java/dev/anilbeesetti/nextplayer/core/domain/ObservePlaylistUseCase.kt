package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObservePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
) {

    operator fun invoke(playlistId: Long): Flow<Playlist?> =
        combine(
            playlistRepository.observePlaylist(playlistId),
            mediaRepository.observeVideos(),
        ) { record, videos ->
            record?.let {
                val videosByUri = videos.associateBy { video -> video.uriString }
                val resolvedVideos = record.orderedUris.mapNotNull(videosByUri::get)
                Playlist(
                    id = record.id,
                    name = record.name,
                    items = resolvedVideos.mapIndexed { position, video ->
                        PlaylistItem(position = position, video = video)
                    },
                )
            }
        }.distinctUntilChanged()
}
