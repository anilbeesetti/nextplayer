package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
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
                val resolvedItems = record.items.mapNotNull { item ->
                    val video = videosByUri[item.uri]
                    if (record.type == PlaylistType.LOCAL && video == null) return@mapNotNull null
                    PlaylistItem(
                        position = item.position,
                        uri = item.uri,
                        title = item.title,
                        tvgLogo = item.tvgLogo,
                        duration = item.duration,
                        groupTitle = item.groupTitle,
                        video = video,
                        lastPlayedAt = item.lastPlayedAt,
                    )
                }
                Playlist(
                    id = record.id,
                    name = record.name,
                    type = record.type,
                    source = record.source,
                    items = resolvedItems.mapIndexed { position, item ->
                        item.copy(position = position)
                    },
                    lastRefreshedAt = record.lastRefreshedAt,
                )
            }
        }.distinctUntilChanged()
}
