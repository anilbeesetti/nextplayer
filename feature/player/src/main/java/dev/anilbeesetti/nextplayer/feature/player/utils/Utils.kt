package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.res.Resources
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object Utils {

    fun pxToDp(px: Float): Float {
        return px / Resources.getSystem().displayMetrics.density
    }

    fun formatMillis(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatMillisSign(millis: Long): String {
        return if (millis >= 0) {
            "+${formatMillis(millis)}"
        } else {
            "-${formatMillis(abs(millis))}"
        }
    }
}
