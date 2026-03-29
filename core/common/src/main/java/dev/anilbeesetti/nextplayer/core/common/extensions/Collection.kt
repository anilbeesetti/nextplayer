package dev.anilbeesetti.nextplayer.core.common.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Maps each element of the list asynchronously using coroutines.
 * All transformations run in parallel and results are collected in order.
 */
suspend inline fun <T, R> List<T>.mapAsync(crossinline transform: suspend (T) -> R): List<R> {
    return coroutineScope { map { async { transform(it) } }.awaitAll() }
}
