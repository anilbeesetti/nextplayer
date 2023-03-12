package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetSortedPlayerItemsUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase
) {

    operator fun invoke(): Flow<List<PlayerItem>> = getSortedVideosUseCase.invoke()
        .map { videos ->
            videos.map { video ->
                PlayerItem(
                    path = video.path,
                    duration = video.duration
                )
            }
        }
}

