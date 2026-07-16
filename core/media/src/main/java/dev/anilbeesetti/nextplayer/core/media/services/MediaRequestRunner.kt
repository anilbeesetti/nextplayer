package dev.anilbeesetti.nextplayer.core.media.services

internal const val MAX_MEDIA_REQUEST_URIS = 2_000

internal suspend fun <T> runMediaRequests(
    items: List<T>,
    maxBatchSize: Int = MAX_MEDIA_REQUEST_URIS,
    itemExists: suspend (T) -> Boolean,
    request: suspend (List<T>) -> Boolean,
): Boolean {
    require(maxBatchSize > 0)

    for (batch in items.distinct().chunked(maxBatchSize)) {
        if (!runMediaRequestBatch(batch, itemExists, request)) return false
    }
    return true
}

private suspend fun <T> runMediaRequestBatch(
    batch: List<T>,
    itemExists: suspend (T) -> Boolean,
    request: suspend (List<T>) -> Boolean,
): Boolean {
    var candidates = batch
    while (candidates.isNotEmpty()) {
        try {
            return request(candidates)
        } catch (_: IllegalArgumentException) {
            val existingItems = candidates.filter { itemExists(it) }
            if (existingItems.size == candidates.size) return false
            candidates = existingItems
        }
    }
    return true
}
