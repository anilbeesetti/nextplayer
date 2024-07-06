package dev.anilbeesetti.nextplayer.core.media.sync

interface MediaSynchronizer {
    suspend fun refresh(path: String? = null): Boolean
    fun startSync()
    fun stopSync()
}
