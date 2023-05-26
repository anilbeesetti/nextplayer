package dev.anilbeesetti.nextplayer.feature.player.utils

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem

class PlaylistManager {

    private val queue = mutableListOf<PlayerItem>()
    private var currentItem: PlayerItem? = null

    /**
     * Listener that gets called when the current playing video changes
     */
    private val onTrackChangedListeners: MutableList<(Uri) -> Unit> = mutableListOf()

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
                it.invoke(Uri.parse(item.uriString))
            }
        }
    }

    fun addOnTrackChangedListener(listener: (Uri) -> Unit) {
        onTrackChangedListeners.add(listener)
    }

    fun removeOnTrackChangedListener(listener: (Uri) -> Unit) {
        onTrackChangedListeners.remove(listener)
    }

    fun clearQueue() {
        queue.clear()
        currentItem = null
    }

    override fun toString(): String = buildString {
        append("########## playlist ##########\n")
        queue.forEach { append(it.path + "\n") }
        append("##############################")
    }
}
