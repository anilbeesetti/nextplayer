package com.graviton.core.domain

import com.graviton.core.data.repository.MediaRepository
import com.graviton.core.data.repository.PlaylistRepository
import com.graviton.core.model.Playlist
import com.graviton.core.model.PlaylistItem
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
                    videosByUri[item.uri]?.let { video ->
                        PlaylistItem(
                            position = item.position,
                            video = video,
                            lastPlayedAt = item.lastPlayedAt,
                        )
                    }
                }
                Playlist(
                    id = record.id,
                    name = record.name,
                    items = resolvedItems.mapIndexed { position, item ->
                        item.copy(position = position)
                    },
                )
            }
        }.distinctUntilChanged()
}
