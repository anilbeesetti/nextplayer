package dev.anilbeesetti.nextplayer.core.common.cache

import android.content.Context
import java.io.File

object StreamCacheStorage {
    private const val ROOT_DIR_NAME = "stream_cache"

    fun rootDir(context: Context): File = File(context.cacheDir, ROOT_DIR_NAME)

    fun sizeBytes(context: Context): Long {
        val root = rootDir(context)
        if (!root.exists()) return 0L
        return root.walkBottomUp()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun clear(context: Context) {
        rootDir(context).deleteRecursively()
    }
}
