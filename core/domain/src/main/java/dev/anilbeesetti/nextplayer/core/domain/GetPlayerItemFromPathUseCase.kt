package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import javax.inject.Inject

class GetPlayerItemFromPathUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {

    operator fun invoke(path: String?): PlayerItem? {
        return path?.let { videoRepository.getVideo(it) }?.toPlayerItem()
    }

}

fun Video.toPlayerItem() = PlayerItem(
    path = path,
    uriString = uriString,
    duration = duration
)