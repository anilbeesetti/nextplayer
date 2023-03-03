package dev.anilbeesetti.nextplayer.core.common

import android.content.res.Resources
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Utility functions.
 */
object Utils {

    /**
     * Converts px to dp.
     */
    fun pxToDp(px: Float): Float {
        return px / Resources.getSystem().displayMetrics.density
    }

    /**
     * Formats the given duration in milliseconds to a string in the format of `mm:ss` or `hh:mm:ss`.
     */
    fun formatDurationMillis(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
            TimeUnit.MINUTES.toSeconds(minutes) -
            TimeUnit.HOURS.toSeconds(hours)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats the given duration in milliseconds to a string in the format of
     * `+mm:ss` or `+hh:mm:ss` or `-mm:ss` or `-hh:mm:ss`.
     */
    fun formatDurationMillisSign(millis: Long): String {
        return if (millis >= 0) {
            "+${formatDurationMillis(millis)}"
        } else {
            "-${formatDurationMillis(abs(millis))}"
        }
    }
}
