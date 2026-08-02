package dev.anilbeesetti.nextplayer.core.common.service.system

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes

interface SystemService {
    fun initialize(activity: ComponentActivity)
    suspend fun pickFolder(): Uri?
    fun getString(@StringRes stringResId: Int): String
    fun showToast(text: String, duration: Int)
}
