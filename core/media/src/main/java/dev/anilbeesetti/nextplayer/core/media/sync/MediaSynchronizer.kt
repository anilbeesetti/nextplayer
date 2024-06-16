package dev.anilbeesetti.nextplayer.core.media.sync

interface MediaSynchronizer {
    suspend fun refresh(): Boolean
    fun startSync()
    fun stopSync()
}
