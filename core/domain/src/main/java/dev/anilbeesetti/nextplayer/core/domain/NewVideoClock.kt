package dev.anilbeesetti.nextplayer.core.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

internal fun newVideoClock(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60.seconds)
    }
}
