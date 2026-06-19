package dev.anilbeesetti.nextplayer.core.media.extensions

import coil3.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun ImageLoader.clearAllCache() = withContext(Dispatchers.Default) {
    diskCache?.clear()
    memoryCache?.clear()
}
