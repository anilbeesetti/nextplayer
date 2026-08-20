package com.graviton.core.domain

import com.graviton.core.common.Dispatcher
import com.graviton.core.common.NextDispatchers
import com.graviton.core.data.repository.VaultRepository
import com.graviton.core.model.Sort
import com.graviton.core.model.Video
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
