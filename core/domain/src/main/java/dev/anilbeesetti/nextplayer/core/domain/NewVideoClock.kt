package dev.anilbeesetti.nextplayer.core.domain

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun newVideoClock(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60.seconds)
    }
}
