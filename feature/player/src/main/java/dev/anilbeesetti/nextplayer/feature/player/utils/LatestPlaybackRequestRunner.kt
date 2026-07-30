package dev.anilbeesetti.nextplayer.feature.player.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LatestPlaybackRequestRunner(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun submit(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
