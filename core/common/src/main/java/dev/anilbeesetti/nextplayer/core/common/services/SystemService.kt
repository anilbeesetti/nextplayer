package dev.anilbeesetti.nextplayer.core.common.services

import android.app.Activity
import androidx.annotation.StringRes

interface SystemService {
    fun initialize(activity: Activity)
    fun getString(@StringRes id: Int): String
    fun getString(id: Int, vararg formatArgs: Any): String
    fun showToast(message: String, showLong: Boolean = false)
    fun showToast(@StringRes id: Int, showLong: Boolean = false) = showToast(getString(id), showLong)
    fun versionName(): String
}
