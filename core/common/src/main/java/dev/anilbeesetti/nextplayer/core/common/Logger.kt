package dev.anilbeesetti.nextplayer.core.common

import android.util.Log

object Logger {

    fun logDebug(tag: String, message: String) {
        Log.d("Logger - $tag", message)
    }

    fun logInfo(tag: String, message: String) {
        Log.i("Logger - $tag", message)
    }

    fun logError(tag: String, message: String) {
        Log.e("Logger - $tag", message)
    }
}
