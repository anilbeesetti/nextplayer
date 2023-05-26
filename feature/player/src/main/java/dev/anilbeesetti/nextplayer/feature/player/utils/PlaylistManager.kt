package dev.anilbeesetti.nextplayer.feature.player.utils

import android.net.Uri

class PlaylistManager {

    private val queue = mutableListOf<Uri>()
    private var currentItem: Uri? = null

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

    fun getNext(): Uri? = queue.getOrNull(currentIndex() + 1)

    fun getPrev(): Uri? = queue.getOrNull(currentIndex() - 1)

    fun size() = queue.size

    fun currentIndex(): Int = queue.indexOfFirst { it == currentItem }.takeIf { it >= 0 } ?: 0

    fun isNotEmpty() = queue.isNotEmpty()

    fun isEmpty() = queue.isEmpty()

    fun getCurrent(): Uri? = currentItem

    fun setPlaylist(items: List<Uri>) {
        queue.clear()
        queue.addAll(items)
    }

    fun updateCurrent(uri: Uri) {
        currentItem = uri
        onTrackChangedListeners.forEach {
            runCatching {
                it.invoke(uri)
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
        queue.forEach { append(it.toString() + "\n") }
        append("##############################")
    }
}
