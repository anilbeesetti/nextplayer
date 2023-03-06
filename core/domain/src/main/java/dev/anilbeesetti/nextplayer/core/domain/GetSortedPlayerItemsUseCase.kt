package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.repository.toPlayerItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSortedPlayerItemsUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase
) {

    operator fun invoke(): Flow<List<PlayerItem>> = getSortedVideosUseCase.invoke()
        .map { videos ->
            videos.map { it.toPlayerItem() }
        }
}
