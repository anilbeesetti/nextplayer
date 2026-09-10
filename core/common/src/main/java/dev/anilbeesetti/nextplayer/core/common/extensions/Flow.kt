package dev.anilbeesetti.nextplayer.core.common.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Collects while [state] has subscribers, allowing five seconds for a screen to return.
 * Uses the same policy as stateIn(WhileSubscribed(5_000)) without adding a permanent
 * subscriber to the mutable state or delaying its direct value updates.
 */
fun <T> Flow<T>.collectWhileSubscribed(
    scope: CoroutineScope,
    state: MutableStateFlow<*>,
    action: suspend (T) -> Unit,
): Job = scope.launch {
    SharingStarted.WhileSubscribed(5_000)
        .command(state.subscriptionCount)
        .collectLatest { command ->
            if (command == SharingCommand.START) {
                this@collectWhileSubscribed.collect(action)
            }
        }
}
