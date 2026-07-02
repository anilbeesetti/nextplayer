package dev.anilbeesetti.nextplayer.core.common.service.system

import android.net.Uri
import androidx.activity.ComponentActivity

interface SystemService {
    fun initialize(activity: ComponentActivity)
    suspend fun pickFolder(): Uri?
}
