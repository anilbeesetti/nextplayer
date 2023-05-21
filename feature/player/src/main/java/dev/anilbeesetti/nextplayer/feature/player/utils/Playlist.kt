package dev.anilbeesetti.nextplayer.feature.player.utils

import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import timber.log.Timber


class Playlist {

    private val queue = mutableListOf<PlayerItem>()
    private var currentItem: PlayerItem? = null

    /**
     * Listener that gets called when the current playing video changes
     */
    private val onTrackChangedListeners: MutableList<(PlayerItem) -> Unit> = mutableListOf()

    fun clear() = queue.clear()

    fun hasNext(): Boolean {
        return currentIndex() + 1 < size()
    }

    fun hasPrev(): Boolean {
        return currentIndex() > 0
    }

    fun getNext(): PlayerItem? = queue.getOrNull(currentIndex() + 1)

    fun getPrev(): PlayerItem? = queue.getOrNull(currentIndex() - 1)

    fun size() = queue.size

    fun currentIndex(): Int = queue.indexOfFirst {
        it.path == currentItem?.path
    }.takeIf { it >= 0 } ?: 0

    fun isNotEmpty() = queue.isNotEmpty()

    fun isEmpty() = queue.isEmpty()

    fun getCurrent(): PlayerItem? = currentItem

    fun setPlayerItems(items: List<PlayerItem>) {
        queue.clear()
        queue.addAll(items)
    }

    fun updateCurrent(item: PlayerItem) {
        currentItem = item
        onTrackChangedListeners.forEach {
            runCatching {
                it.invoke(item)
            }
        }
    }

    fun addOnTrackChangedListener(listener: (PlayerItem) -> Unit) {
        onTrackChangedListeners.add(listener)
    }

    fun removeOnTrackChangedListener(listener: (PlayerItem) -> Unit) {
        onTrackChangedListeners.remove(listener)
    }

    fun printPlaylist() {
        Timber.d("########## playlist ##########")
        queue.forEach {
            Timber.d(it.path)
        }
        Timber.d("###############################")
    }
}