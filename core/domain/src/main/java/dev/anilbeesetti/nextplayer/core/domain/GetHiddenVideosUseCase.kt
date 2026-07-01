package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Returns videos currently hidden in the vault, sorted by the given [Sort] criteria.
 */
class GetHiddenVideosUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(sort: Sort): Flow<List<Video>> {
        return vaultRepository.observeHiddenVideos()
            .map { videos -> videos.sortedWith(sort.videoComparator()) }
            .flowOn(defaultDispatcher)
    }
}
