package dev.anilbeesetti.nextplayer.core.media.services

internal inline fun <T> runMediaStoreQuery(query: () -> List<T>): List<T> {
    return try {
        query()
    } catch (_: SecurityException) {
        emptyList()
    }
}
